package com.tms.agent.device;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import com.tms.agent.receiver.PackageInstallReceiver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Relie les résultats asynchrones de PackageInstaller (reçus par broadcast) à l'appel
 * bloquant qui les attend.
 */
public final class InstallResults {

    public static final String ACTION = "com.tms.agent.action.INSTALL_RESULT";
    private static final String EXTRA_TOKEN = "tms_token";
    private static final String TAG = "TmsInstall";

    private static final AtomicInteger SEQ = new AtomicInteger((int) (System.currentTimeMillis() & 0xFFFF));
    private static final Map<Integer, Pending> PENDING = new ConcurrentHashMap<>();

    private InstallResults() {
    }

    public static Pending register() {
        Pending p = new Pending(SEQ.incrementAndGet());
        PENDING.put(p.token, p);
        return p;
    }

    static IntentSender sender(Context context, Pending pending) {
        Intent intent = new Intent(context, PackageInstallReceiver.class)
                .setAction(ACTION)
                .putExtra(EXTRA_TOKEN, pending.token);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE; // le système ajoute EXTRA_STATUS à l'intent
        }
        return PendingIntent.getBroadcast(context, pending.token, intent, flags).getIntentSender();
    }

    /** Appelé par {@link PackageInstallReceiver}. */
    public static void deliver(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Agent non privilégié : Android demande une confirmation à l'utilisateur
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                } catch (Exception e) {
                    Log.w(TAG, "Impossible d'afficher la confirmation d'installation", e);
                }
            }
            return;
        }
        Pending p = PENDING.remove(intent.getIntExtra(EXTRA_TOKEN, -1));
        if (p != null) {
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            p.complete(status == PackageInstaller.STATUS_SUCCESS
                    ? OpResult.ok("OK")
                    : OpResult.fail("Statut " + status + (msg != null ? " : " + msg : "")));
        }
    }

    public static final class Pending {
        final int token;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile OpResult result;

        Pending(int token) {
            this.token = token;
        }

        void complete(OpResult r) {
            result = r;
            latch.countDown();
        }

        public OpResult await(long timeout, TimeUnit unit) {
            try {
                if (latch.await(timeout, unit)) {
                    return result;
                }
                PENDING.remove(token);
                return OpResult.fail("Délai dépassé (confirmation utilisateur requise ?)");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return OpResult.fail("Interrompu");
            }
        }

        void cancel() {
            PENDING.remove(token);
        }
    }
}

package com.tms.agent.device;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.tms.agent.AgentApp;
import com.tms.agent.receiver.PackageInstallReceiver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Relie les résultats asynchrones de PackageInstaller (reçus par broadcast) à l'appel
 * bloquant qui les attend.
 * <p>
 * Sans Device Owner, Android demande une confirmation à l'écran. Depuis Android 10, une app
 * en arrière-plan ne peut pas ouvrir cette fenêtre elle-même : une notification prioritaire
 * « touchez pour confirmer » est donc affichée, et l'attente ne bloque plus l'agent (le résultat
 * définitif est remonté plus tard, voir {@link Pending#onLateResult}).
 */
public final class InstallResults {

    public static final String ACTION = "com.tms.agent.action.INSTALL_RESULT";
    private static final String EXTRA_TOKEN = "tms_token";
    private static final String TAG = "TmsInstall";
    /** Délai laissé à une confirmation rapide avant de rendre la main au cycle de synchronisation. */
    private static final long USER_ACTION_GRACE_MS = 15_000;

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
        int token = intent.getIntExtra(EXTRA_TOKEN, -1);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Pending p = PENDING.get(token);
            if (p != null) {
                p.userActionRequested = true;
            }
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                askUser(context, token, confirm);
            }
            return;
        }
        cancelNotification(context, token);
        Pending p = PENDING.remove(token);
        if (p != null) {
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            OpResult r;
            if (status == PackageInstaller.STATUS_SUCCESS) {
                r = OpResult.ok(p.userActionRequested ? "Confirmé sur le terminal" : "OK");
            } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                r = OpResult.fail("Refusé ou annulé sur l'écran du terminal");
            } else {
                r = OpResult.fail("Statut " + status + (msg != null ? " : " + msg : ""));
            }
            p.complete(r);
        }
    }

    /** Fenêtre de confirmation : ouverture directe si Android l'autorise, et notification dans tous les cas. */
    private static void askUser(Context context, int token, Intent confirm) {
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(confirm); // bloqué en arrière-plan depuis Android 10
        } catch (Exception e) {
            Log.w(TAG, "Ouverture directe de la confirmation impossible", e);
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent open = PendingIntent.getActivity(context, token, confirm, flags);
        NotificationCompat.Builder n = new NotificationCompat.Builder(context, AgentApp.CHANNEL_ACTIONS_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("TMS2M : installation à confirmer")
                .setContentText("Touchez pour confirmer la mise à jour demandée par le TMS")
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setOngoing(true);
        try {
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(token, n.build());
        } catch (Exception e) {
            Log.w(TAG, "Notification de confirmation impossible", e);
        }
    }

    private static void cancelNotification(Context context, int token) {
        try {
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(token);
        } catch (Exception ignored) {
            // pas de notification affichée
        }
    }

    public interface Listener {
        void onResult(OpResult result);
    }

    public static final class Pending {
        final int token;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile OpResult result;
        volatile boolean userActionRequested;
        private Listener lateListener;

        Pending(int token) {
            this.token = token;
        }

        void complete(OpResult r) {
            Listener l;
            synchronized (this) {
                result = r;
                l = lateListener;
            }
            latch.countDown();
            if (l != null) {
                l.onResult(r);
            }
        }

        /**
         * Attend le résultat. Si Android demande une confirmation à l'écran, rend la main au bout de
         * quelques secondes avec un résultat « en attente » : le cycle de synchronisation continue.
         */
        public OpResult await(long timeout, TimeUnit unit) {
            long start = SystemClock.elapsedRealtime();
            long deadline = start + unit.toMillis(timeout);
            try {
                while (SystemClock.elapsedRealtime() < deadline) {
                    if (latch.await(1, TimeUnit.SECONDS)) {
                        return result;
                    }
                    if (userActionRequested && SystemClock.elapsedRealtime() - start > USER_ACTION_GRACE_MS) {
                        return OpResult.awaitingUser("En attente de confirmation sur l'écran du terminal", this);
                    }
                }
                PENDING.remove(token);
                return OpResult.fail("Délai dépassé (confirmation utilisateur requise ?)");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return OpResult.fail("Interrompu");
            }
        }

        /** Résultat définitif d'une opération confirmée (ou refusée) plus tard à l'écran. */
        public void onLateResult(Listener l) {
            OpResult ready;
            synchronized (this) {
                lateListener = l;
                ready = result;
            }
            if (ready != null) {
                l.onResult(ready);
            }
        }

        void cancel() {
            PENDING.remove(token);
        }
    }
}

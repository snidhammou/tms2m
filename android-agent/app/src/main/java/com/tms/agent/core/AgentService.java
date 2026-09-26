package com.tms.agent.core;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.tms.agent.AgentApp;
import com.tms.agent.R;
import com.tms.agent.config.AgentConfig;
import com.tms.agent.net.ApiClient;
import com.tms.agent.ui.MainActivity;

import java.util.Map;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * Service de premier plan qui maintient la connexion avec le serveur TMS :
 * un cycle de synchronisation toutes les N secondes (intervalle dicté par le serveur).
 */
public class AgentService extends Service {

    private static final String TAG = "TmsAgent";
    private static final String ACTION_SYNC_NOW = "com.tms.agent.action.SYNC_NOW";
    private static final int NOTIFICATION_ID = 1001;
    private static final int FOLLOW_UP_SECONDS = 3;
    private static final int WAIT_TIMEOUT_SECONDS = 45;

    private static volatile AgentService running;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> next;
    private SyncEngine engine;
    /** Synchro demandée pendant un cycle en cours : relancée dès la fin de celui-ci. */
    private volatile boolean syncRequested;
    private volatile boolean cycleRunning;
    private Thread realtime;

    public static void start(Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, AgentService.class));
    }

    /** Déclenche une synchronisation immédiate (UI, AIDL requestSync). */
    public static void syncNow(Context context) {
        AgentService s = running;
        if (s != null) {
            s.schedule(0);
        } else {
            ContextCompat.startForegroundService(context,
                    new Intent(context, AgentService.class).setAction(ACTION_SYNC_NOW));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(getString(R.string.notif_starting)),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0);
        engine = new SyncEngine(AgentApp.get());
        executor = Executors.newSingleThreadScheduledExecutor();
        running = this;
        realtime = new Thread(this::realtimeLoop, "tms-realtime");
        realtime.setDaemon(true);
        realtime.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean syncNow = intent != null && ACTION_SYNC_NOW.equals(intent.getAction());
        synchronized (this) {
            if (next == null || syncNow) {
                schedule(0);
            }
        }
        return START_STICKY;
    }

    private synchronized void schedule(long delaySeconds) {
        if (delaySeconds == 0 && cycleRunning) {
            syncRequested = true; // ne pas être écrasé par la replanification de fin de cycle
            return;
        }
        if (executor == null || executor.isShutdown()) {
            return;
        }
        if (next != null) {
            next.cancel(false);
        }
        next = executor.schedule(this::cycle, delaySeconds, TimeUnit.SECONDS);
    }

    private void cycle() {
        cycleRunning = true;
        syncRequested = false;
        boolean didTasks = false;
        try {
            didTasks = engine.runOnce();
        } catch (Throwable t) {
            Log.e(TAG, "Cycle de synchronisation en échec", t);
            AgentApp.get().config().setLastStatus("Erreur : " + t.getMessage());
        } finally {
            updateNotification(AgentApp.get().config().getLastStatus());
            synchronized (this) {
                cycleRunning = false;
                // Après des tâches : heartbeat rapproché pour remonter l'inventaire à jour (désinstallation…)
                schedule(syncRequested ? 0 : didTasks ? FOLLOW_UP_SECONDS
                        : AgentApp.get().config().getPollIntervalSeconds());
            }
        }
    }

    /**
     * Canal temps réel : requête longue maintenue ouverte vers le serveur. Dès qu'il répond
     * {"sync":true} (tâche créée, synchro forcée depuis la console), un cycle est lancé sans
     * attendre l'intervalle de polling. Le polling classique reste le filet de sécurité.
     */
    private void realtimeLoop() {
        AgentConfig config = AgentApp.get().config();
        ApiClient client = new ApiClient(config);
        long backoffMs = 5_000;
        while (running == this && !Thread.currentThread().isInterrupted()) {
            try {
                if (!config.isConfigured() || !config.isEnrolled()) {
                    Thread.sleep(15_000);
                    continue;
                }
                Response<Map<String, Object>> resp = client.api().waitForSync(WAIT_TIMEOUT_SECONDS).execute();
                if (resp.isSuccessful()) {
                    backoffMs = 5_000;
                    Map<String, Object> body = resp.body();
                    if (body != null && Boolean.TRUE.equals(body.get("sync"))) {
                        Log.i(TAG, "Synchronisation demandée par le serveur");
                        schedule(0);
                    }
                } else {
                    // 401 : ré-enrôlement géré par le cycle ; autres codes : serveur ancien ou en maintenance
                    Thread.sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 120_000);
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                // Réseau coupé, timeout… : nouvel essai avec attente croissante
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    return;
                }
                backoffMs = Math.min(backoffMs * 2, 120_000);
            }
        }
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), piFlags);
        return new NotificationCompat.Builder(this, AgentApp.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        running = null;
        if (realtime != null) {
            realtime.interrupt();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

package com.tms.agent.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tms.agent.AgentApp;
import com.tms.agent.core.AgentService;
import com.tms.agent.core.AppLauncher;

/** Relance l'agent au démarrage du terminal et après sa propre mise à jour. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (AgentApp.get().config().isConfigured()) {
            AgentService.start(context);
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Kiosque (Device Owner) ou application à démarrage automatique
            AppLauncher.onBoot(context);
        }
    }
}

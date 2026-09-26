package com.tms.agent.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tms.agent.device.InstallResults;

/** Reçoit les résultats d'installation / désinstallation de PackageInstaller. */
public class PackageInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (InstallResults.ACTION.equals(intent.getAction())) {
            InstallResults.deliver(context, intent);
        }
    }
}

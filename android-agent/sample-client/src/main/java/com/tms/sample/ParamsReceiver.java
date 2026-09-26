package com.tms.sample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Reçoit la notification de l'agent quand les paramètres TMS de l'app ont changé. */
public class ParamsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(MainActivity.TAG, "Paramètres TMS mis à jour : " + intent.getStringExtra("parameters"));
    }
}

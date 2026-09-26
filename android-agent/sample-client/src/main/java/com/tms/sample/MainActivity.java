package com.tms.sample;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.tms.agent.ITmsAgentService;

/** Exemple d'intégration : liaison AIDL à l'agent TMS et lecture des paramètres de l'app. */
public class MainActivity extends AppCompatActivity {

    static final String TAG = "TmsSample";

    private TextView text;
    private ITmsAgentService agent;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            agent = ITmsAgentService.Stub.asInterface(binder);
            refresh();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            agent = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        text = new TextView(this);
        text.setPadding(32, 32, 32, 32);
        text.setTextIsSelectable(true);
        setContentView(text);

        Intent intent = new Intent("com.tms.agent.action.BIND").setPackage("com.tms.agent");
        if (!bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            text.setText("TMS2M Agent introuvable (com.tms.agent non installé ?)");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (agent == null) {
            return;
        }
        try {
            String info = agent.getTerminalInfo();
            String params = agent.getParameters(getPackageName());
            Log.i(TAG, "terminal=" + info + " parametres=" + params);
            text.setText("Terminal :\n" + info + "\n\nParamètres TMS de " + getPackageName() + " :\n" + params);
        } catch (RemoteException | SecurityException e) {
            text.setText("Erreur agent : " + e);
        }
    }

    @Override
    protected void onDestroy() {
        unbindService(connection);
        super.onDestroy();
    }
}

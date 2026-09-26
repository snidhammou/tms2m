package com.tms.agent.ipc;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;

import com.google.gson.Gson;
import com.tms.agent.AgentApp;
import com.tms.agent.ITmsAgentService;
import com.tms.agent.config.AgentConfig;
import com.tms.agent.core.AgentService;
import com.tms.agent.core.ParameterStore;
import com.tms.agent.device.DeviceManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service lié (AIDL) permettant aux applications du terminal — typiquement l'application
 * de paiement — de lire leurs paramètres TMS et les informations du terminal.
 */
public class TmsAgentBinderService extends Service {

    private final Gson gson = new Gson();
    private ParameterStore parameters;

    @Override
    public void onCreate() {
        super.onCreate();
        parameters = new ParameterStore(this);
    }

    private final ITmsAgentService.Stub binder = new ITmsAgentService.Stub() {

        @Override
        public String getTerminalInfo() {
            AgentConfig config = AgentApp.get().config();
            DeviceManager device = AgentApp.get().device();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("serialNumber", device.serialNumber());
            info.put("manufacturer", device.vendor());
            info.put("model", device.model());
            info.put("firmwareVersion", device.firmwareVersion());
            info.put("enrolled", config.isEnrolled());
            info.put("terminalId", config.getTerminalId());
            info.put("lastSyncAt", config.getLastSyncAt());
            return gson.toJson(info);
        }

        @Override
        public String getParameters(String packageName) {
            enforceCallerOwns(packageName);
            return parameters.getJson(packageName);
        }

        @Override
        public String getParameter(String packageName, String key) {
            enforceCallerOwns(packageName);
            return parameters.get(packageName, key);
        }

        @Override
        public void requestSync() {
            long identity = Binder.clearCallingIdentity();
            try {
                AgentService.syncNow(TmsAgentBinderService.this);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    };

    /** Une application ne peut lire que SES paramètres (vérification par UID appelant). */
    private void enforceCallerOwns(String packageName) {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) {
            return;
        }
        String[] packages = getPackageManager().getPackagesForUid(uid);
        if (packages != null) {
            for (String p : packages) {
                if (p.equals(packageName)) {
                    return;
                }
            }
        }
        throw new SecurityException("Accès refusé aux paramètres de " + packageName);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}

package com.tms.agent.ui;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.tms.agent.AgentApp;
import com.tms.agent.BuildConfig;
import com.tms.agent.R;
import com.tms.agent.admin.AgentDeviceAdmin;
import com.tms.agent.config.AgentConfig;
import com.tms.agent.core.AgentService;
import com.tms.agent.device.DeviceManager;

/** Écran de configuration et de supervision de l'agent (utilisé lors du staging). */
public class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 2000);
        }
    };

    private AgentConfig config;
    private EditText serverUrl;
    private EditText enrollmentKey;
    private TextView status;
    private Button clearOwner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        config = AgentApp.get().config();

        DeviceManager device = AgentApp.get().device();
        TextView deviceInfo = findViewById(R.id.deviceInfo);
        deviceInfo.setText(getString(R.string.device_info,
                device.vendor(), device.model(), device.serialNumber(),
                Build.VERSION.RELEASE, device.firmwareVersion(), BuildConfig.VERSION_NAME,
                device.getClass().getSimpleName()));

        serverUrl = findViewById(R.id.serverUrl);
        enrollmentKey = findViewById(R.id.enrollmentKey);
        status = findViewById(R.id.status);
        serverUrl.setText(config.getServerUrl());
        enrollmentKey.setText(config.getEnrollmentKey());

        Button save = findViewById(R.id.saveButton);
        save.setOnClickListener(v -> saveAndConnect());
        Button sync = findViewById(R.id.syncButton);
        sync.setOnClickListener(v -> {
            AgentService.syncNow(this);
            Toast.makeText(this, R.string.sync_requested, Toast.LENGTH_SHORT).show();
        });
        Button reset = findViewById(R.id.resetButton);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.reset_title)
                .setMessage(R.string.reset_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    config.clearEnrollment();
                    AgentService.syncNow(this);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        clearOwner = findViewById(R.id.clearOwnerButton);
        clearOwner.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.clear_owner_title)
                .setMessage(R.string.clear_owner_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    AgentDeviceAdmin.clearDeviceOwner(this);
                    refreshStatus();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void saveAndConnect() {
        String url = serverUrl.getText().toString().trim();
        String key = enrollmentKey.getText().toString().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            serverUrl.setError(getString(R.string.error_url));
            return;
        }
        if (key.isEmpty()) {
            enrollmentKey.setError(getString(R.string.error_key));
            return;
        }
        config.setConnection(url, key);
        AgentService.start(this);
        AgentService.syncNow(this);
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
    }

    private void refreshStatus() {
        long last = config.getLastSyncAt();
        String lastSync = last == 0 ? getString(R.string.never)
                : DateFormat.getDateFormat(this).format(last) + " " + DateFormat.getTimeFormat(this).format(last);
        String enrolled = config.isEnrolled()
                ? getString(R.string.enrolled_as, config.getTerminalId())
                : getString(R.string.not_enrolled);
        boolean owner = AgentDeviceAdmin.isDeviceOwner(this);
        status.setText(getString(R.string.status_text, enrolled, lastSync,
                config.getPollIntervalSeconds(), config.getLastStatus(),
                getString(owner ? R.string.device_owner_yes : R.string.device_owner_no)));
        clearOwner.setVisibility(owner ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresher);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresher);
        super.onPause();
    }
}

package com.tms.agent;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.tms.agent.config.AgentConfig;
import com.tms.agent.core.AgentService;
import com.tms.agent.device.DeviceManager;
import com.tms.agent.device.DeviceManagerFactory;

public class AgentApp extends Application {

    public static final String CHANNEL_ID = "tms_agent";

    private static AgentApp instance;

    private AgentConfig config;
    private DeviceManager deviceManager;

    public static AgentApp get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        config = new AgentConfig(this);
        deviceManager = DeviceManagerFactory.create(this);
        createNotificationChannel();
        if (config.isConfigured()) {
            AgentService.start(this);
        }
    }

    public AgentConfig config() {
        return config;
    }

    public DeviceManager device() {
        return deviceManager;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}

package com.tms.agent.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Map;
import java.util.TreeMap;

/** Cache local des paramètres TMS par package, servi aux applications via AIDL. */
public class ParameterStore {

    private static final String PREFS = "tms_params";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public ParameterStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Remplace le jeu de paramètres du package ; retourne le JSON stocké. */
    public String save(String packageName, Map<String, String> values) {
        String json = gson.toJson(values != null ? new TreeMap<>(values) : new TreeMap<String, String>());
        prefs.edit().putString(packageName, json).commit();
        return json;
    }

    public String getJson(String packageName) {
        return prefs.getString(packageName, "{}");
    }

    public String get(String packageName, String key) {
        Map<String, String> map = gson.fromJson(getJson(packageName),
                new TypeToken<Map<String, String>>() { }.getType());
        return map != null ? map.get(key) : null;
    }
}

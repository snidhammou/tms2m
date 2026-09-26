package com.tms.agent.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tms.agent.net.Dtos;
import com.tms.agent.net.TmsApi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

/**
 * File persistante des statuts de tâches à remonter : aucun résultat n'est perdu
 * si le réseau tombe entre l'exécution et l'acquittement.
 */
public class StatusOutbox {

    private static final String PREFS = "tms_outbox";
    private static final String KEY = "items";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public StatusOutbox(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void add(long taskId, String status, String message, java.util.Map<String, Object> result) {
        List<Item> items = load();
        items.add(new Item(taskId, status, message, result));
        save(items);
    }

    /** Envoie les statuts dans l'ordre ; s'arrête au premier échec réseau. */
    public synchronized void flush(TmsApi api) throws IOException {
        List<Item> items = load();
        while (!items.isEmpty()) {
            Item item = items.get(0);
            Response<Void> resp = api.updateTaskStatus(item.taskId,
                    new Dtos.TaskStatusUpdate(item.status, item.message, item.result)).execute();
            // 5xx / 401 (jeton à renouveler) : on garde pour plus tard.
            // Autres 4xx (tâche supprimée…) : inutile de réessayer indéfiniment.
            if (!resp.isSuccessful() && (resp.code() >= 500 || resp.code() == 401)) {
                throw new IOException("HTTP " + resp.code());
            }
            items.remove(0);
            save(items);
        }
    }

    private List<Item> load() {
        String json = prefs.getString(KEY, null);
        if (json == null) {
            return new ArrayList<>();
        }
        List<Item> items = gson.fromJson(json, new TypeToken<List<Item>>() { }.getType());
        return items != null ? items : new ArrayList<>();
    }

    private void save(List<Item> items) {
        prefs.edit().putString(KEY, gson.toJson(items)).commit();
    }

    static class Item {
        long taskId;
        String status;
        String message;
        java.util.Map<String, Object> result;

        Item(long taskId, String status, String message, java.util.Map<String, Object> result) {
            this.taskId = taskId;
            this.status = status;
            this.message = message;
            this.result = result;
        }
    }
}

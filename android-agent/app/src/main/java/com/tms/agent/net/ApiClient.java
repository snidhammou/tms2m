package com.tms.agent.net;

import com.tms.agent.config.AgentConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Client HTTP vers le serveur TMS ; reconstruit si l'URL serveur change. */
public class ApiClient {

    private final AgentConfig config;
    private String baseUrl;
    private TmsApi api;

    public ApiClient(AgentConfig config) {
        this.config = config;
    }

    public synchronized TmsApi api() {
        String url = config.getServerUrl() + "/";
        if (api == null || !url.equals(baseUrl)) {
            HttpUrl parsed = HttpUrl.parse(url);
            if (parsed == null) {
                throw new IllegalArgumentException("URL serveur invalide : " + url);
            }
            final String serverHost = parsed.host();
            OkHttpClient http = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        String token = config.getDeviceToken();
                        // Le jeton n'est envoyé qu'au serveur TMS (jamais à un CDN tiers d'URL absolue)
                        if (token == null || !original.url().host().equals(serverHost)) {
                            return chain.proceed(original);
                        }
                        return chain.proceed(original.newBuilder().header("X-Device-Token", token).build());
                    })
                    .build();
            api = new Retrofit.Builder()
                    .baseUrl(url)
                    .client(http)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(TmsApi.class);
            baseUrl = url;
        }
        return api;
    }

    /** Transforme un chemin relatif renvoyé par le serveur ("/api/...") en URL absolue. */
    public String absolute(String pathOrUrl) {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        return config.getServerUrl() + (pathOrUrl.startsWith("/") ? "" : "/") + pathOrUrl;
    }
}

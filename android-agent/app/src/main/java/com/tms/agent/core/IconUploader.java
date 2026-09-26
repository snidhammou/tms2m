package com.tms.agent.core;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.tms.agent.net.TmsApi;

import java.io.ByteArrayOutputStream;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;

/** Envoie au serveur l'icône des applications installées qu'il ne connaît pas encore. */
class IconUploader {

    private static final String TAG = "TmsAgent";
    private static final int SIZE_PX = 96;
    private static final MediaType PNG = MediaType.parse("image/png");

    private final Context context;

    IconUploader(Context context) {
        this.context = context;
    }

    /** Best effort : une icône en échec n'interrompt ni les autres ni le cycle. */
    void upload(TmsApi api, List<String> packages) {
        if (packages == null) {
            return;
        }
        for (String pkg : packages) {
            try {
                byte[] png = render(pkg);
                if (png != null) {
                    retrofit2.Response<okhttp3.ResponseBody> resp = api.uploadIcon(pkg, RequestBody.create(PNG, png)).execute();
                    if (resp.body() != null) resp.body().close();
                }
            } catch (Exception e) {
                Log.w(TAG, "Icône " + pkg + " non envoyée : " + e.getMessage());
            }
        }
    }

    private byte[] render(String pkg) {
        Drawable d;
        try {
            d = context.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        Bitmap bmp;
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            bmp = Bitmap.createScaledBitmap(((BitmapDrawable) d).getBitmap(), SIZE_PX, SIZE_PX, true);
        } else {
            // Icônes adaptatives / vectorielles : rendu sur un canevas
            bmp = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, SIZE_PX, SIZE_PX);
            d.draw(canvas);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }
}

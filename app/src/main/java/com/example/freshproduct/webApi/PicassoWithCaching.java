package com.example.freshproduct.webApi;

import android.content.Context;

import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

public class PicassoWithCaching {

    public static Picasso newInstance(Context context) {
        Picasso.Builder builder = new Picasso.Builder(context);

        builder.downloader(new OkHttp3Downloader(context));
        return builder.build();
    }
}

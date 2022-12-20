package com.example.freshproduct.webApi;

import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class SingleWebApi {

    private static FreshProductApi freshProductApi;
    private static Retrofit retrofit;

    public synchronized static FreshProductApi getInstance() {
        if (freshProductApi == null) {

            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            clientBuilder.addInterceptor(loggingInterceptor)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .connectTimeout(20, TimeUnit.SECONDS);

            retrofit = new Retrofit.Builder()
                    .baseUrl(FreshProductApi.HOST)
                    .client(clientBuilder.build())
                    .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .build();

            freshProductApi = retrofit.create(FreshProductApi.class);
        }

        return freshProductApi;
    }
}

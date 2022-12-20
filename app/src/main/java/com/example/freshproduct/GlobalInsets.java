package com.example.freshproduct;

import android.util.Log;

import androidx.core.view.WindowInsetsCompat;

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;

public class GlobalInsets {

    private static GlobalInsets instance;

    private WindowInsetsCompat insets;
    PublishSubject<WindowInsetsCompat> obs;

    private GlobalInsets() {
        obs = PublishSubject.create();
    }

    public static synchronized GlobalInsets getInstance() {
        if (instance == null) {
            instance = new GlobalInsets();
        }
        return instance;
    }

    public Disposable subscribeToInsets(Consumer<WindowInsetsCompat> consumer) {

        Disposable res = obs.subscribe(consumer);

        try {
            if (insets != null) {
                consumer.accept(insets);
            }
        } catch (Exception e) {
            Log.e("Global insets", e.getMessage());
        }

        return res;
    }

    public WindowInsetsCompat getInsets() {
        return this.insets;
    }

    public synchronized void setInsets(WindowInsetsCompat insets) {
        if (this.insets != null && this.insets.equals(insets)) {
            obs.onNext(insets);
        }
        this.insets = insets;
    }

}

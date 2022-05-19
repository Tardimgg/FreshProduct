package com.example.freshproduct.productInfoLoader;

import android.util.Pair;

import com.example.freshproduct.Result;

public interface InfoDownloadCompleted {

    void event(Result<Pair<String, String>, String> res);

}

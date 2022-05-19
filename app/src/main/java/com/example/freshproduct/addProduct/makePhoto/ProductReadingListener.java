package com.example.freshproduct.addProduct.makePhoto;

import android.util.Pair;

import com.example.freshproduct.Result;

import java.io.Serializable;

public interface ProductReadingListener extends Serializable {

    void productReadyEvent(Result<Pair<String, String>, String> name, long time);

}

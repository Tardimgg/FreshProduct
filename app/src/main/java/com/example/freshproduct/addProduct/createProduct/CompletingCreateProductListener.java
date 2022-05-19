package com.example.freshproduct.addProduct.createProduct;

import java.io.Serializable;

public interface CompletingCreateProductListener extends Serializable {

    void completingCreateProductEvent(String title, String subtitle, long expirationDate);

}

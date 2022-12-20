package com.example.freshproduct.listProducts;

import com.example.freshproduct.dataBase.Product;

import java.io.Serializable;

public interface NeedsChangeProductListener extends Serializable {

    void requiredChangeProductEvent(Product product);

}

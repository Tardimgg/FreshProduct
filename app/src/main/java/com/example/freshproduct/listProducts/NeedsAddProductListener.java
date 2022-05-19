package com.example.freshproduct.listProducts;


import java.io.Serializable;

public interface NeedsAddProductListener extends Serializable {

    void requiredAddProductEvent();

}
package com.example.freshproduct.listProducts;

import java.util.Locale;

public class ProductModel {
    private int imageDrawable;
    private String title;
    private String subtitle;
    public int expirationDate;


    public ProductModel(String title, String subtitle, int expirationDate) {
        //imageDrawable = R.drawable.list_image;
        this.title = title;
        this.subtitle = subtitle;
        this.expirationDate = expirationDate;
    }

    public int getImageDrawable() {
        return imageDrawable;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }
}
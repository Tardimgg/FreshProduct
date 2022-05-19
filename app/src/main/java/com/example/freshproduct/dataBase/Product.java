package com.example.freshproduct.dataBase;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Product {

    @PrimaryKey(autoGenerate = true)
    public long uid;

    @ColumnInfo(name = "left_node_id")
    public long leftNodeId;

    @ColumnInfo(name = "right_node_id")
    public long rightNodeId;


    @ColumnInfo(name = "image_drawable")
    public int imageDrawable;

    @ColumnInfo(name = "product_title")
    public String productTitle;

    @ColumnInfo(name = "product_subtitle")
    public String productSubtitle;

    @ColumnInfo(name = "expiration_date")
    public long expirationDate;


}

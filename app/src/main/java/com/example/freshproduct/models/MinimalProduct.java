package com.example.freshproduct.models;

import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

public class MinimalProduct {

    @PrimaryKey(autoGenerate = true)
    public long uid;

    @ColumnInfo(name = "left_node_id")
    public long leftNodeId;

    @ColumnInfo(name = "right_node_id")
    public long rightNodeId;

    @ColumnInfo(name = "image_url")
    public String imageUrl;

    @ColumnInfo(name = "product_title")
    public String productTitle;

    @ColumnInfo(name = "product_subtitle")
    public String productSubtitle;

    @ColumnInfo(name = "expiration_date")
    public long expirationDate;

    @ColumnInfo(name = "deleted")
    public boolean deleted;
}

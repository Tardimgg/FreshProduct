package com.example.freshproduct.dataBase;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity
public class Product implements Serializable {

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

    @ColumnInfo(name = "start_tracking_date")
    public long startTrackingDate;

    @ColumnInfo(name = "last_notification_date")
    public long lastNotificationDate;

    @ColumnInfo(name = "sync")
    public boolean sync;

    @ColumnInfo(name = "deleted")
    public boolean deleted;

    @ColumnInfo(name = "localOnly")
    public boolean localOnly;

}

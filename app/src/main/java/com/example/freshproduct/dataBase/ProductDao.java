package com.example.freshproduct.dataBase;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.freshproduct.models.MinimalProduct;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;

@Dao
public interface ProductDao {

    @Query("SELECT * FROM product")
    Flowable<List<Product>> getAll();

    @Query("SELECT uid, left_node_id, right_node_id, image_url, product_title, product_subtitle, expiration_date, deleted FROM product")
    Flowable<List<MinimalProduct>> getAllMinimal();

    @Query("SELECT * FROM product")
    Single<List<Product>> getAllOnce();

    @Query("Update product SET `left_node_id` = :newLeftNodeId, `right_node_id` = :newRightNodeId WHERE uid = :uid")
    void updatePosition(long uid, long newLeftNodeId, long newRightNodeId);

    @Query("Update product SET `left_node_id` = :newLeftNodeId WHERE uid = :uid")
    void updateLeftNode(long uid, long newLeftNodeId);

    @Query("Update product SET `right_node_id` = :newRightNodeId WHERE uid = :uid")
    void updateRightNode(long uid, long newRightNodeId);

    @Query("Update product SET `sync` = :sync WHERE uid = :uid")
    void updateSyncStatus(long uid, boolean sync);

    @Query("Update product SET `last_notification_date` = :newLastNotificationDate WHERE uid = :uid")
    void updateLastNotificationDate(long uid, long newLastNotificationDate);

    @Query("SELECT uid FROM product WHERE right_node_id = -1")
    Single<List<Long>> getLastNodeId();

    @Query("SELECT * FROM product WHERE uid= :uid")
    Single<Product> getById(long uid);

    @Query("SELECT MAX(uid) FROM product")
    Single<Long> getMaxId();

    @Insert
    long[] insertAll(Product... products);

    @Update
    void update(Product product);

    @Delete
    void delete(Product product);



}

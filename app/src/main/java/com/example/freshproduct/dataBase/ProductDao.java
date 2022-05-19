package com.example.freshproduct.dataBase;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;

@Dao
public interface ProductDao {

    @Query("SELECT * FROM product")
    Flowable<List<Product>> getAll();

    @Query("SELECT * FROM product")
    Single<List<Product>> getAllOnce();

    @Query("Update product SET `left_node_id` = :newLeftNodeId, `right_node_id` = :newRightNodeId WHERE uid = :uid")
    void updatePosition(long uid, long newLeftNodeId, long newRightNodeId);


    @Query("Update product SET `left_node_id` = :newLeftNodeId WHERE uid = :uid")
    void updateLeftNode(long uid, long newLeftNodeId);

    @Query("Update product SET `right_node_id` = :newRightNodeId WHERE uid = :uid")
    void updateRightNode(long uid, long newRightNodeId);

    @Query("SELECT uid FROM product WHERE right_node_id = -1")
    Single<List<Long>> getLastNodeId();

    @Insert
    long[] insertAll(Product... products);

    @Delete
    void delete(Product product);



}

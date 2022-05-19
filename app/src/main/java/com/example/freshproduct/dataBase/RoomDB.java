package com.example.freshproduct.dataBase;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Product.class}, version = 1)
public abstract class RoomDB extends RoomDatabase {

    private static RoomDB reference;
    private static final String DATA_BASE_NAME = "DATA_BASE_NAME";

    public abstract ProductDao productDao();

    public static synchronized RoomDB getInstance(Context context) {
        if (reference == null) {
            initDB(context);
        }
        return reference;
    }

    private static void initDB(Context context) {
        reference = Room.databaseBuilder(context, RoomDB.class, DATA_BASE_NAME).build();
    }
}

package com.example.freshproduct.dataBase;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.freshproduct.CurrentUser;
import com.example.freshproduct.Func;
import com.example.freshproduct.listProducts.OperationCompletionListener;
import com.example.freshproduct.models.ExternalProduct;
import com.example.freshproduct.models.ExternalProductId;
import com.example.freshproduct.models.Neighbors;
import com.example.freshproduct.models.RequestCreateNewProduct;
import com.example.freshproduct.models.RequestDeleteProduct;
import com.example.freshproduct.models.User;
import com.example.freshproduct.webApi.SingleWebApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Database(entities = {Product.class}, version = 1)
public abstract class RoomDB extends RoomDatabase {

    private volatile static RoomDB reference;
    private static final String DATA_BASE_NAME = "DATA_BASE_NAME";

    private static SharedPreferences sharedPreferences;

    public abstract ProductDao productDao();

    public static synchronized RoomDB getInstance(Context context) {
        if (reference == null) {
            initDB(context);
        }
        return reference;
    }

    private static void initDB(Context context) {
        reference = Room.databaseBuilder(context, RoomDB.class, DATA_BASE_NAME).build();
        sharedPreferences = context.getSharedPreferences("SharedPreferences", Context.MODE_PRIVATE);
    }

    public void setSyncPositions(boolean val) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("sync position", val);
        editor.apply();
    }

    public boolean getSyncPositions() {
        return sharedPreferences.getBoolean("sync position", false);
    }

    public static void removeProduct(Context context, long uid, OperationCompletionListener listener) {
        new Thread(() -> {
            synchronized (inSync) {

                if (inSync.get()) {
                    productToRemove.add(uid);
                } else {

                    synchronized (RoomDB.getInstance(context)) {

                        Product product = null;

                        try {

                            product = RoomDB.getInstance(context).productDao().getById(uid).blockingGet();

                        } catch (RuntimeException e) {
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.post(() -> Toast.makeText(context, "Повторите ещё раз", Toast.LENGTH_SHORT));

                        }

                        if (product != null) {

                            if (syncEnabled) {
                                product.deleted = true;
                                RoomDB.getInstance(context).productDao().update(product);
                            } else {
                                RoomDB.getInstance(context).productDao().delete(product);
                            }

                            RoomDB.getInstance(context).productDao().updateRightNode(product.leftNodeId, product.rightNodeId);

                            RoomDB.getInstance(context).productDao().updateLeftNode(product.rightNodeId, product.leftNodeId);
                        }


                    }
                }
                if (listener != null) {
                    listener.operationCompleted();
                }

            }
        }).start();

    }

    private static final int SYNC_DELAY = 5000;
    public static final AtomicBoolean permissionSync = new AtomicBoolean(true);
    private static final AtomicBoolean inSync = new AtomicBoolean(false);
    private static final List<Long> productToRemove = Collections.synchronizedList(new LinkedList<>());

    private static boolean syncEnabled = false;


    public static void startAutoSync(Context context) {

        syncEnabled = true;

        if (CurrentUser.getInstance().getLogin() != null) {
            new Thread(() -> {

                CurrentUser currentUser = CurrentUser.getInstance(context);
                User user = new User(currentUser.getLogin(), currentUser.getPassword());

                while (true) {

                    while (!permissionSync.get()) {
                        try {
                            Thread.sleep(SYNC_DELAY);
                        } catch (InterruptedException interruptedException) {
                            interruptedException.printStackTrace();
                        }
                    }

                    synchronized (inSync) {
                        inSync.set(true);
                    }

                    synchronized (permissionSync) {

                        try {

                            List<ExternalProduct> externalProducts = SingleWebApi.getInstance().getProducts(user).blockingGet().response;
                            Collections.sort(externalProducts, (f, s) -> Long.compare(f.product_id_on_device, s.product_id_on_device));

                            List<Product> localProduct;
                            synchronized (RoomDB.getInstance(context)) {
                                localProduct = RoomDB.getInstance(context).productDao().getAllOnce().blockingGet();
                            }
//                            Collections.sort(localProduct, (f, s) -> Long.compare(f.uid, s.uid));

                            ListIterator<ExternalProduct> externalData = externalProducts.listIterator();
                            ListIterator<Product> localData = localProduct.listIterator();

                            if (!reference.getSyncPositions()) {

                                HashSet<Long> usedRemoteId = new HashSet<>();

                                HashMap<Long, List<Long>> neighbors = new HashMap<>();

                                long lastId = -1;

                                while (localData.hasNext()) {

                                    Product currentLocal = localData.next();

                                    if (currentLocal.rightNodeId == -1 && !currentLocal.deleted) {
                                        lastId = currentLocal.uid; // работает пока не реализовано удаление при пропадании на сервере (а мб и норм)
                                    }

                                    if (currentLocal.localOnly) {

                                        Product newProduct = sendProductToServer(context, localProduct, currentLocal, v -> {
                                            v.left_node_id = -5;
                                            v.right_node_id = -5;
                                            return v;
                                        });

                                        neighbors.put(newProduct.uid, Arrays.asList(newProduct.leftNodeId, newProduct.rightNodeId));
                                    } else if (currentLocal.deleted) {
                                        RequestDeleteProduct json = new RequestDeleteProduct();

                                        json.login = CurrentUser.getInstance().getLogin();
                                        json.password = CurrentUser.getInstance().getPassword();
                                        json.product_id_on_device = currentLocal.uid;

                                        SingleWebApi.getInstance().deleteProduct(json).blockingAwait();
                                        usedRemoteId.add(currentLocal.uid);

                                        synchronized (RoomDB.getInstance(context)) {
                                            RoomDB.getInstance(context).productDao().delete(currentLocal);
                                        }

                                    } else {

                                        ExternalProduct testProduct = new ExternalProduct();
                                        testProduct.product_id_on_device = currentLocal.uid;

                                        int remoteIndex = Collections.binarySearch(externalProducts, testProduct, (f, s) ->
                                                Long.compare(f.product_id_on_device, s.product_id_on_device));
                                        if (remoteIndex >= 0) {
                                            neighbors.put(currentLocal.uid, Arrays.asList(currentLocal.leftNodeId, currentLocal.rightNodeId));

                                            usedRemoteId.add(currentLocal.uid);

                                            if (!equals(externalProducts.get(remoteIndex), currentLocal)) {
                                                if (currentLocal.sync) {

                                                    Product product = convertExternalProductToLocal(externalProducts.get(remoteIndex));
                                                    product.sync = true;
                                                    product.leftNodeId = currentLocal.leftNodeId;
                                                    product.rightNodeId = currentLocal.rightNodeId;

                                                    synchronized (RoomDB.getInstance(context)) {
                                                        RoomDB.getInstance(context).productDao().update(product);
                                                    }
                                                    // save in local
                                                } else {
                                                    RequestCreateNewProduct requestCreateNewProduct = convertLocalProductToRequestProduct(currentLocal);

                                                    SingleWebApi.getInstance().updateProduct(requestCreateNewProduct).blockingAwait();

                                                    syncLocal(context, currentLocal);
                                                    // update remote data
                                                    // push to server
                                                }
                                            }
                                        } else {
                                            // переместить соседей


                                            synchronized (RoomDB.getInstance(context)) {
                                                RoomDB.getInstance(context).productDao().delete(currentLocal);


                                                Product productInArray = findProduct(localProduct, (v) -> v.uid == currentLocal.leftNodeId);

                                                RoomDB.getInstance(context).productDao().updateRightNode(currentLocal.leftNodeId, currentLocal.rightNodeId);
                                                if (productInArray != null) {
                                                    productInArray.rightNodeId = currentLocal.rightNodeId;
                                                }

                                                productInArray = findProduct(localProduct, (v) -> v.uid == currentLocal.rightNodeId);

                                                RoomDB.getInstance(context).productDao().updateLeftNode(currentLocal.rightNodeId, currentLocal.leftNodeId);
                                                if (productInArray != null) {
                                                    productInArray.leftNodeId = currentLocal.leftNodeId;
                                                }
                                            }

                                            if (neighbors.containsKey(currentLocal.leftNodeId)) {

                                                neighbors.get(currentLocal.leftNodeId).set(1, currentLocal.rightNodeId);
                                            }

                                            if (neighbors.containsKey(currentLocal.rightNodeId)) {

                                                neighbors.get(currentLocal.rightNodeId).set(0, currentLocal.leftNodeId);
                                            }


                                            // delete from local
                                        }
                                    }
                                }

                                if (externalProducts.size() != 0) {
                                    ExternalProduct currentExternal = Collections.min(externalProducts,
                                            (f, s) -> Long.compare(f.left_node_id, s.left_node_id));

                                    if (currentExternal.left_node_id == -1) {

                                        HashMap<Long, Product> forAdd = new HashMap<>();

                                        HashSet<Long> repetitionId = new HashSet();

                                        while (true) {

                                            if (repetitionId.contains(currentExternal.product_id_on_device)) {
                                                break;
                                            }
                                            repetitionId.add(currentExternal.product_id_on_device);

                                            if (!usedRemoteId.contains(currentExternal.product_id_on_device)) {

                                                usedRemoteId.add(currentExternal.product_id_on_device);

                                                Product product = convertExternalProductToLocal(currentExternal);
                                                product.sync = true;
                                                product.leftNodeId = lastId;
                                                product.rightNodeId = -1;

                                                forAdd.put(product.uid, product);

                                                if (forAdd.containsKey(lastId)) {
                                                    forAdd.get(lastId).rightNodeId = currentExternal.product_id_on_device;
                                                    neighbors.get(lastId).set(1, currentExternal.product_id_on_device);
                                                } else {

                                                    long finalLastId = lastId;
                                                    Product productInArray = findProduct(localProduct, (v) -> v.uid == finalLastId);

                                                    RoomDB.getInstance(context).productDao().updateRightNode(finalLastId, product.uid);
                                                    if (productInArray != null) {
                                                        productInArray.rightNodeId = product.uid;
                                                    }
                                                    if (neighbors.containsKey(finalLastId)) {

                                                        neighbors.get(finalLastId).set(1, product.uid);
                                                    }

                                                }

                                                neighbors.put(currentExternal.product_id_on_device, Arrays.asList(lastId, -1L));

                                                lastId = currentExternal.product_id_on_device;
                                            }

                                            if (currentExternal.right_node_id == -1) {
                                                break;
                                            } else {

                                                ExternalProduct testProduct = new ExternalProduct();
                                                testProduct.product_id_on_device = currentExternal.right_node_id;

                                                int nextIndex = Collections.binarySearch(externalProducts, testProduct,
                                                        (f, s) -> Long.compare(f.product_id_on_device, s.product_id_on_device));

                                                currentExternal = externalProducts.get(nextIndex);
                                            }
                                        }

                                        while (externalData.hasNext()) {
                                            ExternalProduct externalProduct = externalData.next();

                                            if (!usedRemoteId.contains(externalProduct.product_id_on_device)) {

                                                Product product = convertExternalProductToLocal(externalProduct);
                                                product.sync = true;
                                                product.leftNodeId = lastId;
                                                product.rightNodeId = -1;

                                                forAdd.put(product.uid, product);

                                                if (forAdd.containsKey(lastId)) {
                                                    forAdd.get(lastId).rightNodeId = externalProduct.product_id_on_device;
                                                    neighbors.get(lastId).set(1, externalProduct.product_id_on_device);
                                                } else {

                                                    long finalLastId = lastId;
                                                    Product productInArray = findProduct(localProduct, (v) -> v.uid == finalLastId);

                                                    RoomDB.getInstance(context).productDao().updateRightNode(finalLastId, product.uid);
                                                    if (productInArray != null) {
                                                        productInArray.rightNodeId = product.uid;
                                                    }
                                                    if (neighbors.containsKey(finalLastId)) {

                                                        neighbors.get(finalLastId).set(1, product.uid);
                                                    }
                                                }

                                                neighbors.put(externalProduct.product_id_on_device, Arrays.asList(lastId, -1L));

                                                lastId = externalProduct.product_id_on_device;
                                            }
                                        }

                                        if (forAdd.size() != 0) {
                                            synchronized (RoomDB.getInstance(context)) {
                                                RoomDB.getInstance(context).productDao().insertAll(forAdd.values().toArray(new Product[0]));
                                            }
                                        }


                                    } else {
                                        Log.e("sync", "external bd incorrect");
                                    }

                                    Neighbors neighborsToSend = new Neighbors();
                                    neighborsToSend.data = neighbors;

                                    SingleWebApi.getInstance().updateNeighbors(neighborsToSend).blockingAwait();
                                }

                            }

                         /*
                        List<ExternalProduct> externalProducts = SingleWebApi.getInstance().getProducts(user).blockingGet().response;
                        Collections.sort(externalProducts, (f, s) -> Long.compare(f.product_id_on_device, s.product_id_on_device));

                        List<Product> localProduct;
                        synchronized (RoomDB.getInstance(context)) {
                            localProduct = RoomDB.getInstance(context).productDao().getAllOnce().blockingGet();
                        }
                        Collections.sort(localProduct, (f, s) -> Long.compare(f.uid, s.uid));

                        ListIterator<ExternalProduct> externalData = externalProducts.listIterator();
                        ListIterator<Product> localData = localProduct.listIterator();



                        while (externalData.hasNext()) {

                            ExternalProduct currentExternal = externalData.next();

                            if (localData.hasNext()) {
                                Product currentLocal = localData.next();

                                if (currentLocal.localOnly) {
                                    externalData.previous();

                                    sendProductToServer(context, localProduct, currentLocal);


                                } else if (currentExternal.product_id_on_device == currentLocal.uid) {
                                    if (currentLocal.deleted) {

                                        RequestDeleteProduct json = new RequestDeleteProduct();

                                        json.login = CurrentUser.getInstance().getLogin();
                                        json.password = CurrentUser.getInstance().getPassword();
                                        json.product_id_on_device = currentLocal.uid;

                                        SingleWebApi.getInstance().deleteProduct(json).blockingAwait();

                                        synchronized (RoomDB.getInstance(context)) {
                                            RoomDB.getInstance(context).productDao().delete(currentLocal);
                                        }

                                        // delete from server

                                    } else if (!equals(currentExternal, currentLocal)) {

                                        if (currentLocal.sync) {
                                            Product product = convertExternalProductToLocal(currentExternal);
                                            product.sync = true;

                                            synchronized (RoomDB.getInstance(context)) {
                                                RoomDB.getInstance(context).productDao().update(product);
                                            }
                                            // update local data
                                        } else {

                                            RequestCreateNewProduct requestCreateNewProduct = convertLocalProductToRequestProduct(currentLocal);

                                            SingleWebApi.getInstance().updateProduct(requestCreateNewProduct).blockingAwait();

                                            syncLocal(context, currentLocal);
                                            // update remote data
                                        }
                                    }
                                } else if (currentExternal.product_id_on_device < currentLocal.uid) {
                                    localData.previous();

                                    Product product = convertExternalProductToLocal(currentExternal);
                                    product.sync = true;

                                    synchronized (RoomDB.getInstance(context)) {
                                        RoomDB.getInstance(context).productDao().insertAll(product);
                                    }
                                    // add product to local db
                                } else {
                                    externalData.previous();

                                    if (currentLocal.sync || currentLocal.deleted) {

                                        synchronized (RoomDB.getInstance(context)) {
                                            RoomDB.getInstance(context).productDao().delete(currentLocal);
                                        }
                                        // delete from local
                                    } else {

                                        sendProductToServer(context, localProduct, currentLocal);

//                                        RequestCreateNewProduct requestCreateNewProduct = convertLocalProductToRequestProduct(currentLocal);
//
//                                        ExternalProductId newId = SingleWebApi.getInstance().sendProduct(requestCreateNewProduct).blockingGet();
//
//                                        syncLocal(context, currentLocal);

                                        // send to server
                                    }
                                }
                            } else {
                                Product product = convertExternalProductToLocal(currentExternal);
                                product.sync = true;

                                synchronized (RoomDB.getInstance(context)) {
                                    RoomDB.getInstance(context).productDao().insertAll(product);
                                }

                                // add product to local db
                            }
                        }

                        while (localData.hasNext()) {
                            Product currentLocal = localData.next();

                            if (currentLocal.sync || currentLocal.deleted) {

                                synchronized (RoomDB.getInstance(context)) {
                                    RoomDB.getInstance(context).productDao().delete(currentLocal);
                                }
                                // delete from local
                            } else {
                                sendProductToServer(context, localProduct, currentLocal);

//                                RequestCreateNewProduct requestCreateNewProduct = convertLocalProductToRequestProduct(currentLocal);
//
//                                SingleWebApi.getInstance().sendProduct(requestCreateNewProduct).blockingAwait();
//
//                                syncLocal(context, currentLocal);

                                // send product to server
                            }
                        }

                         */
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                    }

                    inSync.set(false);

                    while (!productToRemove.isEmpty()) {
                        long currentId = productToRemove.remove(0);
                        synchronized (RoomDB.getInstance(context)) {

                            Product currentProduct = RoomDB.getInstance(context).productDao().getById(currentId).blockingGet();

                            currentProduct.deleted = true;
                            RoomDB.getInstance(context).productDao().update(currentProduct);

                            RoomDB.getInstance(context).productDao().updateRightNode(currentProduct.leftNodeId, currentProduct.rightNodeId);

                            RoomDB.getInstance(context).productDao().updateLeftNode(currentProduct.rightNodeId, currentProduct.leftNodeId);
                        }
                    }

                    try {
                        Thread.sleep(SYNC_DELAY);
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }

            }).start();
        }
    }

    private static Product sendProductToServer(Context context, List<Product> localProduct, Product product,
                                               Func<RequestCreateNewProduct, RequestCreateNewProduct> beforeSend) {

        RequestCreateNewProduct requestCreateNewProduct = convertLocalProductToRequestProduct(product);

        ExternalProductId idOnServer = SingleWebApi.getInstance().sendProduct(beforeSend.apply(requestCreateNewProduct)).blockingGet();

        if (idOnServer.new_product_id != product.uid) {
            Product testProduct = new Product();
            testProduct.uid = idOnServer.new_product_id;

            int index = Collections.binarySearch(localProduct, testProduct, (f, s) -> Long.compare(f.uid, s.uid));

            Log.e("sync", "pj no sosi");
            synchronized (RoomDB.getInstance(context)) {
                Log.e("sync", "in server sync");

                if (index >= 0) {
//                    long maxId = RoomDB.getInstance(context).productDao().getMaxId().blockingGet();
                    long maxId = Collections.max(localProduct, (f, s) -> Long.compare(f.uid, s.uid)).uid;

                    Product movableProduct = localProduct.get(index);

                    movableProduct.uid = maxId + 1;

                    Product productInArray = findProduct(localProduct, (v) -> v.uid == movableProduct.leftNodeId);

                    RoomDB.getInstance(context).productDao().updateRightNode(movableProduct.leftNodeId, movableProduct.uid);
//                    RoomDB.getInstance(context).productDao().updateSyncStatus(movableProduct.leftNodeId, false);
                    if (productInArray != null) {
                        productInArray.rightNodeId = product.uid;
//                        productInArray.sync = false;
                    }

                    productInArray = findProduct(localProduct, (v) -> v.uid == movableProduct.rightNodeId);

                    RoomDB.getInstance(context).productDao().updateLeftNode(movableProduct.rightNodeId, movableProduct.uid);
//                    RoomDB.getInstance(context).productDao().updateSyncStatus(movableProduct.rightNodeId, false);
                    if (productInArray != null) {
                        productInArray.leftNodeId = product.uid;
//                        productInArray.sync = false;
                    }

                    RoomDB.getInstance(context).productDao().insertAll(movableProduct);

                    // при перемещении все связи идут к хуям

                }

                RoomDB.getInstance(context).productDao().delete(product);

                product.uid = idOnServer.new_product_id;

                Product productInArray = findProduct(localProduct, (v) -> v.uid == product.leftNodeId);

                RoomDB.getInstance(context).productDao().updateRightNode(product.leftNodeId, product.uid);
//                RoomDB.getInstance(context).productDao().updateSyncStatus(product.leftNodeId, false);
                if (productInArray != null) {
                    productInArray.rightNodeId = product.uid;
//                    productInArray.sync = false;
                }

                productInArray = findProduct(localProduct, (v) -> v.uid == product.rightNodeId);

                RoomDB.getInstance(context).productDao().updateLeftNode(product.rightNodeId, product.uid);
//                RoomDB.getInstance(context).productDao().updateSyncStatus(product.rightNodeId, false);
                if (productInArray != null) {
                    productInArray.leftNodeId = product.uid;
//                    productInArray.sync = false;
                }

                RoomDB.getInstance(context).productDao().insertAll(product);

            }
        }

        syncLocal(context, product);

        return product;
    }

    private static void syncLocal(Context context, Product product) {
        product.sync = true;
        product.localOnly = false;

        synchronized (RoomDB.getInstance(context)) {
            RoomDB.getInstance(context).productDao().update(product);
        }
    }

    private static Product findProduct(List<Product> localProduct, Func<Product, Boolean> func) {
        for (Product product : localProduct) {
            if (func.apply(product)) {
                return product;
            }
        }
        return null;
    }

    private static boolean equals(ExternalProduct externalProduct, Product product) {
        return externalProduct.product_id_on_device == product.uid &&
//                externalProduct.left_node_id == product.leftNodeId &&
//                externalProduct.right_node_id == product.rightNodeId &&
                externalProduct.image_url.equals(product.imageUrl) &&
                externalProduct.product_title.equals(product.productTitle) &&
                externalProduct.product_subtitle.equals(product.productSubtitle) &&
                externalProduct.expiration_date == product.expirationDate;
    }

    private static Product convertExternalProductToLocal(ExternalProduct externalProduct) {
        Product product = new Product();

        product.uid = externalProduct.product_id_on_device;
        product.leftNodeId = externalProduct.left_node_id;
        product.rightNodeId = externalProduct.right_node_id;
        product.imageUrl = externalProduct.image_url;
        product.productTitle = externalProduct.product_title;
        product.productSubtitle = externalProduct.product_subtitle;
        product.expirationDate = externalProduct.expiration_date;
        product.startTrackingDate = System.currentTimeMillis();
        product.lastNotificationDate = product.startTrackingDate;
        product.sync = false;
        product.deleted = false;
        product.localOnly = false;

        return product;
    }

    private static RequestCreateNewProduct convertLocalProductToRequestProduct(Product product) {
        RequestCreateNewProduct reqProduct = new RequestCreateNewProduct();

        reqProduct.login = CurrentUser.getInstance().getLogin();
        reqProduct.password = CurrentUser.getInstance().getPassword();
        reqProduct.product_id_on_device = product.uid;
        reqProduct.left_node_id = product.leftNodeId;
        reqProduct.right_node_id = product.rightNodeId;
        reqProduct.image_url = product.imageUrl;
        reqProduct.product_title = product.productTitle;
        reqProduct.product_subtitle = product.productSubtitle;
        reqProduct.expiration_date = product.expirationDate;
        reqProduct.start_tracking_date = System.currentTimeMillis();

        return reqProduct;
    }
}

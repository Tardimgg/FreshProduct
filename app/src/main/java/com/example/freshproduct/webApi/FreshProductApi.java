package com.example.freshproduct.webApi;

import com.example.freshproduct.models.ExternalProductId;
import com.example.freshproduct.models.ExternalProducts;
import com.example.freshproduct.models.Neighbors;
import com.example.freshproduct.models.Receipt;
import com.example.freshproduct.models.RequestCreateNewProduct;
import com.example.freshproduct.models.RequestDeleteProduct;
import com.example.freshproduct.models.SimpleResponse;
import com.example.freshproduct.models.ArrayResponse;
import com.example.freshproduct.models.User;

import io.reactivex.Completable;
import io.reactivex.Single;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface FreshProductApi {

    String HOST = "https://fresh-product.herokuapp.com";

    @POST("/register")
    Single<SimpleResponse> register(@Body User user);

//    @HTTP(method = "POST", path = "/product", hasBody = true)w
    @POST("/get_products")
    Single<ExternalProducts> getProducts(@Body User user);

    @POST("/product")
    Single<ExternalProductId> sendProduct(@Body RequestCreateNewProduct product);

    @PATCH("/product")
    Completable updateProduct(@Body RequestCreateNewProduct product);

    @POST("/delete_product")
    Completable deleteProduct(@Body RequestDeleteProduct receipt);

    @POST("/check_registration")
    Single<SimpleResponse> checkRegistration(@Body User user);

    @GET("/get_logo/{name}")
    Single<ArrayResponse> getLogo(@Path(value = "name") String name);

    @POST("/get_receipt_info")
    Single<ArrayResponse> getReceiptInfo(@Body Receipt receipt);

    @POST("/update_neighbors_products")
    Completable updateNeighbors(@Body Neighbors neighbors);

}

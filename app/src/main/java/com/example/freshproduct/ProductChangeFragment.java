package com.example.freshproduct;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.freshproduct.addProduct.CompletingAddProductListener;
import com.example.freshproduct.addProduct.createProduct.CompletingCreateProductListener;
import com.example.freshproduct.addProduct.createProduct.CreateProductFragment;
import com.example.freshproduct.addProduct.makePhoto.MakePhotoFragment;
import com.example.freshproduct.dataBase.Product;
import com.example.freshproduct.dataBase.RoomDB;
import com.example.freshproduct.models.ArrayResponse;
import com.example.freshproduct.webApi.SingleWebApi;

import java.util.Date;
import java.util.List;
import java.util.Observer;

import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;


public class ProductChangeFragment extends Fragment implements CompletingCreateProductListener {


    private static final String PRODUCT_ARG = "PRODUCT_ARG";
//    private static final String SUBTITLE_ARG = "SUBTITLE_ARG";
//    private static final String TIME_ARG = "TIME_ARG";


    private SimpleObservable changeCompleted;


    private Product initialProduct;

    public ProductChangeFragment() {
        changeCompleted = new SimpleObservable();
        // Required empty public constructor
    }

    public void subscribeToCompleteChange(Observer observer) {
        changeCompleted.addObserver(observer);
    }



    public static ProductChangeFragment newInstance(Product product) {

        ProductChangeFragment fragment = new ProductChangeFragment();

        Bundle bundle = new Bundle();

        bundle.putSerializable(PRODUCT_ARG, product);
        fragment.setArguments(bundle);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = getArguments();
        if (bundle != null) {

            initialProduct = (Product) bundle.getSerializable(PRODUCT_ARG);

            Fragment fragmentToAttach = CreateProductFragment.newInstance(
                    initialProduct.productTitle,
                    initialProduct.productSubtitle,
                    initialProduct.expirationDate,
                    this
            );

            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            ft.add(R.id.inner_fragment, fragmentToAttach);
            ft.commit();
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_product_change, container, false);
    }

    @Override
    public void completingCreateProductEvent(String title, String subtitle, long expirationDate) {
        Date currentDate = new Date();

        boolean changed = false;

        if (!initialProduct.productTitle.equals(title)) {
            initialProduct.productTitle = title;
            changed = true;
        }

        if (initialProduct.expirationDate != expirationDate) {
            initialProduct.expirationDate = expirationDate;

            initialProduct.startTrackingDate = currentDate.getTime();
            initialProduct.lastNotificationDate = currentDate.getTime();
            changed = true;
        }

        if (!initialProduct.productSubtitle.equals(subtitle)) {
            initialProduct.productSubtitle = subtitle;
            initialProduct.sync = false;

            SingleWebApi.getInstance()
                    .getLogo(initialProduct.productSubtitle)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe(new DisposableSingleObserver<ArrayResponse>() {
                        @Override
                        public void onSuccess(ArrayResponse urls) {
                            if (urls.response.size() > 0) {
                                initialProduct.imageUrl = urls.response.get(0);
                            } else {
                                initialProduct.imageUrl = "";
                            }
                            synchronized (RoomDB.getInstance(getContext())) {

                                RoomDB.getInstance(getContext()).productDao().update(initialProduct);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                            Log.e("get logo", e.toString());

                            initialProduct.imageUrl = "";
                            synchronized (RoomDB.getInstance(getContext())) {
                                RoomDB.getInstance(getContext()).productDao().update(initialProduct);
                            }
                        }
                    });
        } else if (changed){
            initialProduct.sync = false;

            new Thread(() -> {

                synchronized (RoomDB.getInstance(getContext())) {
                    RoomDB.getInstance(getContext()).productDao().update(initialProduct);
                }
            }).start();
        }
        changeCompleted.notifyObservers();

    }
}
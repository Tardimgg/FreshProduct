package com.example.freshproduct.addProduct;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.os.Looper;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.freshproduct.Result;
import com.example.freshproduct.addProduct.createProduct.CompletingCreateProductListener;
import com.example.freshproduct.addProduct.createProduct.CreateProductFragment;
import com.example.freshproduct.R;
import com.example.freshproduct.dataBase.Product;
import com.example.freshproduct.dataBase.RoomDB;
import com.example.freshproduct.listProducts.ListProductsFragment;
import com.example.freshproduct.addProduct.makePhoto.CompletingPhotoMakingListener;
import com.example.freshproduct.addProduct.makePhoto.MakePhotoFragment;
import com.example.freshproduct.addProduct.makePhoto.ProductReadingListener;

import java.util.LinkedList;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;


public class AddProductFragment extends Fragment implements CompletingPhotoMakingListener, ProductReadingListener, CompletingCreateProductListener {

    private static final String COMPLETING_ADD_PRODUCT_PARAM = "COMPLETING_ADD_PRODUCT_PARAM";
    private static final String WITH_SCANNING_PARAM = "WITH_SCANNING_PARAM";


    private CompletingAddProductListener completingAddProductListener;
    private LinkedList<Pair<Pair<String, String>, Long>> scannedProducts = new LinkedList<>();

    public AddProductFragment() {
        // Required empty public constructor
    }


    public static AddProductFragment newInstance(boolean withScanning, CompletingAddProductListener addProductListener) {
        AddProductFragment fragment = new AddProductFragment();
        Bundle args = new Bundle();
        args.putSerializable(COMPLETING_ADD_PRODUCT_PARAM, addProductListener);
        args.putBoolean(WITH_SCANNING_PARAM, withScanning);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            completingAddProductListener = (CompletingAddProductListener) getArguments().getSerializable(COMPLETING_ADD_PRODUCT_PARAM);
            Fragment fragmentToAttach;
            if (getArguments().getBoolean(WITH_SCANNING_PARAM)) {
//                requireActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.black));
                fragmentToAttach = MakePhotoFragment.newInstance(this, this);
                ((MakePhotoFragment) fragmentToAttach).setInsets(ListProductsFragment.insetsBtn);
            } else {
//                requireActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.white));
                fragmentToAttach = CreateProductFragment.newInstance("", "", -1, this);
                ((CreateProductFragment) fragmentToAttach).setInsets(ListProductsFragment.insetsList);
            }
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            ft.add(R.id.add_product, fragmentToAttach);
            ft.commit();
        }


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_product, container, false);
    }

    @Override
    public void completingPhotoMakingEvent() {
//        requireActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.white));

        Pair<Pair<String, String>, Long> product = scannedProducts.pollFirst();
        CreateProductFragment createProduct;
        if (product != null) {
            if (product.first != null) {
                createProduct = CreateProductFragment.newInstance(product.first.first,
                        product.first.second,
                        product.second,
                        this);
            } else {
                createProduct = CreateProductFragment.newInstance("", "", product.second, this);
            }
        } else {
            createProduct = CreateProductFragment.newInstance("", "", -1, this);
        }
        createProduct.setInsets(ListProductsFragment.insetsList);
        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
        ft.replace(R.id.add_product, createProduct);
        ft.commit();
    }

    @Override
    public void productReadyEvent(Result<Pair<String, String>, String> name, long time) {
        if (name.isHaveValue) {
            scannedProducts.addLast(Pair.create(name.value, time));
        } else {
            scannedProducts.addLast(Pair.create(null, System.currentTimeMillis()));
        }
    }

    @Override
    public void completingCreateProductEvent(String title, String subtitle, long expirationDate) {
        RoomDB.getInstance(getContext()).productDao()
                .getLastNodeId()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.from(Looper.myLooper()))
                .subscribe(new DisposableSingleObserver<List<Long>>() {
                    @Override
                    public void onSuccess(List<Long> uid) {
                        Product product = new Product();
                        product.productTitle = title;
                        product.productSubtitle = subtitle;
                        product.expirationDate = expirationDate;
                        product.leftNodeId = uid.size() == 0 ? -1 : uid.get(0);
                        product.rightNodeId = -1;

                        new Thread(() -> {
                            long[] newIds = RoomDB.getInstance(getContext()).productDao().insertAll(product);
                            RoomDB.getInstance(getContext()).productDao().updateRightNode(product.leftNodeId, newIds[0]);
                        }).start();
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });

        if (scannedProducts.size() == 0) {
            completingAddProductListener.completingAddProduct();
        } else {
            completingPhotoMakingEvent();
        }
    }
}
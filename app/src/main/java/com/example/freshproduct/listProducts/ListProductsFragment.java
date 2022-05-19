package com.example.freshproduct.listProducts;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.freshproduct.MainActivity;
import com.example.freshproduct.R;
import com.example.freshproduct.addProduct.AddProductFragment;
import com.example.freshproduct.dataBase.Product;
import com.example.freshproduct.dataBase.RoomDB;
import com.example.freshproduct.databinding.FragmentListProductsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;


public class ListProductsFragment extends Fragment implements View.OnClickListener {

    private static final String ADD_PRODUCT_PARAM = "ADD_PRODUCT_PARAM";

    public static WindowInsetsCompat insetsBtn;
    public static WindowInsetsCompat insetsList;
    public static WindowInsetsCompat insetsEmptyMessage;

    // TODO: Rename and change types of parameters
    private NeedsAddProductListener needsAddProductListener;

    FragmentListProductsBinding binding;

    public ListProductsFragment() {
        // Required empty public constructor
    }

    public static ListProductsFragment newInstance(NeedsAddProductListener listener) {
        ListProductsFragment fragment = new ListProductsFragment();
        Bundle args = new Bundle();
        args.putSerializable(ADD_PRODUCT_PARAM, listener);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getAllData.dispose();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            needsAddProductListener = (NeedsAddProductListener) getArguments().getSerializable(ADD_PRODUCT_PARAM);
        }
    }

    Disposable getAllData;
    ProductsAdapter adapter;

    private void subscribeGetAllData() {
        getAllData = RoomDB.getInstance(getContext())
                .productDao()
                .getAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(products -> {
                    if (products.size() > 0) {
                        binding.emptyList.setVisibility(View.INVISIBLE);
                    }

                    HashMap<Long, Integer> dictId = new HashMap<>();

                    long startId = -1;
                    for (int i = 0; i < products.size(); ++i) {
                        if (products.get(i).leftNodeId == -1) {
                            startId = products.get(i).uid;
                        }
                        dictId.put(products.get(i).uid, i);
                    }

                    if (startId == -1 && products.size() != 0) {
                        Toast.makeText(getContext(), "Ошибка бд", Toast.LENGTH_LONG).show();
//                        recovery()
                        adapter.setData(products);
                        return;
                    }

                    List<Product> data = new LinkedList<>();

                    long nextId = startId;
                    while (nextId != -1) {
                        Product product = products.get(dictId.get(nextId));
                        data.add(product);

                        if (product.rightNodeId == nextId || data.size() > products.size()) {
                            Toast.makeText(getContext(), "Ошибка бд", Toast.LENGTH_LONG).show();
//                            recovery()
                            adapter.setData(products);
                            return;
                        }
                        nextId = product.rightNodeId;
                    }

                    adapter.setData(data);

                });
    }

    ItemTouchHelper helper;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentListProductsBinding.inflate(inflater, container, false);

        adapter = new ProductsAdapter(new LinkedList<>(), getContext());

        subscribeGetAllData();

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getContext());
        binding.listProducts.setLayoutManager(mLayoutManager);

        binding.listProducts.setAdapter(adapter);

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.DOWN | ItemTouchHelper.UP, ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT) {

            private volatile int startPosition = -1;

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (viewHolder.getAdapterPosition() != -1 && startPosition != -1) {
                    getAllData.dispose();
//                    adapter.updateData(startPosition, viewHolder.getAdapterPosition(), () -> subscribeGetAllData());
                    adapter.updateData(startPosition, viewHolder.getAdapterPosition(), null);
                }
                startPosition = -1;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                final int fromPos = viewHolder.getAdapterPosition();
                final int toPos = target.getAdapterPosition();
                if (startPosition == -1) {
                    startPosition = fromPos;
                }

                adapter.onItemMove(fromPos, toPos);
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                getAllData.dispose();
//                adapter.onItemDismiss(binding.addItemButton, viewHolder.getAdapterPosition(), insetsBtn,
//                        () -> subscribeGetAllData());
                adapter.onItemDismiss(binding.addItemButton, viewHolder.getAdapterPosition(), insetsBtn, null);

            }
        };
        helper = new ItemTouchHelper(callback);
        helper.attachToRecyclerView(binding.listProducts);

        binding.addItemButton.setOnClickListener(this);


        int actionBarHeight;
        TypedValue typedValue = new TypedValue();

        if (requireActivity().getTheme().resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(
                    typedValue.data,
                    getResources().getDisplayMetrics());
        } else {
            actionBarHeight = 0;
        }


        if (insetsList != null) {
            View v = binding.listProducts;
            v.setPadding(v.getPaddingLeft(),
                    v.getPaddingTop() + actionBarHeight >> 1,
                    v.getPaddingRight(),
                    v.getPaddingBottom() + insetsList.getSystemWindowInsets().bottom);
        }
        if (insetsEmptyMessage != null) {
            View v = binding.emptyList;
            v.setPadding(v.getPaddingLeft(),
                    v.getPaddingTop() + insetsEmptyMessage.getSystemWindowInsets().top,
                    v.getPaddingRight(),
                    v.getPaddingBottom() + insetsEmptyMessage.getSystemWindowInsets().bottom);
        }
        if (insetsBtn != null) {
            binding.addItemButton.setTranslationY(-insetsBtn.getSystemWindowInsets().bottom);
            binding.addItemButton.setTranslationX(-insetsBtn.getSystemWindowInsets().right);
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.addItemButton, (v, insets) -> {
            ListProductsFragment.insetsBtn = insets;
            v.setTranslationY(-insets.getSystemWindowInsets().bottom);
            v.setTranslationX(-insets.getSystemWindowInsets().right);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.listProducts, (v, insets) -> {
            ListProductsFragment.insetsList = insets;
            v.setPadding(v.getPaddingLeft(),
                    v.getPaddingTop() + actionBarHeight >> 1,
                    v.getPaddingRight(),
                    v.getPaddingBottom() + insets.getSystemWindowInsets().bottom);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.emptyList, (v, insets) -> {
            ListProductsFragment.insetsEmptyMessage = insets;
            v.setPadding(v.getPaddingLeft(),
                    v.getPaddingTop() + insets.getSystemWindowInsets().top,
                    v.getPaddingRight(),
                    v.getPaddingBottom() + insets.getSystemWindowInsets().bottom);
            return insets;
        });


        return binding.getRoot();
    }



    @Override
    public void onClick(View view) {
        needsAddProductListener.requiredAddProductEvent();
    }
}

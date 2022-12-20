package com.example.freshproduct.listProducts;

import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.freshproduct.GlobalInsets;
import com.example.freshproduct.dataBase.Product;
import com.example.freshproduct.dataBase.RoomDB;
import com.example.freshproduct.databinding.FragmentListProductsBinding;
import com.example.freshproduct.models.MinimalProduct;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;


public class ListProductsFragment extends Fragment implements View.OnClickListener {

    private static final String ADD_PRODUCT_PARAM = "ADD_PRODUCT_PARAM";
    private static final String CHANGE_PRODUCT_PARAM = "CHANGE_PRODUCT_PARAM";

//    public static WindowInsetsCompat insetsBtn;
//    public static WindowInsetsCompat insetsList;
//    public static WindowInsetsCompat insetsEmptyMessage;

    private NeedsAddProductListener needsAddProductListener;
    private NeedsChangeProductListener needsChangeProductListener;

    FragmentListProductsBinding binding;

    public ListProductsFragment() {
        // Required empty public constructor
    }

    public static ListProductsFragment newInstance(NeedsAddProductListener addListener, NeedsChangeProductListener changeListener) {
        ListProductsFragment fragment = new ListProductsFragment();
        Bundle args = new Bundle();
        args.putSerializable(ADD_PRODUCT_PARAM, addListener);
        args.putSerializable(CHANGE_PRODUCT_PARAM, changeListener);
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
            needsChangeProductListener = (NeedsChangeProductListener) getArguments().getSerializable(CHANGE_PRODUCT_PARAM);
        }
    }

    Disposable getAllData;
    ProductsAdapter adapter;

    private void subscribeGetAllData() {
        getAllData = RoomDB.getInstance(getContext())
                .productDao()
                .getAllMinimal()
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io())
//                .onBackpressureLatest()
//                .observeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .subscribe(products -> {

                    if (System.currentTimeMillis() - adapter.lastDelete > 5000) { // кароч эта проверка хуета, нужно придумать чет поумней

                        if (products.size() > 0) {
                            requireActivity().runOnUiThread(() -> binding.emptyList.setVisibility(View.INVISIBLE));
                        } else {
                            requireActivity().runOnUiThread(() -> binding.emptyList.setVisibility(View.VISIBLE));

                        }
                        HashMap<Long, Integer> dictId = new HashMap<>();

                        boolean haveNotDeleted = false;

                        long startId = -1;
                        for (int i = 0; i < products.size(); ++i) {
                            if (!products.get(i).deleted) {

                                haveNotDeleted = true;

                                if (products.get(i).leftNodeId == -1) {

                                    if (startId != -1) {
                                        requireActivity().runOnUiThread(() -> {
                                            Toast.makeText(getContext(), "Ошибка бд", Toast.LENGTH_LONG).show();
//                              recovery()
//                                            adapter.setData(products);
                                        });
                                        return;
                                    }

                                    startId = products.get(i).uid;
                                }
                                dictId.put(products.get(i).uid, i);
                            }
                        }

                        if (startId == -1 && products.size() != 0 && haveNotDeleted) {
                            requireActivity().runOnUiThread(() -> {

                                Toast.makeText(getContext(), "Ошибка бд", Toast.LENGTH_LONG).show();
//                        recovery()
//                                adapter.setData(products);
                            });
                            return;

                        }

                        List<MinimalProduct> data = new LinkedList<>();

                        long nextId = startId;
                        while (nextId != -1) {
                            Integer currentId = dictId.get(nextId);
                            if (currentId == null || products.size() <= currentId) {
                                requireActivity().runOnUiThread(() -> {

                                    Toast.makeText(getContext(), "Ошибка бд", Toast.LENGTH_LONG).show();
//                            recovery()
//                                    adapter.setData(products);
                                });

                                return;
                            }
                            MinimalProduct product = products.get(currentId);
                            data.add(product);

                            if (product.rightNodeId == nextId || data.size() > products.size()) {

                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(getContext(), "Ошибка бд", Toast.LENGTH_LONG).show();
//                            recovery()
//                                    adapter.setData(products);
                                });
                                return;
                            }
                            nextId = product.rightNodeId;
                        }

                        List<MinimalProduct> previousData = adapter.getData();


                        if (data.size() != previousData.size()) {
                            int b = 0;
                            requireActivity().runOnUiThread(() -> adapter.setData(data));
                        } else {
                            for (int i = 0; i < data.size(); ++i) {
                                if (!data.get(i).productTitle.equals(previousData.get(i).productTitle) ||
                                        !data.get(i).productSubtitle.equals(previousData.get(i).productSubtitle) ||
                                        data.get(i).expirationDate != previousData.get(i).expirationDate ||
                                        !data.get(i).imageUrl.equals(previousData.get(i).imageUrl)) {

                                    int a = 1;

                                    requireActivity().runOnUiThread(() -> adapter.setData(data));
                                    return;
                                }
                            }
                        }
                    }
                });
    }

    ItemTouchHelper helper;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentListProductsBinding.inflate(inflater, container, false);

        adapter = new ProductsAdapter(new LinkedList<>(), getContext());

//        binding.fragmentList.setColorSchemeColors(
//          Color.RED, Color.GREEN, Color.BLUE, Color.CYAN);
//
//
//        binding.fragmentList.setOnRefreshListener(() -> {

//            RoomDB.getInstance(getC)

//            binding.fragmentList.setRefreshing(false);
//        });

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
                if (direction == ItemTouchHelper.RIGHT) {

//                    getAllData.dispose();
//                adapter.onItemDismiss(binding.addItemButton, viewHolder.getAdapterPosition(), insetsBtn,
//                        () -> subscribeGetAllData());
                    adapter.onItemDismiss(binding.addItemButton, viewHolder.getAdapterPosition(), GlobalInsets.getInstance().getInsets(), null);
                } else {
                    if (needsChangeProductListener != null) {
                        synchronized (RoomDB.getInstance(getContext())) {

                            RoomDB.getInstance(getContext()).productDao().getById(adapter.getItem(viewHolder.getAdapterPosition()).uid)
                                    .subscribeOn(Schedulers.io())
                                    .subscribe(new DisposableSingleObserver<Product>() {
                                        @Override
                                        public void onSuccess(Product product) {
                                            needsChangeProductListener.requiredChangeProductEvent(product);
                                        }

                                        @Override
                                        public void onError(Throwable e) {

                                        }
                                    });
                        }
                    }
                }

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


//        if (insetsList != null) {
//            View v = binding.listProducts;
//            v.setPadding(v.getPaddingLeft(),
//                    v.getPaddingTop() + actionBarHeight >> 1,
//                    v.getPaddingRight(),
//                    v.getPaddingBottom() + insetsList.getSystemWindowInsets().bottom);
//        }
//        if (insetsEmptyMessage != null) {
//            View v = binding.emptyList;
//            v.setPadding(v.getPaddingLeft(),
//                    v.getPaddingTop() + insetsEmptyMessage.getSystemWindowInsets().top,
//                    v.getPaddingRight(),
//                    v.getPaddingBottom() + insetsEmptyMessage.getSystemWindowInsets().bottom);
//        }
//        if (insetsBtn != null) {
//            binding.addItemButton.setTranslationY(-insetsBtn.getSystemWindowInsets().bottom);
//            binding.addItemButton.setTranslationX(-insetsBtn.getSystemWindowInsets().right);
//        }

        GlobalInsets globalInsets = GlobalInsets.getInstance();

        globalInsets.subscribeToInsets(insets -> {
            View v = binding.listProducts;
            v.setPadding(v.getPaddingLeft(),
                    v.getPaddingTop() + actionBarHeight >> 1,
                    v.getPaddingRight(),
                    insets.getSystemWindowInsets().bottom);


            v = binding.emptyList;
            v.setPadding(v.getPaddingLeft(),
                    v.getPaddingTop() + insets.getSystemWindowInsets().top,
                    v.getPaddingRight(),
                    insets.getSystemWindowInsets().bottom);

            binding.addItemButton.setTranslationY(-insets.getSystemWindowInsets().bottom);
            binding.addItemButton.setTranslationX(-insets.getSystemWindowInsets().right);

//            int prev = binding.fragmentList.getProgressViewStartOffset();
//            binding.fragmentList.setProgressViewOffset(false, prev, insets.getSystemWindowInsets().top);
        });


        ViewCompat.setOnApplyWindowInsetsListener(binding.addItemButton, (v, insets) -> {
            GlobalInsets.getInstance().setInsets(insets);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.listProducts, (v, insets) -> {
            GlobalInsets.getInstance().setInsets(insets);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.emptyList, (v, insets) -> {
            GlobalInsets.getInstance().setInsets(insets);
            return insets;
        });

        binding.listProducts.requestApplyInsets();

        return binding.getRoot();
    }


    @Override
    public void onClick(View view) {
        if (needsAddProductListener != null) {
            if (getActivity() == null) {
                Log.e("add product btn", "activity is null");
                Toast.makeText(requireContext(), "activity is null", Toast.LENGTH_LONG).show();
            } else if (getActivity().getApplicationInfo() == null) {
                Log.e("add product btn", "appinfo is null");
                Toast.makeText(requireContext(), "appinfo is null", Toast.LENGTH_LONG).show();
            } else {
                needsAddProductListener.requiredAddProductEvent();
            }

        } else {
            Toast.makeText(requireContext(), "needsAddProductListener is null", Toast.LENGTH_LONG).show();
        }
    }
}

package com.example.freshproduct.listProducts;

import static com.example.freshproduct.MathFunc.convertDpToPixel;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.freshproduct.R;
import com.example.freshproduct.SimpleObservable;
import com.example.freshproduct.dataBase.Product;
import com.example.freshproduct.dataBase.ProductDao;
import com.example.freshproduct.dataBase.RoomDB;
import com.example.freshproduct.databinding.ProductTagBinding;
import com.example.freshproduct.models.MinimalProduct;
import com.example.freshproduct.webApi.PicassoWithCaching;
import com.google.android.material.snackbar.Snackbar;
import com.squareup.picasso.Callback;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProductsAdapter extends RecyclerView.Adapter<ProductsAdapter.MyViewHolder> implements ItemTouchHelperAdapter {

    private List<MinimalProduct> dataModelList;
    private final Context context;

    public ProductsAdapter(List<MinimalProduct> modelList, Context context) {
        dataModelList = modelList;
        this.context = context;
    }

    public void setData(List<MinimalProduct> list) {
        dataModelList = list;
        notifyDataSetChanged();
    }

    public List<MinimalProduct> getData() {
        return dataModelList;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate out card list item

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.product_tag, parent, false);
        // Return a new view holder

        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        // Bind data for the item at position

        holder.bindData(dataModelList.get(position), context, position);
    }

    @Override
    public int getItemCount() {
        // Return the total number of items
        return dataModelList.size();
    }

    public MinimalProduct getItem(int index) {
        return dataModelList.get(index);
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(dataModelList, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(dataModelList, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    private void deleteValueOnDB(MinimalProduct product, int position, OperationCompletionListener listener) {

        lastDelete = System.currentTimeMillis();
       /* new Thread(() -> {

            synchronized (RoomDB.getInstance(context)) {

                ProductDao db = RoomDB.getInstance(context).productDao();

                if (position != 0) {
                    db.updateRightNode(dataModelList.get(position - 1).uid,
                            position != dataModelList.size() - 1 ? dataModelList.get(position).uid : -1);

//                db.updateSyncStatus(dataModelList.get(position - 1).uid, false);
                }
                if (position != dataModelList.size() - 1) {
                    db.updateLeftNode(dataModelList.get(position).uid,
                            position != 0 ? dataModelList.get(position - 1).uid : -1);

//                db.updateSyncStatus(dataModelList.get(position).uid, false);
                }

                product.deleted = true;
                db.update(product);
            }

            if (listener != null) {
                listener.operationСompleted();
            }
        }).start();

        */

        RoomDB.removeProduct(context, product.uid, listener);


    }

    public AtomicBoolean needToDelete = new AtomicBoolean(false);
    private Snackbar snackbar;

    private void deleteValue(View view, int position, WindowInsetsCompat insets, OperationCompletionListener listener) {
        needToDelete.set(true);
        MinimalProduct product = dataModelList.get(position);
        dataModelList.remove(position);
        notifyItemRemoved(position);
        snackbar = Snackbar.make(view, product.productTitle, 3000).setAction("Отменить удаление", v -> {
            needToDelete.set(false);
            dataModelList.add(position, product);

            notifyItemInserted(position);

            if (listener != null) {
                listener.operationCompleted();
            }
        });
        snackbar.addCallback(new Snackbar.Callback() {

            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (needToDelete.get()) {
                    deleteValueOnDB(product, position, () -> {
                        needToDelete.set(false);
                        listener.operationCompleted();
                    });
                }
            }
        });

        snackbar.getView().setTranslationY(-insets.getSystemWindowInsets().bottom);
        snackbar.getView().setTranslationX(-insets.getSystemWindowInsets().right);

        snackbar.show();
    }

    OperationCompletionListener deleteListener;
    volatile SimpleObservable deleteListenerObservable = new SimpleObservable();
    AtomicBoolean previousDeletionSuccessful = new AtomicBoolean(true);

    public long lastDelete = -1;


    @Override
    public void onItemDismiss(View view, int position, WindowInsetsCompat insets, OperationCompletionListener listener) {
        if (!previousDeletionSuccessful.get()) {
            deleteListenerObservable.addObserver((observable, o) -> {
                deleteListenerObservable.deleteObservers();

                deleteListener = () -> {
                    previousDeletionSuccessful.set(true);
                    if (listener != null) {
                        listener.operationCompleted();
                    }
                    deleteListenerObservable.notifyObservers();
                };

                previousDeletionSuccessful.set(false);
                new Handler(Looper.getMainLooper()).post(() -> deleteValue(view, position, insets, deleteListener));

            });
            snackbar.dismiss();

        } else {
            deleteListener = () -> {
                previousDeletionSuccessful.set(true);
                if (listener != null) {
                    listener.operationCompleted();
                }
                deleteListenerObservable.notifyObservers();
            };
            previousDeletionSuccessful.set(false);
            deleteValue(view, position, insets, deleteListener);
        }
    }

    @Override
    public void updateData(int oldPosition, int position, OperationCompletionListener listener) {
        new Thread(() -> {
            ProductDao db = RoomDB.getInstance(context).productDao();

            long leftNode = -1;
            long rightNode = -1;
            if (position != 0) {
                leftNode = dataModelList.get(position - 1).uid;
            }
            if (position != dataModelList.size() - 1) {
                rightNode = dataModelList.get(position + 1).uid;
            }
            db.updatePosition(dataModelList.get(position).uid, leftNode, rightNode);

            for (int pos : new int[]{oldPosition, position}) {
                if (pos != 0) {
                    db.updateRightNode(dataModelList.get(pos - 1).uid,
                            dataModelList.get(pos).uid);

                }
                db.updateLeftNode(dataModelList.get(pos).uid,
                        pos != 0 ? dataModelList.get(pos - 1).uid : -1);


                if (pos != dataModelList.size() - 1) {
                    db.updateLeftNode(dataModelList.get(pos + 1).uid,
                            dataModelList.get(pos).uid);
                }
                db.updateRightNode(dataModelList.get(pos).uid,
                        pos != dataModelList.size() - 1 ? dataModelList.get(pos + 1).uid : -1);
            }
            if (listener != null) {
                listener.operationCompleted();
            }

        }).start();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {

        private ProductTagBinding binding;
        private Context context;
        private MinimalProduct product;

        private volatile String lastImageUrl;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);

//            itemView.findViewById(R.id.delete_button).setOnClickListener(this);
            binding = ProductTagBinding.bind(itemView);
        }

        public void bindData(MinimalProduct dataModel, Context context, int position) {
            this.context = context;
            this.product = dataModel;
            //cardImageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.list_image));
            binding.mainProductTag.setText(dataModel.productTitle);
            binding.subTitle.setText(dataModel.productSubtitle);

            if (dataModel.expirationDate == -1) {
                binding.expirationDate.setTextColor(Color.BLACK);
                binding.expirationDate.setText("?");

                binding.getRoot().setBackgroundResource(R.drawable.fresh_product);

            } else {
                Date date = new Date();
                double delta = Math.floor((double) ((dataModel.expirationDate - date.getTime()) / 1000 / 60 / 60) / 24);

                binding.expirationDate.setText(Integer.toString((int) delta));

                if (delta <= 3 && dataModel.expirationDate != -1) {
                    binding.expirationDate.setTextColor(Color.RED);
//                binding.getRoot().setStrokeColor(Color.RED);
//                int color = (255 & 0xff) << 24 | (255 & 0xff) << 16 | (230 & 0xff) << 8 | (230 & 0xff);
//                binding.getRoot().setBackgroundColor(color);
                    binding.getRoot().setBackgroundResource(R.drawable.stale_product);
                } else {
                    binding.expirationDate.setTextColor(Color.BLACK);
                    binding.getRoot().setBackgroundResource(R.drawable.fresh_product);

                }
            }

            binding.imageProduct.setImageResource(R.drawable.ic_outline_food_bank_24);

            lastImageUrl = dataModel.imageUrl;

            if (!dataModel.imageUrl.trim().equals("")) {

                SimpleObservable loadImage = new SimpleObservable();

                loadImage.addObserver((observable, o) -> {
                    if (dataModel.imageUrl.equals(lastImageUrl)) {
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) binding.imageProduct.getLayoutParams();

                        int fiveDp = (int) convertDpToPixel(5, context);
                        int tenDp = (int) convertDpToPixel(10, context);

                        params.setMargins(fiveDp, tenDp, fiveDp, tenDp);

                        params.height = binding.rootTag.getHeight() - (int) convertDpToPixel(20, context);
                        binding.imageProduct.setLayoutParams(params);

//              Log.e("adapter", String.valueOf(binding.rootTag.getHeight()));


                        PicassoWithCaching.newInstance(context)
                                .load(dataModel.imageUrl)
//                            .networkPolicy(NetworkPolicy.OFFLINE)
                                .error(R.drawable.ic_outline_food_bank_24)
                                .placeholder(R.drawable.ic_outline_food_bank_24)
                                .into(binding.imageProduct, new Callback() {
                                    @Override
                                    public void onSuccess() {
                                        loadImage.deleteObservers();
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        Log.e("picasso load error", e.toString());
                                        Handler handler = new Handler();
                                        handler.postDelayed(loadImage::notifyObservers, 5000);
                                    }
                                });
                    }
                });

                Handler handler = new Handler();
                handler.postDelayed(loadImage::notifyObservers, 200);


            }
        }
    }
}

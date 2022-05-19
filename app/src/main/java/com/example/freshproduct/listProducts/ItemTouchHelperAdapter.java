package com.example.freshproduct.listProducts;

import android.view.View;

import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

public interface ItemTouchHelperAdapter {

    void onItemMove(int fromPosition, int toPosition);

    void onItemDismiss(View view, int position,  WindowInsetsCompat insets, OperationCompletionListener listener);

    void updateData(int oldPosition, int position, OperationCompletionListener listener);


}

package com.example.freshproduct.addProduct.createProduct;

import android.os.Bundle;

import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.DatePicker;

import com.example.freshproduct.GlobalInsets;
import com.example.freshproduct.databinding.FragmentCreateProductBinding;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import io.reactivex.disposables.Disposable;

public class CreateProductFragment extends Fragment {

    private static final String ARG_TITLE = "ARG_TITLE";
    private static final String ARG_SUBTITLE = "ARG_SUBTITLE";
    private static final String ARG_TIME = "ARG_TIME";
    private static final String ARG_CREATE_PRODUCT = "ARG_CREATE_PRODUCT";

    private FragmentCreateProductBinding binding;

    private String title;
    private String subtitle;
    private long initTime;
    private CompletingCreateProductListener completingCreateProductListener;

    Disposable insetsListener;


    @Override
    public void onDestroy() {
        super.onDestroy();

        if (insetsListener != null) {
            insetsListener.dispose();
        }
    }

    public CreateProductFragment() {
        // Required empty public constructor
    }

    public static CreateProductFragment newInstance(String title, String subtitle, long time, CompletingCreateProductListener listener) {
        CreateProductFragment fragment = new CreateProductFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        args.putLong(ARG_TIME, time);
        args.putSerializable(ARG_CREATE_PRODUCT, listener);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            title = getArguments().getString(ARG_TITLE);
            subtitle = getArguments().getString(ARG_SUBTITLE);
            initTime = getArguments().getLong(ARG_TIME);
            completingCreateProductListener = (CompletingCreateProductListener) getArguments().getSerializable(ARG_CREATE_PRODUCT);
        }
    }

    private boolean changedTime = false;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentCreateProductBinding.inflate(inflater, container, false);

        int actionBarHeight;
        TypedValue typedValue = new TypedValue();

        if (requireActivity().getTheme().resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(
                    typedValue.data,
                    getResources().getDisplayMetrics());
        } else {
            actionBarHeight = 0;
        }

        insetsListener = GlobalInsets.getInstance().subscribeToInsets(insets -> {

            binding.createProductLayout.setTranslationY(actionBarHeight >> 1);
            binding.createProductLayout.setTranslationX(insets.getSystemWindowInsets().right);

            binding.endCreateProduct.setTranslationY(-(actionBarHeight >> 1) - insets.getSystemWindowInsets().bottom);
//            binding.endCreateProduct.setTranslationY(-insets.getSystemWindowInsets().bottom << 1);
//            binding.endCreateProduct.setTranslationX(-insets.getSystemWindowInsets().right);
//            binding.endCreateProduct.setTranslationX(-insets.getSystemWindowInsets().right << 1);

//            binding.mainBackgroundImage.setTranslationY(-insetsLayout.getSystemWindowInsets().bottom);
//            binding.mainBackgroundImage.setTranslationX(-insetsLayout.getSystemWindowInsets().right);

        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.createProductLayout, (v, insets) -> {
            GlobalInsets.getInstance().setInsets(insets);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.endCreateProduct, (v, insets) -> {
            GlobalInsets.getInstance().setInsets(insets);
            return insets;
        });


        binding.editTitleProduct.setText(title);
        binding.editSubtitleProduct.setText(subtitle);
        Calendar calendar = Calendar.getInstance();

        if (initTime != -1) {
            calendar.setTimeInMillis(initTime);
//            binding.expirationDate.updateDate(calendar.get(Calendar.YEAR),
//                    calendar.get(Calendar.MONTH),
//                    calendar.get(Calendar.DAY_OF_MONTH));
        }
        binding.expirationDate.init(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH), (datePicker, year, month, dayOfMonth) -> changedTime = true);

        binding.endCreateProduct.setOnClickListener(new View.OnClickListener() {
            @Override
            public synchronized void onClick(View view) {
                binding.endCreateProduct.setEnabled(false);
                String currentTitle = binding.inputTitleProductLayout.getEditText().getText().toString();
                String currentSubtitle = binding.inputSubtitleProductLayout.getEditText().getText().toString();
                boolean isNotEmpty = true;
                if (currentTitle.isEmpty()) {
                    binding.inputTitleProductLayout.setError("Обязательное поле");
                    isNotEmpty = false;
                } if (currentSubtitle.isEmpty()) {
                    binding.inputSubtitleProductLayout.setError("Обязательное поле");
                    isNotEmpty = false;
                }
                if (isNotEmpty && completingCreateProductListener != null) {

                    if (initTime != -1 || changedTime) {

                        int year = binding.expirationDate.getYear();
                        int month = binding.expirationDate.getMonth();
                        int day = binding.expirationDate.getDayOfMonth();
                        Date date = new GregorianCalendar(year, month, day + 1).getTime();
                        completingCreateProductListener.completingCreateProductEvent(currentTitle, currentSubtitle, date.getTime());
                    } else {
                        completingCreateProductListener.completingCreateProductEvent(currentTitle, currentSubtitle, -1);
                    }
                }
            }
        });

        return binding.getRoot();
    }
}
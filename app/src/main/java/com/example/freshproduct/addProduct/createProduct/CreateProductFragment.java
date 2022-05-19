package com.example.freshproduct.addProduct.createProduct;

import android.os.Bundle;

import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.freshproduct.databinding.FragmentCreateProductBinding;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

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

    private static WindowInsetsCompat insetsLayout;


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

    public void setInsets(WindowInsetsCompat insets) {
        CreateProductFragment.insetsLayout = insets;
    }


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

        if (insetsLayout != null) {
            binding.createProductLayout.setTranslationY(actionBarHeight >> 1);
            binding.createProductLayout.setTranslationX(insetsLayout.getSystemWindowInsets().right);

            binding.endCreateProduct.setTranslationY(-insetsLayout.getSystemWindowInsets().bottom << 1);
            binding.endCreateProduct.setTranslationX(-insetsLayout.getSystemWindowInsets().right << 1);

//            binding.mainBackgroundImage.setTranslationY(-insetsLayout.getSystemWindowInsets().bottom);
//            binding.mainBackgroundImage.setTranslationX(-insetsLayout.getSystemWindowInsets().right);
        }
        binding.editTitleProduct.setText(title);
        binding.editSubtitleProduct.setText(subtitle);
        if (initTime != -1) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(initTime);
            binding.expirationDate.updateDate(calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
        }

        binding.endCreateProduct.setOnClickListener((v) -> {
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
                int year = binding.expirationDate.getYear();
                int month = binding.expirationDate.getMonth();
                int day = binding.expirationDate.getDayOfMonth();
                Date date = new GregorianCalendar(year, month, day + 1).getTime();
                completingCreateProductListener.completingCreateProductEvent(currentTitle, currentSubtitle, date.getTime());
            }
        });

        return binding.getRoot();
    }
}
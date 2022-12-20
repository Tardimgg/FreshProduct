package com.example.freshproduct;

import android.content.Context;
import android.util.DisplayMetrics;

public class MathFunc {

    public static float convertDpToPixel(float dp, Context context) {
        return dp * ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }

}

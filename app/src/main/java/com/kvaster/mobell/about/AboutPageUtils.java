package com.kvaster.mobell.about;

import android.content.Context;
import android.util.TypedValue;

class AboutPageUtils {
    static int getThemeAccentColor(Context context) {
        int colorAttr;
        colorAttr = android.R.attr.colorAccent;
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(colorAttr, outValue, true);
        return outValue.data;
    }
}

package com.kvaster.mobell;

import android.os.Bundle;
import android.app.Activity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class AboutActivity extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView v = findViewById(R.id.aboutview);
        v.setMovementMethod(LinkMovementMethod.getInstance());
        v.setText(Html.fromHtml(getString(R.string.about_text)));
    }
}

package com.kvaster.mobell;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import mehdi.sakout.aboutpage.AboutPage;
import mehdi.sakout.aboutpage.Element;

public class AboutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View aboutPage = new AboutPage(this)
                .isRTL(false)
                .setImage(R.drawable.ic_launcher_foreground)
                .addItem(new Element()
                        .setTitle(getString(R.string.about_version, BuildConfig.VERSION_NAME)))
                .addPlayStore("com.kvaster.mobell")
                .addGitHub("kvaster/mobell")
                .create();

        setContentView(aboutPage);
    }
}

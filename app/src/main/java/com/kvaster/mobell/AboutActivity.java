package com.kvaster.mobell;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import com.kvaster.mobell.about.AboutPage;
import com.kvaster.mobell.about.Element;

public class AboutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String version = "N/A";

        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            // do nothing
        }

        View aboutPage = new AboutPage(this)
                .setImage(R.drawable.mobell_ic_launcher_foreground)
                .addItem(new Element()
                        .setTitle(getString(R.string.mobell_about_version, version)))
                .addPlayStore("com.kvaster.mobell")
                .addGitHub("kvaster/mobell")
                .create();

        setContentView(aboutPage);
    }
}

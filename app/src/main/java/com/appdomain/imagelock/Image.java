package com.appdomain.imagelock;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.ImageView;

public class Image extends AppCompatActivity {

    private Toolbar imageToolbar;
    private ImageView image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        imageToolbar = (Toolbar) findViewById(R.id.imageToolBar);
        setSupportActionBar(imageToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        final Drawable upArrow = ContextCompat
                .getDrawable(getApplicationContext(), R.drawable.ic_keyboard_arrow_left_white_24dp);
        getSupportActionBar().setHomeAsUpIndicator(upArrow);

        Uri imageUri = Uri.parse(getIntent().getStringExtra("URI"));
        image = (ImageView) findViewById(R.id.image);
        image.setImageURI(imageUri);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}

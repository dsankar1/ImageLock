package com.appdomain.imagelock;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.Key;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Gallery extends AppCompatActivity {

    private static final int CAMERA = 1;
    private Toolbar galleryToolBar;
    private ListView galleryListView;
    private ProgressBar galleryProgressBar;
    private String username, key, token, authorities;
    private File mostRecentImage;
    private File localStorage;
    private ArrayList<File> images;
    private GalleryAdapter galleryAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        // Action Bar stuff
        galleryProgressBar = (ProgressBar) findViewById(R.id.galleryProgressBar);
        galleryProgressBar.getIndeterminateDrawable()
                .setColorFilter(Color.parseColor("#2D2D2D"), PorterDuff.Mode.MULTIPLY);
        galleryToolBar = (Toolbar) findViewById(R.id.galleryToolBar);
        setSupportActionBar(galleryToolBar);

        authorities = getApplicationContext().getPackageName() + ".fileprovider";
        username = getIntent().getExtras().getString("USERNAME");
        key = getIntent().getExtras().getString("KEY");
        token = getIntent().getExtras().getString("TOKEN");
        images = new ArrayList<>();
        localStorage = new File(getFilesDir(), username);

        // List view stuff
        galleryListView = (ListView) findViewById(R.id.galleryListView);
        galleryAdapter = new GalleryAdapter(getApplicationContext(), R.layout.gallery_item, images);
        galleryListView.setAdapter(galleryAdapter);
        galleryListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        createLocalStorage();
        setListViewEventListeners();
        new DownloadImages().execute();
    }

    private synchronized void showProgress(boolean show) {
        if (show) {
            galleryProgressBar.setVisibility(View.VISIBLE);
            galleryListView.setVisibility(View.GONE);
        }
        else {
            galleryProgressBar.setVisibility(View.GONE);
            galleryListView.setVisibility(View.VISIBLE);
            galleryListView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in));
        }
    }

    private void createLocalStorage() {
        if (localStorage.exists()) return;
        localStorage.mkdir();
    }

    private void deleteLocalStorage() {
        if (localStorage.exists()) {
            for (File image : localStorage.listFiles()) {
                image.delete();
            }
            localStorage.delete();
        }
    }

    private void setListViewEventListeners() {
        galleryListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(android.view.ActionMode actionMode, int i, long l, boolean b) {
                final int checkedCount = galleryListView.getCheckedItemCount();
                if (checkedCount > 0) {
                    actionMode.setTitle(checkedCount + " selected");
                }
                galleryAdapter.setSelectedState(i, galleryListView.isItemChecked(i));
            }

            @Override
            public boolean onCreateActionMode(android.view.ActionMode actionMode, Menu menu) {
                actionMode.getMenuInflater().inflate(R.menu.gallery_cab_menu, menu);
                return true;
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode actionMode, MenuItem item) {
                if (!isConnectedToInternet()) logout();
                switch (item.getItemId()) {
                    case R.id.action_delete:
                        delete();
                        actionMode.finish();
                        return true;
                    case R.id.action_select_all:
                        selectAll();
                        return true;
                    case R.id.action_logout:
                        logout();
                        return true;
                    default:
                        return true;
                }
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode actionMode) {
                galleryAdapter.removeAllSelections();
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode actionMode, Menu menu) {
                return false;
            }
        });

        galleryListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                File image = images.get(i);
                Uri imageUri = FileProvider.getUriForFile(getApplicationContext(), authorities, image);
                Intent imageIntent = new Intent(getApplicationContext(), Image.class);
                imageIntent.putExtra("URI", imageUri.toString());
                startActivity(imageIntent);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA && resultCode == RESULT_OK) {
            new UploadImage().execute(mostRecentImage);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gallery_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!isConnectedToInternet()) logout();
        switch (item.getItemId()) {
            case R.id.action_camera:
                launchCamera();
                return true;
            case R.id.action_select_all:
                selectAll();
                return true;
            case R.id.action_logout:
                logout();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private File createImageFile() {
        String filename = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpg";
        return new File(localStorage, filename);
    }

    private void launchCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null && hasCameraPermission()) {
            mostRecentImage = createImageFile();
            createLocalStorage();
            Uri imageUri = FileProvider.getUriForFile(getApplicationContext(), authorities, mostRecentImage);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            startActivityForResult(cameraIntent, CAMERA);
        }
        else {
            Snackbar cameraPermission = Snackbar.make(findViewById(android.R.id.content),
                    "Unable to Open Camera", Snackbar.LENGTH_LONG);
            cameraPermission.show();
        }
    }

    private void logout() {
        deleteLocalStorage();
        Intent login = new Intent(getApplicationContext(), Login.class);
        login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(login);
        this.finish();
    }

    private void delete() {
        SparseBooleanArray selected = galleryAdapter.getSelectedImages();
        ArrayList<File> toDelete = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            if (selected.valueAt(i)) {
                File image = images.get(selected.keyAt(i));
                toDelete.add(image);
            }
        }
        new DeleteTask().execute(toDelete);
    }

    private void selectAll() {
        for (int i = 0; i < galleryListView.getCount(); i++) {
            galleryListView.setItemChecked(i, true);
        }
    }

    private boolean hasCameraPermission() {
        int res = getApplicationContext().checkCallingOrSelfPermission("android.permission.CAMERA");
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    private boolean isConnectedToInternet() {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }

    private synchronized void updateImages() {
        images.clear();
        createLocalStorage();
        images.addAll(Arrays.asList(localStorage.listFiles()));
        Collections.reverse(images);
        galleryAdapter.notifyDataSetChanged();
    }

    private class DownloadImages extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            showProgress(true);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                HttpService.downloadImages(localStorage, token, key);
            } catch(Exception e) {
                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                        "Failed to Download Images", Snackbar.LENGTH_LONG);
                snackbar.show();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void noResults) {
            updateImages();
            showProgress(false);
        }

    }

    private class UploadImage extends AsyncTask<File, Void, Void> {

        @Override
        protected Void doInBackground(File... images) {
            File image = images[0];
            String encryptedFilename = image.getName().substring(0, image.getName().lastIndexOf(".")) + ".encrypted";
            File encrypted = new File(localStorage, encryptedFilename);
            encryptImage(image, encrypted);

            try {
                boolean success = HttpService.uploadImage(encrypted, token);
                encrypted.delete();
                if (!success) {
                    image.delete();
                    Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                            "Image failed to upload.", Snackbar.LENGTH_LONG);
                    snackbar.show();
                }
            } catch(Exception e) {
                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                        "Image failed to upload.", Snackbar.LENGTH_LONG);
                snackbar.show();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void noResults) {
            updateImages();
        }

        private void encryptImage(File source, File destination){
            try {
                Key secretKey = new SecretKeySpec(key.getBytes(), "AES");
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);

                FileInputStream inputStream = new FileInputStream(source);
                byte[] inputBytes = new byte[(int) source.length()];
                inputStream.read(inputBytes);

                byte[] outputBytes = cipher.doFinal(inputBytes);

                FileOutputStream outputStream = new FileOutputStream(destination);
                outputStream.write(outputBytes);

                inputStream.close();
                outputStream.close();

            } catch (Exception e) {
                Log.e("ERROR", e.toString());
            }
        }

    }

    private class DeleteTask extends AsyncTask<ArrayList<File>, Void, Integer> {

        @Override
        protected void onPreExecute() {
            showProgress(true);
        }

        @Override
        protected Integer doInBackground(ArrayList<File>... lists) {
            ArrayList<File> images = lists[0];
            int deleted = 0;
            try {
                for (File image : images) {
                    boolean success = HttpService.deleteImage(image.getName(), token);
                    if (success) {
                        image.delete();
                        deleted++;
                    }
                }
                return deleted;
            } catch(Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Integer deleted) {
            updateImages();
            showProgress(false);
            Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                    deleted + " Item(s) Deleted", Snackbar.LENGTH_LONG);
            snackbar.show();
        }

    }

}

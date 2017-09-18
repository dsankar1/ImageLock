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

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferType;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class Gallery extends AppCompatActivity {

    private static final int CAMERA = 1;
    private final String bucket = "imagelocks3";
    private final boolean test = true;

    private Toolbar galleryToolBar;
    private ListView galleryListView;
    private ProgressBar galleryProgressBar;

    private String username, password, prefix, authorities;
    private File mostRecentImage;
    private File localStorage;
    private ArrayList<File> images;
    private GalleryAdapter galleryAdapter;

    private AmazonS3Client s3Client;
    private TransferUtility transferUtility;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        // AWS stuff
        s3Client = new AmazonS3Client(new BasicAWSCredentials
                ("key", "secret"));
        transferUtility = new TransferUtility(s3Client, getApplicationContext());
        // Action Bar stuff
        galleryProgressBar = (ProgressBar) findViewById(R.id.galleryProgressBar);
        galleryProgressBar.getIndeterminateDrawable()
                .setColorFilter(Color.parseColor("#838383"), PorterDuff.Mode.MULTIPLY);
        galleryToolBar = (Toolbar) findViewById(R.id.galleryToolBar);
        setSupportActionBar(galleryToolBar);

        authorities = getApplicationContext().getPackageName() + ".fileprovider";
        username = getIntent().getExtras().getString("USERNAME");
        password = getIntent().getExtras().getString("PASSWORD");
        prefix = getIntent().getExtras().getString("PREFIX");
        images = new ArrayList<>();
        localStorage = new File(getFilesDir(), prefix);

        // List view stuff
        galleryListView = (ListView) findViewById(R.id.galleryListView);
        galleryAdapter = new GalleryAdapter(getApplicationContext(), R.layout.gallery_item, images);
        galleryListView.setAdapter(galleryAdapter);
        galleryListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

        createLocalStorage();
        setListViewEventListeners();
        new DownloadTask().execute();
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
                Log.i("Deleted on Logout", image.getPath());
                image.delete();
            }
            Log.i("Deleting Directory", localStorage.getPath());
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
                if (test) Log.i("Checked", i + " " + galleryListView.isItemChecked(i));
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
            updateImages();
            new UploadTask().execute(mostRecentImage);
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
        transferUtility.cancelAllWithType(TransferType.ANY);
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
                image.delete();
            }
        }
        new DeleteTask().execute(toDelete);
        updateImages();
        Snackbar deleted = Snackbar.make(findViewById(android.R.id.content),
                selected.size() + " Item(s) Deleted", Snackbar.LENGTH_LONG);
        deleted.show();
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

    private class DownloadTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            showProgress(true);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            downloadImages();
            return null;
        }

        private void downloadImages() {
            ObjectListing listing = s3Client.listObjects(new ListObjectsRequest()
                            .withBucketName(bucket)
                            .withPrefix(prefix));
            final List<S3ObjectSummary> summaries = listing.getObjectSummaries();
            if (summaries.isEmpty()){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showProgress(false);
                    }
                });
            }
            for (S3ObjectSummary summary : summaries) {
                String key = summary.getKey();
                String name = new File(key).getName();
                File image = new File(localStorage, name);
                final TransferObserver observer = transferUtility.download(bucket, key, image);
                observer.setTransferListener(new TransferListener() {
                    @Override
                    public void onStateChanged(int i, TransferState transferState) {
                        if (transferState == TransferState.COMPLETED) {
                            if (localStorage.listFiles().length == summaries.size()) {
                                showProgress(false);
                                updateImages();
                            }
                        }
                    }

                    @Override
                    public void onProgressChanged(int i, long l, long l1) {
                        // Stub
                    }

                    @Override
                    public void onError(int i, Exception e) {
                        showProgress(false);
                        Log.e("ERROR", e.toString());
                    }
                });
            }
        }

    }

    private class UploadTask extends AsyncTask<File, Void, Void> {

        @Override
        protected Void doInBackground(File... images) {
            uploadImage(images[0]);
            return null;
        }

        private void uploadImage(File image) {
            String key = prefix + image.getName();
            Log.i("Uploaded Key", key);
            TransferObserver observer = transferUtility.upload(bucket, key, image);
            observer.setTransferListener(new TransferListener() {
                @Override
                public void onStateChanged(int i, TransferState transferState) {
                    // Stub
                }

                @Override
                public void onProgressChanged(int i, long l, long l1) {
                    // Stub
                }

                @Override
                public void onError(int i, Exception e) {
                    final Snackbar error = Snackbar.make(findViewById(android.R.id.content),
                            "Failed to Upload Image", Snackbar.LENGTH_LONG);
                    error.show();
                    Log.e("ERROR", e.toString());
                }
            });
        }

    }

    private class DeleteTask extends AsyncTask<ArrayList<File>, Void, Void> {

        @Override
        protected Void doInBackground(ArrayList<File>... lists) {
            ArrayList<File> images = lists[0];
            for (File image : images) {
                String key = prefix + image.getName();
                Log.i("Deleted Key", key);
                s3Client.deleteObject(bucket, key);
            }
            return null;
        }

    }

}

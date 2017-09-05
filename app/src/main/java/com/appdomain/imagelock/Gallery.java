package com.appdomain.imagelock;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
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
    private File localFilesFolder;
    private File mostRecentCameraSnap;
    private ArrayList<File> images;
    private GalleryAdapter galleryAdapter;

    private AmazonS3Client s3Client;
    private TransferUtility transferUtility;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        galleryToolBar = (Toolbar) findViewById(R.id.galleryToolBar);
        setSupportActionBar(galleryToolBar);

        galleryProgressBar = (ProgressBar) findViewById(R.id.galleryProgressBar);

        username = getIntent().getExtras().getString("USERNAME");
        password = getIntent().getExtras().getString("PASSWORD");
        prefix = getIntent().getExtras().getString("PREFIX");
        authorities = getApplicationContext().getPackageName() + ".fileprovider";
        localFilesFolder = getLocalFilesDirectory();
        images = new ArrayList<>();
        galleryAdapter = new GalleryAdapter(getApplicationContext(), R.layout.gallery_item, images);

        s3Client = new AmazonS3Client(new BasicAWSCredentials
                ("keyId", "secret"));
        transferUtility = new TransferUtility(s3Client, getApplicationContext());

        galleryListView = (ListView) findViewById(R.id.galleryListView);
        configureListView();

        new DownloadTask().execute();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA && resultCode == RESULT_OK) {
            new UploadTask().execute(mostRecentCameraSnap);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.gallery_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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

    // <-PRIMARY FUNCTIONALITY->
    private void launchCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null && hasCameraPermission()) {
            mostRecentCameraSnap = createImageFile();
            Uri imageUri = FileProvider.getUriForFile(getApplicationContext(), authorities, mostRecentCameraSnap);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            startActivityForResult(cameraIntent, CAMERA);
        }
        else {
            final Snackbar cameraPermission = Snackbar.make(findViewById(android.R.id.content),
                    "Unable to Open Camera", Snackbar.LENGTH_LONG);
            cameraPermission.show();
        }
    }

    private void logout() {
        Intent login = new Intent(getApplicationContext(), Login.class);
        login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(login);
        deleteLocalUserFiles();
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
        final Snackbar deleted = Snackbar.make(findViewById(android.R.id.content),
                selected.size() + " Item(s) Deleted", Snackbar.LENGTH_LONG);
        deleted.show();
    }

    private void selectAll() {
        for (int i = 0; i < galleryListView.getCount(); i++) {
            galleryListView.setItemChecked(i, true);
        }
    }

    // <-HELPER FUNCTIONS->
    private void configureListView() {
        galleryListView.setAdapter(galleryAdapter);
        galleryListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        galleryListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(android.view.ActionMode actionMode, int i, long l, boolean b) {
                final int checkedCount = galleryListView.getCheckedItemCount();
                if (checkedCount > 0) {
                    actionMode.setTitle(checkedCount + " selected");
                }
                if (test) Log.i("Checked", i + " " + galleryListView.isItemChecked(i));
                galleryAdapter.setSelection(i, galleryListView.isItemChecked(i));
            }

            @Override
            public boolean onCreateActionMode(android.view.ActionMode actionMode, Menu menu) {
                actionMode.getMenuInflater().inflate(R.menu.gallery_cab_menu, menu);
                return true;
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode actionMode, MenuItem item) {
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
                galleryAdapter.removeSelection();
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

    private File createImageFile() {
        String filename = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpg";
        localFilesFolder.mkdir();
        File file = new File(localFilesFolder, filename);
        return file;
    }

    private boolean hasCameraPermission() {
        String permission = "android.permission.CAMERA";
        int res = getApplicationContext().checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    public boolean isConnectedToInternet() {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }

    private synchronized void updateImages() {
        images.clear();
        images.addAll(Arrays.asList(localFilesFolder.listFiles()));
        Collections.reverse(images);
        if (test) Log.i("updateImages", images.toString());
        galleryAdapter.notifyDataSetChanged();
    }

    private File getLocalFilesDirectory() {
        File directory = getExternalFilesDir(prefix);
        if (test) Log.i("getLocalFilesDirectory", directory.getPath());
        directory.mkdir();
        return directory;
    }

    private void deleteLocalUserFiles() {
        for (File file : localFilesFolder.listFiles()) {
            file.delete();
        }
        localFilesFolder.delete();
    }

    // <-ASYNCHRONOUS STUFF->
    private class DownloadTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                deleteLocalUserFiles();
                downloadImages();
                return true;
            }
            catch (Exception e) {
                Log.e("downloadImages", e.toString());
                return false;
            }
        }

        private void downloadImages() throws Exception {
            ObjectListing listing = s3Client.listObjects(new ListObjectsRequest().withBucketName(bucket).withPrefix(prefix));
            final List<S3ObjectSummary> summaries = listing.getObjectSummaries();

            for (S3ObjectSummary summary : summaries) {
                String key = summary.getKey();
                int index = key.lastIndexOf("/") + 1;
                if (index < key.length()) {
                    String filename = key.substring(index);
                    File file = new File(localFilesFolder, filename);
                    TransferObserver observer = transferUtility.download(bucket, key, file);
                    observer.setTransferListener(new TransferListener() {
                        @Override
                        public void onStateChanged(int i, TransferState transferState) {
                            if (transferState == TransferState.COMPLETED
                                    && localFilesFolder.listFiles().length >= summaries.size() - 1) {
                                updateImages();
                                galleryListView.setVisibility(View.VISIBLE);
                                galleryProgressBar.setVisibility(View.GONE);

                            }
                        }

                        @Override
                        public void onProgressChanged(int i, long l, long l1) {
                            //Stub
                        }

                        @Override
                        public void onError(int i, Exception e) {
                            Log.e("downloadImages", e.toString());
                        }
                    });
                }
            }
        }

    }

    private class UploadTask extends AsyncTask<File, Void, Boolean> {

        @Override
        protected Boolean doInBackground(File... images) {
            try {
                uploadImage(images[0]);
                return true;
            }
            catch (Exception e) {
                Log.e("uploadImage", e.toString());
                return false;
            }
        }

        private void uploadImage(File image) throws Exception {
            int index = image.getPath().lastIndexOf("/") + 1;
            String key = prefix + image.getPath().substring(index);
            TransferObserver observer = transferUtility.upload(bucket, key, image);
            observer.setTransferListener(new TransferListener() {
                @Override
                public void onStateChanged(int i, TransferState transferState) {
                    if (transferState == TransferState.COMPLETED) {
                        updateImages();
                    }
                }

                @Override
                public void onProgressChanged(int i, long l, long l1) {
                    // Stub
                }

                @Override
                public void onError(int i, Exception e) {
                    final Snackbar saved = Snackbar.make(findViewById(android.R.id.content),
                            "Image Failed to Upload", Snackbar.LENGTH_LONG);
                    saved.show();
                    Log.e("uploadImage", e.toString());
                }
            });
        }

    }

    private class DeleteTask extends AsyncTask<ArrayList<File>, Void, Boolean> {

        @Override
        protected Boolean doInBackground(ArrayList<File>... lists) {
            ArrayList<File> images = lists[0];
            try {
                for (File image : images) {
                    deleteImage(image);
                }
                return true;
            }
            catch (Exception e) {
                Log.e("deleteImage", e.toString());
                return false;
            }
        }

        private void deleteImage(File image) throws Exception {
            int index = image.getPath().lastIndexOf("/") + 1;
            String key = prefix + image.getPath().substring(index);
            s3Client.deleteObject(bucket, key);
        }

    }

}

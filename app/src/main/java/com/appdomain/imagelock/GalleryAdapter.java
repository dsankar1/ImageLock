package com.appdomain.imagelock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class GalleryAdapter extends BaseAdapter {

    private Context context;
    private ArrayList<File> images;
    private LayoutInflater inflater;

    public GalleryAdapter(Context context, ArrayList<File> images) {
        this.context = context;
        this.images = images;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return images.size();
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        Holder holder = new Holder();
        View row = inflater.inflate(R.layout.gallery_item, null);
        holder.image = row.findViewById(R.id.galleryItemImage);
        holder.title = row.findViewById(R.id.galleryItemTitle);
        holder.size = row.findViewById(R.id.galleryItemSize);

        File imageFile = images.get(i);
        String imagePath = imageFile.getPath();
        DecimalFormat df = new DecimalFormat("#,##0.0");
        int index = imagePath.lastIndexOf("/") + 1;

        String title = imagePath.substring(index);
        Bitmap imageBitmap = BitmapFactory.decodeFile(imagePath);
        double imageSize = (double)imageFile.length()/1024.0;

        holder.title.setText(title);
        holder.image.setImageBitmap(imageBitmap);
        holder.size.setText(df.format(imageSize) + " kB");

        return row;
    }

    public class Holder {
        TextView title;
        TextView size;
        ImageView image;
    }

}

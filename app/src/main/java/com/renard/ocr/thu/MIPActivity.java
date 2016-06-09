package com.renard.ocr.thu;


import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.renard.ocr.R;

import java.util.ArrayList;

import imagepicker.model.ImageEntry;
import imagepicker.util.Picker;


public class MIPActivity extends AppCompatActivity implements Picker.PickListener {

    private static final String TAG = "Sample activity";
    private RecyclerView mImageSampleRecycler;
    private ArrayList<ImageEntry> mSelectedImages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mip);

        mImageSampleRecycler = (RecyclerView) findViewById(R.id.images_sample);
        setupRecycler();
    }

    private void setupRecycler() {
        final GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        gridLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mImageSampleRecycler.setLayoutManager(gridLayoutManager);
    }

    public void onClickPickImageSingle(View view) {
        new Picker.Builder(this, this, R.style.MIP_theme)
                .setPickMode(Picker.PickMode.SINGLE_IMAGE)
                .build()
                .startActivity();
    }

    public void onClickPickImageMultipleWithLimit(View view) {
        new Picker.Builder(this, this, R.style.MIP_theme)
                .setPickMode(Picker.PickMode.MULTIPLE_IMAGES)
                .setLimit(6)
                .build()
                .startActivity();
    }

    public void onPickImageMultipleInfinite(View view) {
        new Picker.Builder(this, this, R.style.MIP_theme)
                .setPickMode(Picker.PickMode.MULTIPLE_IMAGES)
                .setBackBtnInMainActivity(true)
                .build()
                .startActivity();
    }

    public void onClickPickImageWithVideos(View view) {
        new Picker.Builder(this, this, R.style.MIP_theme)
                .setPickMode(Picker.PickMode.MULTIPLE_IMAGES)
                .setVideosEnabled(true)
                .build()
                .startActivity();
    }

    @Override
    public void onPickedSuccessfully(ArrayList<ImageEntry> images) {
        mSelectedImages = images;
        setupImageSamples();
        Log.d(TAG, "Picked images  " + images.toString());
    }

    private void setupImageSamples() {
        mImageSampleRecycler.setAdapter(new ImageSamplesAdapter());
    }

    @Override
    public void onCancel() {
        Log.i(TAG, "User canceled picker activity");
        Toast.makeText(this, "User canceld picker activtiy", Toast.LENGTH_SHORT).show();
    }

    private class ImageSamplesAdapter extends RecyclerView.Adapter<ImageSampleViewHolder> {

        @Override
        public ImageSampleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final ImageView imageView = new ImageView(parent.getContext());
            return new ImageSampleViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(ImageSampleViewHolder holder, int position) {
            final String path = mSelectedImages.get(position).path;
            loadImage(path, holder.thumbnail);
        }

        @Override
        public int getItemCount() {
            return mSelectedImages.size();
        }

        private void loadImage(final String path, final ImageView imageView) {
            imageView.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 440));//width, height

            Glide.with(MIPActivity.this)
                    .load(path)
                    .asBitmap()
                    .into(imageView);
        }

    }

    class ImageSampleViewHolder extends RecyclerView.ViewHolder {

        protected ImageView thumbnail;

        public ImageSampleViewHolder(View itemView) {
            super(itemView);
            thumbnail = (ImageView) itemView;
        }
    }
}

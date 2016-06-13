package com.renard.ocr.thu;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.renard.ocr.R;

import java.util.ArrayList;

import imagepicker.model.ImageEntry;

/**
 * 图片adapter
 * <p/>
 * update
 * 1.修改图片选中后的显示样式
 */
public class MIPImagesAdapter extends RecyclerView.Adapter<MIPImagesAdapter.ImageViewHolder> {

    protected Context mContext;
    protected final Drawable mCheckIcon;
    protected RecyclerView mRecyclerView;
    protected ArrayList<ImageEntry> mImages;
    protected OnClickImageListener mListener;

    public MIPImagesAdapter(final Context context, final ArrayList<ImageEntry> images, final RecyclerView recyclerView) {
        mImages = images;
        mContext = context;
        mRecyclerView = recyclerView;
        mCheckIcon = createCheckIcon();
        mListener = (OnClickImageListener) mContext;
    }

    @Override
    public int getItemCount() {
        return mImages.size();
    }

    @Override
    public ImageViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        final View view = LayoutInflater.from(mContext).inflate(R.layout.element_image, viewGroup, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ImageViewHolder imageViewHolder, final int position) {
        final ImageEntry imageEntry = mImages.get(position);
        setHeight(imageViewHolder.itemView);
        displayThumbnail(imageViewHolder, imageEntry);
        drawGrid(imageViewHolder, imageEntry);

        imageViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onClickImage(imageEntry);
            }
        });
    }

    public void setHeight(final View convertView) {
        final int height = mRecyclerView.getMeasuredWidth() / mRecyclerView.getResources().getInteger(R.integer.num_columns_images);
        convertView.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
    }

    public void displayThumbnail(final ImageViewHolder holder, final ImageEntry photo) {
        Glide.with(mContext).load(photo.path).asBitmap().centerCrop().into(holder.thumbnail);
    }

    //check icon，暂时不确定是否能够用上
    private Drawable createCheckIcon() {
        Drawable checkIcon = ContextCompat.getDrawable(mContext, R.drawable.ic_action_done_white);
        checkIcon = DrawableCompat.wrap(checkIcon);
        return checkIcon;
    }

    //check需要，暂时不确定是否能够用上
    public void drawGrid(final ImageViewHolder holder, final ImageEntry imageEntry) {
        holder.check.setImageDrawable(mCheckIcon);
        holder.check.setVisibility(View.GONE);
    }

    //ViewHolder
    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final ImageView check;

        public ImageViewHolder(final View itemView) {
            super(itemView);
            thumbnail = (ImageView) itemView.findViewById(R.id.image_thumbnail);
            check = (ImageView) itemView.findViewById(R.id.image_check);
        }
    }

    public interface OnClickImageListener {
        void onClickImage(final ImageEntry imageEntry);
    }

}

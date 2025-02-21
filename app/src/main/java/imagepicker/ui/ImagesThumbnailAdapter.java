package imagepicker.ui;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;
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

import de.greenrobot.event.EventBus;
import imagepicker.model.AlbumEntry;
import imagepicker.model.ImageEntry;
import imagepicker.util.Events;
import imagepicker.util.Picker;
import imagepicker.util.Util;

/**
 * 图片adapter
 *
 * update
 * 1.修改图片选中后的显示样式
 *
 */
public class ImagesThumbnailAdapter extends RecyclerView.Adapter<ImagesThumbnailAdapter.ImageViewHolder> implements Util.OnClickImage {

    protected final AlbumEntry mAlbum;
    protected final RecyclerView mRecyclerView;
    protected final Drawable mCheckIcon;
    protected final Fragment mFragment;
    protected final Picker mPickOptions;

    public ImagesThumbnailAdapter(final Fragment fragment, final AlbumEntry album, final RecyclerView recyclerView, Picker pickOptions) {
        mFragment = fragment;
        mAlbum = album;
        mRecyclerView = recyclerView;
        mPickOptions = pickOptions;
        mCheckIcon = createCheckIcon();
    }

    private Drawable createCheckIcon() {
        Drawable checkIcon = ContextCompat.getDrawable(mRecyclerView.getContext(), R.drawable.ic_action_done_white);
        checkIcon = DrawableCompat.wrap(checkIcon);
        DrawableCompat.setTint(checkIcon, mPickOptions.checkIconTintColor);
        return checkIcon;
    }

    @Override
    public int getItemCount() {
        return mAlbum.imageList.size();
    }

    @Override
    public ImageViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        final View imageLayout = LayoutInflater.from(mRecyclerView.getContext()).inflate(R.layout.element_image, viewGroup, false);
        return new ImageViewHolder(imageLayout, this);
    }

    @Override
    public void onBindViewHolder(ImageViewHolder imageViewHolder, int position) {
        final ImageEntry imageEntry = mAlbum.imageList.get(position);
        setHeight(imageViewHolder.itemView);
        displayThumbnail(imageViewHolder, imageEntry);
        drawGrid(imageViewHolder, imageEntry);
    }

    @Override
    public void onClickImage(View layout, ImageView thumbnail, ImageView check) {
        final int position = Util.getPositionOfChild(layout, R.id.image_layout, mRecyclerView);
        final ImageViewHolder holder = (ImageViewHolder) mRecyclerView.getChildViewHolder(layout);
        pickImage(holder, mAlbum.imageList.get(position));
    }

    public void setHeight(final View convertView) {
        final int height = mRecyclerView.getMeasuredWidth() / mRecyclerView.getResources().getInteger(R.integer.num_columns_images);
        convertView.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
    }

    public void displayThumbnail(final ImageViewHolder holder, final ImageEntry photo) {
        Glide.with(mFragment).load(photo.path).asBitmap().centerCrop().into(holder.thumbnail);
    }

    public void drawGrid(final ImageViewHolder holder, final ImageEntry imageEntry) {
        holder.check.setImageDrawable(mCheckIcon);

        if (imageEntry.isPicked) {
            holder.check.setBackgroundColor(mPickOptions.imageBackgroundColorWhenChecked);
            holder.itemView.setBackgroundColor(mPickOptions.imageBackgroundColorWhenChecked);
            holder.thumbnail.setColorFilter(mPickOptions.checkedImageOverlayColor);
            //final int padding = mRecyclerView.getContext().getResources().getDimensionPixelSize(R.dimen.image_checked_padding);
            //holder.itemView.setPadding(padding, padding, padding, padding);
            holder.itemView.setPadding(0, 0, 0, 0);//hujiawei 去掉此处的padding
        } else {
            holder.check.setBackgroundColor(mPickOptions.imageCheckColor);
            holder.itemView.setBackgroundColor(mPickOptions.imageBackgroundColor);
            holder.thumbnail.setColorFilter(Color.TRANSPARENT);
            holder.itemView.setPadding(0, 0, 0, 0);
        }

        if (mPickOptions.pickMode == Picker.PickMode.SINGLE_IMAGE) {
            holder.check.setVisibility(View.GONE);
        }
    }

    public void pickImage(final ImageViewHolder holder, final ImageEntry imageEntry) {
        if (imageEntry.isPicked) {
            EventBus.getDefault().post(new Events.OnUnpickImageEvent(imageEntry));//Unpick
        } else {
            EventBus.getDefault().postSticky(new Events.OnPickImageEvent(imageEntry));//pick
        }
        drawGrid(holder, imageEntry);
    }

    //ViewHolder
    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final ImageView check;

        public ImageViewHolder(final View itemView, final Util.OnClickImage listener) {
            super(itemView);
            thumbnail = (ImageView) itemView.findViewById(R.id.image_thumbnail);
            check = (ImageView) itemView.findViewById(R.id.image_check);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onClickImage(itemView, thumbnail, check);
                }
            });
        }
    }

}

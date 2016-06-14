/*
 * Copyright (C) 2012,2013 Renard Wellnitz.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.renard.ocr.documents.viewing.grid;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.renard.ocr.R;
import com.renard.ocr.documents.viewing.DocumentContentProvider;
import com.renard.ocr.documents.viewing.DocumentContentProvider.Columns;
import com.renard.ocr.util.PreferencesUtils;
import com.renard.ocr.util.Util;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * adapter for the document grid view
 * <p/>
 * 文档GridView的adapter
 *
 * @author renard
 */
public class DocumentGridAdapter extends CursorAdapter {

    public static final String TAG = DocumentGridAdapter.class.getSimpleName();

    private int mIndexID;
    private int mIndexCreated;
    private LayoutInflater mInflater;
    private OnCheckedChangeListener mCheckedChangeListener = null;
    private Set<Integer> mSelectedDocuments = new HashSet<>();//选中的文档集合
    private final static String[] PROJECTION = {Columns.ID, Columns.CREATED, Columns.PHOTO_PATH};

    public DocumentGridAdapter(Context activity, OnCheckedChangeListener listener) {
        super(activity, activity.getContentResolver().query(DocumentContentProvider.CONTENT_URI, PROJECTION, DocumentContentProvider.Columns.PARENT_ID + "=-1", null, null), true);
        //默认的query是取出parentId=-1的文档

        mInflater = LayoutInflater.from(activity);
        final Cursor c = getCursor();
        mIndexCreated = c.getColumnIndex(Columns.CREATED);
        mIndexID = c.getColumnIndex(Columns.ID);
        mCheckedChangeListener = listener;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        final DocumentViewHolder holder = (DocumentViewHolder) view.getTag();
        final int documentId = cursor.getInt(mIndexID);
        holder.isChecked = mSelectedDocuments.contains(documentId);
        holder.documentId = documentId;
        holder.title.setVisibility(View.GONE);
        long created = cursor.getLong(mIndexCreated);//上面显示时间
        //hujiawei 修改时间显示格式 MMM dd, yyyy h:mmaa
        CharSequence formattedDate = DateFormat.format("yyyyMMdd HH:mm:ss", new Date(created));
        holder.date.setText(formattedDate);

        if (holder.isChecked) {//本来是用来显示文档页数的,现在改成显示item是否选中!!!
            holder.checkbox.setText(R.string.item_check);
        } else {
            holder.checkbox.setText(R.string.item_uncheck);
        }

        //设置图片的长和宽的大小
        holder.thumb.setLayoutParams(new RelativeLayout.LayoutParams(PreferencesUtils.getThumbnailWidth(context), PreferencesUtils.getThumbnailHeight(context)));
        //holder.thumb.setImageBitmap(Util.loadDocumentImage(created));
        String imageFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + Util.IMAGE_DIRECTORY + File.separator + String.valueOf(created) + ".png";
        Log.i(TAG, "image path=" + imageFilePath);
        Glide.with(context).load(imageFilePath).asBitmap().centerCrop().into(holder.thumb);
    }

    @Override
    public long getItemId(int position) {
        if (getCursor().moveToPosition(position)) {
            int index = getCursor().getColumnIndex(Columns.ID);
            return getCursor().getLong(index);
        }
        return -1;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = mInflater.inflate(R.layout.element_document, null, false);
        DocumentViewHolder holder = new DocumentViewHolder(view);
        view.setTag(holder);
        return view;
    }

    //设置选中的文档编号
    public void setSelectedDocumentIds(List<Integer> selection) {
        mSelectedDocuments.addAll(selection);
        if (mCheckedChangeListener != null) {
            mCheckedChangeListener.onCheckedChanged(mSelectedDocuments);
        }
    }

    public Set<Integer> getSelectedDocumentIds() {
        return mSelectedDocuments;
    }

    public void clearAllSelection() {
        mSelectedDocuments.clear();
    }

    //从CheckableGridElement -> DocumentGridAdapter -> DocumentGridActivity
    public interface OnCheckedChangeListener {//grid item check change!!!

        void onCheckedChanged(final Set<Integer> checkedIds);
    }

    private void onCheckedChanged(DocumentViewHolder holder, boolean isChecked) {
        if (isChecked) {
            mSelectedDocuments.add(holder.documentId);
        } else {
            mSelectedDocuments.remove(holder.documentId);
        }

        if (mCheckedChangeListener != null) {
            mCheckedChangeListener.onCheckedChanged(mSelectedDocuments);
        }
    }

    /**
     * ViewHolder，但是特别的是它实现了Checkable接口
     */
    class DocumentViewHolder implements Checkable {

        private TextView date;
        private TextView title;
        private TextView checkbox;
        private ImageView thumb;

        public int documentId;//hujiawei viewholder一般会作为view的tag，所以可以在viewholder中保存一些其他的数据
        public boolean isChecked = false;

        DocumentViewHolder(View v) {
            thumb = (ImageView) v.findViewById(R.id.thumb);
            date = (TextView) v.findViewById(R.id.date);
            checkbox = (TextView) v.findViewById(R.id.checkbox);
            title = (TextView) v.findViewById(R.id.title);
        }

        /**
         * Checkable接口的三个方法
         */
        @Override
        public void setChecked(boolean checked) {
            isChecked = checked;
            updateCheckbox();
            onCheckedChanged(this, isChecked);
        }

        private void updateCheckbox() {
            if (isChecked) {//本来是用来显示文档页数的,现在改成显示item是否选中!!!
                checkbox.setText(R.string.item_check);
            } else {
                checkbox.setText(R.string.item_uncheck);
            }
        }

        @Override
        public boolean isChecked() {
            return isChecked;
        }

        @Override
        public void toggle() {
            isChecked = !isChecked;
            updateCheckbox();
            onCheckedChanged(this, isChecked);
        }

    }
}
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
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.renard.ocr.R;
import com.renard.ocr.documents.viewing.DocumentContentProvider;
import com.renard.ocr.documents.viewing.DocumentContentProvider.Columns;
import com.renard.ocr.documents.viewing.grid.CheckableGridElement.OnCheckedChangeListener;
import com.renard.ocr.util.Util;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * adapter for the document grid view
 * <p>
 * 文档GridView的adapter
 *
 * @author renard
 */
public class DocumentGridAdapter extends CursorAdapter implements OnCheckedChangeListener {

    //从CheckableGridElement -> DocumentGridAdapter -> DocumentGridActivity
    public interface OnCheckedChangeListener {//grid item check change!!!

        void onCheckedChanged(final Set<Integer> checkedIds);
    }

    static class DocumentViewHolder {//ViewHolder

        public CheckableGridElement gridElement;
        private TextView date;
        private TextView title;
        private TextView mPageNumber;
        public int documentId;
        public boolean updateThumbnail;

        CrossFadeDrawable transition;

        DocumentViewHolder(View v) {
            gridElement = (CheckableGridElement) v;
            date = (TextView) v.findViewById(R.id.date);
            mPageNumber = (TextView) v.findViewById(R.id.page_number);
            title = (TextView) v.findViewById(R.id.title);
        }

    }

    private final static String[] PROJECTION = {Columns.ID, Columns.TITLE, Columns.OCR_TEXT, Columns.CREATED, Columns.PHOTO_PATH, Columns.CHILD_COUNT};

    private Set<Integer> mSelectedDocuments = new HashSet<Integer>();//选中的文档集合
    private LayoutInflater mInflater;
    private final DocumentGridActivity mActivity;
    private int mElementLayoutId;//grid element layout id

    private int mIndexCreated;
    private int mIndexTitle;
    private int mIndexID;
    private int mChildCountID;
    private OnCheckedChangeListener mCheckedChangeListener = null;

    public void clearAllSelection() {
        mSelectedDocuments.clear();
    }

    public DocumentGridAdapter(DocumentGridActivity activity, int elementLayout, OnCheckedChangeListener listener) {
        super(activity, activity.getContentResolver().query(DocumentContentProvider.CONTENT_URI, PROJECTION, DocumentContentProvider.Columns.PARENT_ID + "=-1", null, null), true);
        mElementLayoutId = elementLayout;
        mActivity = activity;
        mInflater = LayoutInflater.from(activity);

        final Cursor c = getCursor();
        mIndexCreated = c.getColumnIndex(Columns.CREATED);
        mIndexID = c.getColumnIndex(Columns.ID);
        mIndexTitle = c.getColumnIndex(Columns.TITLE);
        mChildCountID = c.getColumnIndex(Columns.CHILD_COUNT);

        mCheckedChangeListener = listener;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        final DocumentViewHolder holder = (DocumentViewHolder) view.getTag();

        final int documentId = cursor.getInt(mIndexID);
        final int childCount = cursor.getInt(mChildCountID);
        final boolean isSelected = mSelectedDocuments.contains(documentId);
        holder.documentId = documentId;

        holder.title.setVisibility(View.GONE);
        //有title显示title -> 不显示title,界面有问题
//        String title = cursor.getString(mIndexTitle);
//        if (title != null && title.length() > 0) {
//            holder.title.setText(title);
//            holder.title.setVisibility(View.VISIBLE);
//        }else{
//            holder.title.setVisibility(View.INVISIBLE);
//        }

        long created = cursor.getLong(mIndexCreated);//上面显示时间
        //hujiawei 修改时间显示格式 MMM dd, yyyy h:mmaa
        CharSequence formattedDate = DateFormat.format("yyyyMMdd HH:mm:ss", new Date(created));
        holder.date.setText(formattedDate);

        //if (holder.mPageNumber != null) {//文档的页数
        //    holder.mPageNumber.setText(String.valueOf(childCount + 1));
        //}
        if (isSelected) {//本来是用来显示文档页数的,现在改成显示item是否选中!!!
            holder.mPageNumber.setText(R.string.item_check);
        } else {
            holder.mPageNumber.setText(R.string.item_uncheck);
        }

        if (holder.gridElement != null) {
            //如果当前处于快速滑动状态的话,只显示默认的缩略图就行了
            if (mActivity.getScrollState() == AbsListView.OnScrollListener.SCROLL_STATE_FLING || mActivity.isPendingThumbnailUpdate()) {
                holder.gridElement.setImage(Util.sDefaultDocumentThumbnail);
                holder.updateThumbnail = true;
            } else {//否则获取对应文档的缩略图并显示
                final Drawable d = Util.getDocumentThumbnail(documentId);
                holder.gridElement.setImage(d);
                holder.updateThumbnail = false;
            }
        }
        holder.gridElement.setCheckedNoAnimate(isSelected);//不带动画效果的check
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
        View v = null;
        DocumentViewHolder holder = null;
        v = mInflater.inflate(mElementLayoutId, null, false);
        int index = cursor.getColumnIndex(Columns.ID);
        int documentId = cursor.getInt(index);

        holder = new DocumentViewHolder(v);
        holder.documentId = documentId;
        holder.gridElement.setChecked(mSelectedDocuments.contains(documentId));
        holder.gridElement.setOnCheckedChangeListener(this);
        v.setTag(holder);

        //hujiawei 删除下面这段代码没有效果
        FastBitmapDrawable start = Util.sDefaultDocumentThumbnail;
        Bitmap startBitmap = null;
        if (start != null) {
            startBitmap = start.getBitmap();
        }
        final CrossFadeDrawable transition = new CrossFadeDrawable(startBitmap, null);
        transition.setCallback(v);
        transition.setCrossFadeEnabled(true);
        holder.transition = transition;

        return v;
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

    //grid item check change!!!
    @Override
    public void onCheckedChanged(View documentView, boolean isChecked) {
        DocumentViewHolder holder = (DocumentViewHolder) documentView.getTag();
        if (isChecked) {
            mSelectedDocuments.add(holder.documentId);
            holder.mPageNumber.setText(R.string.item_check);
        } else {
            mSelectedDocuments.remove(holder.documentId);
            holder.mPageNumber.setText(R.string.item_uncheck);
        }
        mCheckedChangeListener.onCheckedChanged(mSelectedDocuments);
    }
}

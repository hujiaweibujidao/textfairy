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

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;
import android.widget.Toast;

import com.renard.ocr.R;
import com.renard.ocr.base.PermissionGrantedEvent;
import com.renard.ocr.documents.creation.NewDocumentActivity;
import com.renard.ocr.documents.viewing.DocumentContentProvider;
import com.renard.ocr.documents.viewing.single.DocumentActivity;
import com.renard.ocr.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * main activity of the app
 * <p/>
 * 应用的主界面
 * <p/>
 * update:
 * 1.去掉左侧的菜单栏
 * 2.修改界面布局,添加thu声明
 * 3.删除了修改标题菜单项功能 -> 1.显示title有问题 2.修改title界面有异常
 * 4.去掉grid选择状态下的动画
 * 5.选择模式下的菜单简化，如果只选中了一个文档，那么只有删除操作，多个文档有删除和合并的操作
 * 6.简化不量代码和流程 --> todo 这个界面进入的时候比较耗时，使用了Glide进行缓存，但是貌似图片还是加载了多次，后期需要再次优化
 *
 * @author renard
 */
public class DocumentGridActivity extends NewDocumentActivity implements DocumentGridAdapter.OnCheckedChangeListener, LoaderManager.LoaderCallbacks<Cursor> {

    private static final String LOG_TAG = DocumentGridActivity.class.getSimpleName();

    private GridView mGridView;
    private DocumentGridAdapter mDocumentAdapter;

    private ActionMode mActionMode;
    private static final int JOIN_PROGRESS_DIALOG = 4;
    private static boolean sIsInSelectionMode = false;
    private static final String SAVE_STATE_KEY = "selection";
    private boolean mBusIsRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_grid);

        initToolbar();
        initThumbnailSize();
        initGridView();
    }

    @Override
    protected void initToolbar() {
        super.initToolbar();
        setToolbarMessage(R.string.label_document_grid);//修改标题
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    //初始化gridview
    private void initGridView() {
        mGridView = (GridView) findViewById(R.id.gridview);
        mGridView.setNumColumns(2);//2列
        //registerForContextMenu(mGridView);//toread gridview contextmenu 删除之后也正常！
        View emptyView = findViewById(R.id.empty_view);
        mGridView.setEmptyView(emptyView);//将emptyview设置给gridview

        mGridView.setLongClickable(true);
        mGridView.setOnItemClickListener(new DocumentClickListener());
        mGridView.setOnItemLongClickListener(new DocumentLongClickListener());
        //mGridView.setOnScrollListener(new DocumentScrollListener());//这里是做了一个优化，当用户很快滑动的时候延迟更新缩略图
        //mGridView.setOnTouchListener(new FingerTracker());//和上面的DocumentScrollListener结合在一起，判断用户的手指是不是抬起了
        //当gridview目前是fling状态并且用户手指抬起来了，此时需要更新缩略图显示

        /*final int[] outNum = new int[1];
        final int columnWidth = Util.determineThumbnailSize(this, outNum);
        mGridView.setColumnWidth(columnWidth);//列宽
        mGridView.setNumColumns(outNum[0]);//列数*/

        mDocumentAdapter = new DocumentGridAdapter(this, this);
        mGridView.setAdapter(mDocumentAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mBusIsRegistered) {
            EventBus.getDefault().register(this);
            mBusIsRegistered = true;
        }
        //声明需要访问thuocr文件夹
        ensurePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, R.string.permission_explanation);
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(final PermissionGrantedEvent event) {
        Log.i(LOG_TAG, "Permission Granted");
    }

    //初始化缩略图的大小
    private void initThumbnailSize() {
        final int columnWidth = Util.determineThumbnailSize(this, null);
        Util.setThumbnailSize(columnWidth, columnWidth, this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Set<Integer> selection = mDocumentAdapter.getSelectedDocumentIds();
        ArrayList<Integer> save = new ArrayList<Integer>(selection.size());
        save.addAll(selection);
        outState.putIntegerArrayList(SAVE_STATE_KEY, save);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        ArrayList<Integer> selection = savedInstanceState.getIntegerArrayList(SAVE_STATE_KEY);
        mDocumentAdapter.setSelectedDocumentIds(selection);
    }

    @Override
    public void onBackPressed() {
        if (mDocumentAdapter.getSelectedDocumentIds().size() > 0) {
            cancelMultiSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
        mBusIsRegistered = false;
    }

    @Override
    protected int getParentId() {
        return -1;
    }

    @Override
    protected Dialog onCreateDialog(int id) {//配合着showDialog和dismissDialog方法一起使用
        switch (id) {
            case JOIN_PROGRESS_DIALOG:
                ProgressDialog d = new ProgressDialog(this);
                d.setTitle(R.string.join_documents_title);
                d.setIndeterminate(true);
                return d;
        }
        return super.onCreateDialog(id);
    }

    /**
     * ActionMode指的是当前用户交互的操作模式，这里是ActionMode的回调函数
     * 当check状态发生变化的时候,这个actionCallback会被触发 <- onCheckedChanged
     * 在onCreateActionMode中向menu添加操作菜单
     * 在onActionItemClicked中处理菜单点击事件
     */
    private class DocumentActionCallback implements ActionMode.Callback {

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.item_delete) {
                new DeleteDocumentTask(mDocumentAdapter.getSelectedDocumentIds(), false).execute();
                cancelMultiSelectionMode();
                mode.finish();
                return true;
            } else if (itemId == R.id.item_join) {
                joinDocuments(mDocumentAdapter.getSelectedDocumentIds());
                cancelMultiSelectionMode();
                mode.finish();
                return true;
            }
            /*else if (itemId == R.id.item_export_as_pdf) {
                new CreatePDFTask(mDocumentAdapter.getSelectedDocumentIds()).execute();
                cancelMultiSelectionMode();
                mode.finish();
                return true;
            }
            else if (itemId == R.id.item_edit_title) {
                final Set<Integer> selectedDocs = mDocumentAdapter.getSelectedDocumentIds();
                final int documentId = selectedDocs.iterator().next();
                getSupportLoaderManager().initLoader(documentId, null, DocumentGridActivity.this);//
                return true;
            }*/
            return true;
        }


        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.menu_grid_action_mode, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (mActionMode != null) {
                mActionMode = null;
                cancelMultiSelectionMode();
            }
            mActionMode = null;
        }
    }

    /////////////////  gridview  //////////////////

    //点击
    public class DocumentClickListener implements OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            DocumentGridAdapter.DocumentViewHolder holder = (DocumentGridAdapter.DocumentViewHolder) view.getTag();
            if (sIsInSelectionMode) {//如果是在选择模式下,那么就是选中或者不选中操作;如果不是在选择模式下,那么点击就进入文档界面
                holder.toggle();
            } else {
                Intent intent = new Intent(DocumentGridActivity.this, DocumentActivity.class);
                long documentId = mDocumentAdapter.getItemId(position);
                Uri uri = Uri.withAppendedPath(DocumentContentProvider.CONTENT_URI, String.valueOf(documentId));
                intent.setData(uri);
                startActivity(intent);
            }
        }
    }

    //长按
    private class DocumentLongClickListener implements OnItemLongClickListener {

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            DocumentGridAdapter.DocumentViewHolder holder = (DocumentGridAdapter.DocumentViewHolder) view.getTag();
            if (!sIsInSelectionMode) {//如果之前不是在选择模式,那么长按任何一个item都将进入选择模式
                sIsInSelectionMode = true;
                holder.toggle();
                /*final int childCount = parent.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    DocumentGridAdapter.DocumentViewHolder childHolder = (DocumentGridAdapter.DocumentViewHolder) parent.getChildAt(i).getTag();
                    if (childHolder != null) {
                        childHolder.setChecked(false);//这种情况下,初次进入选择模式的时候要将所有其他的子view的选中状态设置为未选中
                    }
                }*/
            } else {
                holder.toggle();
            }
            return true;
        }
    }

    //DocumentGridAdapter.OnCheckedChangeListener
    //gridview中元素的选择状态发生变化的时候这个方法会被调用,选中的文档个数不同,对应的菜单选项不同
    @Override
    public void onCheckedChanged(Set<Integer> checkedIds) {
        if (mActionMode == null && checkedIds.size() > 0) {
            mActionMode = startSupportActionMode(new DocumentActionCallback());// AppCompatActivity.startSupportActionMode (ActionMode.Callback)
        } else if (mActionMode != null && checkedIds.size() == 0) {//没有选中的了就退出actionMode
            mActionMode.finish();
            mActionMode = null;
        }

        //hujiawei 编辑title、合并文档等功能的显示或者不显示
        if (mActionMode != null) {
            // change state of action mode depending on the selection
            final MenuItem joinItem = mActionMode.getMenu().findItem(R.id.item_join);
            if (checkedIds.size() == 1) {//一个文档的时候可以导出pdf,多个文档的时候可以合并文档
                joinItem.setVisible(false);
                joinItem.setEnabled(false);
            } else {
                joinItem.setVisible(true);
                joinItem.setEnabled(true);
            }
        }
    }

    //取消所有的选中项
    public void cancelMultiSelectionMode() {
        mDocumentAdapter.clearAllSelection(); //.getSelectedDocumentIds().clear();
        sIsInSelectionMode = false;
        final int childCount = mGridView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View v = mGridView.getChildAt(i);
            final DocumentGridAdapter.DocumentViewHolder holder = (DocumentGridAdapter.DocumentViewHolder) v.getTag();
            //holder.gridElement.setChecked(false);//
            holder.setChecked(false);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(final int documentId, final Bundle bundle) {
        final Uri uri = Uri.withAppendedPath(DocumentContentProvider.CONTENT_URI, String.valueOf(documentId));
        return new CursorLoader(this, uri, new String[]{DocumentContentProvider.Columns.TITLE, DocumentContentProvider.Columns.ID}, null, null, "created ASC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (cursor.moveToFirst()) {
            final int titleIndex = cursor.getColumnIndex(DocumentContentProvider.Columns.TITLE);
            final String oldTitle = cursor.getString(titleIndex);
            final int idIndex = cursor.getColumnIndex(DocumentContentProvider.Columns.ID);
            final String documentId = String.valueOf(cursor.getInt(idIndex));
            final Uri documentUri = Uri.withAppendedPath(DocumentContentProvider.CONTENT_URI, documentId);
            askUserForNewTitle(oldTitle, documentUri);
        }
        getSupportLoaderManager().destroyLoader(loader.getId());
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
    }

    ///////////////////////////////////////////////////////////////////////////
    /////////////////////////  合并文档  /////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void joinDocuments(final Set<Integer> selectedDocs) {
        new JoinDocumentsTask(selectedDocs, getApplicationContext()).execute();
    }

    //合并文档的任务
    protected class JoinDocumentsTask extends AsyncTask<Void, Integer, Integer> {

        private Set<Integer> mIds = new HashSet<Integer>();
        private final Context mContext;

        public JoinDocumentsTask(Set<Integer> ids, Context c) {
            mIds.addAll(ids);
            mContext = c;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(JOIN_PROGRESS_DIALOG);
        }

        @Override
        protected void onPostExecute(Integer result) {
            String msg = mContext.getString(R.string.join_documents_result);
            msg = String.format(msg, result);
            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
            dismissDialog(JOIN_PROGRESS_DIALOG);
        }

        //构建in表达式
        private String buidlInExpr(final Collection<Integer> ids) {
            final int length = ids.size();
            int count = 0;
            StringBuilder builder = new StringBuilder();
            builder.append(" in (");
            for (@SuppressWarnings("unused") Integer id : ids) {
                builder.append("?");
                if (count < length - 1) {
                    builder.append(",");
                }
                count++;
            }
            builder.append(")");
            return builder.toString();
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int count = 0;
            final Integer parentId = Collections.min(mIds);//找到id最小的那个
            final int documentCount = mIds.size();
            mIds.remove(parentId);

            String[] selectionArgs = new String[mIds.size() * 2];//这些id重复两遍添加到selectionArgs中
            for (Integer id : mIds) {
                selectionArgs[count++] = String.valueOf(id);
            }
            for (Integer id : mIds) {
                selectionArgs[count++] = String.valueOf(id);
            }

            StringBuilder builder = new StringBuilder();
            final String inExpr = buidlInExpr(mIds);
            builder.append(DocumentContentProvider.Columns.ID);
            builder.append(inExpr);
            builder.append(" OR ");
            builder.append(DocumentContentProvider.Columns.PARENT_ID);
            builder.append(inExpr);

            String selection = builder.toString();

            ContentValues values = new ContentValues(2);
            values.put(DocumentContentProvider.Columns.PARENT_ID, parentId);
            values.put(DocumentContentProvider.Columns.CHILD_COUNT, 0);

            int childCount = getContentResolver().update(DocumentContentProvider.CONTENT_URI, values, selection, selectionArgs);
            values.clear();
            values.put(DocumentContentProvider.Columns.CHILD_COUNT, childCount);
            Uri parentDocumentUri = Uri.withAppendedPath(DocumentContentProvider.CONTENT_URI, String.valueOf(parentId));
            getContentResolver().update(parentDocumentUri, values, null, null);
            return documentCount;
        }

    }

}

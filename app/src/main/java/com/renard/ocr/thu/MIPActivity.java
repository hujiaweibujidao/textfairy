package com.renard.ocr.thu;


import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.view.accessibility.AccessibilityManagerCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.Toast;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.Scale;
import com.googlecode.tesseract.android.OCR;
import com.renard.ocr.R;
import com.renard.ocr.base.MonitoredActivity;
import com.renard.ocr.documents.creation.ImageLoadAsyncTask;
import com.renard.ocr.documents.creation.MemoryWarningDialog;
import com.renard.ocr.documents.creation.NewDocumentActivity;
import com.renard.ocr.documents.viewing.DocumentContentProvider;
import com.renard.ocr.documents.viewing.grid.DocumentGridActivity;
import com.renard.ocr.util.PreferencesUtils;
import com.renard.ocr.util.Screen;
import com.renard.ocr.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import imagepicker.model.ImageEntry;
import imagepicker.ui.SpacesItemDecoration;
import imagepicker.util.Picker;

/**
 * 选择的图片列表以及处理之前的配置界面
 */
public class MIPActivity extends NewDocumentActivity implements Picker.PickListener, MIPImagesAdapter.OnClickImageListener {

    private static final String LOG_TAG = "MIP activity";

    private Button mButtonStart;
    private RecyclerView mRecyclerView;
    private ArrayList<ImageEntry> mImages;
    private MIPImagesAdapter mImageAdapter;

    private MIPOCRTask mOCRTask;
    private ProgressDialog mProgressDialog;

    private String mLanguage;//语言
    private String mHocrText;//识别结果 hocr
    private String mUtf8Text;//识别结果 utf8
    private int mAccuracy;//OCR处理结果的准确度
    private Pix mFinalPix;//每张图片最终OCR处理的Pix

    private OCR mOCR;//OCR核心工具类

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.thu_activity_mip);
        initToolbar();
        mButtonStart = (Button) findViewById(R.id.start_batch);
        mRecyclerView = (RecyclerView) findViewById(R.id.list_images);
        setupRecycler();

        if (getIntent() != null) {
            mImages = (ArrayList<ImageEntry>) getIntent().getSerializableExtra("images");
            setupImageList();
        }

        mButtonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mImages == null || mImages.isEmpty()) {//没有图片 ---> 这种情况不会发生！
                    Toast.makeText(MIPActivity.this, "No Image", Toast.LENGTH_SHORT).show();
                } else {//图片一张一张处理，直接利用原来的可视化的方式不太可行，目前尝试将主要操作放到后台任务中来执行
                    cancelTask();
                    mOCRTask = new MIPOCRTask(MIPActivity.this);
                    mOCRTask.execute();
                }
            }
        });
    }

    @Override
    protected void initToolbar() {
        super.initToolbar();
        setToolbarMessage(R.string.title_mip);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void setupRecycler() {
        mRecyclerView.setHasFixedSize(true);
        final GridLayoutManager gridLayoutManager = new GridLayoutManager(this, getResources().getInteger(R.integer.num_columns_images));
        gridLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(gridLayoutManager);
        mRecyclerView.addItemDecoration(new SpacesItemDecoration(getResources().getDimensionPixelSize(R.dimen.image_spacing)));
    }

    private void setupImageList() {
        mImageAdapter = new MIPImagesAdapter(this, mImages, mRecyclerView);
        mRecyclerView.setAdapter(mImageAdapter);
    }

    //PickListener的两个回调方法，这个界面也还可以继续添加图片
    @Override
    public void onPickedSuccessfully(ArrayList<ImageEntry> images) {
        Log.i(LOG_TAG, "Picked images  " + images.toString());
        if (mImages == null || mImages.isEmpty()) {
            mImages = images;
            setupImageList();
        } else {
            for (ImageEntry imageEntry : images) {//只添加那些没有加入进来的图片
                if (!mImages.contains(imageEntry)) {
                    mImages.add(imageEntry);
                }
            }
            mImageAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onCancel() {
        Toast.makeText(this, R.string.no_image_picked, Toast.LENGTH_SHORT).show();
    }

    //点击某个image触发
    @Override
    public void onClickImage(final ImageEntry imageEntry) {
        fakeCameraResultReady(imageEntry);//这里是假装成原始应用一样开始进入图片裁剪和OCR处理的流程
        //改进之后的点击图片是可以对图片的OCR之前进行一些配置 --> 但是改进的方法是保持原有的调用方式不变，修改原有的onActivityResult的处理方式
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_CROP_PHOTO: {//图片裁剪成功，可以进入到ocr了
                    Toast.makeText(this, "crop image ok", Toast.LENGTH_SHORT).show();
                    long nativePix = data.getLongExtra(EXTRA_NATIVE_PIX, 0);
                    startOcrActivityForResult(nativePix, false);
                    break;
                }
                case REQUEST_CODE_OCR: {//hujiawei 图片OCR返回的结果，本来是不处理的，因为得到结果是直接跳转到DocumentGridActivity中
                    // 但是现在需要批量处理，只需要部分布局信息，之后才能执行完整的OCR操作
                    Toast.makeText(this, "image ocr ok", Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        } else {
            switch (requestCode) {
                case REQUEST_CODE_CROP_PHOTO: {//图片裁剪失败
                    Toast.makeText(this, "crop image fail", Toast.LENGTH_SHORT).show();
                    break;
                }
                case REQUEST_CODE_OCR: {//图片布局分析失败
                    Toast.makeText(this, "image ocr fail", Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
    }

    @Override
    protected int getParentId() {//NewDocumentActivity需要实现的抽象方法，没啥特殊情况下都是返回-1，不是0
        return -1;//-1表示该文档是根文档
    }

    //处理菜单栏的选项
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.thu_menu_mip, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.item_addimage) {//注意，这里并没有和NewDocumentActivity中一样调用之前checkRom
            checkRam(MemoryWarningDialog.DoAfter.START_MIP);
        } else if (item.getItemId() == R.id.item_settings) {
            SettingsDialog.newInstance().show(getSupportFragmentManager(), SettingsDialog.TAG);
        }
        return super.onOptionsItemSelected(item);
    }

    private int mCurrentProgressIndex = 1;

    /**
     * 多张图片的OCR处理任务
     */
    class MIPOCRTask extends AsyncTask<Void, Integer, Boolean> {

        private Context context;

        public MIPOCRTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            Log.i(LOG_TAG, "MIPOCRTask onPreExecute");
            super.onPreExecute();

            Screen.lockOrientation((Activity) context);
            mCurrentProgressIndex = 1;
            mProgressDialog = new ProgressDialog(context);
            mProgressDialog.setCanceledOnTouchOutside(false);//按返回键可以取消，点击窗口外面不可以取消
            mProgressDialog.setMessage(String.format(getString(R.string.progress_ocr_dialog), mCurrentProgressIndex, mImages.size(), ""));//
            mProgressDialog.setCancelable(true);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    cancelTask();
                }
            });
            mProgressDialog.show();

            mOCR = new OCR((MonitoredActivity) context, new Messenger(new MIPOCRHandler()));//handler不能简单地在线程中创建
        }

        @Override
        protected void onPostExecute(Boolean flag) {
            Log.i(LOG_TAG, "MIPOCRTask onPostExecute");
            super.onPostExecute(flag);
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            Screen.unlockOrientation((Activity) context);
            if (flag) {
                startActivity(new Intent(MIPActivity.this, DocumentGridActivity.class));
                finish();//todo 再考虑下是否finish
            } else {
                Toast.makeText(MIPActivity.this, R.string.error_ocr_dialog, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            Log.i(LOG_TAG, "MIPOCRTask onProgressUpdate progress=" + values[0]);
            super.onProgressUpdate(values);
            mProgressDialog.setMessage(String.format(getString(R.string.progress_ocr_dialog), values[0], mImages.size(), ""));//
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Log.i(LOG_TAG, "MIPOCRTask doInBackground");
            for (ImageEntry imageEntry : mImages) {
                if (isCancelled()) return false;//如果已经取消了的话就退出
                boolean flag = imageOCR(imageEntry);
                if (!flag) return false;
                mCurrentProgressIndex++;
                publishProgress(mCurrentProgressIndex);
            }
            return true;
        }

        /**
         * 对单张图片进行OCR处理，返回false表示处理失败
         */
        private boolean imageOCR(ImageEntry image) {
            //1.摘自NewDocumentActivity中loadBitmapFromContentUri方法的代码
            Log.i(LOG_TAG, "load image");
            //判断是否跳过图片裁剪步骤
            AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            boolean isAccessibilityEnabled = am.isEnabled();
            boolean isExploreByTouchEnabled = AccessibilityManagerCompat.isTouchExplorationEnabled(am);
            boolean skipCrop = isExploreByTouchEnabled && isAccessibilityEnabled;//toread 两者都为true就可以跳过图片裁剪

            //2.摘自ImageLoadAsyncTask中的doInBackground方法的代码
            Log.i(LOG_TAG, "image process start");
            Uri imageUri = Uri.fromFile(new File(image.path));//衔接代码：构建出图片Uri

            OCR.startCaptureLogs();
            Pix p = ReadFile.loadWithPicasso(context, imageUri);//用picasso去加载图片得到bitmap再转换成pix
            if (p == null) {
                return false;
            }

            final long pixPixelCount = p.getWidth() * p.getHeight();//加载成功
            if (pixPixelCount < ImageLoadAsyncTask.MIN_PIXEL_COUNT) {//如果像素数目太少的话先缩放
                double scale = Math.sqrt(((double) ImageLoadAsyncTask.MIN_PIXEL_COUNT) / pixPixelCount);//缩放的比例
                Pix scaledPix = Scale.scale(p, (float) scale);//图片缩放
                if (scaledPix.getNativePix() == 0) {//缩放失败
                    return false;
                } else {
                    p.recycle();
                    p = scaledPix;
                }
            }
            Log.i(LOG_TAG, "image process stop " + OCR.stopCaptureLogs());

            //3.1摘自NewDocumentActivity中handleLoadedImage方法部分代码
            int originalHeight = p.getHeight();
            int originalWidth = p.getWidth();
            String ocrLanguage = PreferencesUtils.getOCRLanguage(context).first;

            skipCrop = true;//这里强制设置为true测试
            if (skipCrop) {//跳过裁剪
                //3.2摘自OCRActivity中onEventMainThread方法的部分代码
                if (PreferencesUtils.getLayout(context) == PreferencesUtils.LAYOUT_SIMPLE) {
                    //3.3摘自OCRActivity中onLayoutChosen方法
                    mOCR.startOCRForSimpleLayoutSyn(context, ocrLanguage, p, originalWidth, originalHeight);//这里imageview的width和height简单设置为原图大小
                } else {//非simple layout的话那就是配置了layout

                }
            } else {//没有跳过裁剪，那么就是图片配置了裁剪区域，则执行下面的代码

            }
            return true;
        }
    }

    //取消现有任务
    public void cancelTask() {
        if (mOCRTask != null) {
            mOCRTask.cancel(true);
            mOCRTask = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (mProgressDialog != null) {//有进度条对话框的话先清掉
            mProgressDialog.dismiss();
            mProgressDialog = null;
            cancelTask();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * 仿照的OCRActivity中的ProgressHandler，处理是在UI线程中执行的。这个Handler并不做那么多的处理，有些消息是空处理。
     * todo MIPOCRHandler是非static的，可能会导致内存溢出
     */
    private class MIPOCRHandler extends Handler {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case OCR.MESSAGE_EXPLANATION_TEXT: {//更新toolbar显示的步骤文本 --> hujiawei 不处理该消息
                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        mProgressDialog.setMessage(String.format(getString(R.string.progress_ocr_dialog), mCurrentProgressIndex, mImages.size(), getString(msg.arg1)));//
                    }
                    break;
                }
                case OCR.MESSAGE_TESSERACT_PROGRESS: {//更新显示的处理进度
                    int percent = msg.arg1;
                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        mProgressDialog.setMessage(String.format(getString(R.string.progress_ocr_dialog), mCurrentProgressIndex, mImages.size(), String.format("%s %%", percent)));//
                    }
                    break;
                }
                case OCR.MESSAGE_PREVIEW_IMAGE: {//在OCR的onPregressImage方法中被调用，表示后台对图片进行了处理，前台需要更新 --> hujiawei 不处理该消息
                    break;
                }
                case OCR.MESSAGE_FINAL_IMAGE: {//在OCR的simpleLayout方法中发送消息，表示最终要进行OCR识别的图片
                    long nativePix = (long) msg.obj;
                    if (nativePix != 0) {
                        mFinalPix = new Pix(nativePix);
                    }
                    break;
                }
                case OCR.MESSAGE_LAYOUT_PIX: {//在分析布局的时候会发送该消息，同样这个时候也需要前台更新显示后台处理得到的图片 --> hujiawei 不处理该消息
                    break;
                }
                case OCR.MESSAGE_LAYOUT_ELEMENTS: {//分析出图片中的布局元素，识别出来的结果包含文本片段集合和图片片段集合，接下来由用户选择需要处理的部分
                    //todo 将这里自动化！！！

                    break;
                }
                case OCR.MESSAGE_HOCR_TEXT: {
                    mHocrText = (String) msg.obj;
                    mAccuracy = msg.arg1;
                    break;
                }
                case OCR.MESSAGE_UTF8_TEXT: {
                    mUtf8Text = (String) msg.obj;
                    break;
                }
                case OCR.MESSAGE_END: {//OCR处理结束，结束之后就保存文档
                    saveDocumentSyn(mFinalPix, mHocrText, mUtf8Text, mLanguage, mAccuracy);
                    break;
                }
                case OCR.MESSAGE_ERROR: {//OCR出错了
                    Toast.makeText(getApplicationContext(), getText(msg.arg1), Toast.LENGTH_LONG).show();
                    cancelTask();
                    break;
                }
            }
        }
    }

    /**
     * 摘自OCRActivity中的源码，将异步操作改为同步直接执行：OCR处理结束之后开始保存文档
     * <p/>
     * todo 出错了要取消任务
     */
    private void saveDocumentSyn(final Pix pix, final String hocrString, final String utf8String, final String language, final int accuracy) {
        Log.i(LOG_TAG, "save document text=" + utf8String);

        File imageFile = null;
        long datetime = System.currentTimeMillis();
        String fileName = String.valueOf(datetime);
        try {//直接调用了Util.savePixToSD方法，修改了图片的名称
            imageFile = Util.savePixToSD(pix, fileName);//保存最终处理的图片（多个片段合并在一起的图片），并不是原图
        } catch (IOException e) {
            e.printStackTrace();
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), getText(R.string.error_create_file), Toast.LENGTH_LONG).show();
                }
            });
        }

        try {
            Uri documentUri = saveDocumentToDB(imageFile, hocrString, utf8String, language, datetime);//保存文档
            if (imageFile != null) {//创建一个缩略图保存下来
                Util.createThumbnail(this, imageFile, Integer.valueOf(documentUri.getLastPathSegment()));
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), getText(R.string.error_create_file), Toast.LENGTH_LONG).show();
                }
            });
        } finally {
            if (pix != null) {
                pix.recycle();
            }
        }
    }

    //保存document到数据库中 -> 添加了language和datetime两个值
    private Uri saveDocumentToDB(File imageFile, String hocr, String plainText, String language, long datetime) throws RemoteException {
        ContentProviderClient client = null;
        try {
            ContentValues contentValues = new ContentValues();
            if (imageFile != null) {
                contentValues.put(DocumentContentProvider.Columns.PHOTO_PATH, imageFile.getPath());//图片路径
            }
            if (hocr != null) {
                contentValues.put(DocumentContentProvider.Columns.HOCR_TEXT, hocr);
            }
            if (plainText != null) {
                contentValues.put(DocumentContentProvider.Columns.OCR_TEXT, plainText);
            }
            if (language != null) {
                contentValues.put(DocumentContentProvider.Columns.OCR_LANG, language);
            }
            if (getParentId() > -1) {
                contentValues.put(DocumentContentProvider.Columns.PARENT_ID, getParentId());
            }
            contentValues.put(DocumentContentProvider.Columns.CREATED, datetime);
            client = getContentResolver().acquireContentProviderClient(DocumentContentProvider.CONTENT_URI);
            return client.insert(DocumentContentProvider.CONTENT_URI, contentValues);
        } finally {
            if (client != null) {
                client.release();
            }
        }
    }
}

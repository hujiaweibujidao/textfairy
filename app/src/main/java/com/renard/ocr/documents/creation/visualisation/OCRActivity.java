/*
 * Copyright (C) 2012, 2013, 2014, 2015 Renard Wellnitz.
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
package com.renard.ocr.documents.creation.visualisation;

import android.Manifest;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.Pixa;
import com.googlecode.tesseract.android.OCR;
import com.renard.ocr.R;
import com.renard.ocr.base.MonitoredActivity;
import com.renard.ocr.base.PermissionGrantedEvent;
import com.renard.ocr.documents.creation.visualisation.LayoutQuestionDialog.LayoutChoseListener;
import com.renard.ocr.documents.creation.visualisation.LayoutQuestionDialog.LayoutKind;
import com.renard.ocr.documents.viewing.DocumentContentProvider;
import com.renard.ocr.documents.viewing.DocumentContentProvider.Columns;
import com.renard.ocr.documents.viewing.grid.DocumentGridActivity;
import com.renard.ocr.thu.MIPActivity;
import com.renard.ocr.util.PreferencesUtils;
import com.renard.ocr.util.Screen;
import com.renard.ocr.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;

/**
 * ocr界面
 * <p/>
 * this activity is shown during the ocr process
 *
 * @author renard
 */
public class OCRActivity extends MonitoredActivity implements LayoutChoseListener {

    @SuppressWarnings("unused")
    private static final String LOG_TAG = OCRActivity.class.getSimpleName();

    private static final String OCR_LANGUAGE = "ocr_language";
    public static final String EXTRA_PARENT_DOCUMENT_ID = "parent_id";
    public static final String EXTRA_USE_ACCESSIBILITY_MODE = "ACCESSIBILTY_MODE";

    @Bind(R.id.column_pick_completed)
    protected Button mButtonStartOCR;
    @Bind(R.id.progress_image)
    protected OCRImageView mImageView;

    private int mOriginalHeight = 0;
    private int mOriginalWidth = 0;
    private Pix mFinalPix;
    private String mOcrLanguage; // is set by dialog in
    private int mAccuracy;

    private OCR mOCR;
    // receives messages from background task
    private Messenger messenger = new Messenger(new ProgressHandler());
    // if >=0 its the id of the parent document to which the current page shall be added
    private int mParentId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);

        long nativePix = getIntent().getLongExtra(DocumentGridActivity.EXTRA_NATIVE_PIX, -1);
        mParentId = getIntent().getIntExtra(EXTRA_PARENT_DOCUMENT_ID, -1);//从CropImageActivity跳转过来的话并没有这个参数

        if (nativePix == -1) {//没有对应的图片可以直接返回
            startActivity(new Intent(this, MIPActivity.class));//返回到MIP
            finish();
            return;
        }

        mOCR = new OCR(this, messenger);
        Screen.lockOrientation(this);//锁定屏幕
        setContentView(R.layout.activity_ocr);
        ButterKnife.bind(this);

        initToolbar();
        ensurePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, R.string.permission_explanation);
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(final PermissionGrantedEvent event) {
        long nativePix = getIntent().getLongExtra(DocumentGridActivity.EXTRA_NATIVE_PIX, -1);
        final Pix pixOrg = new Pix(nativePix);
        mOriginalHeight = pixOrg.getHeight();
        mOriginalWidth = pixOrg.getWidth();

        //askUserAboutDocumentLayout();//hujiawei 不再询问用户布局情况，直接使用默认的布局
        Log.i(LOG_TAG, PreferencesUtils.getOCRLanguage(this).first);//chi_sim
        Log.i(LOG_TAG, String.valueOf(PreferencesUtils.getLayout(this)==PreferencesUtils.LAYOUT_SIMPLE));//layout_simple?
        Log.i(LOG_TAG, String.valueOf(PreferencesUtils.getMode(this)==PreferencesUtils.MODE_HTEXT));//mode_htext?

        if (PreferencesUtils.getLayout(this) == PreferencesUtils.LAYOUT_SIMPLE) {
            onLayoutChosen(LayoutKind.SIMPLE, PreferencesUtils.getOCRLanguage(this).first);
        } else {
            onLayoutChosen(LayoutKind.SIMPLE, PreferencesUtils.getOCRLanguage(this).first);
        }
    }

    //询问用户关于图片中文字的布局情况
    /*private void askUserAboutDocumentLayout() {
        LayoutQuestionDialog dialog = LayoutQuestionDialog.newInstance();
        dialog.show(getSupportFragmentManager(), LayoutQuestionDialog.TAG);
    }*/

    //用户选好了布局之后该方法就会被调用
    @Override
    public void onLayoutChosen(LayoutKind layoutKind, String ocrLanguage) {
        long nativePix = getIntent().getLongExtra(DocumentGridActivity.EXTRA_NATIVE_PIX, -1);
        final Pix pixOrg = new Pix(nativePix);

        if (layoutKind == LayoutKind.DO_NOTHING) {//todo 貌似不可能是DO_NOTHING
            saveDocument(pixOrg, null, null, 0);
        } else {
            mOcrLanguage = ocrLanguage;
            setToolbarMessage(R.string.progress_start);

            //todo 改进版本：简单布局的话那就不用继续了？复杂布局的话需要先进行自动布局分析（如果开启的话），然后选择布局情况。
            if (layoutKind == LayoutKind.SIMPLE) {//简单布局，这种情况下可以完全自动 --> todo mAccuracy = 0; ?
                mOCR.startOCRForSimpleLayout(OCRActivity.this, ocrLanguage, pixOrg, mImageView.getWidth(), mImageView.getHeight());
            } else if (layoutKind == LayoutKind.COMPLEX) {//复杂布局，这种情况下还需要选择需要处理的列
                mAccuracy = 0;
                //这里会开启线程进行布局分析，得到了布局结果之后会发送消息，ProgressHandler接到消息就能继续处理了
                mOCR.startLayoutAnalysis(OCRActivity.this, pixOrg, mImageView.getWidth(), mImageView.getHeight());//自动分析布局，这里不需要language
            }
        }
    }

    /**
     * 接收OCR处理进度的Handler
     * receives progress status messages from the background ocr task and displays them in the current activity
     */
    private class ProgressHandler extends Handler {

        //private long layoutPix;
        private int mPreviewWith;
        private String hocrString;
        private String utf8String;
        private int mPreviewHeight;
        private boolean mHasStartedOcr = false;

        public void handleMessage(Message msg) {
            switch (msg.what) {

                case OCR.MESSAGE_EXPLANATION_TEXT: {//更新toolbar显示的步骤文本
                    setToolbarMessage(msg.arg1);
                    break;
                }
                case OCR.MESSAGE_TESSERACT_PROGRESS: {//更新显示的处理进度
                    if (!mHasStartedOcr) {
                        mAnalytics.sendScreenView("Ocr");
                        mHasStartedOcr = true;
                    }
                    int percent = msg.arg1;
                    Bundle data = msg.getData();
                    //设置进度，文字box
                    mImageView.setProgress(percent, (RectF) data.getParcelable(OCR.EXTRA_WORD_BOX),
                            (RectF) data.getParcelable(OCR.EXTRA_OCR_BOX));
                    break;
                }
                case OCR.MESSAGE_PREVIEW_IMAGE: {//在OCR的onPregressImage方法中被调用，表示后台对图片进行了处理，前台需要更新
                    mImageView.setImageBitmapResetBase((Bitmap) msg.obj, true, 0);
                    break;
                }
                case OCR.MESSAGE_FINAL_IMAGE: {//在OCR的simpleLayout方法中发送消息，表示最终要进行OCR识别的图片
                    long nativePix = (long) msg.obj;

                    if (nativePix != 0) {
                        mFinalPix = new Pix(nativePix);
                    }
                    break;
                }
                case OCR.MESSAGE_LAYOUT_PIX: {//在分析布局的时候会发送该消息，同样这个时候也需要前台更新显示后台处理得到的图片
                    Bitmap layoutPix = (Bitmap) msg.obj;
                    mPreviewHeight = layoutPix.getHeight();
                    mPreviewWith = layoutPix.getWidth();
                    mImageView.setImageBitmapResetBase(layoutPix, true, 0);
                    break;
                }
                case OCR.MESSAGE_LAYOUT_ELEMENTS: {//分析出图片中的布局元素，识别出来的结果包含文本片段集合和图片片段集合，接下来由用户选择需要处理的部分
                    int nativePixaText = msg.arg1;//文本集合
                    int nativePixaImages = msg.arg2;//图片集合
                    final Pixa texts = new Pixa(nativePixaText, 0, 0);//width,height
                    final Pixa images = new Pixa(nativePixaImages, 0, 0);

                    ArrayList<Rect> boxes = images.getBoxRects();
                    ArrayList<RectF> scaledBoxes = new ArrayList<>(boxes.size());
                    float xScale = (1.0f * mPreviewWith) / mOriginalWidth;//缩放因子：图片预览大小/图片原始大小
                    float yScale = (1.0f * mPreviewHeight) / mOriginalHeight;
                    // scale to the preview image space
                    for (Rect r : boxes) {
                        scaledBoxes.add(new RectF(r.left * xScale, r.top * yScale, r.right * xScale, r.bottom * yScale));
                    }
                    mImageView.setImageRects(scaledBoxes);//设置图片片段

                    boxes = texts.getBoxRects();
                    scaledBoxes = new ArrayList<>(boxes.size());
                    for (Rect r : boxes) {
                        scaledBoxes.add(new RectF(r.left * xScale, r.top * yScale, r.right * xScale, r.bottom * yScale));
                    }
                    mImageView.setTextRects(scaledBoxes);//设置文本片段

                    mButtonStartOCR.setVisibility(View.VISIBLE);
                    mButtonStartOCR.setOnClickListener(new OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            int[] selectedTexts = mImageView.getSelectedTextIndexes();//获取选中的文本和图片部分，返回的int数组保存的是选中部分的编号
                            int[] selectedImages = mImageView.getSelectedImageIndexes();
                            if (selectedTexts.length > 0 || selectedImages.length > 0) {//有选择的内容了，那就可以进行OCR了
                                mImageView.clearAllProgressInfo();
                                mOCR.startOCRForComplexLayout(OCRActivity.this, mOcrLanguage, texts,
                                        images, selectedTexts, selectedImages);//这里才带上了language参数
                                mButtonStartOCR.setVisibility(View.GONE);
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.please_tap_on_column, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    mAnalytics.sendScreenView("Pick Columns");
                    setToolbarMessage(R.string.progress_choose_columns);
                    break;
                }
                case OCR.MESSAGE_HOCR_TEXT: {
                    this.hocrString = (String) msg.obj;
                    mAccuracy = msg.arg1;
                    break;
                }
                case OCR.MESSAGE_UTF8_TEXT: {
                    this.utf8String = (String) msg.obj;
                    break;
                }
                case OCR.MESSAGE_END: {//OCR处理结束，结束之后就保存文档
                    saveDocument(mFinalPix, hocrString, utf8String, mAccuracy);
                    //hujiawei 算是失败返回了
                    setResult(RESULT_OK);
                    finish();
                    break;
                }
                case OCR.MESSAGE_ERROR: {//OCR出错了
                    Toast.makeText(getApplicationContext(), getText(msg.arg1), Toast.LENGTH_LONG).show();
                    //hujiawei 算是失败返回了
                    setResult(RESULT_CANCELED);
                    finish();
                    break;
                }
            }
        }
    }

    //OCR处理结束之后开始保存文档
    private void saveDocument(final Pix pix, final String hocrString, final String utf8String, final int accuracy) {
        Util.startBackgroundJob(OCRActivity.this, "", getText(R.string.saving_document).toString(), new Runnable() {

            @Override
            public void run() {
                File imageFile = null;
                Uri documentUri = null;

                try {
                    imageFile = saveImage(pix);//保存最终处理的图片（多个片段合并在一起的图片），并不是原图
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
                    documentUri = saveDocumentToDB(imageFile, hocrString, utf8String);//保存文档
                    if (imageFile != null) {//创建一个缩略图保存下来
                        Util.createThumbnail(OCRActivity.this, imageFile, Integer.valueOf(documentUri.getLastPathSegment()));
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
                    recycleResultPix(pix);

                    //准备跳转到 DocumentActivity ---> 批量处理的时候就不跳转了
                    /*
                    if (documentUri != null && !isFinishing()) {
                        Intent intent = new Intent(OCRActivity.this, DocumentActivity.class);//跳到DocumentActivity
                        intent.putExtra(DocumentActivity.EXTRA_ACCURACY, accuracy);
                        intent.putExtra(DocumentActivity.EXTRA_LANGUAGE, mOcrLanguage);
                        intent.setData(documentUri);
                        intent.putExtra(DocumentGridActivity.EXTRA_NATIVE_PIX, pix.getNativePix());
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                        Screen.unlockOrientation(OCRActivity.this);
                    }*/

                    //startActivity(new Intent(OCRActivity.this, DocumentGridActivity.class));
                    //应该采取发送消息的形式，这张图片处理完了之后发送消息，在MIPActivity中接收消息，如果还有未处理的继续处理，如果没有了那就跳转到DocumentGridActivity
                }
            }
        }, new Handler());
    }

    private void recycleResultPix(Pix pix) {
        if (pix != null) {
            pix.recycle();
        }
    }

    //保存最终的Pix到sd卡中
    private File saveImage(Pix p) throws IOException {
        CharSequence id = DateFormat.format("ssmmhhddMMyy", new Date(System.currentTimeMillis()));
        return Util.savePixToSD(p, id.toString());
    }

    //保存document到数据库中
    private Uri saveDocumentToDB(File imageFile, String hocr, String plainText) throws RemoteException {
        ContentProviderClient client = null;
        try {
            ContentValues v = new ContentValues();
            if (imageFile != null) {
                v.put(DocumentContentProvider.Columns.PHOTO_PATH, imageFile.getPath());//图片路径
            }
            if (hocr != null) {
                v.put(Columns.HOCR_TEXT, hocr);
            }
            if (plainText != null) {
                v.put(Columns.OCR_TEXT, plainText);
            }
            v.put(Columns.OCR_LANG, mOcrLanguage);

            if (mParentId > -1) {
                v.put(Columns.PARENT_ID, mParentId);
            }
            client = getContentResolver().acquireContentProviderClient(DocumentContentProvider.CONTENT_URI);
            return client.insert(DocumentContentProvider.CONTENT_URI, v);
        } finally {
            if (client != null) {
                client.release();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mOcrLanguage != null) {
            outState.putString(OCR_LANGUAGE, mOcrLanguage);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (mOcrLanguage == null) {
            mOcrLanguage = savedInstanceState.getString(OCR_LANGUAGE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        if (mFinalPix != null) {
            mFinalPix.recycle();
            mFinalPix = null;
        }
        mImageView.clear();
    }
}

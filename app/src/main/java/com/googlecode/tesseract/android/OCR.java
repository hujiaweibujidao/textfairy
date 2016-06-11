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
package com.googlecode.tesseract.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.googlecode.leptonica.android.Boxa;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.Pixa;
import com.googlecode.leptonica.android.WriteFile;
import com.googlecode.tesseract.android.TessBaseAPI.PageSegMode;
import com.renard.ocr.R;
import com.renard.ocr.analytics.Analytics;
import com.renard.ocr.base.MonitoredActivity;
import com.renard.ocr.base.TextFairyApplication;
import com.renard.ocr.documents.creation.crop.CropImageScaler;
import com.renard.ocr.language.OcrLanguage;
import com.renard.ocr.util.MemoryInfo;
import com.renard.ocr.util.Util;

import java.io.File;

/**
 * OCR操作类
 */
public class OCR extends MonitoredActivity.LifeCycleAdapter implements OcrProgressListener {

    //private static final String TAG = OCR.class.getSimpleName();

    public static final int MESSAGE_PREVIEW_IMAGE = 3;
    public static final int MESSAGE_END = 4;
    public static final int MESSAGE_ERROR = 5;
    public static final int MESSAGE_TESSERACT_PROGRESS = 6;
    public static final int MESSAGE_FINAL_IMAGE = 7;
    public static final int MESSAGE_UTF8_TEXT = 8;
    public static final int MESSAGE_HOCR_TEXT = 9;
    public static final int MESSAGE_LAYOUT_ELEMENTS = 10;
    public static final int MESSAGE_LAYOUT_PIX = 11;
    public static final int MESSAGE_EXPLANATION_TEXT = 12;

    public static final String EXTRA_WORD_BOX = "word_box";
    public static final String EXTRA_OCR_BOX = "ocr_box";
    private static final String LOG_TAG = OCR.class.getSimpleName();

    static {
        System.loadLibrary("pngo");
        System.loadLibrary("lept");
        System.loadLibrary("tess");
        System.loadLibrary("image_processing_jni");
        nativeInit();
    }

    private final Analytics mAnalytics;
    private final Context mApplicationContext;

    private int mPreviewWith;
    private int mPreviewHeight;
    private int mOriginalWidth;
    private int mOriginalHeight;
    private RectF mWordBoundingBox = new RectF();
    private RectF mOCRBoundingBox = new RectF();
    private Messenger mMessenger;
    private boolean mIsActivityAttached = false;

    protected TessBaseAPI mTess;
    private boolean mStopped;
    private int mPreviewHeightUnScaled;
    private int mPreviewWidthUnScaled;
    private boolean mCompleted;

    //传入的messenger在这个类中主要是用来发送消息的
    public OCR(final MonitoredActivity activity, final Messenger messenger) {
        mApplicationContext = activity.getApplicationContext();
        mAnalytics = activity.getAnaLytics();
        mMessenger = messenger;
        mIsActivityAttached = true;
        activity.addLifeCycleListener(this);
    }

    @Override
    public synchronized void onActivityDestroyed(MonitoredActivity activity) {
        mIsActivityAttached = false;
        cancel();
    }

    @Override
    public synchronized void onActivityResumed(MonitoredActivity activity) {
        mIsActivityAttached = true;
    }

    /**
     * TessractAPI会调用这个方法来更新当前处理进度
     * <p/>
     * called from tess api
     *
     * @param percent of ocr process comleted
     * @param left    edge of current word boundary
     * @param right   edge of current word boundary
     * @param top     edge of current word boundary
     * @param bottom  edge of current word boundary
     */
    public void onProgressValues(final int percent, final int left, final int right, final int top, final int bottom, final int left2, final int right2, final int top2, final int bottom2) {
        logProgressToCrashlytics(percent);
        int newBottom = (bottom2 - top2) - bottom;
        int newTop = (bottom2 - top2) - top;
        // scale the word bounding rectangle to the preview image space
        float xScale = (1.0f * mPreviewWith) / mOriginalWidth;
        float yScale = (1.0f * mPreviewHeight) / mOriginalHeight;
        mWordBoundingBox.set((left + left2) * xScale, (newTop + top2) * yScale, (right + left2) * xScale, (newBottom + top2) * yScale);
        mOCRBoundingBox.set(left2 * xScale, top2 * yScale, right2 * xScale, bottom2 * yScale);
        Bundle b = new Bundle();
        b.putParcelable(EXTRA_OCR_BOX, mOCRBoundingBox);
        b.putParcelable(EXTRA_WORD_BOX, mWordBoundingBox);
        sendMessage(MESSAGE_TESSERACT_PROGRESS, percent, b);//发送处理进度的消息
    }

    //记录进度
    private void logProgressToCrashlytics(int percent) {
        if (TextFairyApplication.isRelease()) {
            long availableMegs = MemoryInfo.getFreeMemory(mApplicationContext);
            Crashlytics.log("available ram = " + availableMegs);
            Crashlytics.setInt("ocr progress", percent);
        }
    }

    /**
     * 处理图片发生了变化
     * <p/>
     * 这个方法被native层调用，传入的nativePix是native层处理得到的pix，传回到这里之后要更新界面显示的图片
     * <p/>
     * called from native code
     */
    private synchronized void onProgressImage(final long nativePix) {
        if (mMessenger != null && mIsActivityAttached) {
            Log.i(LOG_TAG, "onProgressImage " + nativePix);
            Pix preview = new Pix(nativePix);
            CropImageScaler scaler = new CropImageScaler();//这里要按照原来的比例缩放native层处理得到的图片以显示在界面上
            final CropImageScaler.ScaleResult scale = scaler.scale(preview, mPreviewWidthUnScaled, mPreviewHeightUnScaled);
            final Bitmap previewBitmap = WriteFile.writeBitmap(scale.getPix());//将pix转成bitmap方便前台展示
            if (previewBitmap != null) {
                scale.getPix().recycle();
                mPreviewHeight = previewBitmap.getHeight();
                mPreviewWith = previewBitmap.getWidth();
                sendMessage(MESSAGE_PREVIEW_IMAGE, previewBitmap);
            }
        }
    }

    /**
     * 提示文本发生了变化
     * <p/>
     * 这个方法被native层调用，根据id值发送不同的消息，实际上就是后端告诉前端处理过程进行到哪一步了
     * <p/>
     * static const int MESSAGE_IMAGE_DETECTION = 0;
     * static const int MESSAGE_IMAGE_DEWARP = 1;
     * static const int MESSAGE_OCR = 2;
     * static const int MESSAGE_ASSEMBLE_PIX = 3;
     * static const int MESSAGE_ANALYSE_LAYOUT = 4;
     */
    private void onProgressText(int id) {
        int messageId = 0;
        switch (id) {
            case 0:
                messageId = R.string.progress_image_detection;//在nativeAnalyseLayout方法中发出
                break;
            case 1:
                messageId = R.string.progress_dewarp;
                break;
            case 2:
                messageId = R.string.progress_ocr;
                break;
            case 3:
                messageId = R.string.progress_assemble_pix;
                break;
            case 4:
                messageId = R.string.progress_analyse_layout;
                break;
        }
        if (messageId != 0) {
            sendMessage(MESSAGE_EXPLANATION_TEXT, messageId);
        }
    }

    /**
     * 这个方法是在native层被调用的，而且是在方法nativeAnalyseLayout中调用的，它识别出图片中的文本片段集合和图片片段集合，通知给前台，由用户来选择内容
     *
     * @param nativePixaText   文本片段集合
     * @param nativePixaImages 图片片段集合
     */
    private void onLayoutElements(int nativePixaText, int nativePixaImages) {
        sendMessage(MESSAGE_LAYOUT_ELEMENTS, nativePixaText, nativePixaImages);
    }

    /**
     * 分析布局的时候会被调用
     * 在native中的nativeAnalyseLayout方法中会调用segmentComplexLayout，后者会调用onLayoutPix方法
     * called from native
     *
     * @param nativePix pix pointer
     */
    private void onLayoutPix(long nativePix) {
        if (mMessenger != null && mIsActivityAttached) {
            Log.i(LOG_TAG, "onLayoutPix " + nativePix);
            Pix preview = new Pix(nativePix);
            CropImageScaler scaler = new CropImageScaler();
            final CropImageScaler.ScaleResult scale = scaler.scale(preview, mPreviewWidthUnScaled, mPreviewHeightUnScaled);
            final Bitmap previewBitmap = WriteFile.writeBitmap(scale.getPix());
            if (previewBitmap != null) {
                scale.getPix().recycle();
                sendMessage(MESSAGE_LAYOUT_PIX, previewBitmap);
            } else {
                sendMessage(MESSAGE_ERROR, R.string.error_title);
            }
        }
    }

    /**
     * called from native
     *
     * @param hocr string
     */
    private void onHOCRResult(String hocr, int accuracy) {
        sendMessage(MESSAGE_HOCR_TEXT, hocr, accuracy);
    }

    /**
     * called from native
     *
     * @param utf8Text string
     */
    private void onUTF8Result(String utf8Text) {
        sendMessage(MESSAGE_UTF8_TEXT, utf8Text);
    }

    private void sendMessage(int what) {
        sendMessage(what, 0, 0, null, null);
    }

    private void sendMessage(int what, int arg1, int arg2) {
        sendMessage(what, arg1, arg2, null, null);
    }

    private void sendMessage(int what, String string) {
        sendMessage(what, 0, 0, string, null);
    }

    private void sendMessage(int what, String string, int accuracy) {
        sendMessage(what, accuracy, 0, string, null);
    }

    private void sendMessage(int what, long nativeTextPix) {
        sendMessage(what, 0, 0, nativeTextPix, null);
    }

    private void sendMessage(int what, int arg1) {
        sendMessage(what, arg1, 0, null, null);
    }

    private void sendMessage(int what, Bitmap previewBitmap) {
        sendMessage(what, 0, 0, previewBitmap, null);
    }

    private void sendMessage(int what, int arg1, Bundle b) {
        sendMessage(what, arg1, 0, null, b);
    }

    //最终都是调用这个sendMessage方法，消息发送之后在OCRActivity中被处理 166行附近
    private synchronized void sendMessage(int what, int arg1, int arg2, Object object, Bundle b) {
        if (mIsActivityAttached && !mStopped) {
            Message m = Message.obtain();
            m.what = what;
            m.arg1 = arg1;
            m.arg2 = arg2;
            m.obj = object;
            m.setData(b);
            try {
                mMessenger.send(m);
            } catch (RemoteException ignore) {
                ignore.printStackTrace();
            }
        }
    }

    //确定ocr模式，中文不支持
    private int determineOcrMode(String lang) {
        boolean hasCubeSupport = OcrLanguage.hasCubeSupport(lang);//是否支持cube
        boolean canCombine = OcrLanguage.canCombineCubeAndTesseract(lang);//
        if (canCombine) {
            return TessBaseAPI.OEM_TESSERACT_CUBE_COMBINED;
        } else if (hasCubeSupport) {
            return TessBaseAPI.OEM_DEFAULT;
        } else {
            return TessBaseAPI.OEM_TESSERACT_ONLY;//
        }
    }

    //确定ocr语言，chi_sim等
    private String determineOcrLanguage(String ocrLanguage) {
        final String english = "eng";
        if (!ocrLanguage.equals(english) && addEnglishData(ocrLanguage)) {
            return ocrLanguage + "+" + english;
        } else {
            return ocrLanguage;
        }
    }

    // when combining languages that have multi byte characters with english
    // training data the ocr text gets corrupted
    // but adding english will improve overall accuracy for the other languages
    private boolean addEnglishData(String mLanguage) {
        return !(mLanguage.startsWith("chi") || mLanguage.equalsIgnoreCase("tha")
                || mLanguage.equalsIgnoreCase("kor")
                //|| mLanguage.equalsIgnoreCase("hin")
                //|| mLanguage.equalsIgnoreCase("heb")
                || mLanguage.equalsIgnoreCase("jap")
                //|| mLanguage.equalsIgnoreCase("ell")
                || mLanguage.equalsIgnoreCase("bel")
                || mLanguage.equalsIgnoreCase("ara")
                || mLanguage.equalsIgnoreCase("grc")
                || mLanguage.equalsIgnoreCase("rus")
                || mLanguage.equalsIgnoreCase("vie"));
    }

    /**
     * 这个方法由OCRActivity的handler在MESSAGE_LAYOUT_ELEMENTS情况下跳转过来的
     * 复杂布局，开始进行OCR处理
     * native code takes care of both Pixa, do not use them after calling this function
     *
     * @param pixaText   must contain the binary text parts
     * @param pixaImages pixaImages must contain the image parts
     */
    public void startOCRForComplexLayout(final Context context, final String lang, final Pixa pixaText, final Pixa pixaImages, final int[] selectedTexts, final int[] selectedImages) {
        if (pixaText == null) {
            throw new IllegalArgumentException("text pixa must be non-null");
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String tessDir = Util.getTessDir(context);

                    //将选中的部分合并起来，作为要识别的内容，得到的是一个新图片！
                    long[] columnData = combineSelectedPixa(pixaText.getNativePixa(), pixaImages.getNativePixa(), selectedTexts, selectedImages);
                    long pixOrgPointer = columnData[0];//
                    long pixOcrPointer = columnData[1];//
                    long boxaColumnsPointer = columnData[2];//

                    sendMessage(MESSAGE_FINAL_IMAGE, pixOrgPointer);
                    sendMessage(MESSAGE_EXPLANATION_TEXT, R.string.progress_ocr);

                    Boxa boxa;
                    Pix pixOcr;
                    synchronized (OCR.this) {
                        logMemory(context);
                        final String ocrLanguages = determineOcrLanguage(lang);
                        int ocrMode = determineOcrMode(lang);
                        if (!initTessApi(tessDir, ocrLanguages, ocrMode)) return; //初始化Tessract

                        pixOcr = new Pix(pixOcrPointer);
                        mTess.setPageSegMode(PageSegMode.PSM_SINGLE_BLOCK);//setPageSegMode 不能单独为某个box设置PageSegMode
                        mTess.setImage(pixOcr);
                        boxa = new Boxa(boxaColumnsPointer);
                        mOriginalHeight = pixOcr.getHeight();
                        mOriginalWidth = pixOcr.getWidth();
                    }

                    synchronized (OCR.this) {
                        if (mStopped) {
                            return;
                        }
                    }

                    int xb, yb, wb, hb;
                    int columnCount = boxa.getCount();//片段总数
                    float accuracy = 0;
                    int[] geometry = new int[4];
                    StringBuilder hocrText = new StringBuilder();
                    StringBuilder htmlText = new StringBuilder();

                    for (int i = 0; i < columnCount; i++) {//一个一个处理
                        if (!boxa.getGeometry(i, geometry)) {
                            continue;
                        }
                        xb = geometry[0];
                        yb = geometry[1];
                        wb = geometry[2];
                        hb = geometry[3];
                        mTess.setRectangle(xb, yb, wb, hb);

                        synchronized (OCR.this) {
                            if (mStopped) {
                                return;
                            }
                        }
                        hocrText.append(mTess.getHOCRText(0));

                        synchronized (OCR.this) {
                            if (mStopped) {
                                return;
                            }
                        }
                        htmlText.append(mTess.getHtmlText());
                        accuracy += mTess.meanConfidence();
                    }
                    int totalAccuracy = Math.round(accuracy / columnCount);
                    pixOcr.recycle();
                    boxa.recycle();

                    sendMessage(MESSAGE_HOCR_TEXT, hocrText.toString(), totalAccuracy);
                    sendMessage(MESSAGE_UTF8_TEXT, htmlText.toString(), totalAccuracy);
                } finally {
                    if (mTess != null) {
                        mTess.end();
                    }
                    mCompleted = true;
                    sendMessage(MESSAGE_END);
                }
            }
        }).start();
    }

    //初始化tess api
    private boolean initTessApi(String tessDir, String lang, int ocrMode) {
        logTessParams(lang, ocrMode);
        mTess = new TessBaseAPI(OCR.this);
        boolean result = mTess.init(tessDir, lang, ocrMode);
        if (!result) {
            sendMessage(MESSAGE_ERROR, R.string.error_tess_init);
            return false;
        }
        mTess.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "ﬀﬁﬂﬃﬄﬅﬆ");//toread 后面这串字符串很奇怪
        //toread setVariable(VAR_TESSEDIT_CHAR_BLACKLIST, "xyz"); to ignore x, y and z.
        return true;
    }

    //记录tess参数
    private void logTessParams(String lang, int ocrMode) {
        if (TextFairyApplication.isRelease()) {
            String pageSegMode = "";
            if (ocrMode == TessBaseAPI.OEM_TESSERACT_ONLY) {
                pageSegMode = "OEM_TESSERACT_ONLY";
            } else if (ocrMode == TessBaseAPI.OEM_TESSERACT_CUBE_COMBINED) {
                pageSegMode = "OEM_TESSERACT_CUBE_COMBINED";
                Crashlytics.setString("page seg mode", "OEM_TESSERACT_CUBE_COMBINED");
            } else if (ocrMode == TessBaseAPI.OEM_DEFAULT) {
                pageSegMode = "OEM_DEFAULT";
            }
            Crashlytics.setString("page seg mode", pageSegMode);
            Crashlytics.setString("ocr language", lang);
        }
    }

    /**
     * 开始进行布局分析
     * <p/>
     * native code takes care of the Pix, do not use it after calling this function
     *
     * @param pixs source pix on which to do layout analysis
     */
    public void startLayoutAnalysis(final Context context, final Pix pixs, int width, int height) {
        if (pixs == null) {
            throw new IllegalArgumentException("Source pix must be non-null");
        }

        mPreviewHeightUnScaled = height;
        mPreviewWidthUnScaled = width;
        mOriginalHeight = pixs.getHeight();
        mOriginalWidth = pixs.getWidth();

        new Thread(new Runnable() {
            @Override
            public void run() {
                nativeAnalyseLayout(pixs.getNativePix());
            }
        }).start();
    }

    /**
     * 在OCRActivity中的onLayoutChosen方法调用之后直接调用，前提是当前布局是简单布局，它假设图片裁剪的区域整个部分是内容区域
     * 简单布局，开始进行OCR处理
     * native code takes care of the Pix, do not use it after calling this function
     *
     * @param context used to access the file system
     * @param pixs    source pix to do ocr on
     */
    public void startOCRForSimpleLayout(final Context context, final String lang, final Pix pixs, int width, int height) {
        if (pixs == null) {
            throw new IllegalArgumentException("Source pix must be non-null");
        }
        mPreviewHeightUnScaled = height;
        mPreviewWidthUnScaled = width;
        mOriginalHeight = pixs.getHeight();
        mOriginalWidth = pixs.getWidth();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    startCaptureLogs();
                    logMemory(context);
                    final String tessDir = Util.getTessDir(context);
                    long nativeTextPix = nativeOCRBook(pixs.getNativePix());//nativeOCRBook 二值化以及矫正操作
                    Pix pixText = new Pix(nativeTextPix);
                    mOriginalHeight = pixText.getHeight();
                    mOriginalWidth = pixText.getWidth();
                    sendMessage(MESSAGE_EXPLANATION_TEXT, R.string.progress_ocr);
                    sendMessage(MESSAGE_FINAL_IMAGE, nativeTextPix);

                    synchronized (OCR.this) {
                        if (mStopped) {
                            return;
                        }
                        final String ocrLanguages = determineOcrLanguage(lang);
                        int ocrMode = determineOcrMode(lang);
                        if (!initTessApi(tessDir, ocrLanguages, ocrMode)) return;

                        mTess.setPageSegMode(PageSegMode.PSM_AUTO);
                        mTess.setImage(pixText);
                    }

                    String hocrText = mTess.getHOCRText(0);
                    int accuracy = mTess.meanConfidence();
                    final String utf8Text = mTess.getUTF8Text();

                    if (utf8Text.isEmpty()) {
                        Log.i(LOG_TAG, "No words found. Looking for sparse text.");
                        mTess.setPageSegMode(PageSegMode.PSM_SPARSE_TEXT);
                        mTess.setImage(pixText);
                        hocrText = mTess.getHOCRText(0);
                        accuracy = mTess.meanConfidence();
                    }

                    synchronized (OCR.this) {
                        if (mStopped) {
                            return;
                        }
                        String htmlText = mTess.getHtmlText();
                        if (accuracy == 95) {
                            accuracy = 0;
                        }

                        sendMessage(MESSAGE_HOCR_TEXT, hocrText, accuracy);
                        sendMessage(MESSAGE_UTF8_TEXT, htmlText, accuracy);
                    }
                } finally {
                    if (mTess != null) {
                        mTess.end();
                    }
                    String logs = stopCaptureLogs();
                    if (TextFairyApplication.isRelease()) {
                        Crashlytics.log(logs);
                    } else {
                        Log.i(LOG_TAG, logs);
                    }
                    mCompleted = true;
                    sendMessage(MESSAGE_END);
                }
            }
        }).start();
    }

    //记录此时的剩余内存情况
    private void logMemory(Context context) {
        if (TextFairyApplication.isRelease()) {
            final long freeMemory = MemoryInfo.getFreeMemory(context);
            Crashlytics.setLong("Memory", freeMemory);
        }
    }

    //--> hujiawei 将其保存到另一个目录，而且并不只保存最后一张图片
    final static String ORIGINAL_PIX_NAME = "last_scan";

    //将pix保存到cache目录中，cache目录是应用的cache目录
    public static void savePixToCacheDir(Context context, Pix pix) {
        File dir = new File(context.getCacheDir(), context.getString(R.string.config_share_file_dir));
        new SavePixTask(pix, dir).execute();
    }

    //应用只保存最后一张图片到cache目录中，这里是得到这张图片文件
    public static File getLastOriginalImageFromCache(Context context) {
        File dir = new File(context.getCacheDir(), context.getString(R.string.config_share_file_dir));
        return new File(dir, ORIGINAL_PIX_NAME + ".png");
    }

    public synchronized void cancel() {
        if (mTess != null) {
            if (!mCompleted) {
                mAnalytics.sendOcrCancelled();
            }
            mTess.stop();
        }
        mStopped = true;
    }

    // ******************
    // * Native methods *
    // ******************

    public static native void startCaptureLogs();

    public static native String stopCaptureLogs();

    private static native void nativeInit();

    /**
     * takes ownership of nativePix.
     *
     * @param nativePix native Pix
     * @return binarized and dewarped version of input pix
     */
    private native long nativeOCRBook(long nativePix);

    private native long[] combineSelectedPixa(long nativePixaTexts, long nativePixaImages, int[] selectedTexts, int[] selectedImages);

    private native long nativeAnalyseLayout(long nativePix);

}

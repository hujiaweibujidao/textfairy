package com.renard.ocr.documents.creation;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.Scale;
import com.googlecode.tesseract.android.OCR;
import com.renard.ocr.base.TextFairyApplication;

import java.io.IOException;


/**
 * 图片加载task
 *
 * @author renard
 */
public class ImageLoadAsyncTask extends AsyncTask<Void, Void, ImageLoadAsyncTask.LoadResult> {

    private static final String LOG_TAG = ImageLoadAsyncTask.class.getSimpleName();

    //加载结果
    public class LoadResult {
        private final Pix mPix;
        private final PixLoadStatus mStatus;

        public LoadResult(PixLoadStatus status) {
            mStatus = status;
            mPix = null;
        }

        public LoadResult(Pix p) {
            mStatus = PixLoadStatus.SUCCESS;
            mPix = p;
        }
    }

    final static String EXTRA_PIX = "pix";
    final static String EXTRA_STATUS = "status";
    final static String EXTRA_SKIP_CROP = "skip_crop";
    final static String ACTION_IMAGE_LOADED = ImageLoadAsyncTask.class.getName() + ".image.loaded";
    final static String ACTION_IMAGE_LOADING_START = ImageLoadAsyncTask.class.getName() + ".image.loading.start";

    public static final int MIN_PIXEL_COUNT = 3 * 1024 * 1024;
    private final boolean skipCrop;//todo hujiawei skipCrop在这个类中并没有起到作用，直接在onPostExecute中返回了
    private final Context context;//ApplicationContext
    private final Uri cameraPicUri;

    public ImageLoadAsyncTask(NewDocumentActivity activity, boolean skipCrop, Uri cameraPicUri) {
        context = activity.getApplicationContext();
        this.skipCrop = skipCrop;
        this.cameraPicUri = cameraPicUri;
    }

    @Override
    protected void onPreExecute() {
        Log.i(LOG_TAG, "onPreExecute");
        Intent intent = new Intent(ACTION_IMAGE_LOADING_START);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);//先发送一个广播，声明开始加载图片了
    }

    @Override
    protected void onPostExecute(LoadResult result) {
        Log.i(LOG_TAG, "onPostExecute");
        Intent intent = new Intent(ACTION_IMAGE_LOADED);
        if (result.mStatus == PixLoadStatus.SUCCESS) {
            intent.putExtra(EXTRA_PIX, result.mPix.getNativePix());
        }
        intent.putExtra(EXTRA_STATUS, result.mStatus.ordinal());
        intent.putExtra(EXTRA_SKIP_CROP, skipCrop);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);//图片加载完成之后再发送一个广播
    }

    @Override
    protected LoadResult doInBackground(Void... params) {
        if (isCancelled()) {
            Log.i(LOG_TAG, "isCancelled");
            return null;
        }
        try {
            OCR.startCaptureLogs();

            Pix p = ReadFile.loadWithPicasso(context, cameraPicUri);//用picasso去加载图片得到bitmap再转换成pix
            if (p == null) {
                if (TextFairyApplication.isRelease()) {
                    Crashlytics.setString("image uri", cameraPicUri.toString());
                    Crashlytics.logException(new IOException("could not load image."));
                }
                return new LoadResult(PixLoadStatus.IMAGE_FORMAT_UNSUPPORTED);
            }

            final long pixPixelCount = p.getWidth() * p.getHeight();//加载成功
            if (pixPixelCount < MIN_PIXEL_COUNT) {
                double scale = Math.sqrt(((double) MIN_PIXEL_COUNT) / pixPixelCount);//缩放的比例
                Pix scaledPix = Scale.scale(p, (float) scale);//图片缩放
                if (scaledPix.getNativePix() == 0) {//缩放失败
                    if (TextFairyApplication.isRelease()) {
                        Crashlytics.log("pix = (" + p.getWidth() + ", " + p.getHeight() + ")");
                        Crashlytics.logException(new IllegalStateException("scaled pix is 0"));
                    }
                } else {
                    p.recycle();
                    p = scaledPix;
                }
            }
            return new LoadResult(p);
        } finally {
            final String msg = OCR.stopCaptureLogs();
            if (TextFairyApplication.isRelease() && !msg.isEmpty()) {
                Crashlytics.log(msg);
            } else {
                Log.e(LOG_TAG, msg);
            }
        }
    }

}

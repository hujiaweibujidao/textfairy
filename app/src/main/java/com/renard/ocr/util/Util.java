/*
 * Copyright (C) 2012,2013 Renard Wellnitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.renard.ocr.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.WriteFile;
import com.renard.ocr.R;
import com.renard.ocr.base.MonitoredActivity;
import com.renard.ocr.documents.viewing.grid.FastBitmapDrawable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 工具类
 */
public class Util {

    public final static String EXTERNAL_APP_DIRECTORY = "thuocr";//一些存放在sd卡上的文件夹目录 textfee
    public final static String CACHE_DIRECTORY = EXTERNAL_APP_DIRECTORY + "/thumbnails";
    public final static String IMAGE_DIRECTORY = EXTERNAL_APP_DIRECTORY + "/pictures";
    public final static String PDF_DIRECTORY = EXTERNAL_APP_DIRECTORY + "/pdfs";
    public final static String OCR_DATA_DIRECTORY = "tessdata";

    private final static String THUMBNAIL_SUFFIX = "png";//缩略图的格式和大小
    public final static int MAX_THUMB_WIDTH = 512;
    public final static int MAX_THUMB_HEIGHT = 512;

    public static FastBitmapDrawable sDefaultDocumentThumbnail;//默认的缩略图对应的drawable
    private static final FastBitmapDrawable NULL_DRAWABLE = new FastBitmapDrawable(null);

    //确定缩略图的大小,返回值是缩略图的宽度,并且传入的int数组的第一个元素保存grid view一行能放几列
    public static int determineThumbnailSize(final Activity context, final int[] outNum) {
        DisplayMetrics metrics = new DisplayMetrics();
        context.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        final int spacing = context.getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        int minSize = context.getResources().getDimensionPixelSize(R.dimen.min_grid_size) + spacing;
        final int h = metrics.heightPixels;
        final int w = metrics.widthPixels;
        final int maxSize = Math.min(h, w) - spacing;
        minSize = Math.min(maxSize, minSize);
        final int screenWidth = (w - spacing);
        final int num = (int) Math.max(2, Math.floor((double) (screenWidth) / minSize));

        int columnWidth = (screenWidth - num * spacing) / num;
        if (columnWidth > (screenWidth - spacing)) {
            columnWidth = screenWidth - spacing;
        }

        if (outNum != null) {
            outNum[0] = num;
        }
        return columnWidth;
    }

    //设置缩略图的大小
    public static void setThumbnailSize(final int w, final int h, final Context c) {
        Drawable drawable = c.getResources().getDrawable(R.drawable.default_thumbnail);
        drawable.setBounds(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(b);
        drawable.draw(canvas);

        sDefaultDocumentThumbnail = new FastBitmapDrawable(b);
        PreferencesUtils.saveThumbnailSize(c, w, h);
    }

    /**
     * 缩略图的LRU缓存
     */
    private static class ThumbnailCache extends LruCache<Integer, FastBitmapDrawable> {
        final private static int cacheSize = 10 * 1024 * 1024;//缓存10MB
        public ThumbnailCache() {
            super(cacheSize);
        }

        @Override
        protected int sizeOf(Integer key, FastBitmapDrawable value) {
            if (value.getBitmap() != null) {
                return value.getBitmap().getRowBytes() * value.getBitmap().getHeight();
            }
            return 0;
        }

        @Override
        protected void entryRemoved(boolean evicted, Integer key, FastBitmapDrawable oldValue, FastBitmapDrawable newValue) {
        }
    }

    private static ThumbnailCache mCache = new ThumbnailCache();

    //根据文档id加载它的缩略图
    public static Bitmap loadDocumentThumbnail(int documentId) {
        Log.i("cache", "loadDocumentThumbnail " + documentId);

        File thumbDir = new File(Environment.getExternalStorageDirectory(), CACHE_DIRECTORY);
        File thumbFile = new File(thumbDir, String.valueOf(documentId) + "." + THUMBNAIL_SUFFIX);
        if (thumbFile.exists()) {
            InputStream stream = null;
            try {
                stream = new FileInputStream(thumbFile);
                BitmapFactory.Options opts = new Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeStream(stream, null, opts);
            } catch (FileNotFoundException e) {
                // Ignore
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException ignore) {
                }
            }
        }
        return null;
    }

    //获取指定文档的缩略图
    public static FastBitmapDrawable getDocumentThumbnail(int documentId) {
        FastBitmapDrawable drawable;
        drawable = mCache.get(documentId);
        if (drawable == null) {
            final Bitmap bitmap = loadDocumentThumbnail(documentId);
            if (bitmap != null) {
                drawable = new FastBitmapDrawable(bitmap);
            } else {
                drawable = NULL_DRAWABLE;
            }
            mCache.put(documentId, drawable);
        }
        return drawable == NULL_DRAWABLE ? sDefaultDocumentThumbnail : drawable;
    }

    //根据时间加载它的缩略图
    public static Bitmap loadDocumentImage(long datetime) {
        Log.i("load document image", "loadDocumentImage " + datetime);

        File pictureDir = new File(Environment.getExternalStorageDirectory(), IMAGE_DIRECTORY);
        File pictureFile = new File(pictureDir, String.valueOf(datetime) + ".png");
        if (pictureFile.exists()) {
            InputStream stream = null;
            try {
                stream = new FileInputStream(pictureFile);
                BitmapFactory.Options opts = new Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeStream(stream, null, opts);
            } catch (FileNotFoundException e) {
                // Ignore
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException ignore) {
                }
            }
        }
        return null;
    }

    /**
     * recycles orgBitmap
     */
    public static Bitmap adjustBitmapSize(int width, int height, Bitmap orgBitmap) {
        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.TRANSPARENT);
        Canvas c = new Canvas(b);

        int border = 2;
        Rect r = new Rect(0, 0, width, height);

        int imageWidth = r.width() - (border * 2);
        int imageHeight = imageWidth * orgBitmap.getHeight() / orgBitmap.getWidth();
        if (imageHeight > r.height() - (border * 2)) {
            imageHeight = r.height() - (border * 2);
            imageWidth = imageHeight * orgBitmap.getWidth() / orgBitmap.getHeight();
        }

        r.left += ((r.width() - imageWidth) / 2) - border;
        r.right = r.left + imageWidth + border + border;
        r.top += ((r.height() - imageHeight) / 2) - border;
        r.bottom = r.top + imageHeight + border + border;

        Paint p = new Paint();
        p.setColor(0xFFC0C0C0);
        c.drawRect(r, p);
        r.left += border;
        r.right -= border;
        r.top += border;
        r.bottom -= border;

        c.drawBitmap(orgBitmap, null, r, null);
        orgBitmap.recycle();
        return b;
    }

    /**
     * reads the specified Bitmap from the SD card and scales it down so that
     * resulting bitmap width nor height exceeds maxSize see also:
     * http://stackoverflow
     * .com/questions/477572/android-strange-out-of-memory-issue/823966#823966
     */
    public static Bitmap decodeFile(String imagePath, int maxWidth, int maxHeight) {
        Options o = decodeImageSize(imagePath);
        // Decode with inSampleSize
        o.inSampleSize = determineScaleFactor(o.outWidth, o.outHeight, maxWidth, maxHeight);
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(imagePath, o);
    }

    private static Options decodeImageSize(String imagePath) {
        Options o = new Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, o);
        return o;
    }

    //确定缩放因子
    public static int determineScaleFactor(int w, int h, int maxWidth, int maxHeight) {
        int scale = 1;
        if (w > maxWidth || h > maxHeight) {
            scale = (int) Math.pow(2, (int) Math.round(Math.log(Math.max(maxWidth, maxHeight) / (double) Math.max(h, w)) / Math.log(0.5)));
        }
        return scale;
    }

    /***
     * 根据uri获取对应的文件路径
     * returns file path for the image at the given uri
     */
    public static String getPathForUri(Context context, Uri uri) {
        final String scheme = uri.getScheme();
        if (scheme == null) {
            return uri.getPath();
        }
        if (scheme.equals("content")) {
            ContentResolver resolver = context.getContentResolver();
            if (resolver == null) {
                return null;
            }
            Cursor cursor = null;
            try {
                cursor = resolver.query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    final int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                    if (idx != -1) {
                        return cursor.getString(idx);
                    }
                }
                return null;
            } catch (SecurityException securityException) {
                return null;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else if (scheme.equals("file")) {
            return uri.getPath();
        }
        return null;

    }

    //保存pix到指定目录，name是last_scan，文件是png类型
    public static File savePixToDir(final Pix pix, final String name, File picDir) throws IOException {
        final String fileName = name + ".png";
        if (!picDir.exists()) {
            if (picDir.mkdirs() || picDir.isDirectory()) {
                createNoMediaFile(picDir);
            } else {
                throw new IOException();
            }
        }
        File image = new File(picDir, fileName);
        image.createNewFile();
        try {
            WriteFile.writeImpliedFormat(pix, image, 85, true);//
        } catch (Exception e) {
            throw new IOException(e);
        }

        return image;
    }

    //保存pix到sd中
    public static File savePixToSD(final Pix pix, final String name) throws IOException {
        File picDir = new File(Environment.getExternalStorageDirectory(), IMAGE_DIRECTORY);
        return savePixToDir(pix, name, picDir);
    }

    //有了这个文件的话就不会被图库识别为图片文件夹
    private static void createNoMediaFile(final File parentDir) {
        File noMedia = new File(parentDir, ".nomedia");
        try {
            noMedia.createNewFile();
        } catch (IOException ignore) {
        }
    }

    //获取训练数据的目录
    public static File getTrainingDataDir(Context appContext) {
        String tessDir = PreferencesUtils.getTessDir(appContext);
        if (tessDir == null) {
            File parent = new File(Environment.getExternalStorageDirectory(), EXTERNAL_APP_DIRECTORY);//textfee
            return new File(parent, Util.OCR_DATA_DIRECTORY);//tessdata
        } else {
            return new File(tessDir, Util.OCR_DATA_DIRECTORY);
        }
    }

    //获取tessdata的目录
    public static String getTessDir(final Context appContext) {
        String tessDir = PreferencesUtils.getTessDir(appContext);
        if (tessDir == null) {
            return new File(Environment.getExternalStorageDirectory(), EXTERNAL_APP_DIRECTORY).getPath() + "/";
        } else {
            return tessDir;
        }
    }

    public static File getPDFDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), PDF_DIRECTORY);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 创建一个缩略图并放入到内存缓存中
     * <p/>
     * creates a thumbnail file and puts it into the in memory cache
     */
    public static void createThumbnail(final Context context, final File image, final int documentId) {
        Bitmap source = Util.decodeFile(image.getPath(), MAX_THUMB_WIDTH, MAX_THUMB_HEIGHT);
        if (source != null && source.getWidth() > 0 && source.getHeight() > 0) {
            // Bitmap thumb = Util.extractMiniThumb(source, width, height);
            // final int color =
            // context.getResources().getColor(R.color.document_element_background);
            int thumbnailHeight = PreferencesUtils.getThumbnailHeight(context);
            int thumbnailWidth = PreferencesUtils.getThumbnailWidth(context);
            Bitmap thumb = Util.adjustBitmapSize(thumbnailWidth, thumbnailHeight, source);

            if (thumb != null) {
                FastBitmapDrawable drawable = new FastBitmapDrawable(thumb);
                mCache.put(documentId, drawable);
                File thumbDir = new File(Environment.getExternalStorageDirectory(), CACHE_DIRECTORY);
                if (!thumbDir.exists()) {
                    thumbDir.mkdirs();
                    createNoMediaFile(thumbDir);
                }
                File thumbFile = new File(thumbDir, String.valueOf(documentId) + "." + THUMBNAIL_SUFFIX);
                FileOutputStream out;
                try {
                    out = new FileOutputStream(thumbFile);
                    thumb.compress(Bitmap.CompressFormat.PNG, 85, out);
                } catch (FileNotFoundException ignore) {
                }
            }
        }
    }

    /*
     * Compute the sample size as a function of minSideLength and
     * maxNumOfPixels. minSideLength is used to specify that minimal width or
     * height of a bitmap. maxNumOfPixels is used to specify the maximal size in
     * pixels that are tolerable in terms of memory usage.
     *
     * The function returns a sample size based on the constraints. Both size
     * and minSideLength can be passed in as IImage.UNCONSTRAINED, which
     * indicates no care of the corresponding constraint. The functions prefers
     * returning a sample size that generates a smaller bitmap, unless
     * minSideLength = IImage.UNCONSTRAINED.
     */
    private static Bitmap transform(Matrix scaler, Bitmap source, int targetWidth, int targetHeight, boolean scaleUp) {
        int deltaX = source.getWidth() - targetWidth;
        int deltaY = source.getHeight() - targetHeight;
        if (!scaleUp && (deltaX < 0 || deltaY < 0)) {
            /*
             * In this case the bitmap is smaller, at least in one dimension,
			 * than the target. Transform it by placing as much of the image as
			 * possible into the target and leaving the top/bottom or left/right
			 * (or both) black.
			 */
            Bitmap b2 = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b2);

            int deltaXHalf = Math.max(0, deltaX / 2);
            int deltaYHalf = Math.max(0, deltaY / 2);
            Rect src = new Rect(deltaXHalf, deltaYHalf, deltaXHalf + Math.min(targetWidth, source.getWidth()), deltaYHalf + Math.min(targetHeight, source.getHeight()));
            int dstX = (targetWidth - src.width()) / 2;
            int dstY = (targetHeight - src.height()) / 2;
            Rect dst = new Rect(dstX, dstY, targetWidth - dstX, targetHeight - dstY);
            c.drawBitmap(source, src, dst, null);
            return b2;
        }
        float bitmapWidthF = source.getWidth();
        float bitmapHeightF = source.getHeight();

        float bitmapAspect = bitmapWidthF / bitmapHeightF;
        float viewAspect = (float) targetWidth / targetHeight;

        if (bitmapAspect > viewAspect) {
            float scale = targetHeight / bitmapHeightF;
            if (scale < .9F || scale > 1F) {
                scaler.setScale(scale, scale);
            } else {
                scaler = null;
            }
        } else {
            float scale = targetWidth / bitmapWidthF;
            if (scale < .9F || scale > 1F) {
                scaler.setScale(scale, scale);
            } else {
                scaler = null;
            }
        }

        Bitmap b1;
        if (scaler != null) {
            // this is used for minithumb and crop, so we want to filter here.
            b1 = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), scaler, true);
        } else {
            b1 = source;
        }

        int dx1 = Math.max(0, b1.getWidth() - targetWidth);
        int dy1 = Math.max(0, b1.getHeight() - targetHeight);

        Bitmap b2 = Bitmap.createBitmap(b1, dx1 / 2, dy1 / 2, targetWidth, targetHeight);

        if (b1 != source) {
            b1.recycle();
        }

        return b2;
    }

    //后台任务的封装类，它既在后台执行某个任务，又能监听某个Activity的生命周期，同时控制着前台显示的进度对话框
    private static class BackgroundJob extends MonitoredActivity.LifeCycleAdapter implements Runnable {

        @Nullable
        private final ProgressDialog mDialog;//后台任务执行的时候显示的进度条对话框
        private final Runnable mJob;//后台执行的任务
        private final MonitoredActivity mActivity;//当前的activity
        private final Handler mHandler;//传入进来的handler用于发送runnable

        public BackgroundJob(MonitoredActivity activity, Runnable job, @Nullable ProgressDialog dialog, Handler handler) {
            mActivity = activity;
            mDialog = dialog;
            mJob = job;
            mActivity.addLifeCycleListener(this);
            mHandler = handler;
        }

        public void run() {
            try {
                mJob.run();//运行runnable
            } finally {
                mHandler.post(mCleanupRunner);//任务结束之后发送消息
            }
        }

        private final Runnable mCleanupRunner = new Runnable() {
            public void run() {
                mActivity.removeLifeCycleListener(BackgroundJob.this);
                if (mDialog != null && mDialog.getWindow() != null)
                    mDialog.dismiss();
            }
        };

        @Override
        public void onActivityDestroyed(MonitoredActivity activity) {
            // We get here only when the onDestroyed being called before the mCleanupRunner.
            // So, run it now and remove it from the queue
            mCleanupRunner.run();
            mHandler.removeCallbacks(mCleanupRunner);
        }

        @Override
        public void onActivityStopped(MonitoredActivity activity) {//如果Activity都销毁了那就把dialog也消失掉
            if (mDialog != null) {
                mDialog.hide();
            }
        }

        @Override
        public void onActivityStarted(MonitoredActivity activity) {
            if (mDialog != null) {
                mDialog.show();
            }
        }
    }

    //开启一个后台任务，前台显示进度对话框
    public static void startBackgroundJob(MonitoredActivity activity, String title, String message, Runnable job, Handler handler) {
        if (!activity.isFinishing()) {
            ProgressDialog dialog = ProgressDialog.show(activity, title, message, true, false);
            new Thread(new BackgroundJob(activity, job, dialog, handler)).start();
        } else {
            new Thread(new BackgroundJob(activity, job, null, handler)).start();
        }
    }

    /**
     * 获取剩余存储空间大小
     *
     * @return the free space on sdcard in bytes
     */
    public static long GetFreeSpaceB() {
        try {
            String storageDirectory = Environment.getExternalStorageDirectory().toString();
            StatFs stat = new StatFs(storageDirectory);
            long availableBlocks = stat.getAvailableBlocks();
            return availableBlocks * stat.getBlockSize();
        } catch (Exception ex) {
            return -1;
        }
    }

    /**
     * 复制数据
     */
    public static long copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024 * 4];
        long count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

}

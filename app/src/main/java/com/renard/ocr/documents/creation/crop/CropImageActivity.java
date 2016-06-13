/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2012,2013,2014,2015 Renard Wellnitz
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

package com.renard.ocr.documents.creation.crop;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.google.common.base.Optional;
import com.googlecode.leptonica.android.Box;
import com.googlecode.leptonica.android.Clip;
import com.googlecode.leptonica.android.Convert;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.Projective;
import com.googlecode.leptonica.android.Rotate;
import com.googlecode.tesseract.android.OCR;
import com.renard.ocr.R;
import com.renard.ocr.base.MonitoredActivity;
import com.renard.ocr.documents.creation.NewDocumentActivity;
import com.renard.ocr.documents.viewing.grid.DocumentGridActivity;
import com.renard.ocr.util.Util;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.greenrobot.event.EventBus;

/**
 * 图片裁剪的界面
 * The activity can crop specific region of interest from an image.
 *
 * update
 * 1.去除了图片模糊的情况下提示对话框
 * 2.去除了第一次进入的时候的提示对话框
 *
 */
public class CropImageActivity extends MonitoredActivity {

    public static final String SCREEN_NAME = "Crop Image";
    private final Handler mHandler = new Handler();

    @Bind(R.id.toolbar)
    protected Toolbar mToolbar;
    @Bind(R.id.cropImageView)
    protected CropImageView mImageView;
    @Bind(R.id.crop_layout)
    protected ViewSwitcher mViewSwitcher;
    @Bind(R.id.item_rotate_left)
    protected ImageView mRotateLeft;
    @Bind(R.id.item_rotate_right)
    protected ImageView mRotateRight;
    @Bind(R.id.item_save)
    protected ImageView mSave;

    boolean mSaving;
    private Pix mPix;
    private int mRotation = 0;
    private CropHighlightView mCrop;
    private Optional<CropData> mCropData = Optional.absent();
    private Optional<PreparePixForCropTask> mPrepareTask = Optional.absent();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        EventBus.getDefault().register(this);
        getWindow().setFormat(PixelFormat.RGBA_8888);//toread
        setContentView(R.layout.activity_cropimage);
        ButterKnife.bind(this);

        initToolbar();
        setToolbarMessage(R.string.crop_title);
        initNavigationAsUp();

        startCropping();
    }

    //toolbar左侧变成返回键
    private void initNavigationAsUp() {
        final Drawable upArrow = getResources().getDrawable(R.drawable.ic_arrow_back_white);
        getSupportActionBar().setHomeAsUpIndicator(upArrow);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @OnClick(R.id.item_rotate_left)
    public void onRotateLeft() {
        onRotateClicked(-1);
    }

    @OnClick(R.id.item_rotate_right)
    public void onRotateRight() {
        onRotateClicked(1);
    }

    //旋转图片
    private void onRotateClicked(int delta) {
        if (mCropData.isPresent()) {
            if (delta < 0) {
                delta = -delta * 3;
            }
            mRotation += delta;
            mRotation = mRotation % 4;
            mImageView.setImageBitmapResetBase(mCropData.get().getBitmap(), false, mRotation * 90);//角度在这里乘以90
            showDefaultCroppingRectangle(mCropData.get().getBitmap());
        }
    }

    //在界面布局完成之后，就可以准备加载图片信息以供裁剪，这里启动了PreparePixForCropTask任务
    private void startCropping() {
        mImageView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                Bundle extras = getIntent().getExtras();
                final long nativePix = extras.getLong(DocumentGridActivity.EXTRA_NATIVE_PIX);
                final float margin = getResources().getDimension(R.dimen.crop_margin);
                final int width = (int) (mViewSwitcher.getWidth() - 2 * margin);
                final int height = (int) (mViewSwitcher.getHeight() - 2 * margin);
                mPix = new Pix(nativePix);
                mRotation = 0;
                mPrepareTask = Optional.of(new PreparePixForCropTask(mPix, width, height));
                mPrepareTask.get().execute();
                mImageView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }

        });
    }

    //在这里接收需要裁剪的图片准备阶段返回的结果 （由PreparePixForCropTask发送结果返回CropData）
    @SuppressWarnings("unused")
    public void onEventMainThread(final CropData cropData) {
        if (cropData.getBitmap() == null) {//should not happen. Scaling of the original document failed some how. Maybe out of memory?
            mAnalytics.sendCropError();
            Toast.makeText(this, R.string.could_not_load_image, Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        mAnalytics.sendBlurResult(cropData.getBlurriness());

        mCropData = Optional.of(cropData);//
        adjustOptionsMenu();
        mViewSwitcher.setDisplayedChild(1);//显示图片，不显示进度条了
        mImageView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                mImageView.setImageBitmapResetBase(cropData.getBitmap(), true, mRotation * 90);
                handleBlurResult(cropData);//
                mImageView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }

        });
    }

    //不同模糊状态下的图片的处理 -> hujiawei 这里进行了重要修改，删除提示信息
    private void handleBlurResult(CropData cropData) {
        mAnalytics.sendScreenView(SCREEN_NAME);
        showDefaultCroppingRectangle(cropData.getBitmap());//没啥问题就显示默认的裁剪区域
        /*switch (cropData.getBlurriness().getBlurriness()) {
            case NOT_BLURRED:
                mAnalytics.sendScreenView(SCREEN_NAME);
                showDefaultCroppingRectangle(cropData.getBitmap());//没啥问题就显示默认的裁剪区域
                break;
            case MEDIUM_BLUR://中度或者重度模糊的话就提示用户
            case STRONG_BLUR:
                setTitle(R.string.image_is_blurred);//显示图片过于模糊的情况
                BlurWarningDialog dialog = BlurWarningDialog.newInstance((float) cropData.getBlurriness().getBlurValue());
                final FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                fragmentTransaction.add(dialog, BlurWarningDialog.TAG).commitAllowingStateLoss();
                break;
        }*/
    }

    //调整操作按钮的显示与否
    private void adjustOptionsMenu() {
        if (mCropData.isPresent()) {//图片数据在的时候就可以旋转
            mRotateLeft.setVisibility(View.VISIBLE);
            mRotateRight.setVisibility(View.VISIBLE);
            mSave.setVisibility(View.VISIBLE);
        } else {
            mRotateLeft.setVisibility(View.GONE);
            mRotateRight.setVisibility(View.GONE);
            mSave.setVisibility(View.GONE);
        }
    }

    @OnClick(R.id.item_save)
    void onSaveClicked() {//点击保存（下一步）
        if (!mCropData.isPresent() || mSaving || (mCrop == null)) {
            return;
        }
        mSaving = true;

        //在后台开启裁剪图片的任务,前端显示进度条旋转
        Util.startBackgroundJob(this, null, getText(R.string.cropping_image).toString(), new Runnable() {
            public void run() {
                try {
                    float scale = 1f / mCropData.get().getScaleResult().getScaleFactor();//
                    Matrix scaleMatrix = new Matrix();
                    scaleMatrix.setScale(scale, scale);

                    final float[] trapezoid = mCrop.getTrapezoid();//曲边梯形
                    final RectF perspectiveCorrectedBoundingRect = new RectF(mCrop.getPerspectiveCorrectedBoundingRect());
                    scaleMatrix.mapRect(perspectiveCorrectedBoundingRect);
                    Box bb = new Box((int) perspectiveCorrectedBoundingRect.left, (int) perspectiveCorrectedBoundingRect.top, (int) perspectiveCorrectedBoundingRect.width(), (int) perspectiveCorrectedBoundingRect.height());

                    Pix pix8 = Convert.convertTo8(mPix);//转为灰度图
                    mPix.recycle();

                    Pix croppedPix = Clip.clipRectangle2(pix8, bb);//裁剪出pix8中bb部分的图片内容
                    if (croppedPix == null) {
                        //throw new IllegalStateException();
                    }
                    pix8.recycle();

                    scaleMatrix.postTranslate(-bb.getX(), -bb.getY());
                    scaleMatrix.mapPoints(trapezoid);

                    //8个数字，分别对应4个点的坐标位置
                    final float[] dest = new float[]{0, 0, bb.getWidth(), 0, bb.getWidth(), bb.getHeight(), 0, bb.getHeight()};
                    Pix bilinear = Projective.projectiveTransform(croppedPix, dest, trapezoid);//计算投影
                    if (bilinear == null) {
                        bilinear = croppedPix;
                    } else {
                        croppedPix.recycle();
                    }

                    if (mRotation != 0 && mRotation != 4) {
                        Pix rotatedPix = Rotate.rotateOrth(bilinear, mRotation);//旋转图片
                        bilinear.recycle();
                        bilinear = rotatedPix;
                    }
                    if (bilinear == null) {
                        //throw new IllegalStateException();
                    }

                    OCR.savePixToCacheDir(CropImageActivity.this, bilinear.copy());//将裁剪之后的数据保存起来
                    //todo 这里需要修改，bilinear是最终裁剪得到的结果，需要配置到ImageEntry中！

                    //图片裁剪完成之后的操作！
                    Intent result = new Intent();
                    result.putExtra(NewDocumentActivity.EXTRA_NATIVE_PIX, bilinear.getNativePix());//裁剪之后的图片Pix放入到extra中
                    setResult(RESULT_OK, result);//进入代码在NewDocumentActivity 470行附近
                } catch (IllegalStateException e) {
                    setResult(RESULT_CANCELED);
                } finally {
                    finish();
                }
            }
        }, mHandler);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(RESULT_CANCELED);
        mPix.recycle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        unbindDrawables(findViewById(android.R.id.content));//toread 递归unbind，根是android.R.id.content

        mImageView.clear();
        if (mPrepareTask.isPresent()) {
            mPrepareTask.get().cancel(true);//取消任务
            mPrepareTask = Optional.absent();
        }
        if (mCropData.isPresent()) {
            mCropData.get().recylce();//回收数据
            mCropData = Optional.absent();
        }
    }

    private void unbindDrawables(View view) {
        if (view.getBackground() != null) {
            view.getBackground().setCallback(null);
        }
        if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
        }
    }

    private void zoomToBlurredRegion(CropData data) {
        float width = data.getBlurriness().getPixBlur().getWidth();
        float height = data.getBlurriness().getPixBlur().getHeight();
        float widthScale = width / data.getBitmap().getWidth();
        float heightScale = height / data.getBitmap().getHeight();
        final Point c = data.getBlurriness().getMostBlurredRegion().getCenter();
        c.set((int) (c.x / widthScale), (int) (c.y / heightScale));
        float[] pts = {c.x, c.y};
        mImageView.getImageMatrix().mapPoints(pts);
        /*
        int w = (Math.min(mBitmap.getWidth(), mBitmap.getHeight())) / 25;
        Rect focusArea = new Rect((int) (Math.max(c.x-w,0)*widthScale), (int) (Math.max(c.y-w,0)*heightScale), (int) (Math.min(c.x+w,mBitmap.getWidth())*widthScale), (int) (Math.min(c.y+w,mBitmap.getHeight())*heightScale));

        //final int progressColor = getResources().getColor(R.color.progress_color);
        //final int edgeWidth = getResources().getDimensionPixelSize(R.dimen.crop_edge_width);
        Clip.clipRectangle2();

        BlurHighLightView hv = new BlurHighLightView(focusArea,progressColor,edgeWidth, mImageView.getImageMatrix());
        mImageView.add(hv);
        */
        mImageView.setMaxZoom(3);
        mImageView.zoomTo(3, pts[0], pts[1], 2000);
    }

    //显示默认的裁剪矩形
    private void showDefaultCroppingRectangle(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        Rect imageRect = new Rect(0, 0, width, height);

        // make the default size about 4/5 of the width or height
        int cropWidth = Math.min(width, height) * 4 / 5;//裁剪矩形默认是图片矩形的4/5
        int x = (width - cropWidth) / 2;//这里除以2是因为左右，下面除以2是因为上下
        int y = (height - cropWidth) / 2;

        RectF cropRect = new RectF(x, y, x + cropWidth, y + cropWidth);
        CropHighlightView hv = new CropHighlightView(mImageView, imageRect, cropRect);

        mImageView.resetMaxZoom();
        mImageView.add(hv);//创建CropHighlightView添加到CropImageView上
        mCrop = hv;
        mCrop.setFocus(true);
        mImageView.invalidate();
    }

}


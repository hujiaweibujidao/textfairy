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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Transformation;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.nineoldandroids.animation.AnimatorSet;
import com.renard.ocr.R;

/**
 * custom view for children of the document grid view TODO remove tight
 * coupling with DocumentGridActivity
 *
 * 自定义的支持check的grid单元格
 *
 * @author renard
 */
public class CheckableGridElement extends RelativeLayout implements Checkable {

    private boolean mIsChecked = false;
    private ImageView mThumbnailImageView;
    private OnCheckedChangeListener mListener;

    private final Transformation mSelectionTransformation;
    private final Transformation mAlphaTransformation;
    private AnimatorSet mAnimatorSet;

    private float mTargetAlpha = 1;
    private float mCurrentAlpha = 1;

    private float mTargetScale = 1;
    private float mCurrentScale = 1;

    private static final float SELECTED_ALPHA = 1.0f;
    private static final float NOT_SELECTED_ALPHA = .35f;//.35f

    private static final float SELECTED_SCALE = 1.0f;
    private static final float NOT_SELECTED_SCALE = .95f;//0.95f

    private final static int ANIMATION_DURATION = 200;
    private final static long TAP_ANIMATION_DURATION = ViewConfiguration.getLongPressTimeout();

    @SuppressWarnings("unused")
    private final String LOG_TAG = CheckableGridElement.class.getSimpleName();

    public interface OnCheckedChangeListener {//选中状态发生变化的监听器
        void onCheckedChanged(View documentView, boolean isChecked);
    }

    public CheckableGridElement(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.setWillNotDraw(false);
        if (!isInEditMode()) {

            //toread!!! 这行代码作用特别大!!!
            //setStaticTransformationsEnabled(true);//如果为true,父容器view会调用子view的getChildStaticTransformation方法来启动变换

            mSelectionTransformation = new Transformation();
            mAlphaTransformation = new Transformation();
            mSelectionTransformation.setTransformationType(Transformation.TYPE_MATRIX);
            mAlphaTransformation.setTransformationType(Transformation.TYPE_ALPHA);
        } else {
            mAlphaTransformation = null;
            mSelectionTransformation = null;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mThumbnailImageView = (ImageView) findViewById(R.id.thumb);
        this.setFocusable(false);
    }

    public void setImage(Drawable d) {
        mThumbnailImageView.setImageDrawable(d);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        mListener = listener;
    }

    /**
     * sets checked state without starting an animation
     *
     * 动画已经屏蔽掉了,所以始终都不会有动画,即使是调用setChecked方法
     */
    public void setCheckedNoAnimate(final boolean checked) {
        mIsChecked = checked;
        // stop any running animation
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            mAnimatorSet.removeAllListeners();
            mAnimatorSet.cancel();
        }
        setCurrentAlpha(getDesiredAlpha());
        setCurrentScale(getDesiredScale());
        if (mListener != null) {
            mListener.onCheckedChanged(this, mIsChecked);
        }
    }

    /**
     * Checkable接口的三个方法
     *
     * sets checked state and starts animation if necessary
     */
    @Override
    public void setChecked(boolean checked) {
        mIsChecked = checked;
        initAnimationProperties(AnimationType.CHECK);
        if (mListener != null) {
            mListener.onCheckedChanged(this, mIsChecked);
        }
    }

    @Override
    public boolean isChecked() {
        return mIsChecked;
    }

    @Override
    public void toggle() {
        mIsChecked = !mIsChecked;
        initAnimationProperties(AnimationType.CHECK);
        if (mListener != null) {
            mListener.onCheckedChanged(this, mIsChecked);
        }
    }

    //得到目标的scale,如果当前在选择模式,item如果选中了那么就是SELECTED_SCALE,如果没有选中那么就是NOT_SELECTED_SCALE
    private float getDesiredScale() {
        if (mIsChecked && DocumentGridActivity.isInSelectionMode()) {
            return SELECTED_SCALE;
        } else if (!mIsChecked && DocumentGridActivity.isInSelectionMode()) {
            return NOT_SELECTED_SCALE;
        } else {
            return SELECTED_SCALE;
        }
    }

    private float getDesiredAlpha() {
        if (mIsChecked && DocumentGridActivity.isInSelectionMode()) {
            return SELECTED_ALPHA;
        } else if (!mIsChecked && DocumentGridActivity.isInSelectionMode()) {
            return NOT_SELECTED_ALPHA;
        } else {
            return SELECTED_ALPHA;
        }
    }

    private enum AnimationType {
        TAP_UP, TAP_DOWN, CHECK
    }

    //初始化动画的属性
    private void initAnimationProperties(final AnimationType type) {
        final long duration;
        switch (type) {

            case CHECK: {//选中和不选中的动画
                duration = ANIMATION_DURATION;
                mTargetAlpha = getDesiredAlpha();
                mTargetScale = getDesiredScale();
                break;
            }
            case TAP_DOWN: {
                duration = TAP_ANIMATION_DURATION;
                mTargetScale = NOT_SELECTED_SCALE;
                break;
            }
            case TAP_UP: {
                duration = ANIMATION_DURATION;
                mTargetScale = SELECTED_SCALE;
                break;
            }
            default:
                duration = ANIMATION_DURATION;
        }

        startAnimation(duration);
    }

    private void setCurrentScale(final float value) {
        mCurrentScale = value;
        getChildAt(0).invalidate();
    }

    private void setCurrentAlpha(final float value) {
        mCurrentAlpha = value;
    }

    //启动动画 -> hujiawei 屏蔽动画!!!
    private void startAnimation(final long anmationDuration) {
        //final long duration = (long) (anmationDuration * (Math.abs(mCurrentScale - mTargetScale) / (SELECTED_SCALE - NOT_SELECTED_SCALE)));
        //ObjectAnimator scale = ObjectAnimator.ofFloat(this, "currentScale", mCurrentScale, mTargetScale);
//        ObjectAnimator alpha = ObjectAnimator.ofFloat(this, "currentAlpha", mCurrentAlpha, mTargetAlpha);
//        mAnimatorSet = new AnimatorSet();
//        mAnimatorSet.setDuration(1000);//duration
        //mAnimatorSet.setInterpolator(new DecelerateInterpolator(1.2f));
        //mAnimatorSet.playTogether(scale, alpha);
//        mAnimatorSet.play(alpha);
//        mAnimatorSet.start();
    }

    //向下滑动的时候启动该动画
    public void startTouchDownAnimation() {
        if (!DocumentGridActivity.isInSelectionMode()) {
            initAnimationProperties(AnimationType.TAP_DOWN);
        }
    }

    //向上滑动的时候启动该动画
    public void startTouchUpAnimation() {
        if (!DocumentGridActivity.isInSelectionMode()) {
            initAnimationProperties(AnimationType.TAP_UP);
        }
    }

    //配合setStaticTransformationsEnabled(true);一起使用 <- hujiawei 那行代码注释掉了,那么这个方法就不会被回调了
    //这两个方法的调用会导致grid在滑动的时候出现缩略图消失的情况
    @Override
    protected boolean getChildStaticTransformation(View child, Transformation t) {
        boolean hasChanged = false;
        if (mTargetAlpha != mCurrentAlpha || mTargetAlpha != SELECTED_ALPHA) {
            mAlphaTransformation.setAlpha(mCurrentAlpha);
            t.compose(mAlphaTransformation);
            hasChanged = true;
        }
        if (mTargetScale != mCurrentScale || mTargetScale != SELECTED_SCALE) {
            mSelectionTransformation.getMatrix().reset();
            final float px = child.getLeft() + (child.getWidth()) / 2;
            final float py = child.getTop() + (child.getHeight()) / 2;
            mSelectionTransformation.getMatrix().postScale(mCurrentScale, mCurrentScale, px, py);
            t.compose(mSelectionTransformation);
            hasChanged = true;
        }
        if (hasChanged) {
            child.invalidate();
            this.invalidate();
        }
        return hasChanged;
    }

}

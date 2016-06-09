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

package com.renard.ocr.install;

import android.Manifest;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.renard.ocr.base.MonitoredActivity;
import com.renard.ocr.base.PermissionGrantedEvent;
import com.renard.ocr.R;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;

/**
 * 安装语言包Activity
 * wrapper activity for the AssetsManager
 * <p>
 * update
 * 1.简化界面布局
 * 2.删除多余的功能
 * 3.简化安装过程的动画
 */
public class InstallActivity extends MonitoredActivity implements InstallTaskFragment.TaskCallbacks {

    private static final String TAG_TASK_FRAGMENT = "task_fragment";
    @SuppressWarnings("unused")
    private static final String LOG_TAG = InstallActivity.class.getSimpleName();

    @Bind(R.id.button_start_app)
    protected TextView mButtonStartApp;
    @Bind(R.id.imageView_fairy)
    protected ImageView mImageViewFairy;
    @Bind(R.id.fairy_text)
    protected TextView mFairyText;

//    @Bind(R.id.content_view)
//    protected View mContentView;
//    @Bind(R.id.fairy_container)
//    protected View mFairyContainer;
//    @Bind(R.id.fairy_text_bubble)
//    protected View mFairySpeechBubble;
//    @Bind(R.id.tip1)
//    protected View mTip1;
//    @Bind(R.id.tip2)
//    protected View mTip2;
//    @Bind(R.id.tip3)
//    protected View mTip3;
//    @Bind(R.id.promo)
//    protected View mYoutube;

    private InstallTaskFragment mInstallTaskFragment;
    private AnimationDrawable mFairyAnimation;

    @Override
    public String getScreenName() {
        return "Install Activity";
    }

    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);

        setContentView(R.layout.activity_install);
        ButterKnife.bind(this);

        mFairyAnimation = (AnimationDrawable) mImageViewFairy.getDrawable();
        FragmentManager fm = getSupportFragmentManager();
        mInstallTaskFragment = (InstallTaskFragment) fm.findFragmentByTag(TAG_TASK_FRAGMENT);

        // If the Fragment is non-null, then it is currently being retained across a configuration change.
        if (mInstallTaskFragment == null) {
            Log.i(LOG_TAG, "ensuring permission for: " + this);
            ensurePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, R.string.permission_explanation_install);
        } else {
            InstallResult result = mInstallTaskFragment.getInstallResult();
            if (result != null) {
                markAsDone(result);
            } else {
                startInstallAnimation();
            }
        }

    }

    @SuppressWarnings("unused")
    public void onEventMainThread(final PermissionGrantedEvent event) {
        Log.i(LOG_TAG, "PermissionGrantedEvent : " + this);
        EventBus.getDefault().unregister(this);//对应前面的ensurePermission
        mInstallTaskFragment = new InstallTaskFragment();
        final FragmentManager supportFragmentManager = getSupportFragmentManager();
        supportFragmentManager.beginTransaction().add(mInstallTaskFragment, TAG_TASK_FRAGMENT).commitAllowingStateLoss();
    }


//    @OnClick(R.id.promo)
//    public void clickOnYoutubeLink() {
//        mAnalytics.sendClickYoutube();
//        final String link = getString(R.string.youtube_promo_link);
//        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
//        startActivity(intent);
//    }


    @Override
    protected int getHintDialogId() {
        return 0;
    }

    private void startInstallAnimation() {

//        mTip1.setAlpha(0);
//        mTip2.setAlpha(0);
//        mTip3.setAlpha(0);
//        mYoutube.setAlpha(0);
//
//        ObjectAnimator anim1 = ObjectAnimator.ofFloat(mTip1, "alpha", 1);
//        ObjectAnimator anim2 = ObjectAnimator.ofFloat(mTip2, "alpha", 1);
//        ObjectAnimator anim3 = ObjectAnimator.ofFloat(mTip3, "alpha", 1);
//        ObjectAnimator anim4 = ObjectAnimator.ofFloat(mYoutube, "alpha", 1);
//        AnimatorSet set = new AnimatorSet();
//        set.setStartDelay(300);
//        set.setDuration(600);
//        set.playTogether(anim1, anim2, anim3, anim4);
//        set.start();

        mFairyAnimation.start();
        mFairyText.setText(R.string.installing);
    }

    @Override
    protected synchronized void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    //标记完成
    private void markAsDone(InstallResult result) {
        fadeInStartButton();
        mFairyAnimation.stop();
        switch (result.getResult()) {
            case OK:
                final View.OnClickListener onClickListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setResult(RESULT_OK);
                        finish();
                    }
                };
                mButtonStartApp.setOnClickListener(onClickListener);
                //mFairyContainer.setOnClickListener(onClickListener);
                //mFairySpeechBubble.setVisibility(View.VISIBLE);
                mFairyText.setText(R.string.install_ok);
                break;
            case NOT_ENOUGH_DISK_SPACE:
                String errorMsg = getString(R.string.install_error_disk_space);
                final long diff = result.getNeededSpace() - result.getFreeSpace();
                errorMsg = String.format(errorMsg, (diff / (1024 * 1024)));
                mFairyText.setText(errorMsg);
                mButtonStartApp.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                });
                break;
            case UNSPECIFIED_ERROR:
                errorMsg = getString(R.string.install_error);
                mFairyText.setText(errorMsg);
                mButtonStartApp.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                });
                break;
        }
    }

    private void fadeInStartButton() {
        mButtonStartApp.setVisibility(View.VISIBLE);
        mButtonStartApp.setAlpha(0);
        mButtonStartApp.animate().alpha(1);
    }

    @Override
    public void onPreExecute() {
        startInstallAnimation();
    }

    @Override
    public void onProgressUpdate(int progress) {//去掉这里的复杂的fairy移动动画
//        final float translateX = getTranslateX(progress);
//        translateTextfairy((int) translateX);
    }

//    private void translateTextfairy(int translateX) {
//        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mFairyContainer.getLayoutParams();
//        lp.leftMargin = translateX;
//        mFairyContainer.setLayoutParams(lp);
//    }
//
//    private float getTranslateX(float progress) {
//        final int fairyEndX = mContentView.getWidth() / 2;
//        final int fairyStartX = mImageViewFairy.getWidth() / 2;
//        final int maxTravelDistance = Math.min(fairyEndX - fairyStartX, mContentView.getWidth() - mFairyContainer.getWidth());
//        return maxTravelDistance * (progress / 100);
//    }

    @Override
    public void onCancelled() {
        mFairyAnimation.stop();//hujiawei
    }

    @Override
    public void onPostExecute(InstallResult result) {
        markAsDone(result);
    }
}

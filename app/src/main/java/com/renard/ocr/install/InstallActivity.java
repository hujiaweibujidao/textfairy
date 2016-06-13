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

    private InstallTaskFragment mInstallTaskFragment;
    private AnimationDrawable mFairyAnimation;

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

    private void startInstallAnimation() {
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
    }

    @Override
    public void onCancelled() {
        mFairyAnimation.stop();//hujiawei
    }

    @Override
    public void onPostExecute(InstallResult result) {
        markAsDone(result);
    }
}

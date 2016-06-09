/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.renard.ocr.base;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.renard.ocr.R;
import com.renard.ocr.analytics.Analytics;
import com.renard.ocr.documents.creation.crop.BaseActivityInterface;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;

/**
 * 受监控的Activity，这个类基本上是该项目中所有的Activity的基类
 * <p>
 * update
 * 1.去掉appicon上的点击事件
 */
public abstract class MonitoredActivity extends AppCompatActivity implements BaseActivityInterface {

    private static final String LOG_TAG = MonitoredActivity.class.getSimpleName();

    static final int MY_PERMISSIONS_REQUEST = 232;

    private final Handler mHandler = new Handler();
    private final ArrayList<LifeCycleListener> mListeners = new ArrayList<LifeCycleListener>();

    private int mDialogId = -1;
    private TextView mToolbarMessage;
    private AlertDialog mPermissionDialog;

    protected Analytics mAnalytics;

    //Activity生命周期监听器
    public interface LifeCycleListener {
        void onActivityCreated(MonitoredActivity activity);

        void onActivityDestroyed(MonitoredActivity activity);

        void onActivityPaused(MonitoredActivity activity);

        void onActivityResumed(MonitoredActivity activity);

        void onActivityStarted(MonitoredActivity activity);

        void onActivityStopped(MonitoredActivity activity);
    }

    //Activity生命周期监听器的适配器
    public static class LifeCycleAdapter implements LifeCycleListener {
        public void onActivityCreated(MonitoredActivity activity) {
        }

        public void onActivityDestroyed(MonitoredActivity activity) {
        }

        public void onActivityPaused(MonitoredActivity activity) {
        }

        public void onActivityResumed(MonitoredActivity activity) {
        }

        public void onActivityStarted(MonitoredActivity activity) {
        }

        public void onActivityStopped(MonitoredActivity activity) {
        }
    }

    public synchronized void addLifeCycleListener(LifeCycleListener listener) {
        if (mListeners.contains(listener))
            return;
        mListeners.add(listener);
    }

    public synchronized void removeLifeCycleListener(LifeCycleListener listener) {
        mListeners.remove(listener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityPaused(this);
        }
    }

    public abstract String getScreenName();

    @Override
    protected void onResume() {
        super.onResume();
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityResumed(this);
        }
        final String screenName = getScreenName();
        if (!TextUtils.isEmpty(screenName)) {
            mAnalytics.sendScreenView(screenName);
        }
    }

    @Override
    protected synchronized void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityCreated(this);
        }
        TextFairyApplication application = (TextFairyApplication) getApplication();
        mAnalytics = application.getAnalytics();

        Log.i(LOG_TAG, "onCreate: " + this.getClass());
    }

    public Analytics getAnaLytics() {
        return mAnalytics;
    }

    protected void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbarMessage = (TextView) toolbar.findViewById(R.id.toolbar_text);
        setToolbarMessage(R.string.app_name);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }

    public void setToolbarMessage(@StringRes int stringId) {
        mToolbarMessage.setVisibility(View.VISIBLE);
        mToolbarMessage.setText(stringId);
    }

    public void setToolbarMessage(String message) {
        mToolbarMessage.setText(message);
    }

    protected abstract int getHintDialogId();

    @Override
    protected synchronized void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        if (mPermissionDialog != null) {
            mPermissionDialog.cancel();
        }
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityDestroyed(this);
        }
    }

    @Override
    protected synchronized void onStart() {
        super.onStart();
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityStarted(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityStopped(this);
        }
        Log.i(LOG_TAG, "onStop: " + this.getClass());
    }

    @Override
    public void setDialogId(int dialogId) {
        mDialogId = dialogId;
    }

    public void ensurePermission(String permission, @StringRes int explanation) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {//解释需要权限
                explainPermission(permission, explanation);
            } else {//申请获取权限
                ActivityCompat.requestPermissions(this, new String[]{permission}, MY_PERMISSIONS_REQUEST);
            }
        } else {
            EventBus.getDefault().post(new PermissionGrantedEvent(permission));//已经拿到了权限，发送通知，回调onEventMainThread(final PermissionGrantedEvent event)方法
        }
    }

    private void explainPermission(final String permission, int explanation) {
        //PermissionExplanationDialog.newInstance(R.string.permission_explanation_title, explanation, permission);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(explanation);
        builder.setTitle(R.string.permission_explanation_title);
        builder.setNegativeButton(R.string.close_app, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ActivityCompat.requestPermissions(MonitoredActivity.this, new String[]{permission}, MY_PERMISSIONS_REQUEST);
            }
        });
        mPermissionDialog = builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    EventBus.getDefault().post(new PermissionGrantedEvent(permissions[0]));
                } else {
                    finish();
                }
            }
        }
    }

}

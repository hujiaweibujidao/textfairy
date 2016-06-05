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
package com.renard.ocr;

import android.app.Application;
import android.os.StrictMode;
import android.util.Log;
import android.view.ViewConfiguration;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.ndk.CrashlyticsNdk;
import com.renard.ocr.analytics.Analytics;
import com.renard.ocr.analytics.AnalyticsFactory;
import com.renard.ocr.util.PreferencesUtils;
import com.squareup.leakcanary.LeakCanary;

import java.lang.reflect.Field;

import io.fabric.sdk.android.Fabric;

public class TextFairyApplication extends Application {

    private static final String LOG_TAG = TextFairyApplication.class.getSimpleName();
    private Analytics mAnalytics;

    public void onCreate() {
        super.onCreate();

        trackCrashes();//Fabric是用来跟踪应用崩溃信息的
        createAnalytics();

        initTextPreferences();

        enableStrictMode();
        alwaysShowOverflowButton();
    }

    private void initTextPreferences() {
        PreferencesUtils.initPreferencesWithDefaultsIfEmpty(getApplicationContext());
    }

    private void createAnalytics() {
        mAnalytics = AnalyticsFactory.createAnalytics(this);
    }

    private void trackCrashes() {
        if (BuildConfig.FLAVOR.contains("playstore")) {//如果包含playstore那就表示是release版本，也就需要启动应用崩溃跟踪分析
            Log.i(LOG_TAG, "Starting Crashlytics");
            final Fabric fabric = new Fabric.Builder(this).kits(new Crashlytics(), new CrashlyticsNdk()).debuggable(BuildConfig.DEBUG).build();
            Fabric.with(fabric);
        }
    }

    //toread StrictMode
    private void enableStrictMode() {
        if (BuildConfig.DEBUG) {//如果是debug模式的话，需要启动LeakCanary来检查内存泄露
            LeakCanary.install(this);
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build();
            StrictMode.setThreadPolicy(policy);
        }
    }

    private void alwaysShowOverflowButton() {
        // force overflow button for actionbar for devices with hardware option button
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception ex) {
            // Ignore
        }
    }

    public Analytics getAnalytics() {
        return mAnalytics;
    }

    public static boolean isRelease() {
        return com.renard.ocr.BuildConfig.FLAVOR.contains("playstore");//如果包含playstore那就表示是release版本
    }
}

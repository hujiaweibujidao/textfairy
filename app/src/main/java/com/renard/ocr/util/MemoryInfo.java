package com.renard.ocr.util;

import android.app.ActivityManager;
import android.content.Context;

/**
 * 内存信息
 */
public class MemoryInfo {

    public static final long MINIMUM_RECOMMENDED_RAM = 120;

    private MemoryInfo() {

    }

    //得到可用内存，单位是mb
    public static long getFreeMemory(Context context) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        return mi.availMem / 1048576L;
    }
}

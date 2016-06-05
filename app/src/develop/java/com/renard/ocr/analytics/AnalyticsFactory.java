package com.renard.ocr.analytics;

import android.content.Context;

/**
 * 用于创建操作日志分析
 */
public class AnalyticsFactory {

    public static Analytics createAnalytics(Context context) {
        return new LoggingAnalytics();
    }
}

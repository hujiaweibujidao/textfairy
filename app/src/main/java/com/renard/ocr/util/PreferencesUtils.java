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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Pair;

import com.renard.ocr.R;
import com.renard.ocr.language.OcrLanguage;

public class PreferencesUtils {

    /* ids of the radio buttons pressed in the options dialogs */
    public final static String PREFERENCES_SPACING_KEY = "line_spacing";
    public final static String PREFERENCES_DESIGN_KEY = "text_design";
    public final static String PREFERENCES_ALIGNMENT_KEY = "text_alignment";
    public final static String PREFERENCES_TEXT_SIZE_KEY = "text_size";
    private final static String PREFERENCES_TRAINING_DATA_DIR = "training_data_dir";

    public final static String PREFERENCES_KEY = "text_preferences";
    private static final String PREFERENCES_THUMBNAIL_HEIGHT = "thumbnail_width";
    private static final String PREFERENCES_THUMBNAIL_WIDTH = "thumbnail_height";
    private static final String PREFERENCES_HAS_ASKED_FOR_FEEDBACK = "has_asked_for_feedback";
    private static final String PREFERENCES_IS_FIRST_START = "is_first_start";

    // actual language
    public static final String PREFERENCES_OCR_LANG = "ocr_language";
    public static final String PREFERENCES_OCR_LANG_DISPLAY = "ocr_language_display";

    //hujiawei 添加的配置项
    public static final String PREFERENCES_LAYOUT = "layout";
    public static final String PREFERENCES_AUTO_LAYOUT = "autolayout";
    public static final String PREFERENCES_MODE = "mode";

    public static final int LAYOUT_SIMPLE = 1;
    public static final int LAYOUT_COMPLEX = 2;

    public static final int MODE_HTEXT = 1;
    public static final int MODE_VTEXT = 2;
    public static final int MODE_IMAGE = 3;
    public static final int MODE_TABLE = 4;

    /**
     * 初始化应用的配置信息
     */
    public static void initPreferencesWithDefaultsIfEmpty(Context appContext) {
        SharedPreferences prefs = getPreferences(appContext);
        Editor edit = prefs.edit();

        //与应用有关的默认配置
        final String defaultLanguage = appContext.getString(R.string.default_ocr_language);//
        final String defaultLanguageDisplay = appContext.getString(R.string.default_ocr_display_language);//
        setIfEmpty(edit, prefs, PREFERENCES_OCR_LANG, defaultLanguage);
        setIfEmpty(edit, prefs, PREFERENCES_OCR_LANG_DISPLAY, defaultLanguageDisplay);

        final int defaultLayout = LAYOUT_SIMPLE;
        setIfEmpty(edit, prefs, PREFERENCES_LAYOUT, defaultLayout);
        final boolean autoLayout = true;
        setIfEmpty(edit, prefs, PREFERENCES_AUTO_LAYOUT, autoLayout);
        final int defaultMode = MODE_HTEXT;
        setIfEmpty(edit, prefs, PREFERENCES_MODE, defaultMode);

        edit.apply();//并没有提交
    }

    private static void setIfEmpty(final Editor edit, final SharedPreferences prefs, final String id, final int value) {
        if (!prefs.contains(id)) {
            edit.putInt(id, value);
        }
    }

    private static void setIfEmpty(final Editor edit, final SharedPreferences prefs, final String id, final boolean value) {
        if (!prefs.contains(id)) {
            edit.putBoolean(id, value);
        }
    }

    private static void setIfEmpty(final Editor edit, final SharedPreferences prefs, final String id, final String value) {
        if (!prefs.contains(id)) {
            edit.putString(id, value);
        }
    }

    public static void saveOCRLanguage(final Context context, String language, String display) {
        SharedPreferences prefs = getPreferences(context);
        Editor edit = prefs.edit();
        edit.putString(PREFERENCES_OCR_LANG, language);
        edit.putString(PREFERENCES_OCR_LANG_DISPLAY, display);
        edit.apply();
    }

    public static void saveOCRLanguage(final Context context, OcrLanguage language) {
        SharedPreferences prefs = getPreferences(context);
        Editor edit = prefs.edit();
        edit.putString(PREFERENCES_OCR_LANG, language.getValue());
        edit.putString(PREFERENCES_OCR_LANG_DISPLAY, language.getDisplayText());
        edit.apply();
    }

    public static Pair<String, String> getOCRLanguage(final Context context) {
        SharedPreferences prefs = getPreferences(context);
        final String defaultLanguage = context.getString(R.string.default_ocr_language);//
        final String defaultLanguageDisplay = context.getString(R.string.default_ocr_display_language);//
        String value = prefs.getString(PREFERENCES_OCR_LANG, defaultLanguage);
        String display = prefs.getString(PREFERENCES_OCR_LANG_DISPLAY, defaultLanguageDisplay);
        return new Pair<>(value, display);
    }

    public static int getMode(final Context context){
        SharedPreferences prefs = getPreferences(context);
        return prefs.getInt(PREFERENCES_MODE, MODE_HTEXT);
    }

    public static void saveMode(Context context, final int value){
        SharedPreferences prefs = getPreferences(context);
        Editor edit = prefs.edit();
        edit.putInt(PREFERENCES_MODE, value);
        edit.apply();
    }

    public static int getLayout(final Context context){
        SharedPreferences prefs = getPreferences(context);
        return prefs.getInt(PREFERENCES_LAYOUT, LAYOUT_SIMPLE);
    }

    public static void saveLayout(Context context, final int value){
        SharedPreferences prefs = getPreferences(context);
        Editor edit = prefs.edit();
        edit.putInt(PREFERENCES_LAYOUT, value);
        edit.apply();
    }

    public static boolean getAutolayout(final Context context){
        SharedPreferences prefs = getPreferences(context);
        return prefs.getBoolean(PREFERENCES_AUTO_LAYOUT, true);
    }

    public static void saveAutolayout(Context context, final boolean value){
        SharedPreferences prefs = getPreferences(context);
        Editor edit = prefs.edit();
        edit.putBoolean(PREFERENCES_AUTO_LAYOUT, value);
        edit.apply();
    }

    public static void saveTessDir(Context appContext, final String value) {
        SharedPreferences prefs = getPreferences(appContext);
        Editor edit = prefs.edit();
        edit.putString(PREFERENCES_TRAINING_DATA_DIR, value);
        edit.apply();
    }

    public static String getTessDir(Context appContext) {
        SharedPreferences prefs = getPreferences(appContext);
        return prefs.getString(PREFERENCES_TRAINING_DATA_DIR, null);
    }

    public static void setNumberOfSuccessfulScans(Context appContext, final int value) {
        SharedPreferences prefs = getPreferences(appContext);
        Editor edit = prefs.edit();
        edit.putInt(PREFERENCES_HAS_ASKED_FOR_FEEDBACK, value);
        edit.apply();
    }

    public static int getNumberOfSuccessfulScans(Context appContext) {
        SharedPreferences prefs = getPreferences(appContext);
        return prefs.getInt(PREFERENCES_HAS_ASKED_FOR_FEEDBACK, 0);
    }

    public static SharedPreferences getPreferences(Context applicationContext) {
        return applicationContext.getSharedPreferences(PREFERENCES_KEY, Context.MODE_PRIVATE);
    }

    public static int getThumbnailWidth(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getInt(PREFERENCES_THUMBNAIL_WIDTH, 20);
    }

    public static int getThumbnailHeight(Context context) {
        SharedPreferences prefs = getPreferences(context);
        return prefs.getInt(PREFERENCES_THUMBNAIL_HEIGHT, 20);
    }

    public static void saveThumbnailSize(Context context, int w, int h) {
        SharedPreferences prefs = getPreferences(context);
        Editor edit = prefs.edit();
        edit.putInt(PREFERENCES_THUMBNAIL_WIDTH, w);
        edit.putInt(PREFERENCES_THUMBNAIL_HEIGHT, h);
        edit.apply();
    }

    public static boolean isFirstStart(Context context) {
        return getPreferences(context).getBoolean(PREFERENCES_IS_FIRST_START, true);
    }

    public static void setFirstStart(Context context, boolean value) {
        SharedPreferences prefs = getPreferences(context);
        Editor edit = prefs.edit();
        edit.putBoolean(PREFERENCES_IS_FIRST_START, value);
        edit.apply();
    }

}

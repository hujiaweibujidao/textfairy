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
package com.renard.ocr.documents.creation.visualisation;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.ViewFlipper;
import android.widget.ViewSwitcher;

import com.renard.ocr.R;
import com.renard.ocr.analytics.Analytics;
import com.renard.ocr.base.MonitoredActivity;
import com.renard.ocr.language.OcrLanguage;
import com.renard.ocr.language.OcrLanguageDataStore;
import com.renard.ocr.util.PreferencesUtils;

import java.util.List;

/**
 * 询问布局的对话框
 */
public class LayoutQuestionDialog extends DialogFragment {

    public static final String TAG = LayoutQuestionDialog.class.getSimpleName();

    private static final String SCREEN_NAME = "Layout Question Dialog";

    private Analytics mAnalytics;

    public static LayoutQuestionDialog newInstance() {
        return new LayoutQuestionDialog();
    }

    public enum LayoutKind {
        SIMPLE, COMPLEX, DO_NOTHING;
    }

    private static LayoutKind mLayout = LayoutKind.SIMPLE;
    private static String mLanguage;

    public interface LayoutChoseListener {
        void onLayoutChosen(final LayoutKind layoutKind, final String language);
    }

    public Analytics getAnalytics() {
        if (mAnalytics == null && getActivity() != null) {
            MonitoredActivity activity = (MonitoredActivity) getActivity();
            return activity.getAnaLytics();
        }
        return mAnalytics;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MonitoredActivity monitoredActivity = (MonitoredActivity) getActivity();
        mAnalytics = monitoredActivity.getAnaLytics();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        getAnalytics().sendScreenView(SCREEN_NAME);
        final Context context = getContext();
        mLayout = null;
        Pair<String, String> language = PreferencesUtils.getOCRLanguage(context);

        final OcrLanguage.InstallStatus installStatus = OcrLanguageDataStore.isLanguageInstalled(language.first, context);

        if (!installStatus.isInstalled()) {
            final String defaultLanguage = context.getString(R.string.default_ocr_language);//不同语言的系统使用不同的默认语言
            final String defaultLanguageDisplay = context.getString(R.string.default_ocr_display_language);
            language = Pair.create(defaultLanguage, defaultLanguageDisplay);
        }
        mLanguage = language.first;

        AlertDialog.Builder builder;
        builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        View layout = View.inflate(context, R.layout.dialog_layout_question, null);
        builder.setView(layout);

        final ViewFlipper titleViewFlipper = (ViewFlipper) layout.findViewById(R.id.layout_title);
        final ImageView columnLayout = (ImageView) layout.findViewById(R.id.column_layout);
        final ImageView pageLayout = (ImageView) layout.findViewById(R.id.page_layout);
        final ImageSwitcher fairy = (ImageSwitcher) layout.findViewById(R.id.fairy_layout);
        fairy.setFactory(new ViewSwitcher.ViewFactory() {
            public View makeView() {
                return new ImageView(context);
            }
        });
        fairy.setImageResource(R.drawable.fairy_looks_center);
        fairy.setInAnimation(context, android.R.anim.fade_in);
        fairy.setOutAnimation(context, android.R.anim.fade_out);

        //选中的布局会有一个tint着色
        final int color = context.getResources().getColor(R.color.progress_color);
        final PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.LIGHTEN);

        columnLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLayout != LayoutKind.COMPLEX) {
                    mLayout = LayoutKind.COMPLEX;
                    titleViewFlipper.setDisplayedChild(2);
                    fairy.setImageResource(R.drawable.fairy_looks_left);
                    columnLayout.setColorFilter(colorFilter);
                    pageLayout.clearColorFilter();
                }

            }
        });
        pageLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLayout != LayoutKind.SIMPLE) {
                    mLayout = LayoutKind.SIMPLE;
                    titleViewFlipper.setDisplayedChild(1);
                    fairy.setImageResource(R.drawable.fairy_looks_right);
                    pageLayout.setColorFilter(colorFilter);
                    columnLayout.clearColorFilter();
                }
            }
        });

        final Spinner langButton = (Spinner) layout.findViewById(R.id.button_language);
        List<OcrLanguage> installedLanguages = OcrLanguageDataStore.getInstalledOCRLanguages(context);

        // actual values uses by tesseract
        final ArrayAdapter<OcrLanguage> adapter = new ArrayAdapter<>(context, R.layout.language_spinner_item, installedLanguages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        langButton.setAdapter(adapter);
        for (int i = 0; i < installedLanguages.size(); i++) {
            OcrLanguage lang = installedLanguages.get(i);
            if (lang.getValue().equals(language.first)) {
                langButton.setSelection(i, false);
                break;
            }
        }
        langButton.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                final OcrLanguage item = adapter.getItem(position);
                mLanguage = item.getValue();
                PreferencesUtils.saveOCRLanguage(context, item);//保存上一次选中的语言
                getAnalytics().sendOcrLanguageChanged(item);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        //选择好了之后回调Activity中实现的onLayoutChosen
        builder.setPositiveButton(R.string.start_scan,
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int id) {
                        if (mLayout == null) {
                            mLayout = LayoutKind.SIMPLE;
                        }
                        LayoutChoseListener listener = (LayoutChoseListener) getActivity();
                        listener.onLayoutChosen(mLayout, mLanguage);
                        getAnalytics().sendOcrStarted(mLanguage, mLayout);
                        dialog.dismiss();
                    }
                });

        builder.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int id) {
                        getActivity().finish();
                        dialog.dismiss();
                        getAnalytics().sendLayoutDialogCancelled();
                    }
                });


        return builder.create();
    }

}

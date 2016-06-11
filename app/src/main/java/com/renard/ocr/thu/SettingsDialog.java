package com.renard.ocr.thu;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import com.renard.ocr.R;
import com.renard.ocr.documents.viewing.single.TopDialogFragment;
import com.renard.ocr.util.PreferencesUtils;

import worker8.com.github.radiogroupplus.RadioGroupPlus;

/**
 * 设置界面
 */
public class SettingsDialog extends TopDialogFragment {

    private static final String SCREEN_NAME = "Settings Dialog";
    public static final String TAG = SettingsDialog.class.getSimpleName();

    private View contentView;
    private RadioGroup groupLanguage;
    private RadioGroup groupLayout;
    private RadioGroupPlus groupMode;
    private CheckBox checkboxAutolayout;

    public static SettingsDialog newInstance() {
        return new SettingsDialog();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        getAnalytics().sendScreenView(SCREEN_NAME);

        AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
        contentView = getActivity().getLayoutInflater().inflate(R.layout.thu_dialog_settings, null);
        builder.setTitle(R.string.dialog_settings);
        builder.setView(contentView);

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                updateSettings();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        findViews();
        initSettings();

        return builder.create();
    }

    //更新设置
    private void updateSettings() {
        int languageId = groupLanguage.getCheckedRadioButtonId();
        switch (languageId) {
            case R.id.settings_rb_eng: {
                PreferencesUtils.saveOCRLanguage(getActivity(), "eng", "English");
                break;
            }
            case R.id.settings_rb_chisim: {
                PreferencesUtils.saveOCRLanguage(getActivity(), "chi_sim", "Chinese Simplified");
                break;
            }
            case R.id.settings_rb_chitra: {
                PreferencesUtils.saveOCRLanguage(getActivity(), "chi_tra", "Chinese Traditional");
                break;
            }
        }

        int layout = groupLayout.getCheckedRadioButtonId();
        if (layout == R.id.settings_rb_simple) {
            PreferencesUtils.saveLayout(getActivity(), PreferencesUtils.LAYOUT_SIMPLE);
        } else {
            PreferencesUtils.saveLayout(getActivity(), PreferencesUtils.LAYOUT_COMPLEX);
        }

        int mode = groupMode.getCheckedRadioButtonId();
        switch (mode) {
            case R.id.settings_rb_htext: {
                PreferencesUtils.saveMode(getActivity(), PreferencesUtils.MODE_HTEXT);
                break;
            }
            case R.id.settings_rb_vtext: {
                PreferencesUtils.saveMode(getActivity(), PreferencesUtils.MODE_VTEXT);
                break;
            }
            case R.id.settings_rb_table: {
                PreferencesUtils.saveMode(getActivity(), PreferencesUtils.MODE_TABLE);
                break;
            }
            case R.id.settings_rb_image: {
                PreferencesUtils.saveMode(getActivity(), PreferencesUtils.MODE_IMAGE);
                break;
            }
        }

        boolean autolayout = checkboxAutolayout.isChecked();
        PreferencesUtils.saveAutolayout(getActivity(), autolayout);
    }

    //初始化设置界面
    private void initSettings() {
        Pair<String, String> pair = PreferencesUtils.getOCRLanguage(getActivity());
        if (pair.first.equalsIgnoreCase("eng")) {
            groupLanguage.clearCheck();
            groupLanguage.check(R.id.settings_rb_eng);
        } else if (pair.first.equalsIgnoreCase("chi_sim")) {
            groupLanguage.clearCheck();
            groupLanguage.check(R.id.settings_rb_chisim);
        } else if (pair.first.equalsIgnoreCase("chi_tra")) {
            groupLanguage.clearCheck();
            groupLanguage.check(R.id.settings_rb_chitra);
        }

        int layout = PreferencesUtils.getLayout(getActivity());
        if (layout == PreferencesUtils.LAYOUT_SIMPLE) {
            groupLayout.clearCheck();
            groupLayout.check(R.id.settings_rb_simple);
        } else if (layout == PreferencesUtils.LAYOUT_COMPLEX) {
            groupLayout.clearCheck();
            groupLayout.check(R.id.settings_rb_complex);
        }

        int mode = PreferencesUtils.getMode(getActivity());
        switch (mode) {
            case PreferencesUtils.MODE_HTEXT: {
                groupMode.clearCheck();
                groupMode.check(R.id.settings_rb_htext);
                break;
            }
            case PreferencesUtils.MODE_VTEXT: {
                groupMode.clearCheck();
                groupMode.check(R.id.settings_rb_vtext);
                break;
            }
            case PreferencesUtils.MODE_IMAGE: {
                groupMode.clearCheck();
                groupMode.check(R.id.settings_rb_image);
                break;
            }
            case PreferencesUtils.MODE_TABLE: {
                groupMode.clearCheck();
                groupMode.check(R.id.settings_rb_table);
                break;
            }
        }

        boolean autolayout = PreferencesUtils.getAutolayout(getActivity());
        checkboxAutolayout.setChecked(autolayout);
    }

    public void findViews() {
        groupLanguage = (RadioGroup) contentView.findViewById(R.id.settings_rg_language);
        groupLayout = (RadioGroup) contentView.findViewById(R.id.settings_rg_layout);
        groupMode = (RadioGroupPlus) contentView.findViewById(R.id.settings_rg_mode);
        checkboxAutolayout = (CheckBox) contentView.findViewById(R.id.settings_cb_autolayout);
    }

}

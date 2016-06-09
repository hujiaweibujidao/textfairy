package com.renard.ocr.language;

import android.content.Context;
import android.net.Uri;

import com.renard.ocr.R;
import com.renard.ocr.util.Util;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;


/**
 * ocr语言训练包存储
 *
 * @author renard
 */
public class OcrLanguageDataStore {

    public static final File[] EMPTY_FILE_ARRAY = new File[0];

    //获取已经安装的ocr语言训练包
    public static List<OcrLanguage> getInstalledOCRLanguages(Context appContext) {
        final List<OcrLanguage> ocrLanguages = getAvailableOcrLanguages(appContext);
        final List<OcrLanguage> result = new ArrayList<>();
        for (OcrLanguage lang : ocrLanguages) {
            if (lang.isInstalled()) {
                result.add(lang);
            }
        }
        return result;
    }

    //获取所有支持的OCR语言，读取一个string array，R.array.ocr_languages
    public static List<OcrLanguage> getAvailableOcrLanguages(Context context) {
        List<OcrLanguage> languages = new ArrayList<>();
        // actual values uses by tesseract
        final String[] languageValues = context.getResources().getStringArray(R.array.ocr_languages);
        // values shown to the user
        final String[] languageDisplayValues = new String[languageValues.length];
        for (int i = 0; i < languageValues.length; i++) {
            final String val = languageValues[i];
            final int firstSpace = val.indexOf(' ');
            languageDisplayValues[i] = languageValues[i].substring(firstSpace + 1, languageValues[i].length());
            languageValues[i] = languageValues[i].substring(0, firstSpace);
        }
        for (int i = 0; i < languageValues.length; i++) {
            final OcrLanguage.InstallStatus installStatus = isLanguageInstalled(languageValues[i], context);
            OcrLanguage language = new OcrLanguage(languageValues[i], languageDisplayValues[i], installStatus.isInstalled(), installStatus.getInstalledSize());
            languages.add(language);
        }
        return languages;
    }

    //判断某种语言是否安装了
    public static OcrLanguage.InstallStatus isLanguageInstalled(final String ocrLang, Context context) {
        final File[] languageFiles = getAllFilesFor(ocrLang, context);
        if (languageFiles.length == 0) {
            return new OcrLanguage.InstallStatus(false, 0);
        }

        OcrLanguage dummy = new OcrLanguage(ocrLang, "", false, 0);
        final List<Uri> downloadUris = dummy.getDownloadUris();
        final boolean isInstalled = languageFiles.length >= downloadUris.size();

        return new OcrLanguage.InstallStatus(isInstalled, sumFileSizes(languageFiles));
    }

    //获取某种语言的所有数据文件
    private static File[] getAllFilesFor(final String ocrLang, Context context) {
        final File tessDir = Util.getTrainingDataDir(context);
        if (!tessDir.exists()) {
            return EMPTY_FILE_ARRAY;
        }

        final File[] files = tessDir.listFiles(new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                return isLanguageFileFor(pathname, ocrLang);
            }
        });
        if (files == null) {
            return EMPTY_FILE_ARRAY;
        } else {
            return files;
        }
    }

    //统计某些文件的大小之和
    private static long sumFileSizes(File[] languageFiles) {
        if (languageFiles == null) {
            return 0;
        }
        long sum = 0;
        for (File f : languageFiles) {
            sum += f.length();
        }
        return sum;
    }

    //判断某个文件是否是某种语言的
    private static boolean isLanguageFileFor(File pathname, String ocrLang) {
        return pathname.getName().startsWith(ocrLang + ".") && pathname.isFile();
    }

    public static boolean deleteLanguage(OcrLanguage language, Context context) {
        final File[] languageFiles = getAllFilesFor(language.getValue(), context);
        if (languageFiles.length == 0) {
            language.setUninstalled();
            return false;
        }

        boolean success = true;
        boolean atLeastOneDeleted = false;

        for (File file : languageFiles) {
            final boolean deleted = file.delete();
            success &= deleted;
            atLeastOneDeleted |= deleted;
        }
        if (atLeastOneDeleted) {
            language.setUninstalled();
        }
        return success;
    }
}

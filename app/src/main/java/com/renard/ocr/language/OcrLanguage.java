package com.renard.ocr.language;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * ocr语言
 * InstallStatus是语言的安装状态，是否安装是根据installedSize来判断
 * 不同语言需要下载的文件不太一样，文件下载的来源也不都相同
 * mValue是语言的简称，mDisplayText是语言的显示文本
 *
 * @author renard
 */
public class OcrLanguage implements Parcelable {

    //语言的安装状态，一个是是否安装了，另一个是全部文件大小
    public static class InstallStatus {
        private final boolean isInstalled;
        private final long installedSize;

        public InstallStatus(boolean isInstalled, long installedSize) {
            this.isInstalled = isInstalled;
            this.installedSize = installedSize;
        }

        public boolean isInstalled() {
            return isInstalled;
        }

        public long getInstalledSize() {
            return installedSize;
        }
    }

    private static final String[] CUBE_FILES = {".cube.fold", ".cube.lm", ".cube.nn", ".cube.params", ".cube.word-freq"};

    private boolean mDownloading;
    private final String mValue;
    private final String mDisplayText;
    private InstallStatus mInstallStatus;

    public OcrLanguage(Parcel in) {
        mValue = in.readString();
        mDisplayText = in.readString();
        long size = in.readLong();
        boolean isInstalled = in.readInt() != 0;
        mInstallStatus = new InstallStatus(isInstalled, size);

    }

    public OcrLanguage(final String value, final String displayText, boolean installed, long size) {
        mInstallStatus = new InstallStatus(installed, size);
        mValue = value;
        mDisplayText = displayText;

    }

    public static final Creator CREATOR = new Creator() {
        public OcrLanguage createFromParcel(Parcel in) {
            return new OcrLanguage(in);
        }

        public OcrLanguage[] newArray(int size) {
            return new OcrLanguage[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getValue());
        dest.writeString(getDisplayText());
        dest.writeLong(mInstallStatus.installedSize);
        dest.writeInt(mInstallStatus.isInstalled ? 1 : 0);
    }

    public boolean isInstalled() {
        return mInstallStatus.isInstalled;
    }

    public void setUninstalled() {
        mInstallStatus = new InstallStatus(false, 0);
    }

    public void setInstallStatus(InstallStatus installStatus) {
        mInstallStatus = installStatus;
    }

    public long getSize() {
        return mInstallStatus.installedSize;
    }

    public String getValue() {
        return mValue;
    }

    public String getDisplayText() {
        return mDisplayText;
    }

    public boolean isDownloading() {
        return mDownloading;
    }

    public void setDownloading(boolean downloading) {
        mDownloading = downloading;
    }

    @Override
    public String toString() {
        return getDisplayText();
    }

    //获取语言的下载地址
    public List<Uri> getDownloadUris() {
        List<Uri> result = new ArrayList<>();
        final String part2 = ".traineddata";
        String networkDir = "https://raw.githubusercontent.com/tesseract-ocr/tessdata/master/";
        if ("guj".equalsIgnoreCase(getValue())) {
            networkDir = "https://parichit.googlecode.com/files/";
        }
        result.add(Uri.parse(networkDir + getValue() + part2));
        if (needsCubeData()) {
            List<String> cubeFiles = getCubeFileNames();
            for (String cubeFileName : cubeFiles) {
                Uri cubeFileUri = Uri.parse(networkDir + cubeFileName);
                result.add(cubeFileUri);
            }
        }
        return result;
    }

    //只有三种语言支持cube
    public static boolean hasCubeSupport(String lang) {
        return "ara".equalsIgnoreCase(lang) || "hin".equalsIgnoreCase(lang) || "rus".equalsIgnoreCase(lang);
    }

    public static boolean canCombineCubeAndTesseract(String lang) {
        return false;
    }

    private boolean needsCubeData() {
        return hasCubeSupport(mValue);
    }

    private List<String> getCubeFileNames() {
        List<String> result = new ArrayList<>();
        for (String cubeFileName : CUBE_FILES) {
            result.add(getValue() + cubeFileName);
        }
        if ("hin".equalsIgnoreCase(mValue)) {
            result.add(getValue() + ".cube.bigrams");
            result.add(getValue() + ".tesseract_cube.nn");
        }
        if ("ara".equalsIgnoreCase(mValue)) {
            result.add(getValue() + ".cube.bigrams");
            result.add(getValue() + ".cube.size");
        }
        if ("rus".equalsIgnoreCase(mValue)) {
            result.add(getValue() + ".cube.size");
        }
        return result;
    }

}

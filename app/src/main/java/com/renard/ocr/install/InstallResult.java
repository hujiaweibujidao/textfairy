package com.renard.ocr.install;

/**
 * 语言包安装的结果
 *
 * @author renard
 */
class InstallResult {

    //没有足够的空间、成功、未知错误
    public enum Result {
        NOT_ENOUGH_DISK_SPACE, OK, UNSPECIFIED_ERROR
    }

    private long mNeededSpace;
    private long mFreeSpace;
    private Result mResult;

    public InstallResult(Result result) {
        mResult = result;
    }

    public InstallResult(Result result, long needed, long free) {
        mResult = result;
        mFreeSpace = free;
        mNeededSpace = needed;
    }

    public Result getResult() {
        return mResult;
    }

    public long getNeededSpace() {
        return mNeededSpace;
    }

    public long getFreeSpace() {
        return mFreeSpace;
    }
}

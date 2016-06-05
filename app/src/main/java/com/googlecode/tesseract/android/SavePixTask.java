package com.googlecode.tesseract.android;

import android.os.AsyncTask;

import com.googlecode.leptonica.android.Pix;
import com.renard.ocr.util.Util;

import java.io.File;
import java.io.IOException;

/**
 * 保存Pix到文件File的操作
 *
 * @author renard
 */
class SavePixTask extends AsyncTask<Void, Void, File> {
    private final Pix mPix;
    private final File mDir;

    SavePixTask(Pix pix, File dir) {
        mPix = pix;
        mDir = dir;
    }

    @Override
    protected File doInBackground(Void... params) {
        try {
            return Util.savePixToDir(mPix, OCR.ORIGINAL_PIX_NAME, mDir);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mPix.recycle();
        }

        return null;
    }

}

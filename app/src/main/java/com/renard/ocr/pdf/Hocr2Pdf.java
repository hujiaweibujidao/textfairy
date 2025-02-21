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
package com.renard.ocr.pdf;


import java.io.UnsupportedEncodingException;

/**
 * 将hocr转成pdf
 */
public class Hocr2Pdf {
    static {
        System.loadLibrary("pngo");
        System.loadLibrary("hocr2pdf");
		System.loadLibrary("hocr2pdfjni");
    }
    
    private PDFProgressListener mListener;//监听处理到多少页了
    
    public interface PDFProgressListener{
    	void onNewPage(int pageNumber);
    }
    
    public Hocr2Pdf(PDFProgressListener listener) {
    	mListener = listener;
    }

    public void hocr2pdf(String[] images, String[] hocr, String pdfFileName, boolean sloppy, boolean overlayImage) {
        byte[][] hocrBytes = new byte[hocr.length][];
        for(int i = 0; i<hocr.length; i++){
            try {
                hocrBytes[i] = hocr[i].getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                hocrBytes[i] = "".getBytes();
            }

        }
        nativeHocr2pdf(images, hocrBytes, pdfFileName, sloppy, overlayImage);
    }
    
    private void onProgress(int pageNumber) {
    	if (mListener!=null){
    		mListener.onNewPage(pageNumber);
    	}
    }
    
    private native void nativeHocr2pdf( String[] images, byte[][] hocr, String pdfFileName, boolean sloppy, boolean overlayImage);
}

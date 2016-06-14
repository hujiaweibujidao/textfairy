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

package com.renard.ocr.documents.viewing.grid;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.RelativeLayout;

/**
 * custom view for children of the document grid view
 * TODO remove tight coupling with DocumentGridActivity
 *
 * 自定义的支持check的grid单元格
 *
 * @author renard
 */
public class CheckableGridElement extends RelativeLayout implements Checkable {

    private final String LOG_TAG = CheckableGridElement.class.getSimpleName();

    private boolean mIsChecked = false;
    private OnCheckedChangeListener mListener;

    //private ImageView mThumbnailImageView;

    //选中状态发生变化的监听器
    public interface OnCheckedChangeListener {
        void onCheckedChanged(View documentView, boolean isChecked);
    }

    public CheckableGridElement(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setWillNotDraw(false);//toread 这行代码的作用？
    }

    /*@Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mThumbnailImageView = (ImageView) findViewById(R.id.thumb);
        this.setFocusable(false);
    }

    public void setImage(Drawable d) {
        mThumbnailImageView.setImageDrawable(d);
    }*/

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        mListener = listener;
    }

    /**
     * Checkable接口的三个方法
     *
     * sets checked state and starts animation if necessary
     */
    @Override
    public void setChecked(boolean checked) {
        mIsChecked = checked;
        if (mListener != null) {
            mListener.onCheckedChanged(this, mIsChecked);
        }
    }

    @Override
    public boolean isChecked() {
        return mIsChecked;
    }

    @Override
    public void toggle() {
        mIsChecked = !mIsChecked;
        if (mListener != null) {
            mListener.onCheckedChanged(this, mIsChecked);
        }
    }

}

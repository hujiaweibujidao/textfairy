package com.renard.ocr.util;

/**
 * Copyright 2014 www.delight.im <info@delight.im>
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

import android.content.pm.ActivityInfo;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.Surface;

/**
 * 屏幕控制类
 */
public class Screen {

	public static class Orientation {

		public static final int LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
		public static final int PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
		public static final int REVERSE_LANDSCAPE = 8; // ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
		public static final int REVERSE_PORTRAIT = 9; // ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
		public static final int UNSPECIFIED = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

	}

	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	public static void lockOrientation(final Activity activity) {
		final Display display = activity.getWindowManager().getDefaultDisplay();
		final int rotation = display.getRotation();

        //获取屏幕的长宽的方式
		final int width, height;
		if (Build.VERSION.SDK_INT >= 13) {
			Point size = new Point();
			display.getSize(size);
			width = size.x;
			height = size.y;
		}
		else {
			width = display.getWidth();
			height = display.getHeight();
		}

		switch (rotation) {
			case Surface.ROTATION_90:
				if (width > height) {
					activity.setRequestedOrientation(Orientation.LANDSCAPE);
				}
				else {
					activity.setRequestedOrientation(Orientation.REVERSE_PORTRAIT);
				}
				break;
			case Surface.ROTATION_180:
				if (height > width) {
					activity.setRequestedOrientation(Orientation.REVERSE_PORTRAIT);
				}
				else {
					activity.setRequestedOrientation(Orientation.REVERSE_LANDSCAPE);
				}
				break;
			case Surface.ROTATION_270:
				if (width > height) {
					activity.setRequestedOrientation(Orientation.REVERSE_LANDSCAPE);
				}
				else {
					activity.setRequestedOrientation(Orientation.PORTRAIT);
				}
				break;
			default:
				if (height > width) {
					activity.setRequestedOrientation(Orientation.PORTRAIT);
				}
				else {
					activity.setRequestedOrientation(Orientation.LANDSCAPE);
				}
		}
	}

	public static void unlockOrientation(final Activity activity) {
		activity.setRequestedOrientation(Orientation.UNSPECIFIED);
	}

}
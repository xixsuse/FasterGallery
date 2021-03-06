/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.fastergallery.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import com.android.fastergallery.app.FilterUtils;
import com.android.fastergallery.data.DataSourceType;
import com.android.fastergallery.util.ThreadPool;
import com.android.fastergallery.util.ThreadPool.JobContext;
import com.android.fasterphotos.data.GalleryBitmapPool;
import com.android.fastergallery.R;

public class AlbumTypeLabelMaker {
	private static final int BORDER_SIZE = 0;

	private final AlbumSetTypeSlotRenderer.LabelSpec mSpec;
	private final TextPaint mTitlePaint;
	private final TextPaint mCountPaint;
	private final Context mContext;

	private int mLabelWidth;
	private int mBitmapWidth;
	private int mBitmapHeight;

	private final LazyLoadedBitmap mLocalSetIcon;
	private final LazyLoadedBitmap mPicasaIcon;
	private final LazyLoadedBitmap mCameraIcon;

	public AlbumTypeLabelMaker(Context context, AlbumSetTypeSlotRenderer.LabelSpec spec) {
		mContext = context;
		mSpec = spec;
		mTitlePaint = getTextPaint(spec.titleFontSize, spec.titleColor, false);
		mCountPaint = getTextPaint(spec.countFontSize, spec.countColor, false);

		mLocalSetIcon = new LazyLoadedBitmap(
				R.drawable.frame_overlay_gallery_folder);
		mPicasaIcon = new LazyLoadedBitmap(
				R.drawable.frame_overlay_gallery_picasa);
		mCameraIcon = new LazyLoadedBitmap(
				R.drawable.frame_overlay_gallery_camera);
	}

	public static int getBorderSize() {
		return BORDER_SIZE;
	}

	private Bitmap getOverlayAlbumIcon(int sourceType) {
		switch (sourceType) {
		case DataSourceType.TYPE_CAMERA:
			return mCameraIcon.get();
		case DataSourceType.TYPE_LOCAL:
			return mLocalSetIcon.get();
		case DataSourceType.TYPE_PICASA:
			return mPicasaIcon.get();
		}
		return null;
	}

	private static TextPaint getTextPaint(int textSize, int color,
			boolean isBold) {
		TextPaint paint = new TextPaint();
		paint.setTextSize(textSize);
		paint.setAntiAlias(true);
		paint.setColor(color);
		// paint.setShadowLayer(2f, 0f, 0f, Color.LTGRAY);
		if (isBold) {
			paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
		}
		return paint;
	}

	private class LazyLoadedBitmap {
		private Bitmap mBitmap;
		private int mResId;

		public LazyLoadedBitmap(int resId) {
			mResId = resId;
		}

		public synchronized Bitmap get() {
			if (mBitmap == null) {
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inPreferredConfig = Bitmap.Config.ARGB_8888;
				mBitmap = BitmapFactory.decodeResource(mContext.getResources(),
						mResId, options);
			}
			return mBitmap;
		}
	}

	public synchronized void setLabelWidth(int width, int height) {
		if (mLabelWidth == width)
			return;
		if (AlbumSetTypeManager.get().getCurrentType()
				== FilterUtils.CLUSTER_BY_LIST) {
			width = width - height;
			mLabelWidth = width;
			int borders = 2 * BORDER_SIZE;
			mBitmapWidth = width + borders;
			mBitmapHeight = mSpec.labelBackgroundHeight*2 + borders;
		} else {
			mLabelWidth = width;
			int borders = 2 * BORDER_SIZE;
			mBitmapWidth = width + borders;
			mBitmapHeight = mSpec.labelBackgroundHeight + borders;
		}

	}

	public ThreadPool.Job<Bitmap> requestLabel(String title, String path, 
			String count, int sourceType, int type) {
		return new AlbumLabelJob(title, path, count, sourceType, type);
	}

	public ThreadPool.Job<Bitmap> requestLabel(String title, String path, 
            String count, String filePath, String fileDate, int sourceType, int type) {
        return new AlbumLabelJob(title, path, count, filePath, fileDate, sourceType, type);
    }

	static void drawText(Canvas canvas, int x, int y, String text,
			int lengthLimit, TextPaint p) {
		// The TextPaint cannot be used concurrently
		synchronized (p) {
			text = TextUtils.ellipsize(text, p, lengthLimit,
					TextUtils.TruncateAt.END).toString();
			canvas.drawText(text, x, y - p.getFontMetricsInt().ascent, p);
		}
	}

	private class AlbumLabelJob implements ThreadPool.Job<Bitmap> {
		private final String mFilePath;
		private final String mFileDate;
	    private final String mTitle;
		private final String mCount;
		private final int mSourceType;
		private final int mViewType;

		public AlbumLabelJob(String title, String path, String count, 
				int sourceType, int type) {
			this(title, path, count, "", "", sourceType, type);
		}

		public AlbumLabelJob(String title, String path, String count, String filePath, String fileDate,
                int sourceType, int type) {
		    mFilePath = (null == filePath) ? "" : filePath;
		    mFileDate = (null == fileDate) ? "" : fileDate;
		    mTitle = title;
            mCount = count;
            mSourceType = sourceType;
            mViewType = type;
        }

		@Override
		public Bitmap run(JobContext jc) {
			AlbumSetTypeSlotRenderer.LabelSpec s = mSpec;

			String filePath = mFilePath;
			String fileDate = mFileDate;
			String title = mTitle;
			String count = mCount;
			Bitmap icon = getOverlayAlbumIcon(mSourceType);

			Bitmap bitmap;
			int labelWidth;

			synchronized (this) {
				labelWidth = mLabelWidth;
				bitmap = GalleryBitmapPool.getInstance().get(mBitmapWidth,
						mBitmapHeight);
			}

			if (bitmap == null) {
				int borders = 2 * BORDER_SIZE;
				int height = s.labelBackgroundHeight + borders;
				if (mViewType == FilterUtils.CLUSTER_BY_LIST) {
				    height = mBitmapHeight;
				}
				bitmap = Bitmap.createBitmap(labelWidth + borders,
						height, Config.ARGB_8888);
			}

			Canvas canvas = new Canvas(bitmap);
			canvas.clipRect(BORDER_SIZE, BORDER_SIZE, bitmap.getWidth()
					- BORDER_SIZE, bitmap.getHeight() - BORDER_SIZE);
			canvas.translate(BORDER_SIZE, BORDER_SIZE);

			switch(mViewType) {
			    case FilterUtils.CLUSTER_BY_LIST:
			        renderByList(jc, s, canvas, title, count, filePath, fileDate, labelWidth);
			        break;
			    case FilterUtils.CLUSTER_BY_TIME:
			        renderByTime(jc, s, canvas, title, count, labelWidth);
			        break;
			    default:
			        renderByOther(jc, s, canvas, title, count, labelWidth, icon);
			        break;
			}
			return bitmap;
		}
	}

	/**
	 * render list view
	 * @param jc
	 * @param s
	 * @param canvas
	 * @param title
	 * @param count
	 * @param filePath
	 * @param fileDate
	 * @param labelWidth
	 */
	private void renderByList(JobContext jc, AlbumSetTypeSlotRenderer.LabelSpec s, Canvas canvas, String title, String count, String filePath, String fileDate, int labelWidth) {
	  //清除上次画布内容
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawPaint(paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));

        //draw title
        drawTitle(jc, s, canvas, title, labelWidth, 0);
        //draw count
        drawCount(jc, s, canvas, count, labelWidth);
        //draw filePath
        drawFilePath(jc, s, canvas, filePath, labelWidth);
        //draw fileDate
        drawFileDate(jc, s, canvas, fileDate, labelWidth);
	}

	/**
	 * render time view
	 * @param jc
	 * @param s
	 * @param canvas
	 * @param title
	 * @param count
	 * @param labelWidth
	 */
	private void renderByTime(JobContext jc, AlbumSetTypeSlotRenderer.LabelSpec s, Canvas canvas, String title, String count, int labelWidth) {
        //draw background
        canvas.drawColor(mSpec.backgroundColor, PorterDuff.Mode.SRC);
        // draw title
        drawTitle(jc, s, canvas, title, labelWidth, s.iconSize);
        // draw count
        drawCount(jc, s, canvas, count, labelWidth);
	}

	/**
	 * render other view (exclude time or list view)
	 * @param jc
	 * @param s
	 * @param canvas
	 * @param title
	 * @param count
	 * @param labelWidth
	 * @param icon
	 */
	private void renderByOther(JobContext jc, AlbumSetTypeSlotRenderer.LabelSpec s, Canvas canvas, String title, String count, int labelWidth, Bitmap icon) {
	  //draw background
        canvas.drawColor(mSpec.backgroundColor, PorterDuff.Mode.SRC);
        // draw title
        drawTitle(jc, s, canvas, title, labelWidth, s.iconSize);
        // draw count
        drawCount(jc, s, canvas, count, labelWidth);
        // draw the icon
        drawIcon(jc, s, canvas, icon);
	}

	/**
	 * draw title
	 * @param jc
	 * @param s
	 * @param canvas
	 * @param title
	 * @param labelWidth
	 * @param iconSize
	 */
	private void drawTitle(JobContext jc, AlbumSetTypeSlotRenderer.LabelSpec s, Canvas canvas, String title, int labelWidth, int iconSize) {
	    if (jc.isCancelled()) {
            return ;
	    }

	    int x = s.leftMargin + iconSize;
        int y = (s.labelBackgroundHeight - s.titleFontSize) / 2;
        drawText(canvas, x, y, title, labelWidth - s.leftMargin - x
                - s.titleRightMargin, mTitlePaint);
	}

	/**
	 * draw count
	 * @param jc
	 * @param s
	 * @param canvas
	 * @param count
	 * @param labelWidth
	 */
	private void drawCount(JobContext jc, AlbumSetTypeSlotRenderer.LabelSpec s, Canvas canvas, String count, int labelWidth) {
	    if (jc.isCancelled()) {
            return ;
        }

        int x = labelWidth - s.titleRightMargin;
        int y = (s.labelBackgroundHeight - s.countFontSize) / 2;
        drawText(canvas, x, y, count, labelWidth - x, mCountPaint);
	}

	/**
	 * draw icon
	 * @param jc
	 * @param s
	 * @param canvas
	 * @param icon
	 */
	private void drawIcon(JobContext jc, AlbumSetTypeSlotRenderer.LabelSpec s, Canvas canvas, Bitmap icon) {
	    if (jc.isCancelled()) {
            return ;
        }

	    float scale = (float) s.iconSize / icon.getWidth();
	    canvas.translate(s.leftMargin, (s.labelBackgroundHeight - Math.round(scale * icon.getHeight())) / 2f);
	    canvas.scale(scale, scale);
	    canvas.drawBitmap(icon, 0, 0, null);
	}

	/**
	 * draw filePath
	 * @param jc
	 * @param s
	 * @param canvas
	 * @param filePath
	 * @param labelWidth
	 */
	private void drawFilePath(JobContext jc, AlbumSetTypeSlotRenderer.LabelSpec s, Canvas canvas, String filePath, int labelWidth) {
	    if (jc.isCancelled()) {
            return ;
        }

	    int offset = (s.labelBackgroundHeight - s.titleFontSize) / 2;
        int lineHeight = (mBitmapHeight - offset - 2 * (offset + s.titleFontSize))/2;

        int x = s.leftMargin;
        int y = offset + s.titleFontSize + lineHeight;
        drawText(canvas, x, y, filePath, labelWidth - x, mTitlePaint);
	}

	/**
	 * draw fileDate
	 * @param jc
	 * @param s
	 * @param canvas
	 * @param fileDate
	 * @param labelWidth
	 */
	private void drawFileDate(JobContext jc, AlbumSetTypeSlotRenderer.LabelSpec s, Canvas canvas, String fileDate, int labelWidth) {
	    if (jc.isCancelled()) {
            return ;
        }

	    int offset = (s.labelBackgroundHeight - s.titleFontSize) / 2;
        int lineHeight = (mBitmapHeight - offset - 2 * (offset + s.titleFontSize))/2;

        int x = s.leftMargin;
        int y = offset + 2*(s.titleFontSize + lineHeight);
        drawText(canvas, x, y, fileDate, labelWidth - x, mTitlePaint);
	}

	public void recycleLabel(Bitmap label) {
		GalleryBitmapPool.getInstance().put(label);
	}
}

/*
 * Copyright © 2013 Michael von Glasow.
 * 
 * This file is part of LSRN Tools.
 *
 * LSRN Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSRN Tools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSRN Tools.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.vonglasow.michael.satstat.widgets;

import com.vonglasow.michael.satstat.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.GpsSatellite;
import android.util.AttributeSet;
import android.util.DisplayMetrics;

public class GpsStatusView extends SquareView {
	private float mYaw = 0, mPitch = 0, mRoll = 0;
	private float mRotation = 0;
	private int mW = 0;
	private int mH = 0;
	private Iterable<GpsSatellite> mSats;
	
	private Paint activePaint;
	private Paint inactivePaint;
	private Paint northPaint;
	private Paint gridPaint;
	private Paint gridBorderPaint;
	private Paint labelPaint;
	private Paint levellingPaint;
	private Path northArrow = new Path();
	private Path labelPathN = new Path();
	private Path labelPathE = new Path();
	private Path labelPathS = new Path();
	private Path labelPathW = new Path();

	
	private int gridStrokeWidth;
	private float snrScale;
	private float density;
	
	// Compensation for display rotation. Use Surface.ROTATION_* as index (0, 90, 180, 270 deg).
	@SuppressWarnings("boxing")
	private final static Integer zeroYaw[] = {0, 90, 180, 270};
	
	public GpsStatusView(Context context) {
		super(context);
		doInit(context);
	}

	public GpsStatusView(Context context, AttributeSet attrs) {
		super(context, attrs);
		doInit(context);
	}
	
	public GpsStatusView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		doInit(context);
	}
	
	private void doInit(Context context) {
		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		density = metrics.density;
		snrScale = 0.2f * density;
		gridStrokeWidth = Math.max(1, (int) (density));
		
		activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		activePaint.setColor(Color.parseColor("#FF80CBC4")); // Teal 200
		activePaint.setStyle(Paint.Style.FILL);
		
		inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		inactivePaint.setColor(Color.parseColor("#FFF44336")); // Red 500
		inactivePaint.setStyle(Paint.Style.FILL);
		
		gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		gridPaint.setColor(Color.parseColor("#FFFF9800")); // Orange 500
		gridPaint.setStyle(Paint.Style.STROKE);
		gridPaint.setStrokeWidth(gridStrokeWidth);
		
		gridBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		gridBorderPaint.setColor(Color.parseColor("#50FF9800")); // Orange 500 @ 30%
		gridBorderPaint.setStyle(Paint.Style.STROKE);

		levellingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		levellingPaint.setColor(Color.parseColor("#FFFFFF"));
		levellingPaint.setStyle(Paint.Style.FILL);

		northPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		northPaint.setColor(Color.parseColor("#FFF44336")); // Red 500
		northPaint.setStyle(Paint.Style.FILL);
		
		labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		labelPaint.setColor(Color.parseColor("#FFFF9800")); // Orange 500
		labelPaint.setStyle(Paint.Style.FILL);
		labelPaint.setTextAlign(Paint.Align.CENTER);
	}
	
	/*
	 * Draws a satellite in the sky grid.
	 */
	private void drawSat(Canvas canvas, int prn, float azimuth, float elevation, float snr, boolean used) {

		float r = (90 - elevation) * mW * 0.9f / 200;
		float x = (float) (r * Math.sin(azimuth * Math.PI / 180));
		float y = (float) -(r * Math.cos(azimuth * Math.PI / 180));
		
		canvas.drawCircle(x, y, snr * snrScale, used?activePaint:inactivePaint);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		final float outerSize = 0.405f;
		int cx = mW / 2;
		int cy = mH / 2;
		float levelX = mRoll / 90, levelY = mPitch / 90;
		float levelDistance, levelAngle;

		//Log.d("GpsStatusView", String.format("Drawing on a %dx%d canvas", w, h));

		canvas.translate(cx, cy);

		// Draw levelling circle in the background
		if (levelY > 1)
			levelY = 2 - levelY;
		if (levelY < -1)
			levelY = -2 - levelY;
		levelDistance = (float) Math.min(Math.sqrt(levelX * levelX + levelY * levelY), 1);
		if (levelX == 0) {
			// Quite improbable, but...
			if (levelY > 0)
				levelAngle = (float)Math.PI / 2f;
			else
				levelAngle = (float)Math.PI * 3f / 2f;
		} else {
			levelAngle = (float) Math.atan(levelY / levelX);
			if (levelX < 0)
				levelAngle += Math.PI;
			else if (levelY < 0)
				levelAngle += Math.PI * 2;
		}

		levellingPaint.setAlpha(
			(int)(16.0f + 80.0f * Math.pow(
				Math.max(1 - levelDistance, 1 - Math.min(Math.abs(levelY), Math.abs(levelX))), 3)
			)
		);
		canvas.drawCircle(
			levelDistance * (float)Math.cos(levelAngle) * mW * outerSize / 6f * 5f,
			levelDistance * (float)Math.sin(levelAngle) * mW * outerSize / 6f * 5f,
			mW * outerSize / 3f * (0.5f + (1 - levelDistance) * 0.5f), levellingPaint
		);

		// Draw the rest of the compass...
		canvas.rotate(-mRotation);

		canvas.drawCircle(0, 0, mW * outerSize / 1.091f, gridBorderPaint);

		canvas.drawLine(-mW * outerSize, 0, mW * outerSize, 0, gridPaint);
		canvas.drawLine(0, -mH * outerSize, 0, mH * outerSize, gridPaint);

		canvas.drawCircle(0,  0,  mW * outerSize, gridPaint);
		canvas.drawCircle(0,  0,  mW * outerSize / 1.5f, gridPaint);
		canvas.drawCircle(0,  0,  mW * outerSize / 3f, gridPaint);
		
		canvas.drawPath(northArrow, northPaint);
		
		canvas.drawTextOnPath(((Activity) getContext()).getString(R.string.value_N),
				labelPathN, 0, -labelPaint.descent(), labelPaint);

		canvas.drawTextOnPath(((Activity) getContext()).getString(R.string.value_S),
				labelPathS, 0, -labelPaint.descent(), labelPaint);

		canvas.drawTextOnPath(((Activity) getContext()).getString(R.string.value_E),
				labelPathE, 0, -labelPaint.descent(), labelPaint);

		canvas.drawTextOnPath(((Activity) getContext()).getString(R.string.value_W),
				labelPathW, 0, -labelPaint.descent(), labelPaint);

		if (mSats != null) {
			for (GpsSatellite sat : mSats) {
				drawSat(canvas, sat.getPrn(), sat.getAzimuth(), sat.getElevation(), sat.getSnr(), sat.usedInFix());
			}
		}
	}
	
	@Override
	protected void onSizeChanged (int w, int h, int oldw, int oldh) {
		mW = w;
		mH = h;
		refreshGeometries();
	}
	
	public void refreshGeometries() {
		gridBorderPaint.setStrokeWidth(mW * 0.0625f);
		
		float arrowWidth = 4 * density;
		
		northArrow.reset();
		northArrow.moveTo(-arrowWidth, - mH * 0.27f);
		northArrow.lineTo(arrowWidth, - mH * 0.27f);
		northArrow.lineTo(0, - mH * 0.405f - gridStrokeWidth * 2);
		northArrow.close();

		labelPaint.setTextSize(mH * 0.045f);
		
		float offsetX = mW * 0.0275f * (float) Math.cos(Math.toRadians(mRotation + 90));
		float offsetY = mW * 0.0275f * (float) Math.sin(Math.toRadians(mRotation + 90));
		float relX = mW * (float) Math.cos(Math.toRadians(mRotation));
		float relY = mH * (float) Math.sin(Math.toRadians(mRotation));
		
		labelPathN.reset();
		labelPathN.moveTo(offsetX - relX, - mH * 0.4275f + offsetY - relY);
		labelPathN.rLineTo(2 * relX, 2 * relY);
		
		labelPathE.reset();
		labelPathE.moveTo(mW * 0.4275f + offsetX - relX, offsetY - relY);
		labelPathE.rLineTo(2 * relX, 2 * relY);
		
		labelPathS.reset();
		labelPathS.moveTo(offsetX - relX, mH * 0.4275f + offsetY - relY);
		labelPathS.rLineTo(2 * relX, 2 * relY);
		
		labelPathW.reset();
		labelPathW.moveTo(- mW * 0.4275f + offsetX - relX, offsetY - relY);
		labelPathW.rLineTo(2 * relX, 2 * relY);
	}
	
	public void setOrientation(float yaw, float pitch, float roll) {
		mYaw = yaw;
		mRotation = mYaw + zeroYaw[((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation()];

		mPitch = pitch;
		mRoll = roll;
		refreshGeometries();
		invalidate();
	}
	
	public void showSats(Iterable<GpsSatellite> sats) {
		mSats = sats;
		invalidate();
	}
}

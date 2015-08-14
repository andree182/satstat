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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.GpsSatellite;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import com.vonglasow.michael.satstat.widgets.GpsSatelliteRender;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the signal-to-noise ratio of the GPS satellites in a bar chart.
 */
public class GpsSnrView extends View {
	final private String TAG = "GpsSnrView";

	private Iterable<GpsSatelliteRender> mSats;

	private Paint satBarPaint;
	private Paint gridPaint;
	private Paint gridPaintStrong;
	private Paint netLabelPaint;

	//FIXME: should be DPI-dependent, this is OK for MDPI
	private int gridStrokeWidth = 2;

	/**
	 * @param context ...
	 */
	public GpsSnrView(Context context) {
		super(context);
		doInit();
	}

	/**
	 * @param context ...
	 * @param attrs ...
	 */
	public GpsSnrView(Context context, AttributeSet attrs) {
		super(context, attrs);
		doInit();
	}

	/**
	 * @param context ...
	 * @param attrs ...
	 * @param defStyle ...
	 */
	public GpsSnrView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		doInit();
	}

	private void doInit() {
		satBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		satBarPaint.setStyle(Paint.Style.FILL);

		gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		gridPaint.setColor(Color.parseColor("#FF4D4D4D"));
		gridPaint.setStyle(Paint.Style.STROKE);
		gridPaint.setStrokeWidth(gridStrokeWidth);

		gridPaintStrong = new Paint(gridPaint);
		gridPaintStrong.setColor(Color.parseColor("#FFFFFFFF"));

		netLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		netLabelPaint.setColor(Color.parseColor("#B0FFFFFF"));
		netLabelPaint.setStyle(Paint.Style.FILL);
		netLabelPaint.setTextAlign(Paint.Align.LEFT);
	}

	/**
	 * Draws the grid lines - range boundaries and auxiliary lines.
	 */
	private void drawGrid(Canvas canvas) {
		//don't use Canvas.getWidth() and Canvas.getHeight() here, they may return incorrect values
		int w = getWidth();
		int h = getHeight();
		int curBar;
		int i;
		final int barGroup = 4;
		int lastGroupEnd = 0;
		float x;

		curBar = 0;
		for (GpsSatelliteRender.SatelliteType t: GpsSatelliteRender.satelliteTypes) {
			if (t.enabled) {
				x = (float) gridStrokeWidth / 2
						+ curBar * (w - gridStrokeWidth) / getNumBars();
				canvas.drawText(t.name, x + gridStrokeWidth * 2, netLabelPaint.getTextSize(), netLabelPaint);

				// start at next boundary
				i = barGroup - (curBar % barGroup);
				curBar += i;
				for (; i < t.max - t.min + 1; i += barGroup, curBar += barGroup) {
					x = (float) gridStrokeWidth / 2
							+ curBar * (w - gridStrokeWidth) / getNumBars();
					canvas.drawLine(x, 0, x, h, gridPaint);
				}

				lastGroupEnd += t.max - t.min + 1;
				x = (float) gridStrokeWidth / 2
						+ lastGroupEnd * (w - gridStrokeWidth) / getNumBars();
				canvas.drawLine(x, 0, x, h, gridPaintStrong);
			}
		}

		// left boundary
		canvas.drawLine((float) gridStrokeWidth / 2, 0, (float) gridStrokeWidth / 2, h, gridPaintStrong);

		// right boundary
		canvas.drawLine(w - (float) gridStrokeWidth / 2, h, w - (float) gridStrokeWidth / 2, 0, gridPaintStrong);
		
		// bottom line
		canvas.drawLine(0, h - (float) gridStrokeWidth / 2, w, h - (float) gridStrokeWidth / 2, gridPaintStrong);
	}
	
	/**
	 * Draws the SNR bar for a satellite.
	 * 
	 * @param canvas The {@code Canvas} on which the SNR view will appear.
	 * @param sat Satellite decription.
	 */
	private void drawSat(Canvas canvas, GpsSatelliteRender sat) {
		int w = getWidth();
		int h = getHeight();
		int nmeaID = sat.sat.getPrn();
		float snr = sat.sat.getSnr();
		boolean used = sat.sat.usedInFix();

		int i = getGridPos(nmeaID);

		int x0 = (i - 1) * (w - gridStrokeWidth) / getNumBars() + gridStrokeWidth / 2;
		int x1 = i * (w - gridStrokeWidth) / getNumBars() - gridStrokeWidth / 2;

		int y0 = h - gridStrokeWidth;
		int y1 = (int) (y0 * (1 - Math.min(snr, 60) / 60));

		satBarPaint.setColor(sat.type.color);
		satBarPaint.setAlpha(used ? 255 : 64);
		canvas.drawRect(x0, y1, x1, h, satBarPaint);
	}

	/**
	 * Returns the position of the SNR bar for a satellite in the grid.
	 * <p>
	 * This function returns the position at which the SNR bar for the
	 * satellite with the given {@code nmeaID} will appear in the grid, taking
	 * into account the visibility of NMEA ID ranges.
	 * 
	 * @param nmeaID The NMEA ID of the satellite, as returned by {@link android.location.GpsSatellite#getPrn()}.
	 * @return The position of the SNR bar in the grid. The position of the first visible bar is 1. If {@code nmeaID} falls within a hidden range, -1 is returned. 
	 */
	private int getGridPos(int nmeaID) {
		int skip = 0;

		if (nmeaID < 1)
			return -1;

		for (GpsSatelliteRender.SatelliteType t: GpsSatelliteRender.satelliteTypes) {
			if (nmeaID >= t.min && nmeaID <= t.max) {
				if (!t.enabled)
					return -1;
				else
					return nmeaID - skip;
			}
			if (!t.enabled)
				skip += t.max - t.min + 1;
		}

		return -1;
	}

	/**
	 * Returns the number of SNR bars to draw
	 *
	 * The number of bars to draw varies depending on the systems supported by
	 * the device. The most common numbers are 32 for a GPS-only receiver or 56
	 * for a combined GPS/GLONASS receiver.
	 * 
	 * @return The number of bars to draw
	 */
	private int getNumBars() {
		int no = 0;

		for (GpsSatelliteRender.SatelliteType t: GpsSatelliteRender.satelliteTypes) {
			if (t.enabled)
				no += t.max - t.min + 1;
		}
		return no;
	}

	/**
	 * Initializes the SNR grid.
	 * <p>
	 * This method iterates through {@link #mSats} to determine which ranges of
	 * NMEA IDs will be drawn. 
	 */
	protected void initializeGrid() {
		boolean someOn = false;
		// iterate through list to find out how many bars to draw
		if (mSats != null) {
			for (GpsSatelliteRender.SatelliteType t: GpsSatelliteRender.satelliteTypes)
				t.enabled = false;

			for (GpsSatelliteRender sat : mSats) {
				int prn = sat.sat.getPrn();
				boolean found = false;

				for (GpsSatelliteRender.SatelliteType t : GpsSatelliteRender.satelliteTypes) {
					if (prn >= t.min && prn <= t.max) {
						t.enabled = true;
						found = true;
					}
				}
				if (!found)
					Log.wtf(TAG, String.format("Got satellite with unknown NMEA ID %d", prn));
			}
		}

		/*
		 * If we didn't get any valid ranges, display at least the GPS range.
		 * No need to check for extended ranges here - if they get drawn, so
		 * will their corresponding base range.
		 */
		for (GpsSatelliteRender.SatelliteType t: GpsSatelliteRender.satelliteTypes) {
			someOn |= t.enabled;
		}

		if (!someOn)
			GpsSatelliteRender.satelliteTypes[0].enabled = true;
	}
	
	/**
	 * Redraws the SNR view.
	 * <p>
	 * This method is called whenever the view needs to be redrawn. Besides the
	 * usual cases of view creation/recreation, this also occurs when the
	 * {@link #showSats(Iterable)} has been called to indicate new SNR data is
	 * available.
	 */
	@Override
	protected void onDraw(Canvas canvas) {
		initializeGrid();
		
		// draw the SNR bars
		if (mSats != null)
			for (GpsSatelliteRender sat : mSats)
				drawSat(canvas, sat);
		
		// draw the grid on top
		drawGrid(canvas);
	}

	@Override
	protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
		int mHeight = (int) Math.min(MeasureSpec.getSize(widthMeasureSpec) * 0.15f, MeasureSpec.getSize(heightMeasureSpec));
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mHeight);
	}

	/**
	 * Refreshes the SNR view with current data.
	 * <p>
	 * Call this method when new SNR data is available. It will update the SNR
	 * view's internal list of {@code GpsSatellite}s and trigger a redraw.
	 * 
	 * @param sats A list of satellites currently in view.
	 */
	public void showSats(Iterable<GpsSatellite> sats) {
		List<GpsSatelliteRender> l = new ArrayList<GpsSatelliteRender>();
		for (GpsSatellite s: sats)
			l.add(new GpsSatelliteRender(s));
		mSats = l;
		invalidate();
	}
}

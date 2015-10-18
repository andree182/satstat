/*
 * Sunclock code by John Mackin.
 * kdewatch code by Stephan Kulow <coolo@kde.org>
 *
 * Copyright © 2015 Andrej Krutak.
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

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.location.Location;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

public class SunMapView extends ImageView {
    private Bitmap overlay;

    private static class Astro {
        /*  JDATE  --  Convert internal GMT date and time to Julian day
	       and fraction.  */
        public static long jdate(Calendar t)
        {
            long c, m, y;

            // TODO: check this has the same meaning as time/tm unix stuff
            y = t.get(Calendar.YEAR);
            m = t.get(Calendar.MONTH) + 1;
            if (m > 2)
                m = m - 3;
            else {
                m = m + 9;
                y--;
            }
            c = y / 100L;		   /* Compute century */
            y -= 100L * c;
            return t.get(Calendar.DAY_OF_MONTH) + (c * 146097L) / 4 + (y * 1461L) / 4 +
                    (m * 153L + 2) / 5 + 1721119L;
        }

        /* JTIME --    Convert internal GMT  date  and	time  to  astronomical
	       Julian  time  (i.e.   Julian  date  plus  day fraction,
	       expressed as a double).	*/
        public static double jtime(Calendar t) {
            return
                    (jdate(t) - 0.5) +
                    (((long) t.get(Calendar.SECOND)) +
                            60L * (t.get(Calendar.MINUTE) + 60L * t.get(Calendar.HOUR_OF_DAY))) / 86400.0;
        }

        /*  KEPLER  --	Solve the equation of Kepler.  */
        public static double kepler(double m, double ecc) {
            double e, delta;
            final double EPSILON = 1E-6;

            e = m = Math.toRadians(m);
            do {
                delta = e - ecc * Math.sin(e) - m;
                e -= delta / (1 - ecc * Math.cos(e));
            } while (Math.abs(delta) > EPSILON);
            return e;
        }

        public static double fixangle(double a) {
            return ((a) - 360.0 * (Math.floor((a) / 360.0)));
        }

        public static class SunPos {
            public double ra;
            public double dec;
            public double rv;
            public double slong;
        }

        /*  SUNPOS  --	Calculate position of the Sun.	JD is the Julian  date
		of  the  instant for which the position is desired and
		APPARENT should be nonzero if  the  apparent  position
		(corrected  for  nutation  and aberration) is desired.
                The Sun's co-ordinates are returned  in  RA  and  DEC,
		both  specified  in degrees (divide RA by 15 to obtain
		hours). Also suns longitude is returned. */
        public static SunPos sunpos(double jd, boolean apparent)
        {
            double t, t2, t3, l, m, e, ea, v, theta, omega,
                    eps;
            SunPos r = new Astro.SunPos();

            /* Time, in Julian centuries of 36525 ephemeris days,
               measured from the epoch 1900 January 0.5 ET. */

            t = (jd - 2415020.0) / 36525.0;
            t2 = t * t;
            t3 = t2 * t;

            /* Geometric mean longitude of the Sun, referred to the
               mean equinox of the date. */

            l = fixangle(279.69668 + 36000.76892 * t + 0.0003025 * t2);

            /* Sun's mean anomaly. */

            m = fixangle(358.47583 + 35999.04975*t - 0.000150*t2 - 0.0000033*t3);

            /* Eccentricity of the Earth's orbit. */

            e = 0.01675104 - 0.0000418 * t - 0.000000126 * t2;

	        /* Eccentric anomaly. */

            ea = kepler(m, e);

	        /* True anomaly */

            v = fixangle(2 * Math.toDegrees(Math.atan(Math.sqrt((1 + e) / (1 - e)) * Math.tan(ea / 2))));

            /* Sun's true longitude. */

            theta = l + v - m;

	        /* Obliquity of the ecliptic. */

            eps = 23.452294 - 0.0130125 * t - 0.00000164 * t2 + 0.000000503 * t3;

            /* Corrections for Sun's apparent longitude, if desired. */

            if (apparent) {
                omega = fixangle(259.18 - 1934.142 * t);
                theta = theta - 0.00569 - 0.00479 * Math.sin(Math.toRadians(omega));
                eps += 0.00256 * Math.cos(Math.toRadians(omega));
            }

            /* Return Sun's radius vector */

            r.rv = (1.0000002 * (1 - e * e)) / (1 + e * Math.cos(Math.toRadians(v)));

	        /* Determine solar co-ordinates. */

            r.ra = fixangle(
                Math.toDegrees(Math.atan2(
                    Math.cos(Math.toRadians(eps)) * Math.sin(Math.toRadians(theta)),
                    Math.cos(Math.toRadians(theta))
                ))
            );
            r.dec = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(eps)) * Math.sin(Math.toRadians(theta))));

            double gt;
            gt = gmst(jd);
            r.slong = fixangle(180.0 + (r.ra - (gt * 15)));

            return r;
        }

        /*  GMST  --  Calculate Greenwich Mean Siderial Time for a given
	      instant expressed as a Julian date and fraction.	*/
        public static double gmst(double jd)
        {
            double t, theta0;

            /* Time, in Julian centuries of 36525 ephemeris days,
               measured from the epoch 1900 January 0.5 ET. */

            t = ((Math.floor(jd + 0.5) - 0.5) - 2415020.0) / 36525.0;

            theta0 = 6.6460656 + 2400.051262 * t + 0.00002581 * t * t;

            t = (jd + 0.5) - (Math.floor(jd + 0.5));

            theta0 += (t * 24.0) * 1.002737908;

            theta0 = (theta0 - 24.0 * (Math.floor(theta0 / 24.0)));

            return theta0;
        }

        /*  PROJILLUM  --  Project illuminated area on the map.  */
        public static void projillum(int wtab[], int xdots, int ydots, double dec)
        {
            boolean ftf = true;
            int i, ilon, ilat, lilon = 0, lilat = 0, xt;
            double m, x, y, z, th, lon, lat, s, c;
            final double TERMINC = 100; /* Circle segments for terminator */

        	/* Clear unoccupied cells in width table */
            for (i = 0; i < ydots; i++)
                wtab[i] = -1;

	        /* Build transformation for declination */
            s = Math.sin(-Math.toRadians(dec));
            c = Math.cos(-Math.toRadians(dec));

	        /* Increment over a semicircle of illumination */
            for (th = -(Math.PI / 2); th <= Math.PI / 2 + 0.001;
                 th += Math.PI / TERMINC) {

		        /* Transform the point through the declination rotation. */
                x = -s * Math.sin(th);
                y = Math.cos(th);
                z = c * Math.sin(th);

                /* Transform the resulting co-ordinate through the
                   map projection to obtain screen co-ordinates. */
                lon = (y == 0 && x == 0) ? 0.0 : Math.toDegrees(Math.atan2(y, x));
                lat = Math.toDegrees(Math.asin(z));

                ilat = (int)(ydots - (lat + 90) * (ydots / 180.0));
                ilon = (int)(lon * (xdots / 360.0));

                if (ftf) {
        			/* First time.  Just save start co-ordinate. */

                    lilon = ilon;
                    lilat = ilat;
                    ftf = false;
                } else {
	    		    /* Trace out the line and set the width table. */

                    if (lilat == ilat) {
                        wtab[(ydots - 1) - ilat] = ilon == 0 ? 1 : ilon;
                    } else {
                        m = ((double) (ilon - lilon)) / (ilat - lilat);
                        for (i = lilat; i != ilat; i += Math.signum(ilat - lilat)) {
                            xt = (int)(lilon + Math.floor((m * (i - lilat)) + 0.5));
                            wtab[(ydots - 1) - i] = xt == 0 ? 1 : xt;
                        }
                    }
                    lilon = ilon;
                    lilat = ilat;
                }
            }

            /* Now tweak the widths to generate full illumination for
               the correct pole. */
            if (dec < 0.0) {
                ilat = ydots - 1;
                lilat = -1;
            } else {
                ilat = 0;
                lilat = 1;
            }

            for (i = ilat; i != ydots / 2; i += lilat) {
                if (wtab[i] != -1) {
                    while (true) {
                        wtab[i] = xdots / 2;
                        if (i == ilat)
                            break;
                        i -= lilat;
                    }
                    break;
                }
            }
        }
    }

    private Astro.SunPos sp;
    private boolean gotLocation;
    private Location location;

    public SunMapView(Context context) {
        super(context);
        doInit();
    }

    public SunMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        doInit();
    }

    public SunMapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        doInit();
    }

    private void doInit() {
        if (this.getHeight() == 0)
            overlay = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888);
        else
            overlay = Bitmap.createBitmap(this.getWidth(), this.getHeight(), Bitmap.Config.ARGB_8888);

        gotLocation = false;
        updateData();
    }

    public void updateData() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        double jt = Astro.jtime(cal);
        sp = Astro.sunpos(jt, false);
        int width = overlay.getWidth();
        int height = overlay.getHeight();
        Canvas c = new Canvas(overlay);

        int sec = cal.get(Calendar.HOUR_OF_DAY)*60*60 + cal.get(Calendar.MINUTE)*60 + cal.get(Calendar.SECOND);
        int gmt_position = width * sec / 86400; // note: greenwich is in the middle!

        int wtab[] = new int[overlay.getHeight()];

        Astro.projillum(wtab, width, height, sp.dec);

        overlay.eraseColor(Color.argb(255, 128, 128, 128));

        Paint p = new Paint();
        p.setColor(Color.argb(255, 255, 255, 255));

        int start, stop;
        int middle = width - gmt_position;
        for (int y=0; y<height; y++) {
            if (wtab[y] > 0) {
                start = middle - wtab[y];
                stop = middle + wtab[y];
                if (start < 0) {
                    c.drawLine(0, y, stop, y, p);
                    c.drawLine(width + start, y, width, y, p);
                } else if (stop > width) {
                    c.drawLine(start, y, width, y, p);
                    c.drawLine(0, y, stop - width, y, p);
                } else
                    c.drawLine(start, y, stop, y, p);
            }
        }

        this.invalidate();
    }

    public void setLocation(Location l) {
        gotLocation = true;
        location = l;
        updateData();
    }

    private float Lon360ToX(float l) {
        return l * overlay.getWidth() / 360;
    }
    private float Lat180ToY(float l) {
        return (180 - l) * overlay.getHeight() / 180;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint p = new Paint();
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        canvas.drawBitmap(overlay, 0, 0, p);

        /* Sun position */
        Paint pSun = new Paint();
        pSun.setColor(Color.YELLOW);
        pSun.setAntiAlias(true);
        pSun.setAlpha(128);
        canvas.drawCircle(
                Lon360ToX((float) sp.slong), Lat180ToY((float) (90 + sp.dec)),
                overlay.getHeight() / 35,
                pSun
        );

        if (gotLocation) {
            /* Our position */
            Paint pPos = new Paint();
            long crossSize = overlay.getWidth() / 50;
            pPos.setColor(Color.RED);
            pPos.setAntiAlias(true);
            pPos.setAlpha(200);
            pPos.setStyle(Paint.Style.STROKE);
            float lx = Lon360ToX(180 + (float) location.getLongitude());
            float ly = Lat180ToY(90 + (float) location.getLatitude());
            canvas.drawLine(lx - crossSize, ly, lx + crossSize, ly, pPos);
            canvas.drawLine(lx, ly - crossSize, lx, ly + crossSize, pPos);
            canvas.drawCircle(lx, ly, crossSize, pPos);
        }

        // TODO: We could paint the satellites here
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        this.setMeasuredDimension(parentWidth, parentWidth / 2);
    }

    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld) {
        super.onSizeChanged(xNew, yNew, xOld, yOld);
        doInit();
    }
}

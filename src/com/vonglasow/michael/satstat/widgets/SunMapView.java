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

import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.Calendar;

import com.vonglasow.michael.satstat.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.GpsSatellite;
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
		hours).  The radius vector to the Sun in  astronomical
                units  is returned in RV and the Sun's longitude (true
		or apparent, as desired) is  returned  as  degrees  in
		SLONG.	*/
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

            /* Return Sun's longitude and radius vector */

            r.slong = theta;
            r.rv = (1.0000002 * (1 - e * e)) / (1 + e * Math.cos(Math.toRadians(v)));

	        /* Determine solar co-ordinates. */

            r.ra = fixangle(
                Math.toDegrees(Math.atan2(
                    Math.cos(Math.toRadians(eps)) * Math.sin(Math.toRadians(theta)),
                    Math.cos(Math.toRadians(theta))
                ))
            );
            r.dec = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(eps)) * Math.sin(Math.toRadians(theta))));

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

    }

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
        Canvas c = new Canvas(overlay);
        c.drawColor(Color.argb(128, 0, 0, 0));

        prepareMask();
    }

    private void prepareMask() {
        Calendar c = Calendar.getInstance();
        double jt = Astro.jtime(c);
        Astro.SunPos sp = Astro.sunpos(jt, false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawBitmap(overlay, 0, 0, null);
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

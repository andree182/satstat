package com.vonglasow.michael.satstat.widgets;

import android.graphics.Color;
import android.location.GpsSatellite;

/**
 * Cache of the render parameters (esp. color) of a satellite (either in snr or status view)
 */
public class GpsSatelliteRender {
    public static class SatelliteType {
        public boolean enabled;
        public String name;
        public int min, max;
        public int color;

        public SatelliteType(boolean _enabled, String _name, int _min, int _max, int _color) {
            enabled = _enabled;
            name = _name;
            min = _min;
            max = _max;
            color = _color;
        }
    }

    public static SatelliteType satelliteTypes[] = {
        new SatelliteType(true, "GPS", 1, 32, Color.parseColor("#4ec95f")), // GPS
        new SatelliteType(true, "SBAS", 33, 54, Color.parseColor("#ff7cf1")), // Various SBAS systems (EGNOS, WAAS, SDCM, GAGAN, MSAS) – some IDs still unused
        new SatelliteType(false, "SBAS", 55, 64, Color.parseColor("#ff7cf1")), // not used (might be assigned to further SBAS systems)
        new SatelliteType(true, "GLONASS", 65, 88, Color.parseColor("#4ebaff")), // GLONASS
        new SatelliteType(true, "GLONASS", 89, 96, Color.parseColor("#4ebaff")), // GLONASS (future extensions?)
        new SatelliteType(false, "", 97, 192, Color.parseColor("#cccccc")), // not used; TODO: do we really want to enable this huge 96-sat block?
        new SatelliteType(true, "QZSS", 193, 195, Color.parseColor("#f1ff54")), // QZSS
        new SatelliteType(true, "QZSS", 196, 200, Color.parseColor("#f1ff54")), // QZSS (future extensions?)
        new SatelliteType(true, "Beidou", 201, 235, Color.parseColor("#ff4444")), // Beidou
    };

    public GpsSatellite sat;
    public SatelliteType type;
    public int pos;

    GpsSatelliteRender(GpsSatellite _sat) {
        int prn;
        sat = _sat;

        prn = sat.getPrn();
        for (SatelliteType t: satelliteTypes) {
            if (prn >= t.min && prn <= t.max) {
                type = t;
                break;
            }
        }
    }
}

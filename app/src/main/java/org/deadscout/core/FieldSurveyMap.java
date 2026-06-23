package org.deadscout.core;

import java.util.ArrayList;
import java.util.List;

public final class FieldSurveyMap {
    public final List<Point> points = new ArrayList<>();
    public String summary() { return "No public survey points in this build."; }
    public Point strongest() { return points.isEmpty() ? null : points.get(0); }
    public String toCsv() { return ""; }
    public String toGeoJson() { return "{}"; }
    public static FieldSurveyMap empty() { return new FieldSurveyMap(); }
    public static final class Point {
        public final long timeMillis; public final double lat; public final double lon; public final String label; public final String protocol; public final long frequencyHz; public final int levelDbm;
        public Point(long timeMillis,double lat,double lon,String label,String protocol,long frequencyHz,int levelDbm){this.timeMillis=timeMillis;this.lat=lat;this.lon=lon;this.label=label;this.protocol=protocol;this.frequencyHz=frequencyHz;this.levelDbm=levelDbm;}
        public String display(){return label+" · "+protocol+" · "+levelDbm+" dBm";}
    }
}

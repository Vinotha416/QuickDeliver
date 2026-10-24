package com.quickdeliver.common;

/** Lenient number/boolean coercion (Mongoose used to cast "12" -> 12 for us). */
public final class Num {
    private Num() { }

    /** 1250.0 -> 1250 (long), 12.5 -> 12.5 so JSON looks like what JavaScript produced. */
    public static Number clean(double d) {
        if (!Double.isInfinite(d) && !Double.isNaN(d) && d == Math.rint(d) && Math.abs(d) < 9.0e15) {
            return Long.valueOf((long) d);
        }
        return Double.valueOf(d);
    }

    public static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.valueOf(o.toString().trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Invalid number: " + o);
        }
    }

    public static Integer toInt(Object o) {
        Double d = toDouble(o);
        return d == null ? null : Integer.valueOf((int) Math.round(d));
    }

    public static Boolean toBool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        return Boolean.valueOf(o.toString().trim());
    }

    public static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

package org.owl.flux.util;

public final class OrdinalFormatter {
    private OrdinalFormatter() {
    }

    public static String offenseLabel(int offenseStep) {
        if (offenseStep <= 0) {
            return "N/A";
        }
        return ordinal(offenseStep) + " offense";
    }

    public static String ordinal(int value) {
        if (value <= 0) {
            return "0th";
        }

        int mod100 = value % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return value + "th";
        }

        return switch (value % 10) {
            case 1 -> value + "st";
            case 2 -> value + "nd";
            case 3 -> value + "rd";
            default -> value + "th";
        };
    }
}

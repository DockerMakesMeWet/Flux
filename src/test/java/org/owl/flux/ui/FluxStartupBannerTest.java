package org.owl.flux.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class FluxStartupBannerTest {
    private static final String[] ASCII_ART = new String[]{
            "______ _             ",
            "|  ____| |            ",
            "| |__  | |_   ___  __ ",
            "|  __| | | | | \\ \\/ / ",
            "| |    | | |_| |>  <  ",
            "|_|    |_|\\__,_/_/\\_\\ "
    };
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[;\\d]*m");

    @Test
    void sendsStructuredCenteredBannerToLogger() {
        System.setProperty("flux.console.width", "110");
        Logger logger = mock(Logger.class);
        try {
            FluxStartupBanner.sendBanner(logger, "1.2.3");
        } finally {
            System.clearProperty("flux.console.width");
        }

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(11)).info(captor.capture());

        List<String> loggedLines = captor.getAllValues();
        List<String> plainLines = loggedLines.stream().map(FluxStartupBannerTest::stripAnsi).toList();
        List<String> expected = buildExpected("1.2.3");

        assertEquals(expected, plainLines);
    }

    private static List<String> buildExpected(String version) {
        int width = maxWidth(ASCII_ART);
        int leftPad = Math.max(0, (110 - width) / 2);
        String separator = "-".repeat(width);

        List<String> expected = new ArrayList<>();
        expected.add(" ".repeat(leftPad) + separator);
        for (String artLine : ASCII_ART) {
            expected.add(" ".repeat(leftPad) + artLine);
        }

        expected.add(centerLine("Flux", leftPad, width));
        expected.add(centerLine("Sanctions, done right.", leftPad, width));
        expected.add(centerLine("Version: " + version, leftPad, width));
        expected.add(" ".repeat(leftPad) + separator);
        return expected;
    }

    private static String centerLine(String line, int leftPad, int width) {
        int linePad = leftPad + Math.max(0, (width - line.length()) / 2);
        return " ".repeat(linePad) + line;
    }

    private static int maxWidth(String[] lines) {
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, line.length());
        }
        return max;
    }

    private static String stripAnsi(String input) {
        return ANSI_PATTERN.matcher(input).replaceAll("");
    }
}

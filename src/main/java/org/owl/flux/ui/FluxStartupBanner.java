package org.owl.flux.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import org.slf4j.Logger;

public final class FluxStartupBanner {
    private static final int DEFAULT_CONSOLE_WIDTH = 110;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final ANSIComponentSerializer ANSI_SERIALIZER = ANSIComponentSerializer.ansi();
    private static final String[] ASCII_ART = new String[]{
            "______ _             ",
            "|  ____| |            ",
            "| |__  | |_   ___  __ ",
            "|  __| | | | | \\ \\/ / ",
            "| |    | | |_| |>  <  ",
            "|_|    |_|\\__,_/_/\\_\\ "
    };

    private FluxStartupBanner() {
    }

    public static void sendBanner(Logger logger, String version) {
        int artWidth = maxWidth(ASCII_ART);
        int leftPad = Math.max(0, (resolveConsoleWidth() - artWidth) / 2);
        String separator = "-".repeat(artWidth);

        logGradientLine(logger, leftPad, separator);
        for (String line : ASCII_ART) {
            logGradientLine(logger, leftPad, line);
        }

        logCenteredLine(logger, leftPad, artWidth, "Flux");
        logCenteredLine(logger, leftPad, artWidth, "Sanctions, done right.");
        logCenteredLine(logger, leftPad, artWidth, "Version: " + version);

        logGradientLine(logger, leftPad, separator);
    }

    private static void logCenteredLine(Logger logger, int leftPad, int width, String line) {
        int linePad = leftPad + Math.max(0, (width - line.length()) / 2);
        logGradientLine(logger, linePad, line);
    }

    private static void logGradientLine(Logger logger, int pad, String line) {
        String escaped = MINI_MESSAGE.escapeTags(line);
        Component gradient = MINI_MESSAGE.deserialize("<gradient:#00FBFF:#9D00FF>" + escaped + "</gradient>");
        Component component = Component.text(" ".repeat(Math.max(0, pad))).append(gradient);
        logger.info(ANSI_SERIALIZER.serialize(component));
    }

    private static int resolveConsoleWidth() {
        int configuredWidth = Integer.getInteger("flux.console.width", -1);
        if (configuredWidth > 0) {
            return configuredWidth;
        }

        String columns = System.getenv("COLUMNS");
        if (columns == null || columns.isBlank()) {
            return DEFAULT_CONSOLE_WIDTH;
        }

        try {
            int parsed = Integer.parseInt(columns);
            return parsed > 0 ? parsed : DEFAULT_CONSOLE_WIDTH;
        } catch (NumberFormatException ignored) {
            return DEFAULT_CONSOLE_WIDTH;
        }
    }

    private static int maxWidth(String[] lines) {
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, line.length());
        }
        return max;
    }
}

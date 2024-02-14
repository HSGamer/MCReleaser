package me.hsgamer.mcreleaser.core.util;

import me.hsgamer.hscore.logger.common.LogLevel;
import me.hsgamer.hscore.logger.provider.LoggerProvider;
import org.apache.tika.Tika;

import java.io.File;
import java.io.IOException;

public class MimeDetector {
    private static final Tika TIKA = new Tika();

    public static String getType(File file) {
        try {
            return TIKA.detect(file);
        } catch (IOException e) {
            LoggerProvider.getLogger(MimeDetector.class).log(LogLevel.WARN, "Error while detecting the mime type. Will return the binary stream type", e);
            return "application/octet-stream";
        }
    }
}

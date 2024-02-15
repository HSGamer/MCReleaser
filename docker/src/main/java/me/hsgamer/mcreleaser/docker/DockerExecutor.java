package me.hsgamer.mcreleaser.docker;

import me.hsgamer.hscore.logger.provider.LoggerProvider;
import me.hsgamer.hscore.logger.system.SystemLogger;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.mcreleaser.bundle.BundlePlatform;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PathUtil;
import me.hsgamer.mcreleaser.core.util.Validate;

import java.nio.file.Paths;
import java.util.Optional;

public class DockerExecutor {
    static {
        LoggerProvider.setLoggerProvider(SystemLogger::new);
    }

    public static void main(String[] args) {
        String platforms = DockerPropertyKey.PLATFORMS.getValue("all");
        boolean runSync = DockerPropertyKey.SYNC.asBoolean(false);

        BundlePlatform bundlePlatform = new BundlePlatform(platforms, runSync);

        CommonPropertyKey.checkPresent();

        FileBundle fileBundle = getFileBundle();
        Optional<BatchRunnable> optional = bundlePlatform.createUploadRunnable(fileBundle);
        if (optional.isEmpty()) {
            throw new RuntimeException("No platform found");
        }

        Thread thread = new Thread(optional.get());
        thread.start();
    }

    private static FileBundle getFileBundle() {
        String fileGlobs = DockerPropertyKey.FILES.getValue();
        Validate.check(fileGlobs != null, "File globs not found");
        return PathUtil.getFileBundle(Paths.get("."), fileGlobs.split("\\s+"));
    }
}
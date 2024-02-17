package me.hsgamer.mcreleaser.cli;

import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.mcreleaser.bundle.BundlePlatform;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PathUtil;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import me.hsgamer.mcreleaser.core.util.StringUtil;
import me.hsgamer.mcreleaser.core.util.Validate;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Optional;

public class CLIExecutor {
    public static void main(String[] args) {
        String platforms = CLIPropertyKey.PLATFORMS.getValue("all");
        boolean runSync = CLIPropertyKey.SYNC.asBoolean(false);

        BundlePlatform bundlePlatform = new BundlePlatform(platforms, runSync);

        if (PropertyKeyUtil.isAbsentAndAnnounce(LoggerFactory.getLogger(CLIExecutor.class), CommonPropertyKey.NAME, CommonPropertyKey.VERSION, CommonPropertyKey.DESCRIPTION)) {
            return;
        }

        FileBundle fileBundle = getFileBundle();
        Optional<BatchRunnable> optional = bundlePlatform.createUploadRunnable(fileBundle);
        if (optional.isEmpty()) {
            throw new RuntimeException("No platform found");
        }

        Thread thread = new Thread(optional.get());
        thread.start();
    }

    private static FileBundle getFileBundle() {
        String fileGlobs = CLIPropertyKey.FILES.getValue();
        Validate.check(fileGlobs != null, "File globs not found");
        return PathUtil.getFileBundle(Paths.get("."), StringUtil.splitSpace(fileGlobs));
    }
}
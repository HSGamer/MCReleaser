package me.hsgamer.mcreleaser.docker;

import me.hsgamer.hscore.logger.provider.LoggerProvider;
import me.hsgamer.hscore.logger.system.SystemLogger;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.Validate;
import me.hsgamer.mcreleaser.github.GithubPlatform;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class DockerExecutor {
    private static final List<Platform> PLATFORMS = List.of(
            new GithubPlatform()
    );

    static {
        LoggerProvider.setLoggerProvider(SystemLogger::new);
    }

    public static void main(String[] args) {
        CommonPropertyKey.checkPresent();

        FileBundle fileBundle = getFileBundle();

        List<BatchRunnable> runnableList = PLATFORMS.stream()
                .map(platform -> platform.createUploadRunnable(fileBundle))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        if (DockerPropertyKey.SYNC.asBoolean(false)) {
            BatchRunnable allRunnable = new BatchRunnable();
            TaskPool taskPool = allRunnable.getTaskPool(0);
            runnableList.forEach(taskPool::addLast);
            Thread thread = new Thread(allRunnable);
            thread.start();
        } else {
            runnableList.forEach(runnable -> {
                Thread thread = new Thread(runnable);
                thread.start();
            });
        }
    }

    private static FileBundle getFileBundle() {
        File primaryDir = new File("primary");
        Validate.check(primaryDir.exists(), "'primary' does not exist");

        File secondaryDir = new File("secondary");
        Validate.check(secondaryDir.exists(), "'secondary' does not exist");

        File[] primaryFiles = primaryDir.listFiles();
        Validate.check(primaryFiles != null && primaryFiles.length > 0, "Files in directory 'primary' is null or empty");

        File primaryFile = primaryFiles[0];
        File[] secondaryFiles = secondaryDir.listFiles();
        Validate.check(secondaryFiles != null, "Files in directory 'secondary' is null. That's weird");

        return new FileBundle(primaryFile, List.of(secondaryFiles));
    }
}
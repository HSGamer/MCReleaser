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
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

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
        String primaryGlob = DockerPropertyKey.PRIMARY_GLOB.getValue();
        Validate.check(primaryGlob != null && !primaryGlob.isEmpty(), "Primary glob is empty");

        String secondaryGlob = DockerPropertyKey.SECONDARY_GLOB.getValue("");

        System.out.println("Primary glob: " + primaryGlob);
        System.out.println("Secondary glob: " + secondaryGlob);

        PathMatcher primaryMatcher = FileSystems.getDefault().getPathMatcher("glob:" + primaryGlob);
        PathMatcher secondaryMatcher = FileSystems.getDefault().getPathMatcher("glob:" + secondaryGlob);

        AtomicReference<File> primaryFileRef = new AtomicReference<>();
        List<File> secondaryFiles = new ArrayList<>();
        try (Stream<Path> pathStream = Files.walk(new File(".").toPath())) {
            pathStream
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        System.out.println(path.toString());
                        if (primaryMatcher.matches(path)) {
                            primaryFileRef.set(path.toFile());
                        } else if (secondaryMatcher.matches(path)) {
                            secondaryFiles.add(path.toFile());
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        File primaryFile = primaryFileRef.get();
        Validate.check(primaryFile != null, "Primary file not found");

        return new FileBundle(primaryFile, secondaryFiles);
    }
}
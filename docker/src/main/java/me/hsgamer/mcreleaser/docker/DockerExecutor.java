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
import java.nio.file.*;
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
        String fileGlobs = DockerPropertyKey.FILES.getValue();
        String[] globs = fileGlobs.split("\\s+");
        PathMatcher primaryMatcher = null;
        List<PathMatcher> secondaryMatchers = new ArrayList<>();
        for (String glob : globs) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            if (primaryMatcher == null) {
                primaryMatcher = matcher;
            } else {
                secondaryMatchers.add(matcher);
            }
        }

        Validate.check(primaryMatcher != null, "Primary glob is empty");

        AtomicReference<File> primaryFileRef = new AtomicReference<>();
        List<File> secondaryFiles = new ArrayList<>();
        Path currentPath = Paths.get(".");
        try (Stream<Path> pathStream = Files.walk(currentPath)) {
            PathMatcher finalPrimaryMatcher = primaryMatcher;
            pathStream
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        Path relativePath = currentPath.relativize(path);
                        if (finalPrimaryMatcher.matches(relativePath)) {
                            primaryFileRef.set(path.toFile());
                        } else {
                            for (PathMatcher secondaryMatcher : secondaryMatchers) {
                                if (secondaryMatcher.matches(relativePath)) {
                                    secondaryFiles.add(path.toFile());
                                    break;
                                }
                            }
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
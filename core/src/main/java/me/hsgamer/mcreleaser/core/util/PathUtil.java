package me.hsgamer.mcreleaser.core.util;

import me.hsgamer.mcreleaser.core.file.FileBundle;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class PathUtil {
    public static FileBundle getFileBundle(Path currentPath, PathMatcher primaryFileMatcher, List<PathMatcher> secondaryFileMatcher) {
        AtomicReference<File> primaryFileRef = new AtomicReference<>();
        List<File> secondaryFiles = new ArrayList<>();
        try (Stream<Path> pathStream = Files.walk(currentPath)) {
            pathStream
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        File file = path.toFile();
                        Path relativePath = currentPath.relativize(path);

                        if (primaryFileMatcher.matches(relativePath)) {
                            if (primaryFileRef.get() == null || !secondaryFileMatcher.isEmpty()) {
                                primaryFileRef.set(file);
                            } else {
                                secondaryFiles.add(file);
                            }
                        }

                        for (PathMatcher matcher : secondaryFileMatcher) {
                            if (matcher.matches(relativePath)) {
                                secondaryFiles.add(file);
                                return;
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

    public static FileBundle getFileBundle(Path currentPath, List<String> fileGlobs) {
        PathMatcher primaryMatcher = null;
        List<PathMatcher> secondaryMatchers = new ArrayList<>();
        for (String glob : fileGlobs) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            if (primaryMatcher == null) {
                primaryMatcher = matcher;
            } else {
                secondaryMatchers.add(matcher);
            }
        }

        Validate.check(primaryMatcher != null, "Primary glob is empty");

        return getFileBundle(currentPath, primaryMatcher, secondaryMatchers);
    }

    public static FileBundle getFileBundle(Path currentPath, String... fileGlobs) {
        return getFileBundle(currentPath, List.of(fileGlobs));
    }
}

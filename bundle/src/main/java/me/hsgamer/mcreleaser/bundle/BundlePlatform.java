package me.hsgamer.mcreleaser.bundle;

import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.github.GithubPlatform;
import me.hsgamer.mcreleaser.modrinth.ModrinthPlatform;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class BundlePlatform implements Platform {
    private static final Map<String, Supplier<Platform>> PLATFORM_MAP = Map.ofEntries(
            Map.entry("github", GithubPlatform::new),
            Map.entry("modrinth", ModrinthPlatform::new)
    );

    private final boolean runSync;
    private final List<Platform> platforms;

    public BundlePlatform(String usingPlatforms, boolean runSync) {
        this.runSync = runSync;

        if (usingPlatforms.equalsIgnoreCase("all")) {
            platforms = PLATFORM_MAP.values()
                    .stream()
                    .map(Supplier::get)
                    .toList();
        } else {
            platforms = Stream.of(usingPlatforms.split("\\s+|,"))
                    .map(PLATFORM_MAP::get)
                    .filter(Objects::nonNull)
                    .map(Supplier::get)
                    .toList();
        }
    }

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (platforms.isEmpty()) {
            return Optional.empty();
        }

        BatchRunnable batchRunnable = new BatchRunnable();

        TaskPool schedulePool = batchRunnable.getTaskPool(0);
        schedulePool.addLast(process -> {
            //noinspection unchecked
            List<Platform> platforms = (List<Platform>) process.getData().get("platforms");
            List<BatchRunnable> runnableList = platforms.stream()
                    .map(platform -> platform.createUploadRunnable(fileBundle))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            process.getData().put("runnableList", runnableList);
            process.next();
        });

        TaskPool executePool = batchRunnable.getTaskPool(1);
        executePool.addLast(process -> {
            //noinspection unchecked
            List<BatchRunnable> runnableList = (List<BatchRunnable>) process.getData().get("runnableList");
            if (runSync) {
                TaskPool pool = process.getCurrentTaskPool();
                runnableList.forEach(pool::addLast);
            } else {
                CompletableFuture<?>[] futures = runnableList.stream()
                        .map(CompletableFuture::runAsync)
                        .toArray(CompletableFuture[]::new);
                CompletableFuture.allOf(futures).thenRun(process::next);
            }
        });

        return Optional.of(batchRunnable);
    }
}

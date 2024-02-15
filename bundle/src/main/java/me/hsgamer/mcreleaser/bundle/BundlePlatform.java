package me.hsgamer.mcreleaser.bundle;

import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.github.GithubPlatform;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class BundlePlatform implements Platform {
    private final Map<String, Supplier<Platform>> platformMap = Map.ofEntries(
            Map.entry("github", GithubPlatform::new)
    );

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        String usingPlatforms = BundlePropertyKey.PLATFORM.getValue("all");
        boolean runSync = BundlePropertyKey.SYNC.asBoolean(false);

        if (usingPlatforms.isBlank()) {
            return Optional.empty();
        }

        BatchRunnable batchRunnable = new BatchRunnable();

        TaskPool preparePool = batchRunnable.getTaskPool(0);
        preparePool.addLast(process -> {
            List<Platform> platforms;
            if (usingPlatforms.equalsIgnoreCase("all")) {
                platforms = platformMap.values()
                        .stream()
                        .map(Supplier::get)
                        .toList();
            } else {
                platforms = Stream.of(usingPlatforms.split("\\s+|,"))
                        .map(platformMap::get)
                        .filter(Objects::nonNull)
                        .map(Supplier::get)
                        .toList();
            }
            process.getData().put("platforms", platforms);
            process.next();
        });

        TaskPool schedulePool = batchRunnable.getTaskPool(1);
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

        TaskPool executePool = batchRunnable.getTaskPool(2);
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

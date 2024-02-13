package me.hsgamer.mcreleaser.core.platform;

import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.mcreleaser.core.file.FileBundle;

import java.util.Optional;

public interface Platform {
    Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle);
}

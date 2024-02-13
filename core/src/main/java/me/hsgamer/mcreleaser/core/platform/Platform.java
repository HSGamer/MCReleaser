package me.hsgamer.mcreleaser.core.platform;

import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.mcreleaser.core.file.FileBundle;

public interface Platform {
    BatchRunnable createUploadTask(FileBundle fileBundle);
}

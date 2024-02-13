package me.hsgamer.mcreleaser.core.file;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public record FileBundle(File primaryFile, List<File> secondaryFiles) {
    public List<File> allFiles() {
        List<File> files = new ArrayList<>();
        files.add(primaryFile);
        files.addAll(secondaryFiles);
        return files;
    }
}

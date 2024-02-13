package me.hsgamer.mcreleaser.core.file;

import java.io.File;
import java.util.List;

public record FileBundle(File primaryFile, List<File> secondaryFiles) {
}

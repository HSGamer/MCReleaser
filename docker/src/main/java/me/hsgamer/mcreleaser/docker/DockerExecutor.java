package me.hsgamer.mcreleaser.docker;

import java.io.File;

public class DockerExecutor {
    public static void main(String[] args) {
        File primaryDir = new File("primary");
        File secondaryDir = new File("secondary");
        System.out.println("Primary: " + primaryDir.getAbsolutePath() + " (" + primaryDir.exists() + ")");
        System.out.println("Secondary: " + secondaryDir.getAbsolutePath() + " (" + secondaryDir.exists() + ")");
    }
}
package me.hsgamer.mcreleaser.github;

import me.hsgamer.hscore.logger.common.LogLevel;
import me.hsgamer.hscore.logger.common.Logger;
import me.hsgamer.hscore.logger.provider.LoggerProvider;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.File;
import java.util.Optional;

public class GithubPlatform implements Platform {
    private final Logger logger = LoggerProvider.getLogger(getClass());

    public GithubPlatform() {
        if (GithubPropertyKey.REF.isPresent()) {
            String version = getVersionFromRef(GithubPropertyKey.REF.getValue());
            if (CommonPropertyKey.VERSION.isAbsent()) {
                CommonPropertyKey.VERSION.setValue(version);
            }
            if (CommonPropertyKey.NAME.isAbsent()) {
                CommonPropertyKey.NAME.setValue(version);
            }
        }
    }

    private String getVersionFromRef(String tagReference) {
        return tagReference.replace("refs/tags/", "");
    }

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (PropertyKeyUtil.isAbsentAndAnnounce(logger, GithubPropertyKey.TOKEN, GithubPropertyKey.REPOSITORY, GithubPropertyKey.REF)) {
            return Optional.empty();
        }

        BatchRunnable runnable = new BatchRunnable();

        TaskPool preparePool = runnable.getTaskPool(0);
        preparePool.addLast(process -> {
            try {
                GitHub gitHub = new GitHubBuilder().withOAuthToken(GithubPropertyKey.TOKEN.getValue()).build();
                process.getData().put("github", gitHub);
                logger.log(LogLevel.INFO, "GitHub instance created");
                process.next();
            } catch (Exception e) {
                logger.log(LogLevel.ERROR, "Failed to create GitHub instance", e);
                process.complete();
            }
        });
        preparePool.addLast(process -> {
            try {
                GHRepository repository = ((GitHub) process.getData().get("github")).getRepository(GithubPropertyKey.REPOSITORY.getValue());
                process.getData().put("repository", repository);
                logger.log(LogLevel.INFO, "Repository instance created");
                process.next();
            } catch (Exception e) {
                logger.log(LogLevel.ERROR, "Failed to get the repository", e);
                process.complete();
            }
        });

        TaskPool releasePool = runnable.getTaskPool(1);
        releasePool.addLast(process -> {
            try {
                GHRepository repository = (GHRepository) process.getData().get("repository");
                GHRelease release = repository.getReleaseByTagName(GithubPropertyKey.REF.getValue());
                if (release == null) {
                    release = repository.createRelease(GithubPropertyKey.REF.getValue())
                            .draft(GithubPropertyKey.DRAFT.asBoolean(false))
                            .prerelease(GithubPropertyKey.PRERELEASE.asBoolean(false))
                            .name(CommonPropertyKey.NAME.getValue())
                            .body(CommonPropertyKey.DESCRIPTION.getValue())
                            .create();
                    logger.log(LogLevel.INFO, "Release created");
                } else {
                    logger.log(LogLevel.INFO, "Release already exists");
                }
                process.getData().put("release", release);
                process.next();
            } catch (Exception e) {
                logger.log(LogLevel.ERROR, "Failed to create the release", e);
                process.complete();
            }
        });

        TaskPool uploadPool = runnable.getTaskPool(2);
        uploadPool.addLast(process -> {
            try {
                GHRelease release = (GHRelease) process.getData().get("release");
                for (File file : fileBundle.allFiles()) {
                    release.uploadAsset(file, "application/octet-stream");
                    logger.log(LogLevel.INFO, "File uploaded: " + file.getName());
                }
                logger.log(LogLevel.INFO, "All files uploaded");
                process.next();
            } catch (Exception e) {
                logger.log(LogLevel.ERROR, "Failed to upload the file", e);
                process.complete();
            }
        });

        return Optional.of(runnable);
    }
}

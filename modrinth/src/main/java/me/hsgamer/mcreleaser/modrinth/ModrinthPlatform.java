package me.hsgamer.mcreleaser.modrinth;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import masecla.modrinth4j.client.agent.UserAgent;
import masecla.modrinth4j.endpoints.version.CreateVersion;
import masecla.modrinth4j.endpoints.version.GetProjectVersions;
import masecla.modrinth4j.endpoints.version.ModifyVersion;
import masecla.modrinth4j.main.ModrinthAPI;
import masecla.modrinth4j.model.version.ProjectVersion;
import me.hsgamer.hscore.logger.common.LogLevel;
import me.hsgamer.hscore.logger.common.Logger;
import me.hsgamer.hscore.logger.provider.LoggerProvider;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.StringUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ModrinthPlatform implements Platform {
    private final Logger logger = LoggerProvider.getLogger(getClass());

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (ModrinthPropertyKey.TOKEN.isAbsent() || ModrinthPropertyKey.PROJECT.isAbsent() || ModrinthPropertyKey.LOADERS.isAbsent() || ModrinthPropertyKey.GAME_VERSIONS.isAbsent()) {
            return Optional.empty();
        }

        Gson gson = new Gson();

        BatchRunnable batchRunnable = new BatchRunnable();
        TaskPool preparePool = batchRunnable.getTaskPool(0);
        preparePool.addLast(process -> {
            ModrinthAPI api = ModrinthAPI.rateLimited(UserAgent.builder().build(), ModrinthPropertyKey.TOKEN.getValue());
            process.getData().put("api", api);
            logger.log(LogLevel.INFO, "Modrinth API is ready");
            process.next();
        });

        TaskPool requestPool = batchRunnable.getTaskPool(1);
        requestPool.addLast(process -> {
            CreateVersion.CreateVersionRequest.CreateVersionRequestBuilder builder = CreateVersion.CreateVersionRequest.builder();

            builder.projectId(ModrinthPropertyKey.PROJECT.getValue());
            builder.featured(ModrinthPropertyKey.FEATURED.asBoolean(true));

            builder.name(CommonPropertyKey.NAME.getValue());
            builder.changelog(CommonPropertyKey.DESCRIPTION.getValue());
            builder.versionNumber(CommonPropertyKey.VERSION.getValue());

            if (ModrinthPropertyKey.VERSION_TYPE.isAbsent()) {
                builder.versionType(ProjectVersion.VersionType.RELEASE);
            } else {
                String versionTypeString = ModrinthPropertyKey.VERSION_TYPE.getValue();
                try {
                    ProjectVersion.VersionType versionType = ProjectVersion.VersionType.valueOf(versionTypeString.toUpperCase());
                    builder.versionType(versionType);
                } catch (IllegalArgumentException e) {
                    logger.log(LogLevel.ERROR, "Invalid version type: " + versionTypeString);
                    process.complete();
                    return;
                }
            }

            if (ModrinthPropertyKey.DEPENDENCIES.isPresent()) {
                TypeToken<List<ProjectVersion.ProjectDependency>> typeToken = new TypeToken<>() {
                };
                try {
                    List<ProjectVersion.ProjectDependency> dependencies = gson.fromJson(ModrinthPropertyKey.DEPENDENCIES.getValue(), typeToken.getType());
                    builder.dependencies(dependencies);
                } catch (Exception e) {
                    logger.log(LogLevel.ERROR, "Invalid dependencies");
                    process.complete();
                    return;
                }
            } else {
                builder.dependencies(Collections.emptyList());
            }

            List<String> loaders = Arrays.asList(StringUtil.splitSpace(ModrinthPropertyKey.LOADERS.getValue()));
            builder.loaders(loaders);

            List<String> gameVersions = Arrays.asList(StringUtil.splitSpace(ModrinthPropertyKey.GAME_VERSIONS.getValue()));
            builder.gameVersions(gameVersions);

            builder.files(fileBundle.allFiles());
            builder.primaryFile(fileBundle.primaryFile().getName());

            process.getData().put("request", builder.build());
            logger.log(LogLevel.INFO, "The request is ready");
            process.next();
        });

        TaskPool unfeaturePool = batchRunnable.getTaskPool(2);
        unfeaturePool.addLast(process -> {
            ModrinthAPI api = (ModrinthAPI) process.getData().get("api");
            String projectId = ModrinthPropertyKey.PROJECT.getValue();

            api.versions()
                    .getProjectVersions(projectId, GetProjectVersions.GetProjectVersionsRequest.builder().featured(true).build())
                    .thenAccept(projectVersions -> {
                        logger.log(LogLevel.INFO, "Get all featured versions");
                        process.getData().put("projectVersions", projectVersions);
                        process.next();
                    });
        });
        unfeaturePool.addLast(process -> {
            ModrinthAPI api = (ModrinthAPI) process.getData().get("api");
            //noinspection unchecked
            List<ProjectVersion> projectVersions = (List<ProjectVersion>) process.getData().get("projectVersions");
            CompletableFuture<?>[] futures = projectVersions.stream()
                    .map(ProjectVersion::getId)
                    .map(projectVersion -> api.versions().modifyProjectVersion(projectVersion, ModifyVersion.ModifyVersionRequest.builder().featured(false).build()))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).thenRun(() -> {
                logger.log(LogLevel.INFO, "All versions are unfeatured");
                process.next();
            });
        });

        TaskPool uploadPool = batchRunnable.getTaskPool(3);
        uploadPool.addLast(process -> {
            ModrinthAPI api = (ModrinthAPI) process.getData().get("api");
            CreateVersion.CreateVersionRequest request = (CreateVersion.CreateVersionRequest) process.getData().get("request");
            api.versions().createProjectVersion(request).thenRun(() -> {
                logger.log(LogLevel.INFO, "The version is uploaded");
                process.next();
            });
        });

        return Optional.of(batchRunnable);
    }
}

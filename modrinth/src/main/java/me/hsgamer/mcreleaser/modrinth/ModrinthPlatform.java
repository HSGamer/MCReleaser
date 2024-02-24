package me.hsgamer.mcreleaser.modrinth;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import masecla.modrinth4j.client.agent.UserAgent;
import masecla.modrinth4j.endpoints.version.CreateVersion;
import masecla.modrinth4j.endpoints.version.GetProjectVersions;
import masecla.modrinth4j.endpoints.version.ModifyVersion;
import masecla.modrinth4j.main.ModrinthAPI;
import masecla.modrinth4j.model.version.ProjectVersion;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import me.hsgamer.mcreleaser.core.util.StringUtil;
import me.hsgamer.mcreleaser.version.MinecraftVersionFetcher;
import me.hsgamer.mcreleaser.version.VersionTypeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ModrinthPlatform implements Platform {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (PropertyKeyUtil.isAbsentAndAnnounce(logger, ModrinthPropertyKey.TOKEN, ModrinthPropertyKey.PROJECT, ModrinthPropertyKey.LOADERS, CommonPropertyKey.GAME_VERSIONS)) {
            return Optional.empty();
        }

        Gson gson = new Gson();

        BatchRunnable batchRunnable = new BatchRunnable();
        TaskPool preparePool = batchRunnable.getTaskPool(0);
        preparePool.addLast(process -> {
            UserAgent userAgent = UserAgent.builder()
                    .projectName(CommonPropertyKey.NAME.getValue())
                    .projectVersion(CommonPropertyKey.VERSION.getValue())
                    .build();
            ModrinthAPI api = ModrinthAPI.rateLimited(userAgent, ModrinthPropertyKey.TOKEN.getValue());
            process.getData().put("api", api);
            logger.info("Modrinth API is ready");
            process.next();
        });

        TaskPool requestPool = batchRunnable.getTaskPool(1);
        requestPool.addLast(process -> {
            List<String> gameVersionFilters = Arrays.asList(StringUtil.splitSpace(CommonPropertyKey.GAME_VERSIONS.getValue()));
            VersionTypeFilter gameVersionTypeFilter = VersionTypeFilter.RELEASE;
            if (CommonPropertyKey.GAME_VERSION_TYPE.isPresent()) {
                try {
                    gameVersionTypeFilter = VersionTypeFilter.valueOf(CommonPropertyKey.GAME_VERSION_TYPE.getValue().toUpperCase());
                } catch (IllegalArgumentException e) {
                    logger.error("Invalid version type: " + CommonPropertyKey.GAME_VERSION_TYPE.getValue(), e);
                    process.complete();
                    return;
                }
            }
            MinecraftVersionFetcher.fetchVersionIds(gameVersionFilters, gameVersionTypeFilter).whenComplete((versionIds, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to fetch version ids", throwable);
                    process.complete();
                    return;
                }
                process.getData().put("versionIds", versionIds);
                logger.info("The version ids are ready");
                process.next();
            });
        });
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
                    logger.error("Invalid version type: " + versionTypeString, e);
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
                    logger.error("Invalid dependencies", e);
                    process.complete();
                    return;
                }
            } else {
                builder.dependencies(Collections.emptyList());
            }

            List<String> loaders = Arrays.asList(StringUtil.splitSpace(ModrinthPropertyKey.LOADERS.getValue()));
            builder.loaders(loaders);

            //noinspection unchecked
            List<String> gameVersions = (List<String>) process.getData().get("versionIds");
            builder.gameVersions(gameVersions);

            builder.files(fileBundle.allFiles());
            builder.primaryFile(fileBundle.primaryFile().getName());

            process.getData().put("request", builder.build());
            logger.info("The request is ready");
            process.next();
        });

        if (ModrinthPropertyKey.UNFEATURE.asBoolean(true)) {
            TaskPool unfeaturePool = batchRunnable.getTaskPool(2);
            unfeaturePool.addLast(process -> {
                ModrinthAPI api = (ModrinthAPI) process.getData().get("api");
                String projectId = ModrinthPropertyKey.PROJECT.getValue();

                api.versions()
                        .getProjectVersions(projectId, GetProjectVersions.GetProjectVersionsRequest.builder().featured(true).build())
                        .whenComplete((projectVersions, throwable) -> {
                            if (throwable != null) {
                                logger.error("Failed to get all featured versions", throwable);
                                process.complete();
                                return;
                            }

                            TaskPool taskPool = process.getCurrentTaskPool();
                            projectVersions.forEach(projectVersion -> taskPool.addLast(process1 -> api.versions().modifyProjectVersion(projectVersion.getId(), ModifyVersion.ModifyVersionRequest.builder().featured(false).build())
                                    .whenComplete((aVoid, throwable1) -> {
                                        if (throwable1 != null) {
                                            logger.error("Failed to un-feature version: " + projectVersion.getId(), throwable1);
                                        } else {
                                            logger.info("Un-featured version: " + projectVersion.getId());
                                        }
                                        process1.next();
                                    })));

                            process.next();
                        });
            });
        }

        TaskPool uploadPool = batchRunnable.getTaskPool(3);
        uploadPool.addLast(process -> {
            ModrinthAPI api = (ModrinthAPI) process.getData().get("api");
            CreateVersion.CreateVersionRequest request = (CreateVersion.CreateVersionRequest) process.getData().get("request");
            api.versions().createProjectVersion(request).whenComplete((projectVersion, throwable) -> {
                if (throwable != null) {
                    logger.info("Failed to upload the version", throwable);
                } else {
                    logger.info("Uploaded the version: " + projectVersion.getId());
                }
                process.complete();
            });
        });

        return Optional.of(batchRunnable);
    }
}

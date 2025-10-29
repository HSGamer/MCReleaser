package me.hsgamer.mcreleaser.modrinth;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import me.hsgamer.mcreleaser.core.util.StringUtil;
import me.hsgamer.mcreleaser.modrinth.api.ApiClient;
import me.hsgamer.mcreleaser.modrinth.api.ApiException;
import me.hsgamer.mcreleaser.modrinth.api.ApiResponse;
import me.hsgamer.mcreleaser.modrinth.api.Configuration;
import me.hsgamer.mcreleaser.modrinth.api.client.VersionsApi;
import me.hsgamer.mcreleaser.modrinth.api.model.CreatableVersionDto;
import me.hsgamer.mcreleaser.modrinth.api.model.EditableVersionDto;
import me.hsgamer.mcreleaser.modrinth.api.model.VersionDependencyDto;
import me.hsgamer.mcreleaser.modrinth.api.model.VersionDto;
import me.hsgamer.mcreleaser.version.MinecraftVersionFetcher;
import me.hsgamer.mcreleaser.version.VersionTypeFilter;
import okhttp3.Call;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class ModrinthPlatform implements Platform {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ModrinthPlatform() {
        if (ModrinthPropertyKey.GAME_VERSIONS.isAbsent() && CommonPropertyKey.GAME_VERSIONS.isPresent()) {
            ModrinthPropertyKey.GAME_VERSIONS.setValue(CommonPropertyKey.GAME_VERSIONS.getValue());
        }
        if (ModrinthPropertyKey.GAME_VERSION_TYPE.isAbsent() && CommonPropertyKey.GAME_VERSION_TYPE.isPresent()) {
            ModrinthPropertyKey.GAME_VERSION_TYPE.setValue(CommonPropertyKey.GAME_VERSION_TYPE.getValue());
        }
    }

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (PropertyKeyUtil.isAbsentAndAnnounce(logger, ModrinthPropertyKey.TOKEN, ModrinthPropertyKey.PROJECT, ModrinthPropertyKey.LOADERS, ModrinthPropertyKey.GAME_VERSIONS)) {
            return Optional.empty();
        }

        Gson gson = new Gson();

        BatchRunnable batchRunnable = new BatchRunnable();
        TaskPool preparePool = batchRunnable.getTaskPool(0);
        preparePool.addLast(process -> {
            Configuration.setApiClientFactory(() -> {
                ApiClient apiClient = new ApiClient();
                apiClient.setUserAgent(CommonPropertyKey.NAME.getValue() + "/" + CommonPropertyKey.VERSION.getValue());
                apiClient.setApiKey(ModrinthPropertyKey.TOKEN.getValue());
                if (ModrinthPropertyKey.ENDPOINT.getValue("production").equalsIgnoreCase("staging")) {
                    logger.info("Use staging endpoints");
                    apiClient.setServerIndex(1);
                }
                return apiClient;
            });
            logger.info("Modrinth API is ready");
            process.next();
        });

        TaskPool requestPool = batchRunnable.getTaskPool(1);
        requestPool.addLast(process -> {
            List<String> gameVersionFilters = Arrays.asList(StringUtil.splitSpace(ModrinthPropertyKey.GAME_VERSIONS.getValue()));
            VersionTypeFilter gameVersionTypeFilter = VersionTypeFilter.RELEASE;
            if (ModrinthPropertyKey.GAME_VERSION_TYPE.isPresent()) {
                try {
                    gameVersionTypeFilter = VersionTypeFilter.valueOf(ModrinthPropertyKey.GAME_VERSION_TYPE.getValue().toUpperCase());
                } catch (IllegalArgumentException e) {
                    logger.error("Invalid version type: " + ModrinthPropertyKey.GAME_VERSION_TYPE.getValue(), e);
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
            CreatableVersionDto dto = new CreatableVersionDto();

            dto.setProjectId(ModrinthPropertyKey.PROJECT.getValue());
            dto.setFeatured(ModrinthPropertyKey.FEATURED.asBoolean(true));

            dto.setName(CommonPropertyKey.NAME.getValue());
            dto.setChangelog(CommonPropertyKey.DESCRIPTION.getValue());
            dto.setVersionNumber(CommonPropertyKey.VERSION.getValue());

            if (ModrinthPropertyKey.VERSION_TYPE.isAbsent()) {
                dto.setVersionType(CreatableVersionDto.VersionTypeEnum.RELEASE);
            } else {
                String versionTypeString = ModrinthPropertyKey.VERSION_TYPE.getValue();
                try {
                    dto.setVersionType(CreatableVersionDto.VersionTypeEnum.valueOf(versionTypeString.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    logger.error("Invalid version type: " + versionTypeString, e);
                    process.complete();
                    return;
                }
            }

            if (ModrinthPropertyKey.DEPENDENCIES.isPresent()) {
                TypeToken<List<VersionDependencyDto>> typeToken = new TypeToken<>() {
                };
                try {
                    List<VersionDependencyDto> dependencies = gson.fromJson(ModrinthPropertyKey.DEPENDENCIES.getValue(), typeToken.getType());
                    dto.setDependencies(dependencies);
                } catch (Exception e) {
                    logger.error("Invalid dependencies", e);
                    process.complete();
                    return;
                }
            } else {
                dto.setDependencies(Collections.emptyList());
            }

            List<String> loaders = Arrays.asList(StringUtil.splitSpace(ModrinthPropertyKey.LOADERS.getValue()));
            dto.setLoaders(loaders);
            process.getData().put("loaders", loaders);

            List<String> gameVersions = process.getData().get("versionIds");
            dto.setGameVersions(gameVersions);

            List<String> fileParts = fileBundle.allFiles().stream().map(File::getName).collect(Collectors.toList());
            dto.setFileParts(fileParts);
            if (fileBundle.primaryFile() != null) {
                dto.setPrimaryFile(fileBundle.primaryFile().getName());
            }

            process.getData().put("request", dto);
            logger.info("The request is ready");
            process.next();
        });

        if (ModrinthPropertyKey.UNFEATURE.asBoolean(true)) {
            TaskPool unfeaturePool = batchRunnable.getTaskPool(2);
            unfeaturePool.addLast(process -> {
                String projectId = ModrinthPropertyKey.PROJECT.getValue();
                Set<String> loaderSet = process.getData().<List<String>>get("loaders").stream().map(String::toLowerCase).collect(Collectors.toSet());
                Set<String> gameVersionSet = process.getData().<List<String>>get("versionIds").stream().map(String::toLowerCase).collect(Collectors.toSet());

                VersionsApi versionsApi = new VersionsApi();
                try {
                    List<VersionDto> projectVersions = versionsApi.getProjectVersions(projectId, null, null, true);
                    TaskPool taskPool = process.getCurrentTaskPool();
                    for (VersionDto projectVersion : projectVersions) {
                        if (projectVersion.getLoaders().stream().map(String::toLowerCase).noneMatch(loaderSet::contains)) {
                            continue;
                        }
                        if (projectVersion.getGameVersions().stream().map(String::toLowerCase).noneMatch(gameVersionSet::contains)) {
                            continue;
                        }
                        taskPool.addLast(() -> {
                            try {
                                versionsApi.modifyVersion(projectVersion.getId(), new EditableVersionDto().featured(false));
                                logger.info("Un-featured version: {}", projectVersion.getId());
                            } catch (Exception e) {
                                logger.error("Failed to un-feature version: {}", projectVersion.getId(), e);
                            }
                        });
                    }
                    process.next();
                } catch (ApiException e) {
                    logger.warn("Failed to retrieve versions for project: {}", projectId, e);
                    process.complete();
                }
            });
        }

        TaskPool uploadPool = batchRunnable.getTaskPool(3);
        uploadPool.addLast(process -> {
            VersionsApi versionsApi = new VersionsApi();
            ApiClient apiClient = versionsApi.getApiClient();
            CreatableVersionDto request = process.getData().get("request");

            Map<String, String> headerParams = new HashMap<>();
            final String[] accepts = {"application/json"};
            final String accept = apiClient.selectHeaderAccept(accepts);
            if (accept != null) {
                headerParams.put("Accept", accept);
            }
            final String[] contentTypes = {"multipart/form-data"};
            final String contentType = apiClient.selectHeaderContentType(contentTypes);
            if (contentType != null) {
                headerParams.put("Content-Type", contentType);
            }

            Map<String, Object> formParams = new HashMap<>();
            formParams.put("data", request);
            for (File file : fileBundle.allFiles()) {
                formParams.put(file.getName(), file);
            }

            String[] authNames = new String[]{"TokenAuth"};
            try {
                Call call = apiClient.buildCall(
                        null,
                        "/version",
                        "POST",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        null,
                        headerParams,
                        Collections.emptyMap(),
                        formParams,
                        authNames,
                        null
                );

                Type responseType = new TypeToken<VersionDto>() {
                }.getType();
                ApiResponse<VersionDto> execute = apiClient.execute(call, responseType);
                VersionDto response = execute.getData();
                logger.info("Uploaded the version: {}", response.getId());
            } catch (Exception e) {
                logger.warn("Failed to upload the version", e);
            }

            process.complete();
        });

        return Optional.of(batchRunnable);
    }
}

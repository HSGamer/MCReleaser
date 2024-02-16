package me.hsgamer.mcreleaser.hangar;

import com.fasterxml.jackson.core.type.TypeReference;
import me.hsgamer.hscore.logger.common.LogLevel;
import me.hsgamer.hscore.logger.common.Logger;
import me.hsgamer.hscore.logger.provider.LoggerProvider;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mc.releaser.hangar.openapi.api.AuthenticationApi;
import me.hsgamer.mc.releaser.hangar.openapi.api.VersionsApi;
import me.hsgamer.mc.releaser.hangar.openapi.invoker.ApiClient;
import me.hsgamer.mc.releaser.hangar.openapi.invoker.ApiException;
import me.hsgamer.mc.releaser.hangar.openapi.invoker.Configuration;
import me.hsgamer.mc.releaser.hangar.openapi.model.MultipartFileOrUrl;
import me.hsgamer.mc.releaser.hangar.openapi.model.PluginDependency;
import me.hsgamer.mc.releaser.hangar.openapi.model.VersionUpload;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import me.hsgamer.mcreleaser.core.util.StringUtil;

import java.util.*;

public class HangarPlatform implements Platform {
    private final Logger logger = LoggerProvider.getLogger(getClass());

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (PropertyKeyUtil.isAbsentAndAnnounce(logger, HangarPropertyKey.KEY, HangarPropertyKey.PROJECT, HangarPropertyKey.PLATFORM, HangarPropertyKey.GAME_VERSIONS)) {
            return Optional.empty();
        }

        BatchRunnable batchRunnable = new BatchRunnable();
        TaskPool connectPool = batchRunnable.getTaskPool(0);
        connectPool.addLast(process -> {
            ApiClient apiClient = Configuration.getDefaultApiClient();
            AuthenticationApi authenticationApi = new AuthenticationApi(apiClient);
            try {
                authenticationApi.authenticate(HangarPropertyKey.KEY.getValue())
                        .whenComplete((apiSession, throwable) -> {
                            if (throwable != null) {
                                logger.log(LogLevel.ERROR, "Failed to authenticate", throwable);
                                process.complete();
                                return;
                            }

                            String token = apiSession.getToken();
                            apiClient.setRequestInterceptor(builder -> builder.header("Authorization", "Bearer " + token));
                            logger.log(LogLevel.INFO, "Authenticated");
                            process.next();
                        });
            } catch (ApiException e) {
                logger.log(LogLevel.ERROR, "Failed to authenticate", e);
                process.complete();
            }
        });

        TaskPool preparePool = batchRunnable.getTaskPool(0);
        preparePool.addLast(process -> {
            ApiClient apiClient = Configuration.getDefaultApiClient();

            VersionUpload versionUpload = new VersionUpload();
            versionUpload.channel(HangarPropertyKey.CHANNEL.getValue("Release"));

            StringBuilder descriptionBuilder = new StringBuilder();
            String nameValue = CommonPropertyKey.NAME.getValue();
            String descriptionValue = CommonPropertyKey.DESCRIPTION.getValue();
            String versionValue = CommonPropertyKey.VERSION.getValue();
            if (!Objects.equals(nameValue, versionValue)) {
                descriptionBuilder.append("# Name: ").append(nameValue).append("\n\n");
            }
            descriptionBuilder.append(descriptionValue);

            versionUpload.version(versionValue);
            versionUpload.description(descriptionBuilder.toString());

            String platformValue = HangarPropertyKey.PLATFORM.getValue();
            me.hsgamer.mc.releaser.hangar.openapi.model.Platform hangarPlatform;
            try {
                hangarPlatform = me.hsgamer.mc.releaser.hangar.openapi.model.Platform.valueOf(platformValue.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.log(LogLevel.ERROR, "Invalid platform: " + platformValue, e);
                process.complete();
                return;
            }

            String[] gameVersionValue = StringUtil.splitCommaOrSpace(HangarPropertyKey.GAME_VERSIONS.getValue());

            versionUpload.platformDependencies(Map.of(hangarPlatform.name(), Set.of(gameVersionValue)));

            if (HangarPropertyKey.DEPENDENCIES.isPresent()) {
                Set<PluginDependency> pluginDependencies;
                TypeReference<Set<PluginDependency>> typeReference = new TypeReference<>() {
                };
                try {
                    pluginDependencies = apiClient.getObjectMapper().readValue(HangarPropertyKey.DEPENDENCIES.getValue(), typeReference);
                } catch (Exception e) {
                    logger.log(LogLevel.ERROR, "Invalid dependencies", e);
                    process.complete();
                    return;
                }
                versionUpload.pluginDependencies(Map.of(hangarPlatform.name(), pluginDependencies));
            }

            versionUpload.addFilesItem(new MultipartFileOrUrl().addPlatformsItem(hangarPlatform).externalUrl(null));

            process.getData().put("versionUpload", versionUpload);
            logger.log(LogLevel.INFO, "Prepared version");
            process.next();
        });

        TaskPool uploadPool = batchRunnable.getTaskPool(0);
        uploadPool.addLast(process -> {
            ApiClient apiClient = Configuration.getDefaultApiClient();
            VersionsApi versionsApi = new VersionsApi(apiClient);
            VersionUpload versionUpload = (VersionUpload) process.getData().get("versionUpload");

            try {
                versionsApi.uploadVersion(HangarPropertyKey.PROJECT.getValue(), versionUpload, List.of(fileBundle.primaryFile()))
                        .whenComplete((version, throwable) -> {
                            if (throwable != null) {
                                logger.log(LogLevel.ERROR, "Failed to upload version", throwable);
                                process.complete();
                                return;
                            }

                            logger.log(LogLevel.INFO, "Uploaded version: " + version.getUrl());
                            process.next();
                        });
            } catch (ApiException e) {
                logger.log(LogLevel.ERROR, "Failed to upload version", e);
                process.complete();
            }
        });

        return Optional.of(batchRunnable);
    }
}

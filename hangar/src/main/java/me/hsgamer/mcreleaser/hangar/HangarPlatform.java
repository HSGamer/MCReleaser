package me.hsgamer.mcreleaser.hangar;

import com.github.mizosoft.methanol.*;
import com.google.gson.reflect.TypeToken;
import me.hsgamer.hscore.logger.common.LogLevel;
import me.hsgamer.hscore.logger.common.Logger;
import me.hsgamer.hscore.logger.provider.LoggerProvider;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import me.hsgamer.mcreleaser.core.util.StringUtil;
import me.hsgamer.mcreleaser.hangar.adapter.GsonAdapter;
import me.hsgamer.mcreleaser.hangar.model.ApiSession;
import me.hsgamer.mcreleaser.hangar.model.VersionUpload;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class HangarPlatform implements Platform {
    private final String baseUrl = "https://hangar.papermc.io/api/v1";
    private final Logger logger = LoggerProvider.getLogger(getClass());

    public HangarPlatform() {
        if (CommonPropertyKey.GAME_VERSIONS.isPresent() && HangarPropertyKey.GAME_VERSIONS.isAbsent()) {
            HangarPropertyKey.GAME_VERSIONS.setValue(CommonPropertyKey.GAME_VERSIONS.getValue());
        }
    }

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (PropertyKeyUtil.isAbsentAndAnnounce(logger, HangarPropertyKey.KEY, HangarPropertyKey.PROJECT, HangarPropertyKey.PLATFORM, HangarPropertyKey.GAME_VERSIONS)) {
            return Optional.empty();
        }

        BatchRunnable batchRunnable = new BatchRunnable();
        TaskPool connectPool = batchRunnable.getTaskPool(0);
        connectPool.addLast(process -> {
            HttpClient client = HttpClient.newBuilder().build();
            process.getData().put("client", client);
            logger.log(LogLevel.INFO, "Prepared client");
            process.next();
        });
        connectPool.addLast(process -> {
            HttpClient client = (HttpClient) process.getData().get("client");
            String key = HangarPropertyKey.KEY.getValue();

            MutableRequest tokenRequest = MutableRequest.POST(baseUrl + "/authenticate?apiKey=" + key, HttpRequest.BodyPublishers.noBody());
            client.sendAsync(tokenRequest, MoreBodyHandlers.ofObject(ApiSession.class))
                    .whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            logger.log(LogLevel.ERROR, "Failed to get token", throwable);
                            process.complete();
                            return;
                        }
                        if (response.statusCode() != 200) {
                            logger.log(LogLevel.ERROR, "Failed to get token: " + response.statusCode());
                            process.complete();
                            return;
                        }
                        process.getData().put("token", response.body().token());
                        logger.log(LogLevel.INFO, "Got token");
                        process.next();
                    });
        });

        TaskPool preparePool = batchRunnable.getTaskPool(0);
        preparePool.addLast(process -> {
            String channel = HangarPropertyKey.CHANNEL.getValue("Release");

            StringBuilder descriptionBuilder = new StringBuilder();
            String nameValue = CommonPropertyKey.NAME.getValue();
            String descriptionValue = CommonPropertyKey.DESCRIPTION.getValue();
            String versionValue = CommonPropertyKey.VERSION.getValue();
            if (!Objects.equals(nameValue, versionValue)) {
                descriptionBuilder.append("# Name: ").append(nameValue).append("\n\n");
            }
            descriptionBuilder.append(descriptionValue);

            String finalDescription = descriptionBuilder.toString();

            String platformValue = HangarPropertyKey.PLATFORM.getValue();
            VersionUpload.Platform hangarPlatform;
            try {
                hangarPlatform = VersionUpload.Platform.valueOf(platformValue.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.log(LogLevel.ERROR, "Invalid platform: " + platformValue, e);
                process.complete();
                return;
            }

            String[] gameVersionValue = StringUtil.splitCommaOrSpace(HangarPropertyKey.GAME_VERSIONS.getValue());

            Map<VersionUpload.Platform, List<String>> platformDependencies = Map.of(hangarPlatform, List.of(gameVersionValue));

            List<VersionUpload.PluginDependency> pluginDependencies;
            if (HangarPropertyKey.DEPENDENCIES.isPresent()) {
                TypeToken<List<VersionUpload.PluginDependency>> typeToken = new TypeToken<>() {
                };
                try {
                    pluginDependencies = GsonAdapter.INSTANCE.fromJson(HangarPropertyKey.DEPENDENCIES.getValue(), typeToken.getType());
                } catch (Exception e) {
                    logger.log(LogLevel.ERROR, "Invalid dependencies", e);
                    process.complete();
                    return;
                }
            } else {
                pluginDependencies = Collections.emptyList();
            }

            List<VersionUpload.MultipartFileOrUrl> files = List.of(new VersionUpload.MultipartFileOrUrl(List.of(hangarPlatform), null));

            VersionUpload versionUpload = new VersionUpload(
                    versionValue,
                    Map.of(hangarPlatform, pluginDependencies),
                    platformDependencies,
                    finalDescription,
                    files,
                    channel
            );

            process.getData().put("versionUpload", versionUpload);
            logger.log(LogLevel.INFO, "Prepared version");
            process.next();
        });

        TaskPool uploadPool = batchRunnable.getTaskPool(0);
        uploadPool.addLast(process -> {
            HttpClient client = (HttpClient) process.getData().get("client");

            String token = (String) process.getData().get("token");
            VersionUpload versionUpload = (VersionUpload) process.getData().get("versionUpload");
            String project = HangarPropertyKey.PROJECT.getValue();

            MultipartBodyPublisher bodyPublisher;
            try {
                bodyPublisher = MultipartBodyPublisher.newBuilder()
                        .formPart("versionUpload", MoreBodyPublishers.ofObject(versionUpload, MediaType.APPLICATION_JSON))
                        .filePart("files", fileBundle.primaryFile().toPath(), MediaType.APPLICATION_OCTET_STREAM)
                        .build();
            } catch (FileNotFoundException e) {
                logger.log(LogLevel.ERROR, "File not found", e);
                process.complete();
                return;
            }

            MutableRequest request = MutableRequest.create()
                    .uri(URI.create(baseUrl + "/projects/" + project + "/upload"))
                    .header("Authorization", "Bearer " + token)
                    .POST(bodyPublisher);

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            logger.log(LogLevel.ERROR, "Failed to upload version", throwable);
                            process.complete();
                            return;
                        }
                        if (response.statusCode() != 200) {
                            logger.log(LogLevel.ERROR, "Failed to upload version: " + response.statusCode() + " - " + response.body());
                            process.complete();
                            return;
                        }
                        logger.log(LogLevel.INFO, "Uploaded version");
                        process.complete();
                    });
        });

        return Optional.of(batchRunnable);
    }
}

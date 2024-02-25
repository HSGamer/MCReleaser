package me.hsgamer.mcreleaser.hangar;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import me.hsgamer.mcreleaser.core.util.StringUtil;
import me.hsgamer.mcreleaser.hangar.model.ApiSession;
import me.hsgamer.mcreleaser.hangar.model.VersionUpload;
import me.hsgamer.mcreleaser.version.MinecraftVersionFetcher;
import me.hsgamer.mcreleaser.version.VersionTypeFilter;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class HangarPlatform implements Platform {
    private final String baseUrl = "https://hangar.papermc.io/api/v1";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public HangarPlatform() {
        if (HangarPropertyKey.GAME_VERSIONS.isAbsent() && CommonPropertyKey.GAME_VERSIONS.isPresent()) {
            HangarPropertyKey.GAME_VERSIONS.setValue(CommonPropertyKey.GAME_VERSIONS.getValue());
        }
    }

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (PropertyKeyUtil.isAbsentAndAnnounce(logger, HangarPropertyKey.KEY, HangarPropertyKey.PROJECT, HangarPropertyKey.PLATFORM, HangarPropertyKey.GAME_VERSIONS)) {
            return Optional.empty();
        }

        Gson gson = new Gson();

        BatchRunnable batchRunnable = new BatchRunnable();
        TaskPool connectPool = batchRunnable.getTaskPool(0);
        connectPool.addLast(process -> {
            CloseableHttpClient client = HttpClients.createMinimal();
            process.getData().put("client", client);
            logger.info("Prepared client");
            process.next();
        });
        connectPool.addLast(process -> {
            HttpClient client = (HttpClient) process.getData().get("client");
            String key = HangarPropertyKey.KEY.getValue();

            HttpPost tokenRequest = new HttpPost(baseUrl + "/authenticate?apiKey=" + key);
            try {
                ApiSession apiSession = client.execute(tokenRequest, response -> {
                    if (response.getCode() != 200) {
                        logger.error("Failed to get token: " + response.getCode());
                        return null;
                    }
                    try {
                        return gson.fromJson(EntityUtils.toString(response.getEntity()), ApiSession.class);
                    } catch (Exception e) {
                        logger.error("Failed to get token", e);
                        return null;
                    }
                });
                if (apiSession == null) {
                    process.complete();
                    return;
                }
                process.getData().put("token", apiSession.token());
                logger.info("Got token");
                process.next();
            } catch (IOException e) {
                logger.error("Failed to get token", e);
                process.complete();
            }
        });

        TaskPool preparePool = batchRunnable.getTaskPool(0);
        preparePool.addLast(process -> {
            List<String> gameVersions = Arrays.asList(StringUtil.splitSpace(CommonPropertyKey.GAME_VERSIONS.getValue()));
            MinecraftVersionFetcher.normalizeVersions(gameVersions, VersionTypeFilter.RELEASE).whenComplete((versions, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to fetch version", throwable);
                    process.complete();
                    return;
                }

                process.getData().put("versions", versions);
                logger.info("Prepared versions");
                process.next();
            });

        });
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
                logger.error("Invalid platform: " + platformValue, e);
                process.complete();
                return;
            }

            //noinspection unchecked
            List<String> gameVersionValue = (List<String>) process.getData().get("versions");

            Map<VersionUpload.Platform, List<String>> platformDependencies = Map.of(hangarPlatform, gameVersionValue);

            List<VersionUpload.PluginDependency> pluginDependencies;
            if (HangarPropertyKey.DEPENDENCIES.isPresent()) {
                TypeToken<List<VersionUpload.PluginDependency>> typeToken = new TypeToken<>() {
                };
                try {
                    pluginDependencies = gson.fromJson(HangarPropertyKey.DEPENDENCIES.getValue(), typeToken.getType());
                } catch (Exception e) {
                    logger.error("Invalid dependencies", e);
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
            logger.info("Prepared version");
            process.next();
        });

        TaskPool uploadPool = batchRunnable.getTaskPool(0);
        uploadPool.addLast(process -> {
            HttpClient client = (HttpClient) process.getData().get("client");

            String token = (String) process.getData().get("token");
            VersionUpload versionUpload = (VersionUpload) process.getData().get("versionUpload");
            String project = HangarPropertyKey.PROJECT.getValue();

            MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                    .addTextBody("versionUpload", gson.toJson(versionUpload), ContentType.APPLICATION_JSON)
                    .addBinaryBody("files", fileBundle.primaryFile());

            HttpPost request = new HttpPost(baseUrl + "/projects/" + project + "/upload");
            request.addHeader("Authorization", "Bearer " + token);
            request.setEntity(builder.build());

            try {
                boolean success = client.execute(request, response -> {
                    if (response.getCode() != 200) {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        logger.error("Failed to upload version: " + response.getCode() + " - " + responseBody);
                        return false;
                    }
                    return true;
                });
                if (success) {
                    logger.info("Uploaded version");
                }
                process.next();
            } catch (IOException e) {
                logger.error("Failed to upload version", e);
                process.complete();
            }
        });
        uploadPool.addLast(process -> {
            CloseableHttpClient client = (CloseableHttpClient) process.getData().get("client");
            try {
                client.close();
                logger.info("Closed client");
            } catch (IOException e) {
                logger.error("Failed to close client", e);
            }
            process.complete();
        });

        return Optional.of(batchRunnable);
    }
}

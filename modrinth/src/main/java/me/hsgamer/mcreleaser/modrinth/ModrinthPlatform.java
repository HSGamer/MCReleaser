package me.hsgamer.mcreleaser.modrinth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import me.hsgamer.mcreleaser.core.util.StringUtil;
import me.hsgamer.mcreleaser.version.MinecraftVersionFetcher;
import me.hsgamer.mcreleaser.version.VersionTypeFilter;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

        TaskPool requestPool = batchRunnable.getTaskPool(1);
        requestPool.addLast(process -> {
            List<String> gameVersionFilters = Arrays.asList(StringUtil.splitSpace(ModrinthPropertyKey.GAME_VERSIONS.getValue()));
            VersionTypeFilter gameVersionTypeFilter = VersionTypeFilter.RELEASE;
            if (ModrinthPropertyKey.GAME_VERSION_TYPE.isPresent()) {
                try {
                    gameVersionTypeFilter = VersionTypeFilter.valueOf(ModrinthPropertyKey.GAME_VERSION_TYPE.getValue().toUpperCase());
                } catch (IllegalArgumentException e) {
                    logger.error("Invalid version type: {}", ModrinthPropertyKey.GAME_VERSION_TYPE.getValue(), e);
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
            JsonObject request = new JsonObject();

            request.addProperty("project_id", ModrinthPropertyKey.PROJECT.getValue());
            request.addProperty("featured", ModrinthPropertyKey.FEATURED.asBoolean(true));

            request.addProperty("name", CommonPropertyKey.NAME.getValue());
            request.addProperty("changelog", CommonPropertyKey.DESCRIPTION.getValue());
            request.addProperty("version_number", CommonPropertyKey.VERSION.getValue());
            request.addProperty("version_type", ModrinthPropertyKey.VERSION_TYPE.getValue("release").toLowerCase());

            if (ModrinthPropertyKey.DEPENDENCIES.isPresent()) {
                try {
                    JsonArray dependencies = gson.fromJson(ModrinthPropertyKey.DEPENDENCIES.getValue(), JsonArray.class);
                    request.add("dependencies", dependencies);
                } catch (Exception e) {
                    logger.error("Invalid dependencies", e);
                    process.complete();
                    return;
                }
            } else {
                request.add("dependencies", gson.toJsonTree(Collections.emptyList()));
            }

            List<String> loaders = Arrays.asList(StringUtil.splitSpace(ModrinthPropertyKey.LOADERS.getValue()));
            request.add("loaders", gson.toJsonTree(loaders));
            process.getData().put("loaders", loaders);

            List<String> gameVersions = process.getData().get("versionIds");
            request.add("game_versions", gson.toJsonTree(gameVersions));

            List<String> fileParts = fileBundle.allFiles().stream().map(File::getName).collect(Collectors.toList());
            request.add("file_parts", gson.toJsonTree(fileParts));
            if (fileBundle.primaryFile() != null) {
                request.addProperty("primary_file", fileBundle.primaryFile().getName());
            }

            process.getData().put("request", request);
            logger.info("The request is ready");
            process.next();
        });

        TaskPool uploadPool = batchRunnable.getTaskPool(3);
        uploadPool.addLast(process -> {
            String baseUrl;
            if (ModrinthPropertyKey.ENDPOINT.getValue("production").equalsIgnoreCase("staging")) {
                logger.info("Use staging endpoint");
                baseUrl = "https://staging-api.modrinth.com";
            } else {
                baseUrl = "https://api.modrinth.com";
            }
            String url = baseUrl + "/v2/version";
            String token = ModrinthPropertyKey.TOKEN.getValue();
            String userAgent = CommonPropertyKey.NAME.getValue() + "/" + CommonPropertyKey.VERSION.getValue();

            JsonObject request = process.getData().get("request");

            try (CloseableHttpClient client = HttpClients.createMinimal()) {
                HttpPost post = new HttpPost(url);
                post.setHeader("Authorization", token);
                post.setHeader("User-Agent", userAgent);
                post.setHeader("Accept", "application/json");

                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addTextBody("data", gson.toJson(request), ContentType.APPLICATION_JSON);
                for (File file : fileBundle.allFiles()) {
                    try {
                        builder.addBinaryBody(file.getName(), file);
                    } catch (Exception e) {
                        logger.warn("Failed to add file", e);
                    }
                }
                HttpEntity entity = builder.build();
                post.setEntity(entity);

                JsonObject versionDto = client.execute(post, response -> {
                    if (response.getCode() != 200) {
                        throw new HttpException("Execute not successful. Got: %d %s", response.getCode(), EntityUtils.toString(response.getEntity()));
                    }
                    try {
                        String body = EntityUtils.toString(response.getEntity());
                        return gson.fromJson(body, JsonObject.class);
                    } catch (Exception e) {
                        throw new IOException("Failed to parse response", e);
                    }
                });
                logger.info("Uploaded the version: {}", versionDto.get("id").getAsString());
            } catch (Exception e) {
                logger.warn("Failed to upload the version", e);
            }

            process.complete();
        });

        return Optional.of(batchRunnable);
    }
}

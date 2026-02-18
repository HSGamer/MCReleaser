package me.hsgamer.mcreleaser.curseforge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
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
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class CurseForgePlatform implements Platform {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public CurseForgePlatform() {
        if (CurseForgePropertyKey.GAME_VERSIONS.isAbsent() && CommonPropertyKey.GAME_VERSIONS.isPresent()) {
            CurseForgePropertyKey.GAME_VERSIONS.setValue(CommonPropertyKey.GAME_VERSIONS.getValue());
        }
    }

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (PropertyKeyUtil.isAbsentAndAnnounce(logger, CurseForgePropertyKey.TOKEN, CurseForgePropertyKey.PROJECT)) {
            return Optional.empty();
        }

        Gson gson = new Gson();

        BatchRunnable batchRunnable = new BatchRunnable();

        TaskPool initPool = batchRunnable.getTaskPool(1);
        initPool.addLast(process -> {
            CloseableHttpClient client = HttpClients.createMinimal();
            process.getData().put("client", client);
            logger.info("Prepared client");
            process.next();
        });

        TaskPool setupPool = batchRunnable.getTaskPool(2);
        String type = CurseForgePropertyKey.TYPE.getValue("minecraft");
        if (type.equalsIgnoreCase("minecraft")) {
            setupPool.addLast(process -> {
                String endpoint = "https://minecraft.curseforge.com";
                process.getData().put("endpoint", endpoint);

                List<Integer> gameVersions = new ArrayList<>();
                process.getData().put("gameVersions", gameVersions);

                CloseableHttpClient client = process.getData().get("client");
                try {
                    HttpGet versionTypesEndpoint = new HttpGet(endpoint + "/api/game/version-types");
                    versionTypesEndpoint.setHeader("X-Api-Token", CurseForgePropertyKey.TOKEN.getValue());
                    JsonArray versionTypes = client.execute(versionTypesEndpoint, response -> {
                        if (response.getCode() != 200) {
                            logger.error("Failed to get version types: {}", response.getCode());
                            return null;
                        }
                        try {
                            return gson.fromJson(EntityUtils.toString(response.getEntity()), JsonArray.class);
                        } catch (Exception e) {
                            logger.error("Failed to get version types", e);
                            return null;
                        }
                    });
                    if (versionTypes == null) {
                        process.complete();
                        return;
                    }

                    HttpGet versionsEndpoint = new HttpGet(endpoint + "/api/game/versions");
                    versionsEndpoint.setHeader("X-Api-Token", CurseForgePropertyKey.TOKEN.getValue());
                    JsonArray versions = client.execute(versionTypesEndpoint, response -> {
                        if (response.getCode() != 200) {
                            logger.error("Failed to get version: {}", response.getCode());
                            return null;
                        }
                        try {
                            return gson.fromJson(EntityUtils.toString(response.getEntity()), JsonArray.class);
                        } catch (Exception e) {
                            logger.error("Failed to get version", e);
                            return null;
                        }
                    });
                    if (versions == null) {
                        process.complete();
                        return;
                    }

                    BiConsumer<String, String> addToGameVersions = (versionName, typeSlug) -> {
                        Set<Integer> versionTypeIds = versionTypes.asList().stream()
                                .map(JsonElement::getAsJsonObject)
                                .filter(versionType -> {
                                    String slug = versionType.get("slug").getAsString();
                                    if (typeSlug.equalsIgnoreCase("minecraft")) {
                                        return slug.startsWith("minecraft");
                                    } else {
                                        return typeSlug.equalsIgnoreCase(slug);
                                    }
                                })
                                .map(versionType -> versionType.get("id").getAsInt())
                                .collect(Collectors.toSet());
                        versions.asList().stream()
                                .map(JsonElement::getAsJsonObject)
                                .filter(version -> {
                                    String name = version.get("name").getAsString();
                                    return versionName.equalsIgnoreCase(name);
                                })
                                .filter(version -> {
                                    int typeId = version.get("gameVersionTypeID").getAsInt();
                                    return versionTypeIds.contains(typeId);
                                })
                                .forEach(version -> {
                                    int id = version.get("id").getAsInt();
                                    logger.info("Added version {} ({})", id, version.get("name").getAsString());
                                    gameVersions.add(id);
                                });
                    };
                    process.getData().put("addToGameVersions", addToGameVersions);
                    process.next();
                } catch (IOException e) {
                    logger.error("Failed to get version types", e);
                    process.complete();
                }
            });
            if (CurseForgePropertyKey.GAME_VERSIONS.isPresent()) {
                setupPool.addLast(process -> {
                    List<String> gameVersions = Arrays.asList(StringUtil.splitSpace(CurseForgePropertyKey.GAME_VERSIONS.getValue()));
                    BiConsumer<String, String> addToGameVersions = process.getData().get("addToGameVersions");
                    MinecraftVersionFetcher.normalizeVersions(gameVersions, VersionTypeFilter.RELEASE).whenComplete((versions, throwable) -> {
                        if (throwable != null) {
                            logger.error("Failed to fetch version", throwable);
                            process.complete();
                            return;
                        }

                        for (String gameVersion : gameVersions) {
                            addToGameVersions.accept(gameVersion, "minecraft");
                        }
                        process.next();
                    });
                });
            }
            if (CurseForgePropertyKey.MOD_LOADERS.isPresent()) {
                setupPool.addLast(process -> {
                    BiConsumer<String, String> addToGameVersions = process.getData().get("addToGameVersions");
                    String[] modLoaders = StringUtil.splitSpace(CurseForgePropertyKey.MOD_LOADERS.getValue());
                    for (String modLoader : modLoaders) {
                        addToGameVersions.accept(modLoader, "modloader");
                    }
                    process.next();
                });
            }
            if (CurseForgePropertyKey.ENVIRONMENT.isPresent()) {
                setupPool.addLast(process -> {
                    BiConsumer<String, String> addToGameVersions = process.getData().get("addToGameVersions");
                    for (String environment : StringUtil.splitSpace(CurseForgePropertyKey.ENVIRONMENT.getValue())) {
                        addToGameVersions.accept(environment, "environment");
                    }
                    process.next();
                });
            }
            if (CurseForgePropertyKey.JAVA_VERSION.isPresent()) {
                setupPool.addLast(process -> {
                    BiConsumer<String, String> addToGameVersions = process.getData().get("addToGameVersions");
                    String javaVersion = CurseForgePropertyKey.JAVA_VERSION.getValue();
                    addToGameVersions.accept("Java " + javaVersion, "java");
                    process.next();
                });
            }
        } else if (type.equalsIgnoreCase("hytale")) {
            setupPool.addLast(process -> {
                String endpoint = "https://legacy.curseforge.com";
                List<Integer> gameVersions = Collections.singletonList(14284);
                process.getData().put("gameVersions", gameVersions);
                process.getData().put("endpoint", endpoint);
                process.next();
            });
        } else {
            logger.error("Unknown platform type. Supported types: MINECRAFT, HYTALE");
            return Optional.empty();
        }

        TaskPool metadataPool = batchRunnable.getTaskPool(3);
        metadataPool.addLast(process -> {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("changelog", CommonPropertyKey.DESCRIPTION.getValue());
            metadata.addProperty("changelogType", "markdown");
            metadata.addProperty("displayName", CommonPropertyKey.NAME.getValue());
            metadata.addProperty("releaseType", CurseForgePropertyKey.RELEASE_TYPE.getValue("release").toLowerCase(Locale.ROOT));

            List<Integer> gameVersions = process.getData().get("gameVersions");
            JsonArray gameVersionsArray = new JsonArray();
            for (Integer gameVersion : gameVersions) {
                gameVersionsArray.add(gameVersion);
            }
            metadata.add("gameVersions", gameVersionsArray);

            if (CurseForgePropertyKey.MANUAL.isPresent()) {
                boolean manual = Boolean.parseBoolean(CurseForgePropertyKey.MANUAL.getValue());
                metadata.addProperty("isMarkedForManualRelease", manual);
            }

            if (CurseForgePropertyKey.RELATIONS.isPresent()) {
                JsonObject relations = new JsonObject();
                JsonArray relationsArray;
                try {
                    relationsArray = gson.fromJson(CurseForgePropertyKey.RELATIONS.getValue(), JsonArray.class);
                } catch (Exception e) {
                    logger.error("Invalid relations", e);
                    process.complete();
                    return;
                }
                relations.add("projects", relationsArray);
                metadata.add("relations", relations);
            }

            process.getData().put("metadata", metadata);
            logger.info("Prepared metadata");
            process.next();
        });

        TaskPool uploadPool = batchRunnable.getTaskPool(4);
        uploadPool.addLast(process -> {
            HttpClient client = process.getData().get("client");

            String endpoint = process.getData().get("endpoint");
            JsonObject metadata = process.getData().get("metadata");
            String project = CurseForgePropertyKey.PROJECT.getValue();

            MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                    .addTextBody("metadata", gson.toJson(metadata), ContentType.APPLICATION_JSON)
                    .addBinaryBody("file", fileBundle.primaryFile());

            HttpPost request = new HttpPost(endpoint + "/api/projects/" + project + "/upload-file");
            request.setHeader("X-Api-Token", CurseForgePropertyKey.TOKEN.getValue());
            request.setEntity(builder.build());

            try {
                boolean success = client.execute(request, response -> {
                    if (response.getCode() != 200) {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        logger.error("Failed to upload version: {} - {}", response.getCode(), responseBody);
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
            CloseableHttpClient client = process.getData().get("client");
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

package me.hsgamer.mcreleaser.discord;

import com.google.gson.*;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

public class DiscordPlatform implements Platform {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = new Gson();

    private String readDefaultMessage() {
        try (InputStream inputStream = getClass().getResourceAsStream("/default-message.json")) {
            if (inputStream == null) {
                logger.error("Default message file not found in resources");
                return null;
            }
            try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
                return scanner.useDelimiter("\\A").next();
            }
        } catch (IOException e) {
            logger.error("Failed to read default message from resources", e);
            return null;
        }
    }

    private JsonElement postProcess(JsonElement element) {
        if (!element.isJsonObject()) return element;
        JsonObject jsonObject = element.getAsJsonObject();

        if (DiscordPropertyKey.PROFILE_NAME.isPresent()) {
            jsonObject.addProperty("username", DiscordPropertyKey.PROFILE_NAME.getValue());
        }
        if (DiscordPropertyKey.PROFILE_AVATAR.isPresent()) {
            jsonObject.addProperty("avatar_url", DiscordPropertyKey.PROFILE_AVATAR.getValue());
        }

        if (jsonObject.has("embeds") && jsonObject.get("embeds").isJsonArray()) {
            JsonArray embeds = jsonObject.getAsJsonArray("embeds");
            if (!embeds.isEmpty() && embeds.get(0).isJsonObject()) {
                JsonObject firstEmbed = embeds.get(0).getAsJsonObject();

                if (DiscordPropertyKey.COLOR.isPresent()) {
                    String colorStr = DiscordPropertyKey.COLOR.getValue();
                    try {
                        int color;
                        if (colorStr.startsWith("#")) {
                            color = Integer.parseInt(colorStr.substring(1), 16);
                        } else {
                            color = Integer.parseInt(colorStr);
                        }
                        firstEmbed.addProperty("color", color);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid color format: {}", colorStr);
                    }
                }

                if (DiscordPropertyKey.LINKS.isPresent()) {
                    String linksJson = DiscordPropertyKey.LINKS.getValue();
                    try {
                        JsonObject linksObj = JsonParser.parseString(linksJson).getAsJsonObject();
                        StringBuilder linksBuilder = new StringBuilder();
                        for (Map.Entry<String, JsonElement> entry : linksObj.entrySet()) {
                            if (!linksBuilder.isEmpty()) linksBuilder.append("\n");
                            linksBuilder.append("[").append(entry.getKey()).append("](").append(entry.getValue().getAsString()).append(")");
                        }

                        if (!linksBuilder.isEmpty()) {
                            JsonArray fields = firstEmbed.has("fields") && firstEmbed.get("fields").isJsonArray()
                                    ? firstEmbed.getAsJsonArray("fields") : new JsonArray();
                            JsonObject linksField = new JsonObject();
                            linksField.addProperty("name", "Links");
                            linksField.addProperty("value", linksBuilder.toString());
                            fields.add(linksField);
                            firstEmbed.add("fields", fields);
                        }
                    } catch (Exception e) {
                        logger.warn("Invalid links format: {}", linksJson);
                    }
                }
            }
        }
        return jsonObject;
    }

    private JsonElement replacePlaceholders(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject jsonObject = element.getAsJsonObject();
            JsonObject newObject = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                newObject.add(entry.getKey(), replacePlaceholders(entry.getValue()));
            }
            return newObject;
        } else if (element.isJsonArray()) {
            JsonArray jsonArray = element.getAsJsonArray();
            JsonArray newArray = new JsonArray();
            for (JsonElement e : jsonArray) {
                newArray.add(replacePlaceholders(e));
            }
            return newArray;
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) {
                String value = primitive.getAsString();
                value = value
                        .replace("{name}", CommonPropertyKey.NAME.getValue(""))
                        .replace("{version}", CommonPropertyKey.VERSION.getValue(""))
                        .replace("{description}", CommonPropertyKey.DESCRIPTION.getValue(""));
                return new JsonPrimitive(value);
            }
        }
        return element;
    }

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (PropertyKeyUtil.isAbsentAndAnnounce(logger, DiscordPropertyKey.URL)) {
            return Optional.empty();
        }

        BatchRunnable batchRunnable = new BatchRunnable();
        batchRunnable.getTaskPool(1).addLast(process -> {
            String webhookUrl = DiscordPropertyKey.URL.getValue();
            String messageFilePath = DiscordPropertyKey.FILE.getValue();

            String rawContent;
            if (messageFilePath == null || messageFilePath.isEmpty()) {
                rawContent = readDefaultMessage();
            } else {
                Path messageFile = Paths.get(messageFilePath);
                if (!Files.exists(messageFile)) {
                    logger.warn("Message file not found: {}. Using default message.", messageFile);
                    rawContent = readDefaultMessage();
                } else {
                    try {
                        rawContent = Files.readString(messageFile);
                    } catch (IOException e) {
                        logger.error("Failed to read message file: {}. Using default message.", messageFile, e);
                        rawContent = readDefaultMessage();
                    }
                }
            }

            if (rawContent == null) {
                process.complete();
                return;
            }

            JsonElement jsonElement;
            try {
                jsonElement = JsonParser.parseString(rawContent);
            } catch (Exception e) {
                logger.error("Failed to parse message content", e);
                process.complete();
                return;
            }

            jsonElement = replacePlaceholders(jsonElement);
            jsonElement = postProcess(jsonElement);
            String content = gson.toJson(jsonElement);

            logger.info("Sending Discord message: {}", content);

            try (CloseableHttpClient client = HttpClients.createMinimal()) {
                HttpPost post = new HttpPost(webhookUrl);
                post.setEntity(new StringEntity(content, ContentType.APPLICATION_JSON));
                client.execute(post, response -> {
                    if (response.getCode() >= 200 && response.getCode() < 300) {
                        logger.info("Successfully sent Discord message");
                    } else {
                        logger.error("Failed to send Discord message: {} - {}", response.getCode(), EntityUtils.toString(response.getEntity()));
                    }
                    return null;
                });
            } catch (Exception e) {
                logger.error("Failed to send Discord message", e);
            }
            process.next();
        });

        return Optional.of(batchRunnable);
    }
}

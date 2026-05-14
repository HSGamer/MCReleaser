package me.hsgamer.mcreleaser.discord;

import com.google.gson.*;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import me.hsgamer.mcreleaser.core.util.StringUtil;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

public class DiscordPlatform implements Platform {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = new Gson();

    private JsonElement getDefaultMessage() {
        JsonObject jsonObject = new JsonObject();
        String project = CommonPropertyKey.PROJECT.getValue("");
        String version = CommonPropertyKey.VERSION.getValue("");

        StringBuilder contentBuilder = new StringBuilder();
        if (DiscordPropertyKey.MENTIONS.isPresent()) {
            String[] mentions = StringUtil.splitCommaOrSpace(DiscordPropertyKey.MENTIONS.getValue());
            boolean hasMentions = false;
            for (String id : mentions) {
                id = id.trim();
                if (id.isEmpty()) continue;
                contentBuilder.append("<@&").append(id).append("> ");
                hasMentions = true;
            }
            if (hasMentions) {
                contentBuilder.append("\n");
            }
        }

        if (project.isEmpty()) {
            contentBuilder.append("Version **").append(version).append("** has been released!");
        } else {
            contentBuilder.append("**").append(project).append("** has been updated to `").append(version).append("`!");
        }
        jsonObject.addProperty("content", contentBuilder.toString().trim());

        if (DiscordPropertyKey.PROFILE_NAME.isPresent()) {
            jsonObject.addProperty("username", DiscordPropertyKey.PROFILE_NAME.getValue());
        }
        if (DiscordPropertyKey.PROFILE_AVATAR.isPresent()) {
            jsonObject.addProperty("avatar_url", DiscordPropertyKey.PROFILE_AVATAR.getValue());
        }

        JsonArray embeds = new JsonArray();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", CommonPropertyKey.NAME.getValue(""));
        embed.addProperty("description", CommonPropertyKey.DESCRIPTION.getValue(""));

        int color = 5814783;
        if (DiscordPropertyKey.COLOR.isPresent()) {
            String colorStr = DiscordPropertyKey.COLOR.getValue();
            try {
                if (colorStr.startsWith("#")) {
                    color = Integer.parseInt(colorStr.substring(1), 16);
                } else {
                    color = Integer.parseInt(colorStr);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid color format: {}", colorStr);
            }
        }
        embed.addProperty("color", color);

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
                    JsonArray fields = new JsonArray();
                    JsonObject linksField = new JsonObject();
                    linksField.addProperty("name", "Links");
                    linksField.addProperty("value", linksBuilder.toString());
                    fields.add(linksField);
                    embed.add("fields", fields);
                }
            } catch (Exception e) {
                logger.warn("Invalid links format: {}", linksJson);
            }
        }

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Created by MCReleaser");
        embed.add("footer", footer);

        embeds.add(embed);
        jsonObject.add("embeds", embeds);
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
                        .replace("{project}", CommonPropertyKey.PROJECT.getValue(""))
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

            JsonElement jsonElement;
            if (messageFilePath == null || messageFilePath.isEmpty()) {
                jsonElement = getDefaultMessage();
            } else {
                Path messageFile = Paths.get(messageFilePath);
                if (!Files.exists(messageFile)) {
                    logger.error("Message file not found: {}", messageFile);
                    process.complete();
                    return;
                }
                try {
                    String rawContent = Files.readString(messageFile);
                    jsonElement = replacePlaceholders(JsonParser.parseString(rawContent));
                } catch (Exception e) {
                    logger.error("Failed to read or parse message file: {}", messageFile, e);
                    process.complete();
                    return;
                }
            }

            String content = gson.toJson(jsonElement);

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

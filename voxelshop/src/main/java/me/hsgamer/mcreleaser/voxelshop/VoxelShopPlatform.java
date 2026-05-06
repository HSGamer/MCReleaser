package me.hsgamer.mcreleaser.voxelshop;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.property.PropertyKey;
import me.hsgamer.mcreleaser.core.property.PropertyPrefix;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import me.hsgamer.mcreleaser.renderer.html.MarkdownToHTMLConverter;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class VoxelShopPlatform implements Platform {
    static {
        // This is here to remain the backward compatibility for the old property keys from Polymart
        PropertyPrefix polymart = new PropertyPrefix("polymart");
        PropertyKey polymartKey = polymart.key("key");
        PropertyKey polymartResource = polymart.key("resource");
        PropertyKey polymartTag = polymart.key("tag");

        if (polymartKey.isPresent() && VoxelShopPropertyKey.KEY.isAbsent()) {
            VoxelShopPropertyKey.KEY.setValue(polymartKey.getValue());
        }
        if (polymartResource.isPresent() && VoxelShopPropertyKey.RESOURCE.isAbsent()) {
            VoxelShopPropertyKey.RESOURCE.setValue(polymartResource.getValue());
        }
        if (polymartTag.isPresent() && VoxelShopPropertyKey.TAG.isAbsent()) {
            VoxelShopPropertyKey.TAG.setValue(polymartTag.getValue());
        }
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (PropertyKeyUtil.isAbsentAndAnnounce(logger, VoxelShopPropertyKey.KEY, VoxelShopPropertyKey.RESOURCE)) {
            return Optional.empty();
        }

        BatchRunnable batchRunnable = new BatchRunnable();
        TaskPool connectPool = batchRunnable.getTaskPool(0);
        connectPool.addLast(process -> {
            CloseableHttpClient client = HttpClients.createMinimal();
            process.getData().put("client", client);
            logger.info("Prepared client");
            process.next();
        });

        TaskPool preparePool = batchRunnable.getTaskPool(1);
        preparePool.addLast(process -> {
            Map<String, String> jsonBody = new HashMap<>();
            jsonBody.put("api_key", VoxelShopPropertyKey.KEY.getValue());
            jsonBody.put("product", VoxelShopPropertyKey.RESOURCE.getValue());
            jsonBody.put("version", CommonPropertyKey.VERSION.getValue());
            jsonBody.put("file_name", fileBundle.primaryFile().getName());
            jsonBody.put("tag", VoxelShopPropertyKey.TAG.getValue("release"));

            if (CommonPropertyKey.NAME.getValue() != null && !CommonPropertyKey.NAME.getValue().isEmpty()) {
                jsonBody.put("update_title", CommonPropertyKey.NAME.getValue());
            }

            try {
                String description = CommonPropertyKey.DESCRIPTION.getValue();
                String textDescription = MarkdownToHTMLConverter.convert(description);
                jsonBody.put("update_description", textDescription);
            } catch (Exception e) {
                logger.error("Failed to convert description", e);
                process.complete();
                return;
            }

            Gson gson = new Gson();
            String jsonString = gson.toJson(jsonBody);
            HttpEntity entity = new StringEntity(jsonString, ContentType.APPLICATION_JSON);
            process.getData().put("entity", entity);
            process.getData().put("file", fileBundle.primaryFile());
            logger.info("Prepared update request");
            process.next();
        });

        TaskPool uploadPool = batchRunnable.getTaskPool(2);
        uploadPool.addLast(process -> {
            HttpClient client = process.getData().get("client");
            HttpEntity entity = process.getData().get("entity");

            HttpPost request = new HttpPost("https://api.voxel.shop/v1/doPostUpdate");
            request.setEntity(entity);

            try {
                boolean success = client.execute(request, response -> {
                    if (response.getCode() != 200) {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        logger.error("Failed to post update: {} - {}", response.getCode(), responseBody);
                        return false;
                    }

                    try {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        JsonObject responseNode = JsonParser.parseString(responseBody).getAsJsonObject();
                        JsonObject uploadNode = responseNode.getAsJsonObject("response").getAsJsonObject("upload");

                        String uploadUrl = uploadNode.get("url").getAsString();

                        process.getData().put("uploadUrl", uploadUrl);
                        return true;
                    } catch (Exception e) {
                        logger.error("Failed to parse upload response", e);
                        return false;
                    }
                });
                if (success) {
                    logger.info("Posted update successfully");
                    process.next();
                } else {
                    process.complete();
                }
            } catch (IOException e) {
                logger.error("Failed to post update", e);
                process.complete();
            }
        });

        uploadPool.addLast(process -> {
            HttpClient client = process.getData().get("client");
            String uploadUrl = process.getData().get("uploadUrl");

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            // Add the file
            builder.addBinaryBody("file", process.getData().<File>get("file"));

            HttpPut uploadRequest = new HttpPut(uploadUrl);
            uploadRequest.setEntity(builder.build());

            try {
                boolean success = client.execute(uploadRequest, response -> {
                    if (response.getCode() != 200 && response.getCode() != 204) {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        logger.error("Failed to upload file: {} - {}", response.getCode(), responseBody);
                        return false;
                    }
                    return true;
                });
                if (success) {
                    logger.info("Uploaded file successfully");
                }
                process.next();
            } catch (IOException e) {
                logger.error("Failed to upload file", e);
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

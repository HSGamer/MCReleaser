package me.hsgamer.mcreleaser.polymart;

import com.google.gson.Gson;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class PolymartPlatform implements Platform {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public Optional<BatchRunnable> createUploadRunnable(FileBundle fileBundle) {
        if (PropertyKeyUtil.isAbsentAndAnnounce(logger, PolymartPropertyKey.KEY, PolymartPropertyKey.RESOURCE)) {
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

        TaskPool preparePool = batchRunnable.getTaskPool(1);
        preparePool.addLast(process -> {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("api_key", PolymartPropertyKey.KEY.getValue());
            builder.addTextBody("resource_id", PolymartPropertyKey.RESOURCE.getValue());
            builder.addTextBody("version", CommonPropertyKey.VERSION.getValue());
            builder.addTextBody("title", CommonPropertyKey.NAME.getValue());

            try {
                String description = CommonPropertyKey.DESCRIPTION.getValue();
                String htmlDescription = MarkdownToHTMLConverter.convert(description);
                builder.addTextBody("message", htmlDescription);
            } catch (Exception e) {
                logger.error("Failed to convert description", e);
                process.complete();
                return;
            }

            builder.addBinaryBody("file", fileBundle.primaryFile());

            if (PolymartPropertyKey.BETA.asBoolean(false)) {
                builder.addTextBody("beta", "1");
            }
            if (PolymartPropertyKey.SNAPSHOT.asBoolean(false)) {
                builder.addTextBody("snapshot", "1");
            }

            HttpEntity entity = builder.build();
            process.getData().put("entity", entity);
            logger.info("Prepared entity");
            process.next();
        });

        TaskPool uploadPool = batchRunnable.getTaskPool(2);
        uploadPool.addLast(process -> {
            HttpClient client = (HttpClient) process.getData().get("client");
            HttpEntity entity = (HttpEntity) process.getData().get("entity");

            HttpPost request = new HttpPost("https://api.polymart.org/v1/postUpdate");
            request.setEntity(entity);

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

package me.hsgamer.mcreleaser.curseforge;

import com.google.gson.Gson;
import me.hsgamer.hscore.task.BatchRunnable;
import me.hsgamer.hscore.task.element.TaskPool;
import me.hsgamer.mcreleaser.core.file.FileBundle;
import me.hsgamer.mcreleaser.core.platform.Platform;
import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.util.PropertyKeyUtil;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

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
            if (PropertyKeyUtil.isAbsentAndAnnounce(logger, CurseForgePropertyKey.GAME_VERSIONS)) {
                return Optional.empty();
            }

        } else if (type.equalsIgnoreCase("hytale")) {

        } else {
            logger.error("Unknown platform type. Supported types: MINECRAFT, HYTALE");
            return Optional.empty();
        }

        TaskPool uploadPool = batchRunnable.getTaskPool(3);

        return Optional.of(batchRunnable);
    }
}

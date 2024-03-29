package me.hsgamer.mcreleaser.core.util;

import me.hsgamer.mcreleaser.core.property.CommonPropertyKey;
import me.hsgamer.mcreleaser.core.property.PropertyKey;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class PropertyKeyUtil {
    public static boolean isAbsentAndAnnounce(Logger logger, PropertyKey... keys) {
        List<PropertyKey> missingKeys = new ArrayList<>();
        for (PropertyKey key : keys) {
            if (key.isAbsent()) {
                missingKeys.add(key);
            }
        }

        if (missingKeys.isEmpty()) {
            return false;
        } else {
            if (CommonPropertyKey.ANNOUNCE_MISSING_KEY.asBoolean(false)) {
                StringBuilder builder = new StringBuilder("The following keys are missing: ");
                for (PropertyKey missingKey : missingKeys) {
                    builder.append(missingKey.getKey()).append(", ");
                }
                builder.delete(builder.length() - 2, builder.length());
                logger.error(builder.toString());
            }
            return true;
        }
    }
}

package me.hsgamer.mcreleaser.polymart;

import me.hsgamer.mcreleaser.core.property.PropertyKey;
import me.hsgamer.mcreleaser.core.property.PropertyPrefix;

public interface PolymartPropertyKey {
    PropertyPrefix POLYMART = new PropertyPrefix("polymart");
    PropertyKey KEY = POLYMART.key("key");
    PropertyKey RESOURCE = POLYMART.key("resource");
}

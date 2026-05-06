package me.hsgamer.mcreleaser.voxelshop;

import me.hsgamer.mcreleaser.core.property.PropertyKey;
import me.hsgamer.mcreleaser.core.property.PropertyPrefix;

public interface VoxelShopPropertyKey {
    PropertyPrefix VOXELSHOP = new PropertyPrefix("voxelshop");
    PropertyKey KEY = VOXELSHOP.key("key");
    PropertyKey RESOURCE = VOXELSHOP.key("resource");
    PropertyKey TAG = VOXELSHOP.key("tag");
}

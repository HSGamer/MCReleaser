package me.hsgamer.mcreleaser.discord;

import me.hsgamer.mcreleaser.core.property.PropertyKey;
import me.hsgamer.mcreleaser.core.property.PropertyPrefix;

public interface DiscordPropertyKey {
    PropertyPrefix DISCORD = new PropertyPrefix("discord");
    PropertyKey URL = DISCORD.key("url");
    PropertyKey FILE = DISCORD.key("file");
    PropertyKey PROFILE_NAME = DISCORD.key("profileName");
    PropertyKey PROFILE_AVATAR = DISCORD.key("profileAvatar");
    PropertyKey COLOR = DISCORD.key("color");
    PropertyKey LINKS = DISCORD.key("links");
}

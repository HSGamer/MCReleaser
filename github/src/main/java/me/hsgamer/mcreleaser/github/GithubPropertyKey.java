package me.hsgamer.mcreleaser.github;

import me.hsgamer.mcreleaser.core.property.PropertyKey;
import me.hsgamer.mcreleaser.core.property.PropertyPrefix;

public interface GithubPropertyKey {
    PropertyPrefix GITHUB = new PropertyPrefix("github");
    PropertyKey TOKEN = GITHUB.key("token");
    PropertyKey REPOSITORY = GITHUB.key("repository");
    PropertyKey TAG = GITHUB.key("tag");
    PropertyKey DRAFT = GITHUB.key("draft");
    PropertyKey PRERELEASE = GITHUB.key("prerelease");
}

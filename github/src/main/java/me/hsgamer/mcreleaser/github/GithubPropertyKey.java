package me.hsgamer.mcreleaser.github;

import me.hsgamer.mcreleaser.core.property.PropertyKey;

public interface GithubPropertyKey {
    PropertyKey TOKEN = new PropertyKey("token");
    PropertyKey REPOSITORY = new PropertyKey("repository");
    PropertyKey TAG = new PropertyKey("tag");
    PropertyKey DRAFT = new PropertyKey("draft");
    PropertyKey PRERELEASE = new PropertyKey("prerelease");
}

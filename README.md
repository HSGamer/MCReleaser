# MCReleaser

A program to publish artifacts to multiple Minecraft-related platforms

## Usage

### CLI

```shell
java 
    -Dname="Artifact Name"
    -Dversion="Artifact Version"
    -Ddescription="Artifact Description"
    -DgameVersions="Game Versions"
    -jar mcreleaser.jar
```

> Use `-D` to set environment variables as properties in camel case, e.g., `GITHUB_TOKEN` becomes `-DgithubToken`

### Docker

```shell
docker run
    -e NAME="Artifact Name"
    -e VERSION="Artifact Version"
    -e DESCRIPTION="Artifact Description"
    -e GAME_VERSIONS="Game Versions"
    ghcr.io/hsgamer/mcreleaser:master
```

### Github Actions

Check [action-mcreleaser](https://github.com/HSGamer/action-mcreleaser)

## Environment Variables

### Common

| Name                   | Description                                  | Required | Default |
|------------------------|----------------------------------------------|----------|---------|
| `NAME`                 | The name of the artifact                     | Yes      |         |
| `VERSION`              | The version of the artifact                  | Yes      |         |
| `DESCRIPTION`          | The description of the artifact              | Yes      |         |
| `GAME_VERSIONS`        | The game versions that the artifact supports | No       |         |
| `ANNOUNCE_MISSING_KEY` | Whether to announce the missing variables    | No       | `false` |

### Github

| Name                | Description                                     | Required | Default |
|---------------------|-------------------------------------------------|----------|---------|
| `GITHUB_TOKEN`      | The Github token to publish the artifact        | Yes      |         |
| `GITHUB_REPOSITORY` | The Github repository to publish the artifact   | Yes      |         |
| `GITHUB_REF`        | The Github ref to publish the artifact          | Yes      |         |
| `GITHUB_DRAFT`      | Whether to publish the artifact as a draft      | No       | `false` |
| `GITHUB_PRERELEASE` | Whether to publish the artifact as a prerelease | No       | `false` |

### Hangar

| Name                   | Description                                                                       | Required | Default |
|------------------------|-----------------------------------------------------------------------------------|----------|---------|
| `HANGAR_KEY`           | The Hangar API key to publish the artifact                                        | Yes      |         |
| `HANGAR_PROJECT`       | The Hangar project to publish the artifact                                        | Yes      |         |
| `HANGAR_CHANNEL`       | The Hangar channel to publish the artifact                                        | Yes      |         |
| `HANGAR_GAME_VERSIONS` | The game versions that the artifact supports <br> Will use `GAME_VERSIONS` if set | Yes      |         |
| `HANGAR_PLATFORM`      | The Hangar platform to publish the artifact                                       | No       | Release |
| `DEPENDENCIES`         | The dependencies of the artifact                                                  | No       |         |

### Modrinth

| Name                     | Description                                                                       | Required | Default |
|--------------------------|-----------------------------------------------------------------------------------|----------|---------|
| `MODRINTH_TOKEN`         | The Modrinth token to publish the artifact                                        | Yes      |         |
| `MODRINTH_PROJECT`       | The Modrinth project to publish the artifact                                      | Yes      |         |
| `MODRINTH_GAME_VERSIONS` | The game versions that the artifact supports <br> Will use `GAME_VERSIONS` if set | Yes      |         |
| `MODRINTH_LOADERS`       | The loaders that the artifact supports                                            | Yes      |         |
| `MODRINTH_VERSION_TYPE`  | The Modrinth version type to publish the artifact                                 | No       | Release |
| `MODRINTH_DEPENDENCIES`  | The dependencies of the artifact                                                  | No       |         |
| `MODRINTH_FEATURED`      | Whether to feature the artifact                                                   | No       | `true`  |
| `MODRINTH_UNFEATURE`     | Whether to unfeature other versions                                               | No       | `true`  |

### Polymart

| Name                | Description                                   | Required | Default |
|---------------------|-----------------------------------------------|----------|---------|
| `POLYMART_KEY`      | The Polymart API key to publish the artifact  | Yes      |         |
| `POLYMART_RESOURCE` | The Polymart resource to publish the artifact | Yes      |         |
| `POLYMART_BETA`     | Whether to publish the artifact as a beta     | No       | `false` |
| `POLYMART_SNAPSHOT` | Whether to publish the artifact as a snapshot | No       | `false` |

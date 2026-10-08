# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
[markdownlint](https://dlaa.me/markdownlint/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-10-02

### changed in 2.0.0

- Upgraded to Senzing v4 (`senzing/senzingsdk-runtime:4.4.2`, Senzing Java SDK `sz-sdk` 4.4.2)
- Upgraded to elasticsearch-java 9.5.4
- Upgraded to Java 25
- Renamed the repository to `elasticsearch-v4` and the Docker image to `senzing/elasticsearch-v4`
- Renamed the Java package to `com.senzing.elasticsearch`, the main class to `SenzingToElastic`, and the jar to `elasticsearch-v4.jar`
- `ELASTIC_URL` replaces `ELASTIC_HOSTNAME` and `ELASTIC_PORT`; it takes the full URL, so `https` and credentials in the URL are supported
- `ELASTIC_URL` accepts hostnames with underscores, such as Docker container names, over `http`
- Docker image is now a multi-stage build
- Removed `postgresql-client` from the Docker image
- The default Elasticsearch index name is now `senzing-index` instead of `g2index`
- Indexes each entity with its entity ID as the document `_id`, so re-running the indexer replaces documents instead of duplicating them
- Records without `JSON_DATA` no longer stop the export
- Exits with status 1 instead of -1 when `SENZING_ENGINE_CONFIGURATION_JSON` is not set
- Exits with status 1 when `ELASTIC_URL` is not a valid `http` or `https` URL
- Exits with a non-zero status if any entity fails to index, or if elasticsearch doesn't report on every entity
- Errors are written to standard error; indexing failures report the entity ID and error type, not the error reason
- Elasticsearch client warnings and errors are logged instead of discarded
- Added unit tests and JaCoCo coverage reporting
- Removed the unused `JsonFieldValueFinder` class and identifier helper methods
- docker-compose uses the official `postgres` image and `senzing/init-database`
- docker-compose names the default database `senzing` instead of `G2` and stores it in a named volume
- docker-compose passes the engine configuration to `senzing/init-database` as `SENZING_TOOLS_CORE_SETTINGS` instead of `SENZING_TOOLS_ENGINE_CONFIGURATION_JSON`, which it does not read

## [1.0.0] - 2023-07-06

### changed in 1.0.0

- Project remade; uses elasticsearch 8.0
- Now only posts all currently loaded G2 entities to elastic for searching

## [0.0.1] - 2018-12-22

### Added to 0.0.1

- Initial prototype

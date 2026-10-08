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
- Docker image is now a multi-stage build
- Removed `postgresql-client` from the Docker image
- The default Elasticsearch index name is now `senzing-index` instead of `g2index`
- Exits with status 1 instead of -1 when `SENZING_ENGINE_CONFIGURATION_JSON` is not set
- Exits with status 1 when `ELASTIC_PORT` is not a valid port number
- Exits with a non-zero status if any entity fails to index
- Added unit tests
- docker-compose uses the official `postgres` image and `senzing/init-database`
- docker-compose names the default database `senzing` instead of `G2` and stores it in a named volume

## [1.0.0] - 2023-07-06

### changed in 1.0.0

- Project remade; uses elasticsearch 8.0
- Now only posts all currently loaded G2 entities to elastic for searching

## [0.0.1] - 2018-12-22

### Added to 0.0.1

- Initial prototype

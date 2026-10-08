ARG BASE_IMAGE=senzing/senzingsdk-runtime:4.4.2@sha256:1f86d22ca02fe4558010420d76e64c8536a9c6dc53783ddda3da5dfda34d5fb9
ARG BUILDER_IMAGE=maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976

# -----------------------------------------------------------------------------
# Stage: senzing_runtime
# -----------------------------------------------------------------------------

FROM ${BASE_IMAGE} AS senzing_runtime

# -----------------------------------------------------------------------------
# Stage: builder
# -----------------------------------------------------------------------------

# The jar is platform-independent, so build it once on the build platform instead of under emulation.

FROM --platform=$BUILDPLATFORM ${BUILDER_IMAGE} AS builder

# The Senzing v4 Java SDK is not on Maven Central; install the copy that ships with the runtime.

COPY --from=senzing_runtime /opt/senzing/er/sdk/java/sz-sdk.jar /tmp/sz-sdk.jar
COPY elasticsearch /build
WORKDIR /build

RUN SZ_SDK_VERSION="$(mvn -B -q help:evaluate -Dexpression=sz-sdk.version -DforceStdout)" \
  && mvn -B install:install-file \
      -Dfile=/tmp/sz-sdk.jar \
      -DgroupId=com.senzing \
      -DartifactId=sz-sdk \
      -Dversion="${SZ_SDK_VERSION}" \
      -Dpackaging=jar \
  && mvn -B clean package

# -----------------------------------------------------------------------------
# Stage: final
# -----------------------------------------------------------------------------

FROM senzing_runtime

ENV REFRESHED_AT=2026-10-02

LABEL Name="senzing/elasticsearch-v4" \
      Maintainer="support@senzing.com" \
      Version="2.0.0"

# Run as "root" for system installation.

USER root

RUN apt-get update \
  && apt-get -y install --no-install-recommends \
      openjdk-25-jre-headless \
  && apt-get -y clean \
  && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/target/g2elasticsearch-2.0.0.jar /app/

HEALTHCHECK CMD test -f /app/g2elasticsearch-2.0.0.jar

USER 1001

WORKDIR /app
CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "g2elasticsearch-2.0.0.jar"]

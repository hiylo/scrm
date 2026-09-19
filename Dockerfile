# Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
# Project : scrm
# File : Dockerfile
# Date : 2026/09/18 00:00:00
# Author : Hsi Chu
# Contact : hiylo@live.com
#
# 后端运行镜像 (多阶段构建):
#   阶段 1 maven  编译打包出 scrm-server jar
#   阶段 2 jre    仅保留运行时依赖, 非 root 用户运行

# ---------- 阶段 1: 编译 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先拷贝 pom 以利用依赖缓存层
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------- 阶段 2: 运行 ----------
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appuser \
    && useradd -r -g appuser appuser

WORKDIR /app
COPY --from=build /build/target/scrm-server-*.jar app.jar
RUN chown -R appuser:appuser /app

USER appuser
EXPOSE 8840

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -f http://localhost:8840/actuator/health || exit 1

ENTRYPOINT ["java", "-Xmx1024m", "-jar", "app.jar"]

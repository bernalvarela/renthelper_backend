# Imagen nativa de GraalVM del backend de RentHelper (la API y el bundle del frontend que va
# commiteado en src/main/resources/static). Sale un único binario totalmente estático (musl): sin
# JVM y sin glibc en tiempo de ejecución, corre en cualquier x86-64 y arranca en menos de un
# segundo.
#
#   docker build -t renthelper-backend .
#
# Es la variante autocontenida, que compila dentro de Docker: tarda y pide mucha memoria (más de
# 7 GB). En CI el binario se compila en el runner y sólo se empaqueta con Dockerfile.runtime.
# Dockerfile.jvm queda como alternativa clásica sobre la JVM.

# ---- compilación: GraalVM JDK 25 con native-image y la toolchain musl ----------------------
FROM ghcr.io/graalvm/native-image-community:25-muslib AS build
WORKDIR /src

# Primero las dependencias, para que queden en caché entre cambios de código
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B -Pnative,native-static dependency:go-offline

COPY src src
# Los tests se pasan en CI antes de construir la imagen. Aquí Spring hace el procesado AOT
# (perfil "native" del parent) y native-image compila en estático contra musl.
RUN ./mvnw -B -Pnative,native-static -DskipTests native:compile \
    && ls -la target/renthelper-backend

# ---- ejecución -----------------------------------------------------------------------------
# El binario es estático: la base sólo aporta un usuario, certificados de CA (Telegram, SMTP,
# proveedores de LLM), datos de zona horaria y wget para el healthcheck de docker-compose.
FROM debian:bookworm-slim
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates tzdata wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system renthelper && useradd --system --gid renthelper --home /app renthelper
USER renthelper

COPY --from=build /src/target/renthelper-backend /app/renthelper-backend

EXPOSE 8080

# La imagen nativa acepta los flags de heap de siempre (p. ej. -Xmx256m)
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec /app/renthelper-backend $JAVA_OPTS"]

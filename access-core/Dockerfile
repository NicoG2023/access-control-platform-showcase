# Etapa 1: Construcción de la aplicación (Gradle + Mandrel) - Java 21
FROM quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21 AS build

WORKDIR /workspace
USER root

# Instalar herramientas necesarias para Gradle
RUN microdnf install -y findutils && microdnf clean all

# Copiar solo los archivos relevantes para `access-core`
COPY ../gradlew ../gradlew.bat ../gradle.properties ../settings.gradle ./
COPY ../gradle ./gradle/
COPY ./access-core ./access-core

# Ejecutar la construcción nativa para `access-core`
RUN echo "Iniciando la construcción nativa de Quarkus" && \
    ./gradlew :access-core:build -Dquarkus.native.enabled=true --no-daemon -Dquarkus.native.container-build=true --stacktrace --info && \
    echo "Construcción nativa completada"

# Verificación: Mostrar el contenido del directorio donde se debería generar el binario nativo
RUN echo "Verificar si el binario nativo existe en la ruta predeterminada" && \
    ls -l /workspace/access-core/build/

# Verificación: Verificar si el directorio `native-image` existe
RUN echo "Verificar si el directorio native-image existe" && \
    ls -l /workspace/access-core/build/native-image

# Buscar específicamente el archivo "runner" en el directorio build para asegurar que se generó
RUN echo "Buscando el archivo runner en el directorio build..." && \
    find /workspace/access-core/build -name runner

# Etapa 2: Crear la imagen final liviana con el binario nativo
FROM registry.access.redhat.com/ubi9/ubi-minimal:9.3 AS runtime

WORKDIR /app

# Copiar el binario nativo generado desde la etapa build
COPY --from=build /workspace/access-core/build/native-image/runner /app/application

# Exponer el puerto donde la aplicación se ejecutará
EXPOSE 8080

# Comando para ejecutar el binario nativo
CMD ["./application"]

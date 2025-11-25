FROM eclipse-temurin:21 AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew
RUN ./gradlew bootJar
RUN mkdir -p /opt/fop/config /opt/fop/resources

FROM gcr.io/distroless/java21-debian12 AS final

LABEL maintainer="Robert Wolf <hello@robertwolf.dev>" \
      org.opencontainers.image.title="Apache FOP REST Service" \
      org.opencontainers.image.description="REST API for generate PDFs via XSL-FO (Apache FOP)" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.authors="Robert Wolf" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.source="https://github.com/rwolfdev/fopservice" \
      org.opencontainers.image.documentation="https://github.com/rwolfdev/fopservice#readme"


COPY --from=build /app/build/libs/*.jar /app.jar
COPY --from=build /opt/fop /opt/fop

ENV FOP_CONFIG_PATH=/opt/fop/config/fop.xconf
ENV FOP_RESOURCE_BASE=/opt/fop/resources

VOLUME ["/opt/fop/config", "/opt/fop/resources"]
ENTRYPOINT ["java", "-jar", "/app.jar"]

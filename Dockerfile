# Build the jar first:  mvn clean package -DskipTests   (produces target/dvmp.jar)
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 1001 dvmp && mkdir -p /app/data /app/uploads && chown -R dvmp /app
COPY target/dvmp.jar app.jar
USER dvmp
EXPOSE 8080
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=dev
VOLUME ["/app/data", "/app/uploads"]
ENTRYPOINT ["java","-jar","app.jar"]

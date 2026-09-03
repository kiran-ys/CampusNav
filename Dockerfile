FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY src ./src
COPY scripts ./scripts
RUN /bin/sh scripts/compile.sh

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S campusnav && adduser -S campusnav -G campusnav
COPY --from=build /app/out/main ./out/main
COPY frontend ./frontend
COPY lib ./lib
USER campusnav
ENV CAMPUSNAV_HOST=0.0.0.0
EXPOSE 8080
CMD ["java","-cp","out/main:lib/*","com.campusnav.api.ApiMain"]

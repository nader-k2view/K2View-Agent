# Use Ubuntu as the base image
FROM vegardit/graalvm-maven:22.3.1-java17 as build

# Set the working directory
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline
COPY src/ /app/src/
RUN mvn package -Pnative -DskipTests

FROM ubuntu:latest
COPY --from=build /app/target/K2v-Agent /usr/local/bin/
CMD ["K2v-Agent"]

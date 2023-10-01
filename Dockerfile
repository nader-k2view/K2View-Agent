# Use Ubuntu as the base image
FROM vegardit/graalvm-maven:22.3.1-java17 as build

# Set the working directory
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline
COPY src/ /app/src/
RUN mvn package -Pnative -DskipTests

FROM alpine:latest

ENV GLIBC_REPO=https://github.com/sgerrand/alpine-pkg-glibc
ENV GLIBC_VERSION=2.30-r0

RUN set -ex && \
    apk --update add libstdc++ curl ca-certificates && \
    for pkg in glibc-${GLIBC_VERSION} glibc-bin-${GLIBC_VERSION}; \
        do curl -sSL ${GLIBC_REPO}/releases/download/${GLIBC_VERSION}/${pkg}.apk -o /tmp/${pkg}.apk; done

# Install glibc with overwrite option
RUN apk add --allow-untrusted --force-overwrite /tmp/*.apk

# Fix the symbolic link issue after glibc installation
RUN ln -sf /usr/glibc-compat/lib/ld-2.30.so /usr/glibc-compat/lib/ld-linux-x86-64.so.2

# Cleanup
RUN rm -v /tmp/*.apk && \
    /usr/glibc-compat/sbin/ldconfig /lib /usr/glibc-compat/lib

COPY --from=build /app/target/K2v-Agent /usr/local/bin/
CMD ["K2v-Agent"]

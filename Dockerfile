FROM eclipse-temurin:17-jdk-jammy

ARG DEBIAN_FRONTEND=noninteractive
ENV TZ=Etc/UTC

RUN apt update && apt install -y \
wget \
git \
build-essential \
ant \
unzip \
&& rm -rf /var/lib/apt/lists/*

ENV MVND_HOME=/opt/mvnd \
    MAVEN_HOME=/opt/maven \
    PATH=/opt/mvnd/bin:/opt/maven/bin:$PATH

RUN set -eux; \
    ARCH="$(uname -m)"; \
    if [ "$ARCH" = "x86_64" ] || [ "$ARCH" = "amd64" ]; then \
        echo "Installing native mvnd for $ARCH"; \
        curl -fsSL "https://dlcdn.apache.org/maven/mvnd/1.0.2/maven-mvnd-1.0.2-linux-amd64.zip" -o /tmp/mvnd.zip; \
        unzip -q /tmp/mvnd.zip -d /opt; \
        mv /opt/maven-mvnd-1.0.2-linux-amd64 /opt/mvnd; \
        ln -s /opt/mvnd/bin/mvnd /usr/local/bin/mvnd; \
        ln -s /opt/mvnd/bin/mvnd /usr/local/bin/mvn; \
        rm /tmp/mvnd.zip; \
    else \
        echo "No native mvnd for $ARCH, falling back to Maven"; \
        curl -fsSL "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz" \
          -o /tmp/maven.tgz; \
        mkdir -p "$MAVEN_HOME"; \
        tar -xzf /tmp/maven.tgz -C "$MAVEN_HOME" --strip-components=1; \
        rm /tmp/maven.tgz; \
        # make mvnd an alias so build scripts stay unchanged
        printf '#!/usr/bin/env bash\nexec mvn "$@"\n' > /usr/local/bin/mvnd; \
        chmod +x /usr/local/bin/mvnd; \
    fi

# ENV MVND_HOME=/usr/local/mvnd
# ENV PATH=$MVND_HOME/bin:$PATH

RUN adduser --disabled-password --gecos 'dog' nonroot


RUN cat <<'INLINE_SCRIPT' > /root/setup_repo.sh
#!/bin/bash
set -euxo pipefail
git clone -o origin https://github.com/jenkinsci/dependency-track-plugin /testbed
chmod -R 777 /testbed
cd /testbed
git reset --hard 81f46bdc1db6743193e0fdeabf335e131faca074
git remote remove origin
apt-get update -y
apt-get install -y nodejs npm
# run full test phase ONLINE so all plugin/runtime deps (incl. surefire-junit-platform) get cached
mvn -B -Dmaven.resolver.transport=wagon test -Dmaven.test.failure.ignore=true
# quick compile check OFFLINE (network disabled) – also skips npm step
mvn -B --offline -Dmaven.resolver.transport=wagon -Dexec.skip=true test-compile
INLINE_SCRIPT
RUN chmod +x /root/setup_repo.sh
RUN /bin/bash /root/setup_repo.sh

WORKDIR /testbed/
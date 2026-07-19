FROM jenkins/jenkins:lts-jdk17

USER root

# Install Docker CLI + gosu (for privilege-drop in entrypoint)
RUN apt-get update && \
    apt-get install -y ca-certificates curl gnupg lsb-release gosu && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    chmod a+r /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
         https://download.docker.com/linux/debian $(lsb_release -cs) stable" \
         > /etc/apt/sources.list.d/docker.list && \
    apt-get update && \
    apt-get install -y docker-ce-cli && \
    rm -rf /var/lib/apt/lists/*

# Pre-create docker group with GID 999 (standard Linux); entrypoint corrects it at runtime
RUN groupadd -f -g 999 docker && usermod -aG docker jenkins

# Entrypoint: realigns docker group GID to match the mounted socket (needed on macOS)
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

USER jenkins

# Install plugins at build time so they are ready on first start
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# Disable setup wizard — JCasC handles configuration
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Xmx1g"
ENV CASC_JENKINS_CONFIG=/var/jenkins_casc

# Must stay root so entrypoint can call groupmod/usermod before dropping to jenkins
USER root
ENTRYPOINT ["/docker-entrypoint.sh"]

FROM jenkins/jenkins:lts-jdk17

USER root

# Install Docker CLI so Jenkins can run docker build / docker push
RUN apt-get update && \
    apt-get install -y ca-certificates curl gnupg lsb-release && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    chmod a+r /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
         https://download.docker.com/linux/debian $(lsb_release -cs) stable" \
         > /etc/apt/sources.list.d/docker.list && \
    apt-get update && \
    apt-get install -y docker-ce-cli && \
    rm -rf /var/lib/apt/lists/*

# Add jenkins user to docker group (GID 999 = standard docker group on Linux)
RUN groupadd -f -g 999 docker && usermod -aG docker jenkins

USER jenkins

# Install plugins at build time so they are ready on first start
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

# Disable setup wizard — JCasC handles configuration
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Xmx1g"
ENV CASC_JENKINS_CONFIG=/var/jenkins_casc

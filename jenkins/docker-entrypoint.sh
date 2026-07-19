#!/bin/bash
set -e

if [ -S /var/run/docker.sock ]; then
    SOCKET_GID=$(stat -c '%g' /var/run/docker.sock)
    if [ "$SOCKET_GID" = "0" ]; then
        # macOS Docker Desktop: socket owned by root:root — make it world-accessible
        chmod 666 /var/run/docker.sock
    else
        # Linux: align docker group GID with the socket's actual GID
        CURRENT_GID=$(getent group docker | cut -d: -f3)
        if [ "$CURRENT_GID" != "$SOCKET_GID" ]; then
            groupmod -g "$SOCKET_GID" docker 2>/dev/null || true
        fi
        usermod -aG docker jenkins 2>/dev/null || true
    fi
fi

exec gosu jenkins /usr/bin/tini -- /usr/local/bin/jenkins.sh

#!/bin/sh

set -e

JOBS_IMAGE_NAME="${JOBS_IMAGE_NAME:-"stackgres/jobs:development-jvm"}"
CONTAINER_BASE=$(buildah from "registry.access.redhat.com/ubi8/openjdk-11:1.3-10")
TARGET_JOBS_IMAGE_NAME="${TARGET_JOBS_IMAGE_NAME:-docker-daemon:$JOBS_IMAGE_NAME}"

# Include binaries
buildah config --user root:root "$CONTAINER_BASE"
buildah config --workingdir='/app/' "$CONTAINER_BASE"
buildah copy --chown jboss:jboss "$CONTAINER_BASE" 'jobs/target/stackgres-jobs-runner.jar' '/app/stackgres-jobs.jar'
buildah copy --chown jboss:jboss "$CONTAINER_BASE" 'jobs/target/lib/*' '/app/lib/'
cat << 'EOF' > jobs/target/stackgres-jobs.sh
#!/bin/sh

JAVA_OPTS="${JAVA_OPTS:-"-Djava.net.preferIPv4Stack=true -Djava.awt.headless=true -XX:MaxRAMPercentage=75.0"}"
APP_OPTS="${APP_OPTS:-"-Dquarkus.http.host=0.0.0.0 -Dquarkus.http.port=8080 -Dquarkus.http.ssl-port=8443 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"}"
if [ "$DEBUG_JOBS" = true ]
then
  set -x
  JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=$([ "$DEBUG_JOBS_SUSPEND" = true ] && echo y || echo n)"
fi
if [ -n "$JOBS_LOG_LEVEL" ]
then
  JAVA_OPTS="$JAVA_OPTS -Dquarkus.log.level=$JOBS_LOG_LEVEL"
fi
if [ "$JOBS_SHOW_STACK_TRACES" = true ]
then
  JAVA_OPTS="$JAVA_OPTS -Dquarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{4.}] (%t) %s%e%n"
fi
JAVA_JAR="-jar /app/stackgres-jobs.jar"
exec java $JAVA_OPTS $APP_OPTS $JAVA_JAR "$@"
EOF
buildah copy --chown jboss:jboss "$CONTAINER_BASE" 'jobs/target/stackgres-jobs.sh' '/app/'
buildah run "$CONTAINER_BASE" chmod 775 '/home/jboss' -R
buildah run "$CONTAINER_BASE" chmod 775 '/app' -R
buildah run "$CONTAINER_BASE" chmod 775 '/app/stackgres-jobs.sh'

## Run our server and expose the port
buildah config --user jboss:jboss "$CONTAINER_BASE"
buildah config --env 'HOME=/app' "$CONTAINER_BASE"
buildah config --cmd 'sh /app/stackgres-jobs.sh' "$CONTAINER_BASE"
buildah config --port 8080 "$CONTAINER_BASE"
buildah config --port 8443 "$CONTAINER_BASE"
buildah config --env LANG="C.UTF-8" --env LC_ALL="C.UTF-8" "$CONTAINER_BASE"

## Commit this container to an image name
buildah commit "$CONTAINER_BASE" "$JOBS_IMAGE_NAME"
buildah push -f "${BUILDAH_PUSH_FORMAT:-docker}" "$JOBS_IMAGE_NAME" "$TARGET_JOBS_IMAGE_NAME"
buildah delete "$CONTAINER_BASE"

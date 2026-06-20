#!/usr/bin/env sh
set -eu
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER_JAR" ]; then
  exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  echo "[gradlew fallback] gradle-wrapper.jar is not bundled; using Gradle from PATH." >&2
  exec gradle "$@"
fi
echo "gradle-wrapper.jar not found and gradle is not installed." >&2
echo "Install Gradle or generate the wrapper with: gradle wrapper --gradle-version 9.4.1 --distribution-type bin" >&2
exit 1

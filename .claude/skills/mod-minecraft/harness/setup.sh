#!/bin/bash
# Prepares a multiplayer test of a Fabric 26.3 mod: a dedicated server and a second client (copies of
# the mod's Gradle project), the ctest driver mod, a flat peaceful world, Mario and Luigi as operators.
# Mario's client runs from the mod folder itself (its run/ is git-ignored).
#   setup.sh <mod folder> <test folder in the scratchpad>
#   setup.sh /home/user/Minecraft-mod/mods/chiappe-colossali "$SCRATCH/test"     (absolute paths)
# The first run downloads Minecraft through Gradle: if the network blocks it, you cannot test in game;
# say so to the user, build through GitHub Actions and verify what you can.
set -e
MOD=$(realpath "$1"); T=$2
H=$(dirname "$(realpath "$0")")
# Java 25 for 26.3 (the container's default java is often older: do not keep its JAVA_HOME)
JDK=${JDK:-$(ls -d /home/user/jdk/jdk-25* 2>/dev/null | head -1)}
[ -x "$JDK/bin/java" ] || { echo "no JDK 25: download it from api.adoptium.net into /home/user/jdk/ or set JDK=..."; exit 1; }
export JAVA_HOME=$JDK
mkdir -p "$T"
for d in srv cli2; do
  mkdir -p "$T/$d/run"
  cp -r "$MOD/gradle" "$MOD/gradlew" "$MOD/settings.gradle" "$MOD/build.gradle" "$MOD/gradle.properties" "$T/$d/"
done
cp "$H/server.properties" "$H/ops.json" "$T/srv/run/"
echo "eula=true" > "$T/srv/run/eula.txt"
for r in "$MOD/run" "$T/cli2/run"; do
  mkdir -p "$r/mods"
  [ -f "$r/options.txt" ] || cp "$H/options.txt" "$r/options.txt"
done
# the mod first (the driver may read its classes), then the driver with the mod's own wrapper
(cd "$MOD" && ./gradlew build -q)
JAR=$(ls "$MOD"/build/libs/*.jar | grep -v -- '-sources' | head -1)
rm -rf "$T/ctest" && cp -r "$H/ctest" "$T/ctest" && cp -r "$MOD/gradle" "$MOD/gradlew" "$T/ctest/"
(cd "$T/ctest" && ./gradlew build -q -PmodJar="$JAR")
echo "ready: $T"
echo "mod jar: $JAR"
echo "driver:  $T/ctest/build/libs/ctest-1.0.0.jar"

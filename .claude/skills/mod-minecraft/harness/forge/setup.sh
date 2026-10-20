#!/bin/bash
# Prepares a multiplayer test of a Forge 1.20.1 mod: a dedicated server and two clients (copies of the
# mod's Gradle project, so the repository is never touched), the ctest driver mod, a flat peaceful
# world, Mario and Luigi as operators.
#   setup.sh <mod folder> <test folder in the scratchpad>       (absolute paths)
# Commands of the mod being tested: write <test folder>/ctest/src/main/java/ctest/Extra.java (see
# Driver.java) and run this again; the generic driver files are refreshed, Extra.java is kept.
set -e
MOD=$(realpath "$1"); T=$2
H=$(dirname "$(realpath "$0")")
ROOT=$(git -C "$MOD" rev-parse --show-toplevel)       # its Gradle wrapper (8.8) builds every Forge mod
# Java 17 for Forge 1.20.1 (the container's default java is often another one)
JDK=${JDK:-$(ls -d /home/user/jdk/jdk-17* 2>/dev/null | head -1)}
[ -x "$JDK/bin/java" ] || { echo "no JDK 17: download it from api.adoptium.net into /home/user/jdk/ or set JDK=..."; exit 1; }
export JAVA_HOME=$JDK
RETRY="-Dorg.gradle.internal.repository.max.tentatives=8 -Dorg.gradle.internal.repository.initial.backoff=2000"
mkdir -p "$T"
for d in srv cli1 cli2; do
  mkdir -p "$T/$d/run"
done
cp "$H/server.properties" "$H/ops.json" "$T/srv/run/"
echo "eula=true" > "$T/srv/run/eula.txt"
for r in "$T/cli1/run" "$T/cli2/run"; do
  mkdir -p "$r/mods"
  [ -f "$r/options.txt" ] || cp "$H/options.txt" "$r/options.txt"
done
# the mod first (the driver reads its classes), then the driver with "jar": no reobfuscation, the dev
# clients use Mojang's names
"$ROOT/gradlew" -p "$MOD" build -q $RETRY
mkdir -p "$T/ctest" && cp -r "$H/ctest/." "$T/ctest/"
"$ROOT/gradlew" -p "$T/ctest" jar -q $RETRY -PmodJar="$MOD/build/classes/java/main"
# Minecraft's sounds and textures for the dev runs (once per container)
"$ROOT/gradlew" -p "$MOD" downloadAssets -q $RETRY
echo "ready: $T"
echo "driver: $T/ctest/build/libs/ctest-1.0.0.jar"

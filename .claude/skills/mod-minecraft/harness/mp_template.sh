#!/bin/bash
# Multiplayer test of a Fabric 26.3 mod: dedicated server + Mario (Xvfb :77, Vulkan) + Luigi (Xvfb :78,
# OpenGL). Real input with xdotool; the ctest driver mod logs what each client sees ("[ctest] ..." lines)
# and runs camera/command/screenshot requests written into ftest_cmd.txt.
#
# Copy this file into the test folder made by setup.sh, set MOD, write the phases at the bottom, run it
# in the background and read its output:   SCRATCH=... ./mp_mymod.sh all > driver.log 2>&1 &
# Never edit it while it runs (bash reads scripts a piece at a time): copy it, edit the copy.
# Stop it with "stop.sh <test folder>" (never pkill -f a text that is in your own command line).
S=${SCRATCH:?set SCRATCH to the scratchpad folder}
MOD=/home/user/Minecraft-mod/mods/CHANGE-ME          # the mod being tested
T=${T:-$(dirname "$(realpath "$0")")}               # the folder prepared by setup.sh (default: where this script is)
SRV=$T/srv
B=$T/cli2
OUT=$T/out-$(date +%H%M%S)
PHASES="${1:-all}"
# Java 25 for 26.3 (the container's default java is often older: do not keep its JAVA_HOME)
JDK=${JDK:-$(ls -d /home/user/jdk/jdk-25* 2>/dev/null | head -1)}
export JAVA_HOME=$JDK PATH=$JDK/bin:$PATH
export XDG_RUNTIME_DIR=/tmp/xdg-test LIBGL_ALWAYS_SOFTWARE=1
mkdir -p $XDG_RUNTIME_DIR $OUT
echo $$ > $T/driver.pid     # stop.sh $T stops this script

# fresh copies of the mod for the server and the second client; the driver mod in both clients
for d in $SRV $B; do
  rm -rf $d/src && cp -r $MOD/src $d/src && cp $MOD/build.gradle $MOD/gradle.properties $d/
done
for r in $MOD/run $B/run; do
  mkdir -p $r/mods; rm -f $r/mods/ctest-*.jar; cp $T/ctest/build/libs/ctest-1.0.0.jar $r/mods/
  rm -f $r/ftest_cmd.txt
  [ -d $r/screenshots ] && mv $r/screenshots $OUT/old-shots-$(date +%s%N); mkdir -p $r/screenshots
  sed -i 's/^preferredGraphicsBackend:.*/preferredGraphicsBackend:"opengl"/' $r/options.txt
  [ "$r" = "$MOD/run" ] && sed -i 's/^preferredGraphicsBackend:.*/preferredGraphicsBackend:"vulkan"/' $r/options.txt
done
# a new world every time (the old one is moved, not deleted), unless KEEP_WORLD=1
if [ -d $SRV/run/world ] && [ "$KEEP_WORLD" != "1" ]; then mv $SRV/run/world $OUT/old-world; fi

LA=$OUT/mario.log; LB=$OUT/luigi.log; LS=$OUT/server.log
(cd $SRV && timeout 3600 ./gradlew runServer --offline > $LS 2>&1) &
SPID=$!
say() { echo "[driver $(date +%H:%M:%S)] $*"; }
declare -A MARK
waitlog() { # <file> <pattern> <seconds>: waits for a NEW line matching the pattern
  local f=$1 p=$2 key="$1|$2"
  for i in $(seq 1 $(( $3 * 2 ))); do
    local n=$(grep -c -- "$p" $f 2>/dev/null)
    [ "$n" -gt "${MARK[$key]:-0}" ] && { MARK[$key]=$n; return 0; }
    sleep 0.5
  done
  say "TIMEOUT waiting for '$p' in $(basename $f)"; return 1
}
for i in $(seq 1 900); do grep -q "Done (" $LS 2>/dev/null && break; sleep 1; done
grep -q "Done (" $LS || { say "server did not start"; tail -30 $LS; kill $SPID; exit 1; }
say "server up"

Xvfb :77 -screen 0 1280x720x24 +extension GLX +render -noreset > $OUT/xvfb77.log 2>&1 &
X1=$!
Xvfb :78 -screen 0 1280x720x24 +extension GLX +render -noreset > $OUT/xvfb78.log 2>&1 &
X2=$!
sleep 2
LVP=$(ls /usr/share/vulkan/icd.d/lvp_icd*.json 2>/dev/null | head -1)
(cd $MOD && VK_ICD_FILENAMES=$LVP DISPLAY=:77 timeout 3600 ./gradlew runClient --offline --args="--username Mario --quickPlayMultiplayer localhost:25565 --width 1280 --height 720" > $LA 2>&1) &
CA=$!
sleep 25
(cd $B && DISPLAY=:78 timeout 3600 ./gradlew runClient --offline --args="--username Luigi --quickPlayMultiplayer localhost:25565 --width 1280 --height 720" > $LB 2>&1) &
CB=$!

# --- helpers: $1 = A (Mario) or B (Luigi)
disp() { [ "$1" = A ] && echo :77 || echo :78; }
rundir() { [ "$1" = A ] && echo $MOD/run || echo $B/run; }
logf() { [ "$1" = A ] && echo $LA || echo $LB; }
win() { DISPLAY=$(disp $1) xdotool search --onlyvisible --name "Minecraft" 2>/dev/null | head -1; }
# a tap: fine for keys read as clicks (consumeClick); too short for keys held down (jump, sprint)
key() { local W=$(win $1); DISPLAY=$(disp $1) xdotool windowfocus --sync $W 2>/dev/null; DISPLAY=$(disp $1) xdotool key $2; sleep 0.3; }
down() { local W=$(win $1); DISPLAY=$(disp $1) xdotool windowfocus --sync $W 2>/dev/null; DISPLAY=$(disp $1) xdotool keydown $2; }
up() { DISPLAY=$(disp $1) xdotool keyup $2; }
hold() { down $1 $2; sleep ${3:-0.15}; up $1 $2; }            # hold <who> <key> [seconds]
click() { local W=$(win $1); DISPLAY=$(disp $1) xdotool mousemove --window $W $2 $3; sleep 0.3; DISPLAY=$(disp $1) xdotool click 1; sleep 0.6; }
rhold() { local W=$(win $1); DISPLAY=$(disp $1) xdotool windowfocus --sync $W 2>/dev/null; DISPLAY=$(disp $1) xdotool mousedown 3; sleep ${2:-1}; DISPLAY=$(disp $1) xdotool mouseup 3; }   # hold right click <who> [seconds]
slot() { key $1 $2; }                                         # hotbar slot 1-9
guiclick() { click $1 $(( $2 * 2 )) $(( $3 * 2 )); }        # GUI coordinates (guiScale 2)
# centre of a button whose label contains $2, from the last screen description in the log
button() { grep "\[ctest\] screen " $(logf $1) | tail -1 | tr '|' '\n' | grep "button '[^']*$2" | head -1 | awk '{ n=NF; print int($(n-3) + $(n-1)/2), int($(n-2) + $n/2) }'; }
do_() { local who=$1; shift; local f=$(rundir $who)/ftest_cmd.txt; for l in "$@"; do echo "$l" >> $f.tmp; done; mv $f.tmp $f; sleep 0.7; }
cmd() { local who=$1; shift; local lines=(); for c in "$@"; do lines+=("cmd $c"); done; do_ $who "${lines[@]}"; }
shot() { do_ $1 "shot $2"; sleep 1.2; }
burst() { do_ $1 "burst $2 $3 $4"; }                          # burst <who> <name> <count> <every ticks>
status() { do_ $1 status; sleep 0.3; grep "\[ctest\] st " $(logf $1) | tail -1 | sed "s/^.*\[ctest\] /[$1] /"; }
respawn() { do_ $1 screen; sleep 0.8; local RB=$(button $1 "Rinasci"); [ -n "$RB" ] && { guiclick $1 $RB; say "$1 respawned"; sleep 3; }; }

say "waiting for both players"
waitlog $LA "chat Luigi" 900 || say "Mario does not see Luigi join"
sleep 4
G=-60   # the ground of the flat world
cmd A "difficulty peaceful" "gamerule minecraft:advance_time false" "gamerule minecraft:advance_weather false" \
      "gamerule minecraft:spawn_mobs false" "time set 6000" "weather clear" "tp Mario 0 $G 0 0 0" "tp Luigi 4 $G -6 0 0"
sleep 2
do_ A "hud off"; do_ B "hud off"
status A; status B

# ---------------------------------------------------------------- phases: write the test here
if [[ "$PHASES" == *example* || "$PHASES" == all ]]; then
  say "example: Luigi looks at Mario from behind and takes a picture; Mario presses a key"
  do_ B "orbit Mario 30 5 1"; sleep 1.5
  shot B example_back
  burst B example_burst 10 2; sleep 0.1; key A z      # the mod's key
  sleep 2
  # damage measured on pigs with 1000 health:
  # cmd A "summon minecraft:pig 3 $G 0 {NoAI:1b,Silent:1b,attributes:[{id:\"minecraft:max_health\",base:1000}],Health:1000f}"
  # do_ A "ent pig"; grep '\[ctest\] ent entity.minecraft.pig' $LA | tail -1
fi

say "done"
grep -iE "exception|error" $LA $LB $LS | grep -v "JAVA_TOOL\|Exception in thread \"Thread-\|DEBUG\|minecraftservices\|Services are unavailable\|UnknownHost\|ALSA\|OpenAL\|SoundSystem\|No key layers" | head -20
kill $CA $CB 2>/dev/null; sleep 5
for p in $(pgrep -f "fabric.dli.config=$MOD/") $(pgrep -f "fabric.dli.config=$B/") $(pgrep -f "fabric.dli.config=$SRV/"); do kill $p 2>/dev/null; done
sleep 3; kill $SPID $X1 $X2 2>/dev/null
cp $MOD/run/screenshots/*.png $B/run/screenshots/*.png $OUT/ 2>/dev/null
echo "screenshots: $(ls $OUT/*.png 2>/dev/null | wc -l) in $OUT"
rm -f $T/driver.pid

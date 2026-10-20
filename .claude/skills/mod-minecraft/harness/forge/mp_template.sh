#!/bin/bash
# Multiplayer test of a Forge 1.20.1 mod: dedicated server + Mario (Xvfb :77) + Luigi (Xvfb :78), both
# in OpenGL with Mesa llvmpipe. Server and clients run from copies of the mod (srv/, cli1/, cli2/ in the
# test folder), so the repository is never touched. Real input with xdotool; the ctest driver mod logs
# what each client sees ("[ctest] ..." lines) and runs requests written into ftest_cmd.txt.
#
# Copy this file into the test folder made by setup.sh, set MOD, write the phases at the bottom, run it
# in the background and read its output:   SCRATCH=... ./mp_mymod.sh all > driver.log 2>&1 &
# Never edit it while it runs (bash reads scripts a piece at a time): copy it, edit the copy.
# Stop it with "stop.sh <test folder>" (never pkill -f a text that is in your own command line).
S=${SCRATCH:?set SCRATCH to the scratchpad folder}
MOD=/home/user/Minecraft-mod/mods/CHANGE-ME          # the mod being tested
ROOT=/home/user/Minecraft-mod                       # its Gradle wrapper builds every Forge mod
H=$ROOT/.claude/skills/mod-minecraft/harness/forge
T=${T:-$(dirname "$(realpath "$0")")}               # the folder prepared by setup.sh (default: where this script is)
SRV=$T/srv; CA_=$T/cli1; CB_=$T/cli2
OUT=$T/out-$(date +%H%M%S)
PHASES="${1:-all}"
GW="$ROOT/gradlew -Dorg.gradle.internal.repository.max.tentatives=8 -Dorg.gradle.internal.repository.initial.backoff=2000 ${OFFLINE:+--offline}"
# Java 17 for Forge 1.20.1 (the container's default java is often another one)
JDK=${JDK:-$(ls -d /home/user/jdk/jdk-17* 2>/dev/null | head -1)}
export JAVA_HOME=$JDK PATH=$JDK/bin:$PATH
export XDG_RUNTIME_DIR=/tmp/xdg-test LIBGL_ALWAYS_SOFTWARE=1
mkdir -p $XDG_RUNTIME_DIR $OUT
echo $$ > $T/driver.pid     # stop.sh $T stops this script

# fresh copies of the mod (with the test run arguments) for the server and the clients; the driver in the clients
for d in $SRV $CA_ $CB_; do
  rm -rf $d/src && cp -r $MOD/src $d/src && cp $MOD/settings.gradle $MOD/gradle.properties $d/
  cat $MOD/build.gradle $H/run_args.gradle > $d/build.gradle
done
for r in $CA_/run $CB_/run; do
  mkdir -p $r/mods; rm -f $r/mods/ctest-*.jar; cp $T/ctest/build/libs/ctest-1.0.0.jar $r/mods/
  rm -f $r/ftest_cmd.txt
  [ -d $r/screenshots ] && mv $r/screenshots $OUT/old-shots-$(date +%s%N); mkdir -p $r/screenshots
done
# a new world every time (the old one is moved, not deleted), unless KEEP_WORLD=1
if [ -d $SRV/run/world ] && [ "$KEEP_WORLD" != "1" ]; then mv $SRV/run/world $OUT/old-world; fi

LA=$OUT/mario.log; LB=$OUT/luigi.log; LS=$OUT/server.log
(cd $SRV && timeout 3600 $GW -p $SRV runServer > $LS 2>&1) &
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
(cd $CA_ && CT_USER=Mario DISPLAY=:77 timeout 3600 $GW -p $CA_ runClient > $LA 2>&1) &
CA=$!
sleep 25
(cd $CB_ && CT_USER=Luigi DISPLAY=:78 timeout 3600 $GW -p $CB_ runClient > $LB 2>&1) &
CB=$!

# --- helpers: $1 = A (Mario) or B (Luigi)
disp() { [ "$1" = A ] && echo :77 || echo :78; }
rundir() { [ "$1" = A ] && echo $CA_/run || echo $CB_/run; }
logf() { [ "$1" = A ] && echo $LA || echo $LB; }
win() { DISPLAY=$(disp $1) xdotool search --onlyvisible --name "Minecraft" 2>/dev/null | head -1; }
focus() { local W=$(win $1); DISPLAY=$(disp $1) xdotool windowfocus --sync $W 2>/dev/null; }
# a tap: fine for keys read as clicks (consumeClick); too short for keys held down (jump, sprint, sneak)
key() { focus $1; DISPLAY=$(disp $1) xdotool key $2; sleep 0.3; }
down() { focus $1; DISPLAY=$(disp $1) xdotool keydown $2; }
up() { DISPLAY=$(disp $1) xdotool keyup $2; }
hold() { down $1 $2; sleep ${3:-0.15}; up $1 $2; }            # hold <who> <key> [seconds]
# in game (no screen open): mouse buttons without moving the mouse (moving it turns the camera)
lclick() { focus $1; DISPLAY=$(disp $1) xdotool mousedown 1; sleep 0.08; DISPLAY=$(disp $1) xdotool mouseup 1; }
rclick() { focus $1; DISPLAY=$(disp $1) xdotool mousedown 3; sleep 0.08; DISPLAY=$(disp $1) xdotool mouseup 3; }
rhold() { focus $1; DISPLAY=$(disp $1) xdotool mousedown 3; sleep ${2:-1}; DISPLAY=$(disp $1) xdotool mouseup 3; }   # hold right click <who> [seconds]
# in a screen: click at window coordinates
click() { local W=$(win $1); DISPLAY=$(disp $1) xdotool mousemove --window $W $2 $3; sleep 0.3; DISPLAY=$(disp $1) xdotool click 1; sleep 0.6; }
hover() { local W=$(win $1); DISPLAY=$(disp $1) xdotool mousemove --window $W $2 $3; sleep 0.4; }
slot() { key $1 $2; }                                         # hotbar slot 1-9
guiclick() { click $1 $(( $2 * 2 )) $(( $3 * 2 )); }        # GUI coordinates (guiScale 2)
# centre of a button whose label contains $2, from the last screen description in the log
button() { grep "\[ctest\] screen " $(logf $1) | tail -1 | tr '|' '\n' | grep "button '[^']*$2" | head -1 | awk '{ n=NF; print int($(n-3) + $(n-1)/2), int($(n-2) + $n/2) }'; }
do_() { local who=$1; shift; local f=$(rundir $who)/ftest_cmd.txt; for l in "$@"; do echo "$l" >> $f.tmp; done; mv $f.tmp $f; sleep 0.7; }
cmd() { local who=$1; shift; local lines=(); for c in "$@"; do lines+=("cmd $c"); done; do_ $who "${lines[@]}"; }
shot() { do_ $1 "shot $2"; sleep 1.2; }
burst() { do_ $1 "burst $2 $3 $4"; }                          # burst <who> <name> <count> <every ticks>
# a burst that starts right away (do_ waits 0.7 s): for an action that follows at once, e.g. "bnow B hit 20 1; lclick A"
bnow() { local f=$(rundir $1)/ftest_cmd.txt; echo "burst $2 $3 $4" > $f.tmp; mv $f.tmp $f; sleep 0.1; }
status() { do_ $1 status; sleep 0.3; grep "\[ctest\] st " $(logf $1) | tail -1 | sed "s/^.*\[ctest\] /[$1] /"; }
last() { grep "\[ctest\] $2" $(logf $1) | tail -${3:-1} | sed "s/^.*\[ctest\] /[$1] /"; }   # last <who> <prefix> [n]: latest driver lines
respawn() { do_ $1 screen; sleep 0.8; local RB=$(button $1 "Rinasci"); [ -n "$RB" ] && { guiclick $1 $RB; say "$1 respawned"; sleep 3; }; }

say "waiting for both players"
waitlog $LA "chat Luigi" 900 || say "Mario does not see Luigi join"
sleep 4
G=-60   # the ground of the flat world
cmd A "difficulty peaceful" "gamerule doDaylightCycle false" "gamerule doWeatherCycle false" \
      "gamerule doMobSpawning false" "time set 6000" "weather clear" "tp Mario 0 $G 0 0 0" "tp Luigi 4 $G -6 0 0"
sleep 2
do_ A "hud off"; do_ B "hud off"
status A; status B

# ---------------------------------------------------------------- phases: write the test here
if [[ "$PHASES" == *example* || "$PHASES" == all ]]; then
  say "example: Luigi looks at Mario from behind and takes a picture; Mario attacks"
  do_ B "orbit Mario 30 5 1"; sleep 1.5
  shot B example_back
  burst B example_burst 10 2; sleep 0.1; lclick A
  sleep 2
  # damage measured on pigs with 1000 health:
  # cmd A "summon minecraft:pig 3 $G 0 {NoAI:1b,Silent:1b,Attributes:[{Name:\"generic.max_health\",Base:1000}],Health:1000f}"
  # do_ A "ent pig"; last A "ent "
fi

say "done"
grep -iE "exception|error" $LA $LB $LS | grep -v "JAVA_TOOL\|DEBUG\|minecraftservices\|Services are unavailable\|UnknownHost\|ALSA\|OpenAL\|SoundSystem\|SoundEngine\|realms\|YggdrasilUserApi\|Failed to fetch user properties" | head -20
kill $CA $CB 2>/dev/null; sleep 5
for p in $(pgrep -f "forgeclientuserdev") $(pgrep -f "forgeserveruserdev"); do kill $p 2>/dev/null; done
sleep 3; kill $SPID $X1 $X2 2>/dev/null
cp $CA_/run/screenshots/*.png $CB_/run/screenshots/*.png $OUT/ 2>/dev/null
echo "screenshots: $(ls $OUT/*.png 2>/dev/null | wc -l) in $OUT"
rm -f $T/driver.pid

#!/bin/bash
# Stops a running driver script and everything it started:   stop.sh <test folder>
H=$(dirname "$(realpath "$0")")
if [ -n "$1" ] && [ -f "$1/driver.pid" ]; then
  kill "$(cat "$1/driver.pid")" 2>/dev/null && echo "driver stopped"
  rm -f "$1/driver.pid"
fi
"$H/cleanup.sh"

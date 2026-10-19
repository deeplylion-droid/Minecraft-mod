#!/bin/bash
# Stops every test server, test client and Xvfb of the harness. It is a separate script on purpose:
# a "pkill -f <text>" typed in your own shell command matches that same command and kills your shell.
for p in $(pgrep -f "fabric.dli.config="); do kill $p 2>/dev/null; done
sleep 4
for p in $(pgrep -f "GradleWrapperMain") $(pgrep -f "Xvfb :7[78]"); do kill $p 2>/dev/null; done
sleep 2
echo "still running: $(ps aux | grep -E 'Xvfb|java' | grep -v grep | wc -l)"

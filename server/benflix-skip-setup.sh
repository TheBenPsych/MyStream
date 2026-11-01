#!/bin/bash
# Benflix direct play only needs RetroArch, so EmulationStation's own setup (and its big download) is skipped.
# This file is read by the container's start-up, so it must not call "exit" (that would stop the whole container).
true

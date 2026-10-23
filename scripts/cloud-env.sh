#!/usr/bin/env bash
# Source this file; it configures this shell without changing system toolchains.
WISP_TOOLS_ROOT="${WISP_TOOLS_ROOT:-/workspace/.wisp-tools}"
export WISP_TOOLS_ROOT
export JAVA_HOME="$WISP_TOOLS_ROOT/jdk"
export ANDROID_HOME="$WISP_TOOLS_ROOT/android"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$WISP_TOOLS_ROOT/android-user"
export GRADLE_USER_HOME="$WISP_TOOLS_ROOT/gradle-user"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

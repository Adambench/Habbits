#!/usr/bin/env bash
# Source this before running Gradle:  source tools/dev-env.sh
#
# The Android Gradle Plugin does not support JDK 25+, which is what current
# Fedora ships as default. This pins a JDK 21 for Gradle without touching the
# system default.

_habbits_find_jdk21() {
  local c
  for c in "$JAVA_HOME" "$HOME/.local/jdks"/jdk-21* /usr/lib/jvm/java-21-openjdk \
           /usr/lib/jvm/temurin-21-jdk /opt/java/jdk-21*; do
    [ -x "${c:-}/bin/javac" ] || continue
    case "$("$c/bin/javac" -version 2>&1)" in javac\ 21.*) echo "$c"; return 0;; esac
  done
  return 1
}

if _jdk="$(_habbits_find_jdk21)"; then
  export JAVA_HOME="$_jdk"
  unset _jdk
else
  echo "dev-env: no JDK 21 found." >&2
  echo "  Install one, e.g.:" >&2
  echo "    mkdir -p ~/.local/jdks && curl -L 'https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse' | tar -xz -C ~/.local/jdks" >&2
fi

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
[ -d "$ANDROID_HOME" ] || echo "dev-env: ANDROID_HOME does not exist: $ANDROID_HOME" >&2
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

echo "JAVA_HOME=${JAVA_HOME:-<unset>}"
echo "ANDROID_HOME=$ANDROID_HOME"

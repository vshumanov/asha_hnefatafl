#!/usr/bin/env bash
#
# Build the Hnefatafl MIDlet (MIDP 2.0 / CLDC 1.1) into dist/hnefatafl.jar +
# .jad, ready to sideload onto the Nokia Asha 210.
#
# Default path is Docker (no local JDK 8 / WTK needed): compiles in a JDK 8
# container and preverifies with ProGuard's -microedition mode, which writes the
# CLDC StackMap attributes the phone's VM requires.
#
# Usage:
#   ./build.sh                 # Docker path (recommended)
#   WTK_HOME=~/WTK2.5.2 JAVA8=$(/usr/libexec/java_home -v 1.8)/bin/javac \
#     ./build.sh --wtk         # classic Wireless Toolkit path
#
set -e
cd "$(dirname "$0")"

if [ "$1" != "--wtk" ]; then
  PG=/tmp/pg
  if [ ! -e "$PG"/proguard-*/lib/proguard.jar ]; then
    echo ">> fetching ProGuard 7.4.2 into $PG"
    curl -sSL -o /tmp/pg.zip \
      https://github.com/Guardsquare/proguard/releases/download/v7.4.2/proguard-7.4.2.zip
    mkdir -p "$PG" && unzip -q /tmp/pg.zip -d "$PG"
  fi
  docker run --rm -v "$PWD":/src -v "$PG":/pg \
    eclipse-temurin:8-jdk bash /src/build/docker-build.sh
  exit 0
fi

# ---- classic WTK path -------------------------------------------------------
: "${WTK_HOME:?set WTK_HOME to your Wireless Toolkit 2.5.2 install}"
JAVAC="${JAVA8:-javac}"
PREVERIFY="$WTK_HOME/bin/preverify"
MIDPAPI="$WTK_HOME/lib/midpapi20.jar"
CLDCAPI="$WTK_HOME/lib/cldcapi11.jar"
BOOTCP="$CLDCAPI:$MIDPAPI"
NAME=hnefatafl
for f in "$PREVERIFY" "$MIDPAPI" "$CLDCAPI"; do
  [ -e "$f" ] || { echo "missing: $f"; exit 1; }
done
rm -rf out out_pv dist; mkdir -p out out_pv dist
echo ">> compile"
"$JAVAC" -bootclasspath "$BOOTCP" -source 1.3 -target 1.3 -d out $(find src -name '*.java')
echo ">> preverify"
"$PREVERIFY" -classpath "$BOOTCP" -d out_pv out
echo ">> package"
jar cfm "dist/$NAME.jar" build/manifest-tafl.mf -C out_pv .
SIZE=$(wc -c < "dist/$NAME.jar" | tr -d ' ')
grep -v '^Manifest-Version' build/manifest-tafl.mf > "dist/$NAME.jad"
echo "MIDlet-Jar-URL: $NAME.jar" >> "dist/$NAME.jad"
echo "MIDlet-Jar-Size: $SIZE" >> "dist/$NAME.jad"
echo ">> dist/$NAME.jar ($SIZE bytes) + dist/$NAME.jad"

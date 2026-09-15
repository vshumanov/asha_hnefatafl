#!/usr/bin/env bash
# Runs INSIDE an eclipse-temurin:8-jdk container. Builds the MIDlet:
#   compile (class v47) -> ProGuard -microedition preverify -> jar + jad.
# Expects the repo mounted at /src and a ProGuard dist at /pg.
set -e
SRC=/src
PG=$(ls /pg/proguard-*/lib/proguard.jar | head -1)
W=/build
NAME=hnefatafl
rm -rf "$W"; mkdir -p "$W/api" "$W/app" "$W/pv" "$SRC/dist"

echo ">> compile API stubs (compile-time surface, real MIDP constants)"
javac -source 1.3 -target 1.3 -nowarn -d "$W/api" $(find "$SRC/stubs" -name '*.java')

echo ">> compile app to CLDC-range bytecode (v47)"
javac -source 1.3 -target 1.3 -nowarn -bootclasspath "$JAVA_HOME/jre/lib/rt.jar" \
      -cp "$W/api" -d "$W/app" $(find "$SRC/src" -name '*.java')

echo ">> preverify with ProGuard (-microedition), no shrink/optimize/obfuscate"
cat > "$W/pg.pro" <<PRO
-injars $W/app
-outjars $W/pv
-libraryjars $JAVA_HOME/jre/lib/rt.jar
-libraryjars $W/api
-dontshrink
-dontoptimize
-dontobfuscate
-dontnote
-dontwarn
-microedition
-keepattributes *
-keep class ** { *; }
PRO
java -jar "$PG" @"$W/pg.pro"

echo ">> package jar + jad"
jar cfm "$SRC/dist/$NAME.jar" "$SRC/build/manifest-tafl.mf" -C "$W/pv" tafl
SIZE=$(wc -c < "$SRC/dist/$NAME.jar" | tr -d ' ')
grep -v '^Manifest-Version' "$SRC/build/manifest-tafl.mf" > "$SRC/dist/$NAME.jad"
echo "MIDlet-Jar-URL: $NAME.jar" >> "$SRC/dist/$NAME.jad"
echo "MIDlet-Jar-Size: $SIZE" >> "$SRC/dist/$NAME.jad"
echo ">> DONE: $NAME.jar ($SIZE bytes) + $NAME.jad"
echo ">> class file version (expect major 47):"
javap -verbose -cp "$W/pv" tafl.TaflMIDlet 2>/dev/null | grep -i "major version" | head -1
echo ">> StackMap present after preverify?"
javap -verbose -cp "$W/pv" tafl.Board 2>/dev/null | grep -i "StackMap" | head -1 \
  || echo "   (javap may not print it; ProGuard -microedition writes CLDC StackMap)"

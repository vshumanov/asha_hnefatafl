# Build & packaging notes

Target: **Nokia Asha 210**, Series 40 — **MIDP 2.0 / CLDC 1.1**, non-touch,
ITU-T keypad, 240×320. Output: a single sideloadable `dist/hnefatafl.jar`
(+ matching `.jad`).

## Quickest path: Docker (this is how the checked build was produced)

No local JDK 8 or Wireless Toolkit needed. Compiles in a JDK 8 container and
preverifies with ProGuard's `-microedition` mode, which adds the CLDC StackMap
attributes the phone's KVM requires (in place of WTK's `preverify` binary):

```bash
./build.sh
```

That fetches ProGuard 7.4.2 to `/tmp/pg` once, then runs
`build/docker-build.sh` inside `eclipse-temurin:8-jdk`. Output:
`dist/hnefatafl.jar` + `dist/hnefatafl.jad`. The script prints the class-file
version (**major 47** = CLDC-compatible) and confirms StackMap attributes were
written. Only the app's `tafl.*` classes go in the jar — the compile-time stubs
never ship.

## Why not plain `javac` from a modern JDK

A MIDlet is not just compiled Java:

1. **CLDC-range bytecode.** JDK 9+ refuses to emit it. Use **JDK 8**
   (`-source 1.3 -target 1.3`), as the container does.
2. **Preverification.** The KVM needs StackMap attributes added by a preverify
   pass — here ProGuard `-microedition`, otherwise WTK's `preverify`.

## Classic Wireless Toolkit path (alternative)

```bash
WTK_HOME=~/WTK2.5.2 JAVA8=$(/usr/libexec/java_home -v 1.8)/bin/javac \
  ./build.sh --wtk
```

Needs Sun/Oracle Wireless Toolkit 2.5.2 for CLDC (provides `preverify`,
`midpapi20.jar`, `cldcapi11.jar`) and any JDK 8 `javac`.

## Validate the rules engine without a phone

`src/tafl/Board.java`, `Undo.java`, and `AI.java` have **zero MIDP imports**, so
they compile and run on a normal JDK. `test/RulesTest.java` checks the layout,
custodial capture, king capture on the throne, king safety on the edge, the
corner escape, throne pass-over, restricted-square landing, the corner-anvil
asymmetry, and apply/undo round-tripping:

```bash
mkdir -p bin
javac -d bin src/tafl/Board.java src/tafl/Undo.java test/RulesTest.java
java  -cp bin RulesTest      # -> "ALL CHECKS PASS"
```

## Compile-check the whole MIDlet without a phone

`stubs/` holds minimal type-stubs of the `javax.microedition.*` classes used
(`midlet`, `lcdui`), carrying the **real MIDP constant values** (they get inlined
into the app). Never ship them — the real classes live on the device.

```bash
mkdir -p bin_check
javac -d bin_check $(find stubs src -name '*.java')
```

## Install on the phone

Copy the **`.jar` and its matching `.jad` together** into the same folder on the
SD card. On the phone, open the `.jad` (or the `.jar`) from Files to install.
`MIDlet-Jar-Size` in the `.jad` must equal the jar's byte size exactly —
`build.sh` fills it in, so don't hand-edit the jar afterward without rebuilding.
The MIDlet needs no permissions (no file or network access).

# Hnefatafl — Copenhagen (11×11) for Nokia Asha 210

A single-JAR J2ME MIDlet (MIDP 2.0 / CLDC 1.1) of **Copenhagen Hnefatafl**, the
Viking "king's table" game, for the Nokia Asha 210 — non-touch, ITU-T keypad,
240×320. Play the king side or the attackers against a built-in alpha-beta AI.

## The game

An asymmetric siege on an 11×11 board:

- **13 defenders** — a king plus 12 — start in a plus/diamond around the central
  throne. The defender's goal: get the **king to any of the four corners**.
- **24 attackers** in four T-shaped groups on the edges. Their goal: **capture
  the king** by surrounding it on all four sides.

### Rules enforced

- Canonical Copenhagen starting layout (24 attackers / king + 12 defenders).
- **Rook movement**: all pieces move orthogonally any distance, no jumping.
- **Restricted squares**: the central throne and the four corners. Only the king
  may stop on them; any piece may pass over the *empty* throne but not stop on it.
- **Hostile squares** (used as the far anvil in a capture): the empty throne is
  hostile to all; the corners and the occupied throne are hostile to attackers.
- **Custodial capture**: flank a single enemy soldier between two hostile squares
  (a friendly piece, or a hostile special square). Multiple pieces can be taken
  by one move; the king is never taken this way.
- **King capture** (strong king): the king is taken only when surrounded on all
  four orthogonal sides by attackers (the throne counts as one side). Against the
  board edge it cannot be surrounded, so it is safe there.
- **Win / loss**: king reaches a corner → defenders win; king surrounded →
  attackers win; a side with no legal move loses.

### Rule I simplified

**Copenhagen's edge-only rules are omitted**: no shield-wall (edge-row) captures,
no exit-fort defender win, and no perimeter-encirclement attacker win; there is
also no threefold-repetition draw. Everything above is enforced exactly.

## Controls (non-touch keypad)

| Key | Action |
|-----|--------|
| `2` `4` `6` `8` or D-pad | move the cursor |
| `5` or Fire | pick up your piece / place it on a highlighted square |
| `*` | cancel the selection |
| `#` | help overlay |
| Options (soft menu) | New game · Switch side · Undo · Level · Help · Exit |

Green dots show the legal destinations of the selected piece. **Level** cycles
Easy/Normal/Hard (search depth 1/2/3). You play the king side by default; *Switch
side* flips it and starts a new game.

## Build

See [BUILD.md](BUILD.md). Short version, with Docker:

```bash
./build.sh      # -> dist/hnefatafl.jar + dist/hnefatafl.jad
```

Copy the `.jar` **and** `.jad` together onto the SD card and open the `.jad` on
the phone to install. No permissions required.

## Layout

```
src/tafl/Board.java      rules engine — layout, rook moves, capture, win (no MIDP)
src/tafl/Undo.java       reusable undo record (no per-move allocation in search)
src/tafl/AI.java         alpha-beta search with iterative deepening (no MIDP)
src/tafl/BoardView.java  full-screen Canvas: rendering, keypad, game loop
src/tafl/TaflMIDlet.java MIDlet entry point
test/RulesTest.java      desktop rules validation (no phone needed)
stubs/                   compile-time javax.microedition.* stubs (never shipped)
build/                   manifest + in-container build script
```

The search does no per-move allocation: move buffers and undo records are
preallocated per ply and the board is mutated/undone in place, keeping the heap
modest on ARM9.

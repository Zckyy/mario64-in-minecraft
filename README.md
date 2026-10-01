# Mario 64 in Minecraft

Play as the **real Super Mario 64 Mario** in Minecraft Java 1.21.4 (Fabric). Mario's movement isn't an imitation: it's
the decompiled game code from [n64decomp/sm64](https://github.com/n64decomp/sm64) (`mario.c`, `mario_actions_*.c`,
`mario_step.c`, surface collision, animations), compiled into a native library through
[libsm64](https://github.com/libsm64/libsm64). Mario's model, textures, animations and sounds are loaded at runtime from
**your own** SM64 ROM. This repository contains no Nintendo assets.

![poster](showcase/poster.png) · clip: [`showcase/mario64_in_minecraft.mp4`](showcase/mario64_in_minecraft.mp4)

## What works (verified in game)
- Verified by the automated tests: run, single/double/triple jump, **long jump** (14.5 blocks), **wall kick**,
  **backflip** and **side flip** (4.5 blocks each, both launch at SM64's vel.y 62), **slide kick**, ledge grab and climb,
  punch, move-punch, dive, ground pound, water plunge and swimming (breaststroke and flutter), and knockback.
- Minecraft blocks become SM64 collision around Mario every tick. Straight stairs become SM64-style non-slippery
  ramps, slabs and carpets are step-ups, ice is SM64 ice (slippery), and lava and magma are `SURFACE_BURNING`
  (lava boost "yeowch" jump). Soul sand is shallow quicksand. Footstep sounds follow the block (grass, stone, snow,
  sand, wood).
- Minecraft water sets SM64's water level, so Mario really swims.
- Punches, kicks, dives and ground pounds damage mobs, and landing on a mob stomps it and bounces Mario (Goomba-style).
- SM64's power meter mirrors your hearts. Taking damage plays Mario's knockback.
- Mario's voice and SFX come from SM64's own sound engine (32 kHz, from the ROM).

## Controls
Everything is rebindable under **Options → Controls → Key Binds → Mario 64**.

| Key Binds entry | Default | SM64 |
|---|---|---|
| Toggle Mario | **M** | Become Mario / back to Steve |
| Button A: Jump / Swim | *Not Bound* → uses your **Jump** key (Space) | A |
| Button B: Punch / Kick / Dive | *Not Bound* → uses your **Attack** key (left click) | B |
| Button Z: Crouch / Ground Pound | *Not Bound* → uses your **Sneak** key (Left Shift) | Z |
| Controls Card | **H** | Pin or hide the on-screen controls card |

Movement uses your normal Forward/Left/Back/Right keys as the analog stick, relative to the camera, and the mouse
turns the camera. Mario's A/B/Z start unbound on purpose. Minecraft lets one key drive only one binding, so
defaulting them to Space, left click and Shift would break normal jumping and sneaking when you're not Mario. Bind
them to a key of their own only if you want Mario on different keys.

When you become Mario, a **controls card** appears for 8 seconds on the right. It lists the buttons and SM64's combos
using your current key names. Press **H** to keep it on screen.

| Move | How |
|---|---|
| Triple jump | A three times, each right as you land |
| Long jump | Run, then Z + A |
| Backflip | Hold Z standing still, then A |
| Side flip | Run, reverse direction, then A while skidding |
| Wall kick | A just as you hit a wall mid-jump |
| Ground pound | Z in the air |
| Slide kick | Run, then Z + B |
| Dive | Run, then B |

### Gamepad (Xbox-style controllers)
Any controller Windows sees as an Xbox/XInput pad works through GLFW's standard mapping. It was tested with GLFW
detecting a GameSir G7 SE as "XInput Gamepad (GLFW)". The left stick is truly analog, as on the N64: a half tilt walks
(SM64 speed about 8) and a full tilt runs (about 32).

| Controller | SM64 |
|---|---|
| Left stick | Analog stick |
| Right stick | Camera |
| A | A (jump / swim) |
| X or B | B (punch / kick / dive) |
| LT, RT or LB | Z (crouch / ground pound) |
| Menu | Pause |
| View | Pin / hide the controls card |

Settings live under **Options → Controls → Mario 64 Gamepad…**: gamepad on/off, stick deadzone (default 15 %), right-stick
camera speed and invert Y. They're saved in `config/mario64/mario64.json`, and the screen shows which controller was
detected. Keyboard and mouse keep working alongside the pad. *GameSir G7 SE tip:* its two back paddles are remapped in
the controller itself. Mapping one to **LT** puts Z (long jump, backflip, ground pound) under a finger.

## Super Mario 64 music
The original soundtrack, played live by SM64's own sequencer from **your ROM's** sequences and soundbanks (no recordings
are shipped). In **Auto** the track follows where you are, and the track name pops up above the hotbar when it changes:

| Where | Track |
|---|---|
| Grassland, daytime | Bob-omb Battlefield |
| Swimming / underwater | Dire, Dire Docks |
| Deep underground (no sky, at least 8 blocks below the surface) | Cave Dungeon (Hazy Maze Cave) |
| Covered but shallow (houses, overhangs) | Inside the Castle Walls |
| Snowy biomes | Snow Mountain |
| Deserts, the Nether | Lethal Lava Land |
| Night | Haunted House (Big Boo's Haunt) |
| The End | Koopa's Road |
| Ender Dragon / Wither / Warden nearby | Koopa's Theme (Bowser battle) |

Under **Options → Controls → Mario 64 Settings…**, *Music* can be **While Mario** (default), **Always** or **Off**, and
*Track* can be **Auto** or any of 20 tracks (Slider, Ultimate Koopa, Metallic Mario, Staff Roll, File Select…).
Minecraft's own music pauses while SM64 music plays. SM64 music follows Minecraft's **Music** slider, and Mario's voice
and SFX follow **Players**.

Separate music volume needs the small libsm64 patch in `patches/libsm64-music-volume.patch` (adds `sm64_set_music_volume`).
Apply it before `make`.

## Install (Windows)
1. Install Fabric Loader 0.16.10 for 1.21.4 and Fabric API 0.119.4+1.21.4.
2. Put `mario64mc-0.1.0.jar` in `mods/`.
3. Put `sm64.dll` (built below) and your own ROM, renamed to `baserom.us.z64` (US release, SHA1 `9bef1128717f958171a4afac3ed78ee2bb4e86ce`), in `<game dir>/config/mario64/`.
4. In a single-player world, press **M**.

## Build
- `sm64.dll`: clone libsm64, run `git apply patches/libsm64-music-volume.patch`, then run `python import-mario-geo.py` (it fetches Mario's geo/model sources from n64decomp/sm64), then `make lib CC=gcc` with w64devkit/MSYS2 gcc.
- Mod: `cd mod && ./gradlew build` (JDK 21) produces `mod/build/libs/mario64mc-0.1.0.jar`.
- Automated in-game tests: `./gradlew runAutotest` (hills: moves), `runCourse`, `runShow` (flat course), `runTech`
  (long jump and wall kick), `runFlips` (backflip, side flip, slide kick) `runControls` (controls card, rebinding
  Mario's A to K while vanilla Jump stays on Space, and screenshots of the Key Binds section) and `runGamepad` (real pad
  detection, deadzone maths, and a virtual Xbox pad checking that a half tilt walks and a full tilt runs, plus the
  right-stick camera, A/LT/X, and screenshots of the settings screens) and `runMusic` (grass, cave, water and End
  each start the right SM64 track with audible output, the Music slider silences it, and 20 s of output is recorded to
  `mod/run/mario_music_bobomb.wav`). The last two end with a
  `RESULT …=PASS|FAIL` line. Each opens a
  lab world, becomes Mario, drives scripted controller input, logs `MARIO64TEST` state lines (SM64 action IDs,
  positions, mob HP) and saves screenshots to `mod/run/screenshots`.

## Limitations
- Single-player only. In multiplayer, other players see Steve and the server checks your movement against Steve's hitbox.
- Only the US ROM has been tested. Windows only for now (libsm64 also builds on Linux and macOS; load `libsm64.so` / `.dylib`).
- Curved and complex block shapes are approximated by their collision boxes.

## Credits
- The n64decomp team, for the SM64 decompilation.
- libsm64 (jaburns and contributors).
- FabricMC.
- Built by Claude (Anthropic AI) with Zckyy.

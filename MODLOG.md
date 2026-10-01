# MODLOG: Mario 64 in Minecraft

## Goal
Real SM64 Mario (decomp mechanics: actions, physics, animations, model) playable inside Minecraft Java.
Done = a Fabric mod where you toggle into Mario, run/jump/triple-jump/long-jump/wall-kick/ground-pound on Minecraft
blocks, rendered with Mario's real model and textures from the user's ROM. Plus a short clip.

## Facts
- Minecraft: `%APPDATA%\.minecraft`, Microsoft Store launcher, Java Edition 1.21.4.
- ROM: `Super Mario 64 (USA).z64` in the project root, SHA1 9bef1128717f958171a4afac3ed78ee2bb4e86ce = US. User-owned and NEVER committed.
- Single-player only. No anti-cheat concerns.

## Route (decided)
**libsm64 + Fabric 1.21.4 client mod (JNI-free, JNA or Panama FFI).**
- libsm64 (github.com/libsm64/libsm64) compiles the n64decomp/sm64 Mario code (mario.c, mario_actions_*.c,
  mario_step.c, surface collision, animations) into a native DLL. At runtime it reads Mario's geometry, textures
  and animations from the user's ROM. So it's the real decomp mechanics, and we ship no Nintendo assets.
- The Fabric mod loads the DLL, feeds it the Minecraft blocks around Mario as SM64 static/dynamic surfaces,
  ticks it at 30 Hz with player input, puts the MC player at Mario's position, and renders Mario's mesh.
- Why: cheapest route to *faithful* mechanics. A Java port of mario_actions_* would be weeks of work and less accurate.
- Units: 1 MC block = 100 SM64 units (about Mario-height scale: Mario is ~160 units tall vs player 1.8 blocks).

## Toolchain
- tools/ (gitignored): portable Temurin JDK 21, w64devkit (gcc/make).

## Log
- 2026-10-01: recon done, user chose 1.21.4 and approved the installs.

- Built sm64.dll with w64devkit gcc 2.10 (deps: KERNEL32 and msvcrt only). Mario geo/model fetched from n64decomp/sm64@06ec56d.
- Fabric 1.21.4 mod: JNA direct mapping (MC already ships JNA 5.15). 30 Hz SM64 tick from a GameRenderer.renderWorld HEAD
  mixin, with the player position set to interpolated Mario before the camera is placed.
- Install: launcher profile "Mario 64 (Fabric 1.21.4)", gameDir `.minecraft\mario64` (isolated from the user's other profiles).
  Backup of launcher_profiles.json is in backups/. To undo: delete that profile entry and the mario64 folder.

## Gotchas (numbered)
1. libsm64 audio debug_print floods the log (MBs per second). Filter the callback.
2. Untextured tris have uv (1,1). With GL_REPEAT that samples texel (0,0) and makes black speckles. Skip them in the decal pass.
3. SM64 shading is mix(color, tex, tex.a). Draw a colour pass, then a translucent texture pass nudged 0.35 units along the normals.
4. SM64 "no floor" = wall. Always give every column a floor (scan 64 blocks down, else a fallback floor).
5. The integrated server checks moves against Steve's 0.6x1.8 box and "moved wrongly" snaps Mario back on ramps. Set
   player.noClip=true for the Mario player at ServerPlayNetworkHandler.onPlayerMove HEAD.
6. libsm64 casts angles to int16 (rad/pi*32768). Wrap MC yaw to [-180,180) first.
7. Stick convention: stickY=-1 is forward and camLook = Mario - camera. Coordinates map x->x, z->z (both right-handed, y up).
8. Hiding chat (chatVisibility:2) also blocks /commands. Autotest runs setup through the server command manager silently.
9. sm64_surface_find_ceil segfaults with a NULL out-pointer.

## Verification (MARIO64TEST logs)
- hills: walk 0x04000440, jump/double 0x03000880/881, ledge grab/climb 0x0800034B/0x0000054F, punch 0x00800380,
  ground pound 0x008008A9 -> land 0x0080023C.
- flat show: triple jump 0x01000882 (+6 blocks), stairs ramp, stomp pig 10->5 HP + bounce, dive 0x0188088A, water plunge
  0x300022E2, swimming 0x300024D0/D1/E1, swim mesh 788 tris.
- tech (runTech): reactive inputs. Z while running -> crouch slide 0x04808459, fresh A -> long jump 0x03000888, z 7.0 -> 21.5
  (14.49 blocks), land 0x00000479. Running jump into a stone wall at z=30 -> AIR_HIT_WALL 0x000008A7 at z=29.50
  (radius 50), fresh A next frame -> WALL_KICK_AIR 0x03000886, flies back to z=24.8. RESULT long_jump=PASS wall_kick=PASS.
10. Wall kick needs a *fresh* A press (INPUT_A_PRESSED is an edge) within actionTimer <= 2 of ACT_AIR_HIT_WALL, and only
    happens if forwardVel > 16 when hitting the wall. Scripted tests must react to the action state, not fixed ticks.

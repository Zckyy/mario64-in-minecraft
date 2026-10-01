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

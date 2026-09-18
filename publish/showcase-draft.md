# EnsPillars — showcase page draft (EN, shared core)

> Reuse for Hangar / Modrinth / SpigotMC / BuiltByBit. Per-platform notes at the bottom.
> Screenshots/GIFs to attach: arena GUI, live game, replay 3D viewer, stats screen.
> Attach: `branding/banner-1920.png` (header), `branding/logo-512.png` (icon).

## Tagline

Free, modular **Pillars of Fortune** minigame for Paper 1.20+ — arenas, voting, parties, cosmetics, stats and crash-safe replays.

## Description

EnsPillars is a complete Pillars of Fortune minigame plugin: build arenas in-game with a guided setup mode, let players vote for game/map modes, run parties, sell cosmetics, track stats — and record every match as a replay you can rewatch in an offline 3D viewer.

No NMS, no paywalled features, MIT licensed.

## Features

- **Arena lifecycle** — create, guided GUI setup (spawns, center, spectator, border, timers, modes), enable/disable, teleport inspection
- **Game modes & modifiers** — Normal, Shuffle, Swap; Lava Rise, Fragile, TNT Rain, Shrinking Border, UHC hook
- **Voting** — chat + GUI votes for game/map modes with cooldowns
- **Party system** — create, invite, accept/decline, mass-join an arena
- **Shop & cosmetics** — cages, kill-messages, death-cries with coin economy and rank-locked items
- **Stats & spectating** — persistent stats, win-streaks, leaderboards, spectate menu
- **Replays** — binary `.ens` recording, keyframes, crash-safe partial replays, retention limits, offline 3D web viewer
- **Safety** — two-step confirmation on destructive commands, audit logging, inventory snapshot/restore on every exit path, block rollback

## Requirements

- Paper 1.20.1+ (1.20.x–1.21.x friendly), Java 17+ on the server
- Optional: PlaceholderAPI (`%enspillars_*%`)
- Storage: YAML (default), SQLite or MySQL (`/enspillars import` migrates YAML → SQL once)

## Commands & permissions (summary)

`/pof join|leave|menu|stats|shop|vote|party|spectate|autojoin|replay|arena|setup|setspawn|forcestart|cosmetics` · `/enspillars info|help|reload|import|debug`
`enspillars.use/vote/party/cosmetics/stats/replay` (default true) · `enspillars.admin.*` + `enspillars.forcestart` (op)

## Support

Discord: https://discord.gg/xnE8zYkCyz · Issues: GitHub

---

## Per-platform notes

- **Hangar** — publish first (review queue). Use Markdown above as-is. Add `Paper` + version range tags.
- **Modrinth** — enable update-check in config (`platform: modrinth`, `slug: <project-slug>`) after publishing; summary ≤ 300 chars: use the tagline.
- **SpigotMC** — BBCode version needed (convert headers/lists); resource icon = logo-512.png.
- **BuiltByBit** — free listing; link back to Hangar/Modrinth as download mirrors if desired.

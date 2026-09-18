# EnsPillars 1.0.0

Free, modular **Pillars of Fortune** minigame for Paper 1.20+. MIT licensed.

Build arenas in-game with a guided setup mode, let players vote for game/map modes, run parties, sell cosmetics, track stats — and record every match as a replay you can rewatch in an offline 3D viewer.

- 🌍 Language: [Türkçe](README.tr.md) | **English**
- 💬 Support: [Discord](https://discord.gg/xnE8zYkCyz)
- 📊 Metrics: [bStats](https://bstats.org/plugin/bukkit/EnsPillars/34112)

## Features

- **Arena lifecycle** — `WAITING → STARTING → PRE_GAME → CAGED → ACTIVE → FINAL → ENDING → RESETTING`; create, guided GUI setup (spawns, center, spectator, border, timers, modes), enable/disable, teleport inspection
- **Game & map modes** — Normal, Shuffle, Swap; Lava Rise, Fragile, TNT Rain, Shrinking Border, UHC hook
- **Voting** — chat + GUI votes for game/map modes with cooldowns
- **Party system** — create, invite, accept/decline, mass-join an arena (max 5)
- **Shop & cosmetics** — cages, kill-messages, death-cries; coin economy, rank-locked items
- **Stats & spectating** — persistent stats, win-streaks, paginated leaderboards, spectate menu
- **Replays** — binary `.ens` recording with keyframes, crash-safe partial replays, retention/size pruning, offline 3D web viewer (bundled three.js)
- **Safety** — two-step confirmation on destructive commands (`arena delete|rollback`, `replay delete`, `setspawn`, `import`), audit logging, inventory snapshot/restore on every exit path (quit/restart included), block rollback

## Requirements

- **Server:** Paper 1.20.1+ (1.20.x–1.21.x friendly, no NMS), Java 17+
- **Optional:** PlaceholderAPI (expansion `%enspillars_*%`)
- **Build:** JDK 17–21 to run Gradle (compiles with a Java 17 toolchain)

## Installation

1. Download `EnsPillars-1.0.0.jar` (GitHub Releases) or build it: `./gradlew shadowJar` → `build/libs/`
2. Drop it into your server's `plugins/` folder, restart
3. Configure `plugins/EnsPillars/config.yml` to taste

## Quick start (arena setup, all in-game)

1. `/pof arena create <id>`
2. `/pof arena setup <id>` — enters setup mode (inventory is backed up and restored on exit)
3. In the management GUI: adjust player counts, border, timers and modes with +/- buttons
4. Click Center / Spectator, then click a block in the world to set each point
5. Spawn capture: click one block per spawn (height above block: `setup.spawn-height`); ender-eye tool teleports through spawns to inspect
6. Save & Enable when the status item is green
7. `/pof setspawn` — optional global return point (used when the arena has no lobby/spawn)

Reloading a single arena never touches other running games. `/enspillars reload` while games are running asks for confirmation.

## Commands

| Command | Description |
|---|---|
| `/pof` | Main menu |
| `/pof join [arena]` · `autojoin` · `leave` · `menu` | Play |
| `/pof stats` · `shop` · `vote <game\|map> <value>` | Stats, store, voting |
| `/pof party create\|invite\|accept\|decline\|leave\|join` | Party |
| `/pof spectate [player]` | Watch a match |
| `/pof replay list\|latest\|open\|download\|delete` | Replays (`delete` is admin + confirmed) |
| `/pof arena list\|info\|create\|delete\|enable\|disable\|tp\|setup\|edit\|rollback` | Arena admin |
| `/pof setup <id>` · `/pof setspawn` · `/pof forcestart` | Setup shortcut, global spawn, force start |
| `/enspillars` · `help` · `reload <…>` · `import` · `debug` | Info & maintenance |

Roots, aliases and descriptions are configurable in `commands.yml`.

## Permissions

| Node | Default | Description |
|---|---|---|
| `enspillars.use` | true | Base command access |
| `enspillars.vote` · `enspillars.party` · `enspillars.cosmetics` · `enspillars.stats` · `enspillars.replay` | true | Player features |
| `enspillars.forcestart` | op | Force-start a game |
| `enspillars.admin` (+ `reload`, `import`, `arena`, `debug`, `setup` children) | op | Administration |
| `enspillars.cages.gold` · `enspillars.cages.netherite` · `enspillars.deathcries.dragon` | op | Rank-locked cosmetics |

## Storage

`storage.type`: `YAML` (default), `SQLITE` or `MYSQL` (see `storage.mysql` in config.yml).
Switching to SQL later? `/enspillars import` (perm `enspillars.admin.import`, confirmed) migrates `players.yml` + `cosmetics.yml` once.

## Production notes

- One arena per world is strongly recommended: the vanilla world border is shared per world, so two active arenas in the same world will fight over it (startup logs a warning).
- Replay web viewer binds to `127.0.0.1` by default. If you bind a public address, set `replay.web.auth-token`; requests without the token receive `401`.
- Replays record delta snapshots (full keyframe every `replay.keyframe-interval` ticks), survive crashes as partial replays, and are pruned by `replay.retention-days` / `replay.max-total-mb`.
- Player inventories (contents, armor, XP, effects, gamemode, flight) are snapshotted on join and restored on every exit path, including quit and server restart (disk backup in `snapshots.yml`).
- Quitting mid-game counts as a loss, so win-streaks cannot be protected by disconnecting.
- Statistics auto-save interval: `settings.auto-save-seconds` (0 disables).
- Update checker is off by default; enable it in `config.yml → update-check` once the Hangar/Modrinth slug is known.

## Support

- Discord: https://discord.gg/xnE8zYkCyz
- Bug reports & feature requests: GitHub Issues

## License

MIT — see [LICENSE](LICENSE).

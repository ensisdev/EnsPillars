# EnsPillars Replay System

## Goal

The replay system is designed as a first-class competitive-review system rather than a simple event log. It uses `.ens` as the canonical binary recording and converts that stream into a browser-friendly representation only when a replay is opened.

## Recorded data

- Every active-match tick: position, yaw/pitch, health, hunger, fire state, movement state and game mode.
- Inventory contents, held slot and item display names.
- Active potion effects.
- Match lifecycle events.
- Hits and damage.
- Block place/break.
- Match chat.
- Initial arena block scene.
- Player UUID/name mapping.

## Browser viewer

The optional built-in web server provides `/replay/<id>` and `/api/replay/<id>`.
The viewer supports:

- Free camera
- Top-down camera
- Follow-player camera
- First-person camera
- Tick scrubbing
- 0.25x–16x playback
- Player inspector
- Inventory/effect inspection
- Event markers
- Screenshot capture
- Browser-side WebM clip recording

## Security / deployment

Set `replay.web.bind` and `replay.web.port` to the desired listener. If the server is behind a reverse proxy, set `replay.web.public-url` to the public HTTPS base URL. The built-in server is intentionally small; for public production deployments, put it behind an HTTPS reverse proxy and access control.

## Future-proofing

The `.ens` format has an explicit magic value and version. New record types can be added without changing the viewer contract. The browser only consumes the normalized API representation, allowing the binary recorder and web viewer to evolve independently.

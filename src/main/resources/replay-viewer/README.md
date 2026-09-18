# EnsPillars 3D Replay Viewer

EnsPillars records match state into the native `.ens` replay format and exposes an optional browser viewer.

## Viewer

- `/replay/<id>` opens a full 3D browser replay.
- `/api/replay/<id>` returns the normalized replay document.
- Free / top / follow / first-person camera modes.
- Tick-accurate timeline and event markers.
- Player health, hunger, effects, game mode and inventory inspection.
- Screenshot and browser-side WebM clip capture.
- Match chat, combat, elimination and block events are recorded as timeline events.

The viewer uses Three.js from a browser import map. For a completely offline deployment, replace the CDN imports in `index.html` with a locally hosted Three.js build.

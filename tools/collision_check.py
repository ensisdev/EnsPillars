"""Carpisma + yetki sizma kontrolu (skill full-track Phase 9 gati).

Kullanim:  python tools/collision_check.py docs/command-ast.json
Kontroller:
  1) Ayni root icinde case-insensitive yol carpismasi.
  2) Farkli handler'a giden ayni yol (golgeleme).
  3) Tab-completion sizintisi: admin/confirm dallari permissionGated=true olmali.
  4) Console'a acik dallarda requiresPlayer celiskisi.
Cikis kodu 0 = PASS, 1 = FAIL.
"""
from __future__ import annotations

import json
import sys

FAILURES: list[str] = []


def fail(msg: str) -> None:
    FAILURES.append(msg)


SENSITIVE = ("arena delete", "arena rollback", "arena setup", "arena edit",
             "replay delete", "import", "setspawn", "setup", "clearspawns")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("kullanim: python tools/collision_check.py <ast.json>")
        return 2
    with open(argv[1], encoding="utf-8") as fh:
        ast = json.load(fh)
    paths: dict[str, list[str]] = {}
    for root in ast.get("roots", []):
        rname = root.get("name", "?")
        for b in root.get("branches", []):
            full = [rname] + list(b.get("path", []))
            key = "/".join(full).lower()
            paths.setdefault(key, []).append(b.get("handler", "?"))
            low = key
            # completion sizinti kontrolu
            comps = b.get("completion", [])
            gated_l1 = any(c.get("level") == 1 and c.get("permissionGated") for c in comps)
            if any(s in low for s in SENSITIVE) and not gated_l1:
                fail(f"sizinti: '{'/'.join(full)}' hassas dal ama L1 completion permissionGated degil")
            # console celiskisi
            ctxs = set(b.get("contexts", []))
            if b.get("requiresPlayer") and "console" in ctxs:
                fail(f"celiski: '{'/'.join(full)}' requiresPlayer ama console context acik")
            if not b.get("requiresPlayer") and root.get("console") == "deny-quiet":
                fail(f"celiski: '{'/'.join(full)}' console reddi sessiz (deny-quiet yasak)")
    for key, handlers in paths.items():
        if len(handlers) > 1 and len(set(handlers)) > 1:
            fail(f"golgeleme: '{key}' -> farkli handler'lar {handlers}")
    # kok adlari tekil mi
    names = [r.get("name") for r in ast.get("roots", [])]
    if len(set(n.lower() for n in names)) != len(names):
        fail(f"kok adi carpismasi: {names}")
    if FAILURES:
        print("COLLISION CHECK: FAIL")
        for f in FAILURES:
            print(f"  - {f}")
        return 1
    print("COLLISION CHECK: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))

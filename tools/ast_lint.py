"""EnsPillars command-AST linter (skill full-track Phase 9 gati).

Kullanim:  python tools/ast_lint.py docs/command-ast.json schemas/command-ast.schema.json
Cikis kodu 0 = PASS, 1 = FAIL. jsonschema kurulu degilse dahili mini-dogrulama
calisir (required alanlar + astVersion==4 + enum/pattern kontrolleri); CI'da
`pip install jsonschema` ile tam dogrulama onerilir.
"""
from __future__ import annotations

import json
import re
import sys

FAILURES: list[str] = []


def fail(msg: str) -> None:
    FAILURES.append(msg)


def load_json(path: str):
    with open(path, encoding="utf-8-sig") as fh:
        return json.load(fh)


SCHEMA_TYPES = ("string", "integer", "boolean", "array", "object")


def check_type(value, spec: dict, trail: str) -> None:
    t = spec.get("type")
    if t is None:
        return
    ok = {
        "string": isinstance(value, str),
        "integer": isinstance(value, bool | int) and isinstance(value, int) and not isinstance(value, bool),
        "boolean": isinstance(value, bool),
        "array": isinstance(value, list),
        "object": isinstance(value, dict),
    }.get(t, True)
    if not ok:
        fail(f"{trail}: beklenen tip {t}, gelen {type(value).__name__}")
        return
    if "const" in spec and value != spec["const"]:
        fail(f"{trail}: const ihlali (beklenen {spec['const']!r})")
    if "enum" in spec and value not in spec["enum"]:
        fail(f"{trail}: enum disi deger {value!r} (izinli: {spec['enum']})")
    if "pattern" in spec and isinstance(value, str):
        if not re.search(spec["pattern"], value):
            fail(f"{trail}: pattern ihlali {spec['pattern']!r}")
    if t == "object":
        for req in spec.get("required", []):
            if req not in value:
                fail(f"{trail}: eksik alan '{req}'")
        for key, sub in spec.get("properties", {}).items():
            if key in value:
                check_type(value[key], sub, f"{trail}.{key}")
    if t == "array":
        if "minItems" in spec and len(value) < spec["minItems"]:
            fail(f"{trail}: en az {spec['minItems']} oge gerekli")
        item_spec = spec.get("items")
        if item_spec:
            for i, item in enumerate(value):
                check_type(item, item_spec, f"{trail}[{i}]")


def semantic_checks(ast: dict) -> None:
    if ast.get("astVersion") != 4:
        fail("astVersion == 4 olmali")
    perm_nodes = {p.get("node") for p in ast.get("permissions", []) if isinstance(p, dict)}
    unchecked = sorted(
        p.get("node", "?")
        for p in ast.get("permissions", [])
        if isinstance(p, dict) and not p.get("checked", False)
    )
    if unchecked:
        fail(f"checked=false permission kalamaz (olum node): {unchecked}")
    seen_paths: dict[str, str] = {}
    vote_l2: set[str] = set()
    for root in ast.get("roots", []):
        for b in root.get("branches", []):
            path = "/".join([root.get("name", "?")] + list(b.get("path", [])))
            key = path.lower()
            if key in seen_paths:
                fail(f"carpisma: '{path}' daha once tanimli")
            seen_paths[key] = path
            if b.get("permission") not in perm_nodes:
                fail(f"{path}: permission '{b.get('permission')}' permissions tablosunda yok")
            if not b.get("messages"):
                fail(f"{path}: en az bir messages.yml anahtari gerekli")
            if not b.get("completion"):
                fail(f"{path}: completion BOS birakilamaz")
            for c in b.get("completion", []):
                if not str(c.get("source", "")).strip():
                    fail(f"{path}: completion source bos")
            if b.get("confirmation") == "none" and any(
                k in key for k in ("arena delete", "replay delete", "import", "setspawn", "rollback", "clearspawns")
            ):
                fail(f"{path}: yikici dalda confirmation=none yasak")
            if key.startswith("pof/vote/") and len(b.get("path", [])) == 2:
                _l2 = b["path"][1].upper()
                if _l2 in ("GAME", "MAP"):
                    # L2 kategori dallari degil, L3 enum dallari toplanir.
                    for sib in root.get("branches", []):
                        if len(sib.get("path", [])) == 3 and sib["path"][0] == "vote" and sib["path"][1] == b["path"][1]:
                            vote_l2.add(sib["path"][2].upper())
                else:
                    vote_l2.add(_l2)
    # Vote enum capraz kontrolu: ArenaSetupMenu L23 + ModifierService modlari + arena.yml voting listeleri.
    expected = {"NORMAL", "SHUFFLE", "SWAP", "LAVA_RISE", "FRAGILE", "TNT_RAIN", "ABLOCKALYPSE", "SHRINKING_BORDER"}
    if vote_l2 and vote_l2 != expected:
        fail(f"vote L2 enum eslesmiyor: AST={sorted(vote_l2)} beklenen={sorted(expected)}")
    # Doc-drift guard: /pof setup alias'i AST'de olmak zorunda (plugin.yml:16 + messages.yml:33 ilani).
    if "pof/setup" not in seen_paths:
        fail("doc-drift: '/pof setup' alias dali AST'de yok (plugin.yml:16 + messages.yml:33 ilani)")


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("kullanim: python tools/ast_lint.py <ast.json> <schema.json>")
        return 2
    ast, schema = load_json(argv[1]), load_json(argv[2])
    # 1) sema iskeleti (semantik kontrollerden once, sema dosyasi uyusmazligini yakala)
    if not isinstance(schema, dict) or "properties" not in schema:
        fail("sema dosyasi obje degil / properties yok")
    else:
        check_type(ast, schema, "$")
        # 2) jsonschema varsa tam dogrulama (yoksa mini-dogrulama gecerli sayilir)
        try:
            import jsonschema  # type: ignore

            jsonschema.validate(ast, schema)
        except ImportError:
            print("NOT: jsonschema yok, dahili mini-dogrulama kullanildi", flush=True)
        except Exception as exc:  # jsonschema.ValidationError dahil
            fail(f"jsonschema: {exc}")
    # 3) semantik kontroller (P0 kurallari)
    if isinstance(ast, dict):
        semantic_checks(ast)
    if FAILURES:
        print("AST LINT: FAIL")
        for f in FAILURES:
            print(f"  - {f}")
        return 1
    print("AST LINT: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))

import json
def B(path, perm, req, ctxs, conf, comp, handler, msgs, disp, extra=None):
    b = {"path": path, "permission": perm, "requiresPlayer": req, "contexts": ctxs,
         "confirmation": conf, "completion": comp, "handler": handler,
         "messages": msgs, "disposition": disp}
    if extra: b.update(extra)
    return b
def C(lvl, src, gated=False):
    c = {"level": lvl, "source": src}
    if gated: c["permissionGated"] = True
    return c
P="player"; CO="console"; CB="command-block"
g_enum = ["NORMAL","SHUFFLE","SWAP"]
m_enum = ["NORMAL","LAVA_RISE","FRAGILE","TNT_RAIN","ABLOCKALYPSE","SHRINKING_BORDER"]
extra = []
for g in g_enum:
    extra.append(B(["vote","game",g], "enspillars.vote", True, [P], "none",
        [C(1,"literal:game"),C(2,"enum:"+",".join(g_enum))],
        "VoteCommands.voteGame", ["vote.game-ok"], "preserve", {"hotbar": True}))
for m in m_enum:
    extra.append(B(["vote","map",m], "enspillars.vote", True, [P], "none",
        [C(1,"literal:map"),C(2,"enum:"+",".join(m_enum))],
        "VoteCommands.voteMap", ["vote.map-ok"], "fix" if m=="SHRINKING_BORDER" else "preserve", {"hotbar": True}))
with open("docs/pof_branches3.json","w",encoding="utf-8") as f:
    json.dump(extra,f,ensure_ascii=False)
print(f"vote L3: {len(extra)} dal")

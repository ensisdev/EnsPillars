import json
V2 = "NORMAL,SHUFFLE,SWAP"
V2M = "NORMAL,LAVA_RISE,FRAGILE,TNT_RAIN,ABLOCKALYPSE,SHRINKING_BORDER"
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
P = "player"; CO = "console"; CB = "command-block"
branches = [
 B(["hub"], "enspillars.use", True, [P], "none", [C(1,"none:bare-root")], "PofCommand.bare", ["help-pof"], "preserve", {"hotbar": True}),
 B(["help"], "enspillars.use", False, [P,CO,CB], "none", [C(1,"none")], "PofCommand.help", ["help-pof"], "preserve"),
 B(["join"], "enspillars.use", True, [P], "none", [C(1,"arena-ids:WAITING")], "GameFlowCommands.join", ["join.not-player","join.none","join.already","join.fail"], "preserve", {"hotbar": True, "compatNote": "[arena] opsiyonel korunur"}),
 B(["leave"], "enspillars.use", True, [P], "none", [C(1,"none")], "GameFlowCommands.leave", ["join.not-player","leave.ok"], "preserve", {"hotbar": True}),
 B(["menu"], "enspillars.use", True, [P], "none", [C(1,"none")], "GameFlowCommands.menu", ["cmd.console-only"], "fix", {"hotbar": True, "compatNote": "console sessizligi onarildi"}),
 B(["spectate"], "enspillars.use", True, [P], "none", [C(1,"online-players:in-game")], "GameFlowCommands.spectate", ["join.not-player","spectate.joined","spectate.player-gone"], "preserve", {"hotbar": True, "compatNote": "[oyuncu] opsiyonel korunur"}),
 B(["autojoin"], "enspillars.use", True, [P], "none", [C(1,"none")], "GameFlowCommands.autojoin", ["auto.joined","auto.none"], "preserve", {"hotbar": True}),
 B(["forcestart"], "enspillars.forcestart", True, [P], "none", [C(1,"none",True)], "GameFlowCommands.forcestart", ["force.started","force.fail"], "fix", {"compatNote": "admin fallback"}),
 B(["vote"], "enspillars.vote", True, [P], "none", [C(1,"literal:game,map")], "VoteCommands.usage", ["vote.usage"], "preserve", {"hotbar": True}),
 B(["vote","game"], "enspillars.vote", True, [P], "none", [C(1,"literal:game"),C(2,"enum:"+V2)], "VoteCommands.voteGame", ["vote.game-ok","vote.invalid"], "preserve", {"hotbar": True}),
 B(["vote","map"], "enspillars.vote", True, [P], "none", [C(1,"literal:map"),C(2,"enum:"+V2M)], "VoteCommands.voteMap", ["vote.map-ok","vote.invalid"], "fix", {"hotbar": True, "compatNote": "SHRINKING_BORDER eklendi"}),
 B(["party"], "enspillars.party", True, [P], "none", [C(1,"literal:create,invite,accept,decline,leave,join")], "PartyCommands", ["party.usage"], "preserve"),
 B(["party","create"], "enspillars.party", True, [P], "none", [C(1,"literal:create"),C(2,"none")], "PartyCommands.create", ["party.created","party.already"], "preserve"),
 B(["party","invite"], "enspillars.party", True, [P], "none", [C(1,"literal:invite"),C(2,"online-players")], "PartyCommands.invite", ["party.invite-sent"], "preserve"),
 B(["party","accept"], "enspillars.party", True, [P], "none", [C(1,"literal:accept"),C(2,"pending-invites opsiyonel")], "PartyCommands.accept", ["party.accepted","party.no-invite"], "preserve", {"compatNote": "[player] opsiyonel korunur"}),
 B(["party","decline"], "enspillars.party", True, [P], "none", [C(1,"literal:decline"),C(2,"pending-invites opsiyonel")], "PartyCommands.decline", ["party.declined"], "preserve"),
 B(["party","leave"], "enspillars.party", True, [P], "none", [C(1,"literal:leave"),C(2,"none")], "PartyCommands.leave", ["party.left"], "preserve"),
 B(["party","join"], "enspillars.party", True, [P], "none", [C(1,"literal:join"),C(2,"arena-ids")], "PartyCommands.join", ["party.joined"], "preserve"),
 B(["shop"], "enspillars.cosmetics", True, [P], "none", [C(1,"literal:cages,killmessages,deathcries")], "ShopCommands.shop", ["cmd.console-only"], "fix"),
 B(["cosmetics"], "enspillars.cosmetics", True, [P], "none", [C(1,"material-or-catalog")], "ShopCommands.cosmetics", ["cosmetics.selected","cosmetics.invalid"], "fix"),
 B(["stats"], "enspillars.stats", True, [P], "none", [C(1,"none")], "ShopCommands.stats", ["cmd.console-only"], "fix", {"compatNote": "cosmetics->stats ayrildi"}),
 B(["leaderboard"], "enspillars.stats", True, [P], "none", [C(1,"literal:leaderboard")], "ShopCommands.stats", ["cmd.console-only"], "alias"),
 B(["replay"], "enspillars.replay", False, [P,CO,CB], "none", [C(1,"literal:list,latest,open,download,delete",True)], "ReplayCommands", ["replay.usage"], "preserve"),
 B(["replay","list"], "enspillars.replay", False, [P,CO,CB], "none", [C(1,"literal:list"),C(2,"none")], "ReplayCommands.list", ["replay.list-row","replay.none"], "fix"),
 B(["replay","latest"], "enspillars.replay", False, [P,CO,CB], "none", [C(1,"literal:latest"),C(2,"arena-ids opsiyonel")], "ReplayCommands.latest", ["replay.header"], "extend"),
 B(["replay","open"], "enspillars.replay", False, [P,CO,CB], "none", [C(1,"literal:open"),C(2,"replay-ids")], "ReplayCommands.open", ["replay.open-id"], "extend"),
 B(["replay","download"], "enspillars.replay", False, [P,CO,CB], "none", [C(1,"literal:download"),C(2,"replay-ids")], "ReplayCommands.download", ["replay.open-id"], "extend"),
 B(["replay","delete"], "enspillars.admin.arena", False, [P,CO,CB], "confirm-token", [C(1,"literal:delete",True),C(2,"replay-ids")], "ReplayCommands.delete", ["confirm.warn","replay.deleted"], "fix", {"compatNote": "P0 onaysiz silme onarildi"}),
]
with open("docs/pof_branches.json","w",encoding="utf-8") as f:
    json.dump(branches,f,ensure_ascii=False)
print(f"pof part1: {len(branches)} dal")

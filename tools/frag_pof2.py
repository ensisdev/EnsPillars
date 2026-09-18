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
branches = [
 B(["arena"], "enspillars.admin.arena", False, [P,CO,CB], "none", [C(1,"literal:list,info,create,delete,enable,disable,tp,setup,edit,rollback",True)], "ArenaAdminCommands", ["arena-admin.usage"], "preserve"),
 B(["arena","list"], "enspillars.admin.arena", False, [P,CO,CB], "none", [C(1,"literal:list"),C(2,"none")], "ArenaAdminCommands.list", ["arena.list-header","arena.list-row"], "fix"),
 B(["arena","info"], "enspillars.admin.arena", False, [P,CO,CB], "none", [C(1,"literal:info"),C(2,"arena-ids")], "ArenaAdminCommands.info", ["arena.info-row","arena-admin.not-found"], "fix"),
 B(["arena","create"], "enspillars.admin.arena", False, [P,CO,CB], "none", [C(1,"literal:create"),C(2,"new-id")], "ArenaAdminCommands.create", ["arena-admin.created"], "preserve"),
 B(["arena","delete"], "enspillars.admin.arena", False, [P,CO,CB], "confirm-token", [C(1,"literal:delete",True),C(2,"arena-ids")], "ArenaAdminCommands.delete", ["confirm.warn","arena-admin.deleted"], "fix", {"compatNote": "P0 kritik onaysiz dosya silme"}),
 B(["arena","enable"], "enspillars.admin.arena", False, [P,CO,CB], "none", [C(1,"literal:enable"),C(2,"arena-ids:disabled")], "ArenaAdminCommands.enable", ["arena-admin.state-updated"], "preserve"),
 B(["arena","disable"], "enspillars.admin.arena", False, [P,CO,CB], "none", [C(1,"literal:disable"),C(2,"arena-ids:enabled")], "ArenaAdminCommands.disable", ["arena-admin.state-updated"], "preserve"),
 B(["arena","tp"], "enspillars.admin.arena", True, [P], "none", [C(1,"literal:tp"),C(2,"arena-ids")], "ArenaAdminCommands.tp", ["setup.teleported"], "preserve"),
 B(["arena","setup"], "enspillars.admin.setup", True, [P], "none", [C(1,"literal:setup",True),C(2,"arena-ids")], "ArenaAdminCommands.setup", ["setup.enter"], "preserve"),
 B(["arena","edit"], "enspillars.admin.setup", True, [P], "none", [C(1,"literal:edit",True),C(2,"arena-ids")], "ArenaAdminCommands.setup", ["setup.enter"], "alias"),
 B(["arena","rollback"], "enspillars.admin.arena", False, [P,CO,CB], "confirm-token", [C(1,"literal:rollback",True),C(2,"arena-ids")], "ArenaAdminCommands.rollback", ["confirm.warn","arena-admin.rollback-done"], "fix", {"compatNote": "P0 onaysiz rollback"}),
 B(["setup"], "enspillars.admin.setup", True, [P], "none", [C(1,"literal:setup",True),C(2,"arena-ids")], "ArenaAdminCommands.setup", ["setup.enter"], "alias", {"compatNote": "doc-drift EKLE: /pof setup ilan ediliyordu"}),
 B(["setspawn"], "enspillars.admin.setup", True, [P], "confirm-token", [C(1,"none",True)], "ArenaAdminCommands.setspawn", ["confirm.warn","setup.setspawn-ok"], "fix", {"compatNote": "P0 onaysiz global-lobby ezme"}),
]
with open("docs/pof_branches2.json","w",encoding="utf-8") as f:
    json.dump(branches,f,ensure_ascii=False)
print(f"pof part2: {len(branches)} dal")

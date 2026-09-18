import json
base = json.load(open("docs/command-ast.json", encoding="utf-8"))
ens = {"name":"enspillars","permission":"enspillars.use","console":"allow","enabledFlag":"commands.enspillars.enabled","aliases":[],"branches":[
 {"path":["info"],"permission":"enspillars.use","requiresPlayer":False,"contexts":["player","console","command-block"],"confirmation":"none","completion":[{"level":1,"source":"none"}],"handler":"InfoCommand.info","messages":["info"],"disposition":"preserve"},
 {"path":["version"],"permission":"enspillars.use","requiresPlayer":False,"contexts":["player","console","command-block"],"confirmation":"none","completion":[{"level":1,"source":"none"}],"handler":"InfoCommand.info","messages":["info","version"],"disposition":"alias","compatNote":"info ile ayni"},
 {"path":["help"],"permission":"enspillars.use","requiresPlayer":False,"contexts":["player","console","command-block"],"confirmation":"none","completion":[{"level":1,"source":"none"}],"handler":"InfoCommand.help","messages":["help"],"disposition":"preserve"},
 {"path":["reload"],"permission":"enspillars.admin.reload","requiresPlayer":False,"contexts":["player","console","command-block"],"confirmation":"existing-reload-guard","completion":[{"level":1,"source":"literal:all,config,messages,arena,shop,scoreboards,hotbar","permissionGated":True},{"level":2,"source":"literal:confirm"}],"handler":"InfoCommand.reload","messages":["reload.confirm-warn","reloaded"],"disposition":"preserve"},
 {"path":["reload","<category>","confirm"],"permission":"enspillars.admin.reload","requiresPlayer":False,"contexts":["player","console","command-block"],"confirmation":"existing-reload-guard","completion":[{"level":1,"source":"literal:category","permissionGated":True},{"level":2,"source":"literal:confirm"}],"handler":"InfoCommand.reload","messages":["reload.confirm-warn","reloaded"],"disposition":"extend"},
 {"path":["import"],"permission":"enspillars.admin.import","requiresPlayer":False,"contexts":["player","console","command-block"],"confirmation":"confirm-token","completion":[{"level":1,"source":"literal:confirm","permissionGated":True}],"handler":"ImportRunner.run","messages":["confirm.warn","import.done"],"disposition":"extend","compatNote":"P0 onay"},
 {"path":["debug"],"permission":"enspillars.admin.debug","requiresPlayer":False,"contexts":["player","console","command-block"],"confirmation":"none","completion":[{"level":1,"source":"none","permissionGated":True}],"handler":"InfoCommand.debug","messages":["debug.line"],"disposition":"fix"},
]}
b1 = json.load(open("docs/pof_branches.json", encoding="utf-8"))
b2 = json.load(open("docs/pof_branches2.json", encoding="utf-8"))
b3 = json.load(open("docs/pof_branches3.json", encoding="utf-8"))
pof = {"name":"pof","permission":"enspillars.use","console":"allow-limited","enabledFlag":"commands.pof.enabled","aliases":[],"branches": b1+b2+b3}
base["roots"] = [ens, pof]
json.dump(base, open("docs/command-ast.json","w",encoding="utf-8"), ensure_ascii=False)
print(f"roots=2 enspillars={len(ens['branches'])} pof={len(b1+b2+b3)}")

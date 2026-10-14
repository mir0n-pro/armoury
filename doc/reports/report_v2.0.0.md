# Release Report: HEAD~1 → v2.0.0

**Repo:** `armoury/main`  
**Top commit:** `f0ec13f`

---

## Release Notes

### doc/release_notes.txt

Armoury 2.0  
Armoury -- utils, taijitu, hauberk  

**v2.0.0-2610.1418**  v2.0.0 -- the first release: the libraries leave Esquire  
&nbsp;: Refactoring: armoury-utils, the utility library, comes from Esquire services, where it was  
&nbsp;                 mir0n-utils  
&nbsp;: Refactoring: armoury-taijitu, the recoverable cache, is a jar of its own; in Esquire it was  
&nbsp;                 part of mir0n-utils  
&nbsp;: Refactoring: armoury-hauberk, the load-test harness, comes from Esquire explorer; the Esquire  
&nbsp;                 load tests stay with Esquire  
&nbsp;: Doc:         README.md  
&nbsp;                 doc\Armoury.Utils.md  
&nbsp;                 doc\Armoury.Taijitu.md  
&nbsp;                 doc\Armoury.Haubergeon.md  
&nbsp;   Components:   utils (new),  
&nbsp;                 taijitu (new),  
&nbsp;                 hauberk (new)  
&nbsp;   The history before v2.0.0 is in the Esquire release notes, release v1.2.16:  
&nbsp;     utils, taijitu -- as mir0n-utils:  
&nbsp;       https://github.com/mir0n-pro/esquire.services/blob/release/v1.2.16/doc/release_notes.txt  
&nbsp;     hauberk:  
&nbsp;       https://github.com/mir0n-pro/esquire.explorer/blob/release/v1.2.16/doc/release_notes.txt  

---

## Code Changes

### hauberk/src/main/java/pro/mir0n/hauberk/changes.txt

armoury hauberk changes -- pro.mir0n.hauberk  

**10/13/2026** mir0n  v2.0.0 -- the split: the generic half of the harness becomes Armoury Hauberk  
**config.HauberkConfig**  
&nbsp;- reduced to the generic half: the file loader, the overlay, ${key} expansion, the typed accessors  
&nbsp;   (require / optional / optionalLowercase / requireInt / requireIntMin0 / requireIntMin / keysUnder),  
&nbsp;   the cmd.* map, METRICS_ENABLED, SIMULATIONS_PACKAGE and the four perf capture header names. Every  
&nbsp;   Esquire setting (gw.base, bff.base, enyman.base, playground.parent.id, prepare.* / move.* /  
&nbsp;   super.*) left for the Esquire-side config class, which reads it through these accessors  
config.AuthConfig  (new)  
&nbsp;- the OIDC half of the old HauberkConfig, lifted out whole so a target with no identity provider  
&nbsp;   never triggers the kc.* requirements. Keys are unchanged (kc.base, kc.realm, kc.client.id,  
&nbsp;   kc.client.secret, kc.admin.*, tokenRelay.type); kc.admin.client.id no longer defaults to an  
&nbsp;   Esquire client name  
**auth.KcTokenClient**  
&nbsp;- reads the client and the token endpoint from AuthConfig  
**auth.RefreshableToken**  
&nbsp;- moved into Armoury, unchanged  
**chain.Cmd**  
&nbsp;- moved into Armoury unchanged: the step runs whatever the cmd. setting says, so it belongs to  
&nbsp;   the harness and not to any one target  
**health.HealthPreCheck**  
&nbsp;- the probe LIST moved out of the code into the properties file: health.probe.= for a  
&nbsp;   plain probe, health.auth.probe= for the one that presents the edge credential  
**perf.PerformanceMatrix**  
&nbsp;- the five capture header names come from the properties file (perf.header.capture /  
&nbsp;   perf.header.gw.outer / .gw.inner / .srv.outer / .srv.inner) instead of being compiled-in mirrors of  
&nbsp;   EsqConstants, a blank name meaning that column is not reported; instrument() takes the target base  
&nbsp;   URL; the CSV preamble carries "label" (perf.run.label) and "target" in place of the client /  
&nbsp;   kc_base / gw_base lines  
**simulations.AHauberkSimulation**  
&nbsp;- renamed from HauberkSimulation with the A prefix; the target base URL became a constructor argument  
&nbsp;   instead of a read of the Esquire gw.base setting  
**simulations.SimulationInfo**  
&nbsp;- moved into Armoury, unchanged  
**cli.HauberkCli**  
&nbsp;- kc-reconcile dropped with the rest of the Esquire half, so this root carries the generic four;  
&nbsp;   version 2.0.0  
**cli.RunCommand**  
&nbsp;- reduced to the knobs that belong to any run (--metrics, --output, --config, --times,  
&nbsp;- -no-health-check) plus the general --set key=value; applyProperties() and setIfPresent() are  
&nbsp;   protected so a consumer adds its own knobs by extending the class  
**cli.SimulationCatalog**  
&nbsp;- the package to scan comes from the simulations.package setting  
**cli.ListCommand**  
&nbsp;- moved into Armoury, unchanged  
**cli.SummaryCommand**  
&nbsp;- moved into Armoury, unchanged  
**cli.DiffCommand**  
&nbsp;- moved into Armoury, unchanged  
**cli.CsvSnapshot**  
&nbsp;- moved into Armoury, unchanged  
**pom.xml**  
&nbsp;- artifactId armoury-hauberk, parent armoury-parent 2.0.0, finalName armoury-hauberk, shade main  
&nbsp;   class pro.mir0n.hauberk.cli.HauberkCli. The postgresql driver dropped -- it was there for  
&nbsp;   kc-reconcile, which stayed on the Esquire side. Compiles at 21 while the rest of Armoury is at the  
&nbsp;   parent's level  
**hauberk.properties**  
&nbsp;- replaced by a generic sample that points at nothing: it documents every setting the harness itself  
&nbsp;   reads, with the target-dependent ones commented out  
**hauberk.cmd**  
&nbsp;- the launcher runs target/armoury-hauberk.jar  
(not moved -- the Esquire half)  
&nbsp;- chain (37 operation classes), simulations (26), reconcile.KcRecover, cli.KcReconcileCommand,  
&nbsp;   config.EntityKinds, the Esquire half of config.HauberkConfig, and the target-specific scripts and  
&nbsp;   properties overlays all stay on the Esquire side, over this jar  

**10/11/2026** mir0n  v1.2.16 -- one operation per cell: the read scenarios, and the paired driver  
chain.ReadTreeRoot  (new)  
&nbsp;- Chain GET /esq-cmd-tree on the driver's own root office; used by Tree load  
chain.ReadTreeUser  (new)  
&nbsp;- Chain GET /esq-cmd-tree on one client user's subtree; used by TreeSmall load  
chain.ReadCacheRoot  (new)  
&nbsp;- Chain GET /esq-tree on the driver's own root, served from the bizTree cache; used by Cache load  
chain.ReadCacheUser  (new)  
&nbsp;- Chain GET /esq-tree on one customer's subtree from the bizTree cache; used by CacheSmall load  
chain.ReadAcctDetail  (new)  
&nbsp;- Chain GET /esq-cmd?kind=50&id=pickedAcctId for a single account; used by ReadAcct load  
**simulations.LoadScenarios**  
&nbsp;- five read scenarios added: TREE (ReadTreeRoot), TREE_SMALL (PoolFetchUsers +  
&nbsp;   PickRandomFromPool.userChain + ReadTreeUser), CACHE (ReadCacheRoot), CACHE_SMALL (PoolFetchUsers +  
&nbsp;   PickRandomFromPool.userChain + ReadCacheUser) and READ_ACCT (PoolFetchAccounts +  
&nbsp;   PickRandomFromPool.acctChain + ReadAcctDetail). The three pooled ones carry the same  
&nbsp;   exitHereIfFailed() between the setup and the .forever() loop  
**simulations.SuperLoadSimulation**  
&nbsp;- TREE, READ_ACCT, TREE_SMALL, CACHE and CACHE_SMALL join the parallel populations, each injected only  
&nbsp;   when its worker count is above zero; the nothing-to-run guard sums all ten. The javadoc and  
&nbsp;   @SimulationInfo no longer say five  
**config.HauberkConfig**  
&nbsp;- added SUPER_TREE_WORKERS, SUPER_READACCT_WORKERS, SUPER_TREESMALL_WORKERS, SUPER_CACHE_WORKERS and  
&nbsp;   SUPER_CACHESMALL_WORKERS (super.tree.workers, super.readacct.workers, super.treesmall.workers,  
&nbsp;   super.cache.workers, super.cachesmall.workers), each requireIntMin0  
**cli.RunCommand**  
&nbsp;- added --tree, --readacct, --treesmall, --cache and --cachesmall, each overriding its  
&nbsp;   super.*.workers property  
**hauberk.properties**  
&nbsp;- super.tree.workers, super.readacct.workers, super.treesmall.workers, super.cache.workers and  
&nbsp;   super.cachesmall.workers, all 0  
**perf-matrix.ps1**  
&nbsp;- -Kinds: ONE KIND PER CELL, named "-", driving that scenario alone at -KindVus with every  
&nbsp;   other scenario passed an explicit 0; the kinds of a config share one build, and the names are checked  
&nbsp;   against KIND_NAMES  
&nbsp;- -MetricsVus / -MetricsDuration: a LIGHT --metrics leg pass per kind after the plateau cycles, never  
&nbsp;   sharing a cycle with one, landing as "--legs"  
&nbsp;- -PrepDepth / -PrepClients / -PrepAccounts replace the fixed 5 / 5 / 2 passed to prepare-for-anything  
&nbsp;- -PoolMax sets BIZTREE_H2_POOL_MAX on the workload that holds the tree cache (gateward in compact,  
&nbsp;   biztree in classic); -ServiceMetrics sets GW_SERVICE_METRICS_ENABLED on the gate after each build  
&nbsp;- Save-Result takes the duration the row was measured over, so a leg row's rps is not divided by the  
&nbsp;   plateau duration  
&nbsp;- the resume check matches the exact cell names this invocation produces instead of the config prefix,  
&nbsp;   and the discard sweeps the capture rows with the plateau rows  
perf-pairs.ps1  (new)  
&nbsp;- per-operation pairs: for each kind, the classic cell then the compact cell back to back, each one a  
&nbsp;   separate perf-matrix.ps1 invocation, with a full helm drain between cells, a refusal to measure while  
&nbsp;   any esquire pod survives the drain, and an optional -RecycleVm restart of the Docker VM  

**09/07/2026** mir0n  v1.2.15 -- kc-reconcile reads every environment  
**KcRecover.java**  
&nbsp;- adminToken() takes a client_credentials token on the realm admin client instead of a  
&nbsp;   master-realm password grant  
**HauberkConfig.java**  
&nbsp;- added KC_ADMIN_CLIENT_ID (kc.admin.client.id, default esq-kcMaster) and KC_ADMIN_SECRET, the  
&nbsp;   latter read from the environment only (KC_ADMIN_SECRET / KCMASTER_ADMIN_SECRET)  
**hauberk-oke.properties**  
&nbsp;- pg.url added: the OKE database over a port-forward on 35432  
**hauberk-aws.properties**  
&nbsp;- pg.url to the in-pod postgres over a port-forward on 45432; cluster and statefulset in cmd.*  
&nbsp;   moved to esquire-aws-compact and esquire-gateward  

**09/06/2026** mir0n  v1.2.15 -- kc-reconcile reads the stack database  
**hauberk.properties**  
&nbsp;- pg.url port 5432 -> 5433, the port the compose stack publishes its own postgres on  
**hauberk-compact.properties**  
&nbsp;- pg.url port 5432 -> 5433, the port the compose stack publishes its own postgres on  

**09/01/2026** mir0n  v1.2.14 -- the AWS run profile  
hauberk-aws.properties  (new)  
&nbsp;- the AWS overlay: it inherits hauberk.properties and replaces only the endpoints and the  
&nbsp;   infra-orchestration commands. One public host serves all three paths on AWS, so KC, the BFF and the  
&nbsp;   gateway differ by path rather than by hostname  

**08/26/2026** mir0n  v1.2.13 -- the transfer body, and the test-data marker removed  
**Transfer.java**  
&nbsp;- the transfer body sends the amount NEGATED, a rate of 1, and refCode "other"  
**EntityKinds.java**  
&nbsp;- TEST_EMAIL_DOMAIN removed  
**CreateUser.java**  
&nbsp;- userEmail builds the domain inline  
**CreateAccount.java**  
&nbsp;- the account body no longer carries desc  

**08/20/2026** mir0n  v1.2.13 -- the composed services on the cloud  
**perf-matrix.ps1**  
&nbsp;- six k8sc-* configs for the compact stack (x1/x2 x OFF/LOG/FULL), each carrying dir=k8s-compact; APPS_COMPACT  
&nbsp;   is gateward/mesnie/pacman/aukeep/backend, and Wl() returns the workload name per profile -- esquire-  
&nbsp;   on compact, esquire-- on classic  
&nbsp;**- Build-K8s takes its stack folder and its fleet from the config instead of the fixed services\k8s**  
&nbsp;- Wait-Url reads the status: 2xx/3xx passes, and 4xx passes only with -GuardedOk; the gateway gate uses it  
&nbsp;   and compact gates on /esq-enode, classic on /actuator/health  
**oke-perf-matrix.ps1**  
&nbsp;- Set-OkeConfig exports ESQ_REPLICAS before the arm, and Restore-Default's helm upgrade carries  
&nbsp;- -force-conflicts  

**08/15/2026** mir0n  v1.2.13 -- gateWard  
**chain.EnsureOffice**  
&nbsp;- the note on the accepted statuses states that an absent office is a 404; 400 stays accepted  
**chain.LookupOfficeIdByName**  
&nbsp;- the note on the accepted statuses states that an absent office is a 404; 400 stays accepted  
**chain.LookupAccessProfile**  
&nbsp;- the class note states that "no access profile yet" is a 404; 400 stays accepted  
**simulations.LoadScenarios**  
&nbsp;- the in-loop transient-failure example no longer names a status code  

**07/19/2026** mir0n  v1.2.11 -- T12: the OKE performance matrix  
oke-perf-matrix.ps1  (new)  
&nbsp;- the OKE twin of perf-matrix.ps1: drives the 200-VU super-load across x1/x2 x OFF/LOG/FULL on the LIVE OKE  
&nbsp;   cluster, same CSV schema (perf-matrix-report.py reads it). TOGGLE IN PLACE, not from scratch -- OKE is live  
&nbsp;   and ALTER-migrated, no PVC wipe: each cell scales the replicas + calls oke-o11y-on , with clean-house  
&nbsp;   between cells. The broker is NEVER rolled (SKIP_INFRA_ROLL -- a broker bounce drops the app's messagingBus,  
&nbsp;   which does not self-heal); Wait-OkePods kicks any straggler. ONE run per config (no from-scratch, so a second  
&nbsp;   run is not an independent replicate -- it only deepens the run-order drift), MaxLoads 12 (OKE warms far  
&nbsp;   slower over the ~55ms RTT). Starts and ends at the OKE default (ERROR logging, no stack, x2)  
oke-perf-matrix.bat  (new)  
&nbsp;- entry point for the OKE matrix: launches oke-perf-matrix.ps1 detached so a multi-hour run survives the  
&nbsp;   console (resumable); --fresh starts a new CSV, --fg runs in the current console; refuses docker-desktop  

**07/17/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
**perf-matrix.ps1**  
&nbsp;- the o11y arms go from OFF/ON to three modes OFF / LOG / FULL so logging is isolated from tracing+metrics;  
&nbsp;   the knob is levelMir0n (not levelRoot, which cannot silence the app -- pro.mir0n reaches the console by  
&nbsp;   additivity); -Scale scales the VU count, -Only runs a subset; docker is dropped from the o11y measurement  
&nbsp;   (uncapped -> its sag biases the delta) and kept only as a smoke test; required-infra-only removes kafka and  
&nbsp;   (at x1) redis where nothing under test uses them (I49)  

**07/15/2026** mir0n  v1.2.11 -- T11 cleanup: fix/resolve issues noted during development  
hauberk-S.properties  (new)  
&nbsp;- the VANILLA token-relay overlay: tokenRelay.type=vanilla + the esq-hauberk-S client (HTTP Basic at the edge,  
&nbsp;   the gateway runs client_credentials on its behalf) (I8)  
hauberk-M.properties  (new)  
&nbsp;- the PHANTOM token-relay overlay: tokenRelay.type=phantom + the esq-hauberk-M client (a Bearer the gateway  
&nbsp;   exchanges via RFC 8693) (I8)  

**07/14/2026** mir0n  v1.2.11 -- the performance matrix; a VU whose setup failed no longer spins; the user pool excludes the create loop's office  
perf-matrix.bat  (new)  
&nbsp;- entry point for the performance matrix: 6 configurations (docker / k8s x1 / k8s x2, each o11y OFF and ON) x 2  
&nbsp;   runs x 4+ loads. Launches perf-matrix.ps1 detached so a multi-hour matrix survives the console; --fresh starts  
&nbsp;   a new CSV, --fg runs in the current console  
perf-matrix.ps1  (new)  
&nbsp;- the matrix engine. Every run is built FROM SCRATCH -- the stack is torn down and rebuilt and the Postgres,  
&nbsp;   KeyCloak and broker data dropped (k8s-down.bat leaves the PVCs and `docker compose down` keeps the volume, so  
&nbsp;   both are deleted explicitly). Loads run until the throughput STOPS MOVING (4 minimum, extended while the last  
&nbsp;   load is still climbing >3%, capped at 6). o11y OFF also UNINSTALLS the viewing stack. Only one stack is ever  
&nbsp;   up: before the docker half it uninstalls every esquire helm release and ABORTS if any pod survives. Readiness  
&nbsp;   is gated on the DB being seeded + the realm imported + the gateway UP, and a failed playground prepare aborts  
&nbsp;   the run. Results are appended to matrix.csv after every load and a completed run is skipped on rerun  
perf-matrix-report.py  (new)  
&nbsp;- reads matrix.csv and prints the three answers: does super-load pass (KO rate per config), what is the noise  
&nbsp;   floor (the gap between a config's own two from-scratch runs), and what each comparison costs. Steady state is  
&nbsp;   the PLATEAU -- the trailing loads within 3% of each other; load 1 (warm-up) and any load still climbing or  
&nbsp;   sagging are excluded. Every comparison is printed next to the noise it must beat, plus a separation check  
**simulations.LoadScenarios**  
&nbsp;- exitHereIfFailed() between the setup and the .forever() loop in all five scenarios -- a VU whose setup failed  
&nbsp;   used to walk into the loop with no session attributes, so its requests were never SENT and the loop spun at  
&nbsp;   full CPU issuing nothing  
**chain.PoolFetchUsers**  
&nbsp;- userPool EXCLUDES users parented by the deepest office -- the CREATE scenario's churn ground. It fetches the  
&nbsp;   subtree, finds the deepest ORG by the same rule LoadScenarios.CREATE uses, and drops the users whose parentId  
&nbsp;   is that office; an empty pool marks the VU failed  
**hauberk-k8s.properties**  
&nbsp;- kc.base and gw.base both moved onto the ingress (esquire.localhost / api.esquire.localhost); no port-forward.  
&nbsp;   gw.base was localhost:8808, a single-threaded userspace proxy that capped every k8s load run; kc.base was  
&nbsp;   localhost:8081 to dodge a JDK-HttpClient/nginx hang that KcTokenClient already fixes by pinning HTTP_1_1, and  
&nbsp;   the forward died on every KeyCloak rollout  

**07/03/2026** mir0n  v1.2.10 -- KCEsquire reconcile utility; write-time X-Request-ID companion  
cli.KcReconcileCommand  (new)  
&nbsp;- picocli "kc-reconcile" subcommand -- detect (and optionally --repair) drift between esq2025 and KeyCloak, PG(JDBC) + KC(REST) directly, out of the Esquire services  
reconcile.KcRecover  (new)  
&nbsp;- KCEsquire data-recover core: reads connected-user state from esq2025 over PG/JDBC + KeyCloak over REST admin; diffs and (repair mode) fixes a stale KC esq_rootpath in place  
**cli.HauberkCli**  
&nbsp;- registered the kc-reconcile subcommand  
**config.HauberkConfig**  
&nbsp;- added PG_URL / PG_USER / PG_PASSWORD (pg.url / pg.user / pg.password) for the kc-reconcile utility's direct JDBC read of esq2025  
**simulations.HauberkSimulation**  
&nbsp;- every outbound request carries a fresh X-Request-ID (UUID) so hauberk writes satisfy the services' write-time X-Request-ID guard  
**pom.xml**  
&nbsp;- added org.postgresql:postgresql (JDBC driver for kc-reconcile)  
**hauberk.properties**  
&nbsp;- pg.url / pg.user / pg.password (kc-reconcile, docker: localhost:5432/esq2025)  
**hauberk-k8s.properties**  
&nbsp;- pg.url (kc-reconcile, local k8s: port-forward localhost:25432)  

**06/29/2026** mir0n  v1.2.10 -- R6 query-timeout HA smoke  
simulations.HaTimeoutSmokeSimulation  (new)  
&nbsp;- hits enyMan's flag-gated /test slow-query hook directly (not via the gateway): a capped slow query  
&nbsp;   (/test/slow-query) is cancelled at the cap (timedOut=true); an opt-out slow query  
&nbsp;   (/test/slow-query-optout, the move / cache-load mechanism) completes (timedOut=false)  
**config.HauberkConfig**  
&nbsp;- added ENYMAN_BASE (enyman.base, required) -- enyMan reached directly for the R6 timeout smoke  
**hauberk.properties**  
&nbsp;- added enyman.base=http://localhost:3003 (enyMan's own port; compose exposes 3003, local k8s port-forward)  

**06/10/2026** mir0n  v1.2.7 -- SimulationInfo descriptions trimmed to the hauberk list 90-char cap  
**simulations.MessageLossSimulation**  
&nbsp;- @SimulationInfo trimmed 112 -> 86 chars (SimulationCatalogContractTest.descriptionFitsTerminalWidth)  
**simulations.MessageLossTerminateSimulation**  
&nbsp;- @SimulationInfo trimmed 114 -> 85 chars  
**simulations.ResidueCleanupSimulation**  
&nbsp;- @SimulationInfo trimmed 105 -> 74 chars (dropped the -Dcleanup.prefix default-value note)  

**06/03/2026** mir0n  v1.2.7 -- PerformanceMatrix AIOOBE fix in the post-run summary  
**perf.PerformanceMatrix**  
&nbsp;- printSummary() made synchronized (same monitor as add()) so a straggler response cannot grow a  
&nbsp;   request's row list mid-summary; column() snapshots rows.size() once so the loop bound never exceeds  
&nbsp;   the allocated array (was AIOOBE at high sample counts, aborting the run summary / CSV / HTML report)  

**06/02/2026** mir0n  v1.2.6 race-8c -- KC esq_rootpath verification + single-shot repro + KC orphan cleanup  
chain.KcAdminAuth  (new)  
&nbsp;- POST master-realm token endpoint (admin-cli, grant_type=password, kc.admin.user / kc.admin.password);  
&nbsp;   saves access_token as session "kcAdminToken" for subsequent KC admin REST calls  
chain.CleanupKcOrphans  (new)  
&nbsp;- GET esquire-realm users (search=hauberk-) then DELETE each via admin REST (accepts 200 / 204 / 404);  
&nbsp;   reports kcOrphansDeleted  
simulations.RaceMoveCreateKcSimulation  (new)  
&nbsp;- race-8c verify: move-oscillate + connected USR creates; verifier diffs DB ep_path (/esq-cmd-tree) against  
&nbsp;   KC esq_rootpath (admin REST). Poll-until-stable with per-tick KcAdminAuth refresh (60s token) and a  
&nbsp;   KC-count stability gate (3 equal ticks) so a zero-mismatch reading is only taken once kcMaster has drained  
simulations.RaceMoveCreateKcSingleShotSimulation  (new)  
&nbsp;- deterministic single-shot race-8c: one disabled user + exactly ONE move during the keySmith hold, nothing  
&nbsp;   heals it; buffer OFF (ttl=-1) -> FAIL (reproduce), buffer ON (10000) -> PASS (fix proven)  
simulations.KcCleanupSimulation  (new)  
&nbsp;- standalone teardown: KcAdminAuth + CleanupKcOrphans -- DELETE hauberk-* KC users left behind after CleanHouse  
**chain.MoveEntity**  
&nbsp;- accept HTTP 202 in addition to 200 (v1.2.6 Goal 3 made /esq-move async-ack, returns 202 at submit)  
**config.HauberkConfig**  
&nbsp;- KC_ADMIN_USER / KC_ADMIN_PASSWORD added (kc.admin.user / kc.admin.password, default admin/q)  
**simulations.RaceMoveCreateSimulation**  
&nbsp;- bounded load (during(dur) replaces forever()) + 200ms / 100ms pacing; poll-until-quiescent verifier  
&nbsp;   (5s tick, 5-min budget) replaces the single post-load snapshot; int counters + snapshot maps replace  
&nbsp;   the List collectors  
**hauberk.properties**  
&nbsp;- kc.admin.user / kc.admin.password (default admin/q) for the KC admin REST sims  
**simulations.ResidueCleanupSimulation**  
&nbsp;- name-prefix driven: lists offices directly under Test House and runs each whose name starts with  
&nbsp;- Dcleanup.prefix (default "hauberk-office-smoke") through CleanupOfficeByName (disconnect connected  
&nbsp;   USRs, then delete the subtree bottom-up via the FK /esq-cmd-tree). Catches msgloss / other-named  
&nbsp;   leftovers the old exact-match missed; deletes via the API so it works on prod (direct DB deletes blocked)  

**05/23/2026** mir0n  v1.2.5 Taijitu night-watch -- message-loss sims + infra-command framework  
chain.Cmd  (new)  
&nbsp;- sim step that runs a configured infra command (cmd.) through the OS shell (cmd /c on  
&nbsp;   Windows, sh -c elsewhere); {KEY} placeholder substitution from a passed env map; blocks until exit  
chain.ForceSweep  (new)  
&nbsp;- POST /esq-sweep through the gateway (authenticated) to force a night-watch sweep; checks 202  
chain.WaitCacheReady  (new)  
&nbsp;- poll GET /esq-tree until bizTree answers 200 (cache loaded + serving), after a restart/recreate  
chain.WaitBizTreeDown  (new)  
&nbsp;- poll GET /esq-tree until 5xx -- confirms OVER REST that the TERMINATE reaction took bizTree down  
simulations.MessageLossSimulation  (new)  
&nbsp;- night-watch SWAP scenario: recreate biztree (SWAP), stop AMQ, create a user (CREATE broadcast  
&nbsp;   lost), prove it MISSING, wait one sweep, prove the SWAP promoted the fresh shadow (user present);  
&nbsp;   fully automatic via Cmd  
simulations.MessageLossTerminateSimulation  (new)  
&nbsp;- night-watch TERMINATE scenario: recreate biztree (TERMINATE), stop AMQ, create a user, prove  
&nbsp;   drift, force a sweep -> mismatch System.exit, assert bizTree DOWN (WaitBizTreeDown); restores the stack  
**config.HauberkConfig**  
&nbsp;- added the cmd.* infra-command map (COMMANDS) + static command(key) accessor (cmd. property,  
&nbsp;- Dcmd. override)  
**simulations.RaceCacheLoadSimulation**  
&nbsp;- automated the operator restart: a race-restart population fires Cmd.run("restart-biztree") ~15s in  
&nbsp;   (was a manual banner); verify population waits via WaitCacheReady; maxDuration margin raised  
**hauberk.properties**  
&nbsp;- added cmd.* defaults (stop-amq / start-amq / restart-biztree / recreate-biztree -- docker)  
**hauberk-k8s.properties**  
&nbsp;- cmd.* overrides for local k8s (kubectl scale/wait AMQ; kubectl set env + rollout restart biztree)  
**hauberk-oke.properties**  
&nbsp;- cmd.* overrides for OKE prod (same kubectl steps, -n default); enables the message-loss sims  
&nbsp;   against the live cluster (run with the OKE kubectl context active)  

**05/17/2026** created: Gatling-based stress / load / smoke / race-repro harness  
auth.KcTokenClient  
&nbsp;- plain-Java client_credentials grant against KC /token  
**auth.RefreshableToken**  
&nbsp;- thread-safe access-token holder; lazy first fetch; auto-refresh at TTL-30s  
**config.HauberkConfig**  
&nbsp;- typed config (endpoints, KC client+secret, playground id, prepare/move/super knobs, metrics)  
&nbsp;    loaded from hauberk.properties + optional overlay via --config  
&nbsp;- added tokenRelay.type (plain | vanilla | phantom) + isBasicAuth() + basicAuthHeader()  
&nbsp;    so the harness can present HTTP Basic at the edge for Vanilla Token Relay runs; overlay javadoc decoupled from JWS+ naming  
**config.EntityKinds**  
&nbsp;- kind-code constants (ORG=20, USR_CLIENT=34, ACCT_CLIENT=50, TX_DEPOSIT/WITHDRAW/TRANSFER, ...)  
**health.HealthPreCheck**  
&nbsp;- KC well-known + gateway health probes run before sim start; aborts on failure  
&nbsp;- auth-path probe branches on HauberkConfig.AUTH_MODE  
&nbsp;    presents Basic for Vanilla Token Relay, Bearer otherwise;  
**perf.PerformanceMatrix**  
&nbsp;- per-response transformResponse hook capturing the four observability headers  
&nbsp;   (X-Response-Time / Esq-Gw-Inner-Time / Esq-Srv-Outer-Time / Esq-Srv-Inner-Time);  
**cli.HauberkCli**  
&nbsp;- picocli @Command root entry point; dispatches to subcommands  
**cli.RunCommand**  
&nbsp;- programmatic Gatling launch with --metrics, --output, --config, --times, shape knobs  
**cli.ListCommand**  
&nbsp;- catalogue of discovered Simulations with @SimulationInfo descriptions  
**cli.SummaryCommand**  
&nbsp;- per-URL percentile summary from a saved perf-matrix CSV  
**cli.DiffCommand**  
&nbsp;- side-by-side per-URL diff of two perf-matrix CSV snapshots  
**cli.CsvSnapshot**  
&nbsp;- in-memory snapshot of a perf-matrix CSV (YAML preamble + row arrays)  
**cli.SimulationCatalog**  
&nbsp;- classpath scanner for concrete Simulation classes; resolves short / kebab / FQCN names  
**chain.***  
&nbsp;- 26 atomic ChainBuilder building blocks:  
**simulations.SimulationInfo**  
&nbsp;- SimulationInfo: class-level annotation carrying the one-line catalog description (read reflectively by ListCommand)  
**simulations.HauberkSimulation**  
&nbsp;- abstract base for every hauberk Simulation -- pulls up lazy RefreshableToken,  
&nbsp;   instrumented httpProtocol with PerformanceMatrix, and after() perf-matrix flush  
&nbsp;- Authorization header branches on HauberkConfig.AUTH_MODE --  
&nbsp;   Basic for Vanilla Token Relay, Bearer (KC JWT) otherwise  
**simulations.LoadScenarios**  
&nbsp;- shared scenario library (READ/UPDATE/CREATE/MOVE/TX) used by SuperLoad and the five standalone *LoadSimulation variants  
**simulations.SmokeSimulation**  
&nbsp;- single-VU wiring check (KC token + one GET /esq-kinds)  
**simulations.EntitySmokeSimulation**  
&nbsp;- end-to-end entity walk -- every CRUD Chain exercised sequentially by a single VU  
**simulations.CleanHouseSimulation**  
&nbsp;- stateless name-driven teardown -- find hauberk-office-smoke + delete its subtree  
**simulations.CompareTreesSimulation**  
&nbsp;- standalone CompareTrees runner across the playground subtree  
**simulations.PrepareForAnythingSimulation**  
&nbsp;- load-test playground builder (D nested offices x N users x M accounts)  
**simulations.MoveSmokeSimulation**  
&nbsp;- /esq-move smoke for USR + ORG re-parenting on a D-deep chain  
**simulations.KcIntegrationSmokeSimulation**  
&nbsp;- access-profile end-to-end via enyMan + keySmith + kcMaster + KC + BFF login  
**simulations.CreateLoadSimulation**  
&nbsp;- standalone variants of the create load scenarios  
**simulations.ReadLoadSimulation**  
&nbsp;- standalone variants of the read load scenarios  
**simulations.UpdateLoadSimulation**  
&nbsp;- standalone variants of the update load scenarios  
**simulations.MoveLoadSimulation**  
&nbsp;- standalone variants of the move load scenarios  
**simulations.TxLoadSimulation**  
&nbsp;- standalone variants of the deposit-withdrawal load scenarios  
simulations.SuperLoadSimulation:  
&nbsp;- all 5 LoadScenarios in parallel for super.duration.seconds  
**simulations.RaceCacheLoadSimulation**  
&nbsp;- Race repro (cache-load race); self-validating PASS/FAIL  
**simulations.RaceMoveCreateSimulation**  
&nbsp;- Race repro (move + concurrent create path race); self-validating PASS/FAIL  
**simulations.ResidueCleanupSimulation**  
&nbsp;- targeted residue purge for hauberk-office-smoke leftovers  

### taijitu/src/main/java/pro/mir0n/taijitu/changes.txt

armoury taijitu changes -- pro.mir0n.taijitu  

**10/14/2026** mir0n  v2.0.0 -- taijitu is a project of its own  
(project)  
&nbsp;- moved from armoury/utils to armoury/taijitu: artifactId armoury-taijitu, parent armoury-parent,  
&nbsp;   version 2.0.0; it depends on armoury-utils. The package pro.mir0n.utils.taijitu -> pro.mir0n.taijitu;  
&nbsp;   the twelve classes are otherwise unchanged  
(earlier history)  
&nbsp;- up to v2.0.0 taijitu was part of the utils project. Its history, back to v1.2.5, is in the utils  
&nbsp;   changes.txt, in the entries named taijitu.*:  
&nbsp;   utils/src/main/java/pro/mir0n/utils/changes.txt  

### utils/src/main/java/pro/mir0n/o11y/changes.txt

armoury o11y changes -- pro.mir0n.o11y  

**10/13/2026** mir0n  v2.0.0 -- the three Micrometer facades lifted out of the Esquire common library  
BizMeters  (new here)  
&nbsp;- was EsqBizMeters in the Esquire common library. The code is unchanged; the javadoc no longer argues from  
&nbsp;   one product's call sites. Meter NAMES are the caller's, so no dashboard moves with it  
TraceMark  (new here)  
&nbsp;- was EsqTraceMark. The code is unchanged; it only ever imported Micrometer and the JDK, which is what let  
&nbsp;   it move below both products that use it  
Gauges  (new here)  
&nbsp;- was EsqGauge. The code is unchanged; the name no longer says Esquire, because nothing here does. The  
&nbsp;   build-time guard that keeps Gauge.builder unreachable elsewhere lives with each consumer  
(where it ships)  
&nbsp;- inside the utils project, as armoury-utils, which declares micrometer-core and micrometer-observation  
&nbsp;   at provided scope (pinned at 1.15.7, the version Spring Boot 3.5.9 manages). The package is  
&nbsp;   pro.mir0n.o11y, NOT pro.mir0n.utils.o11y: it keeps its own root so that giving these primitives a jar  
&nbsp;   of their own later is a pom and nothing else -- no rename, no consumer touched. mir0n, 2026-10-13  
(why it exists)  
&nbsp;- the keep writer in the messaging line called EsqBizMeters and EsqTraceMark, which sat in the Esquire  
&nbsp;   domain library. With the keep moving into Herald that was a cycle -- herald -> esquire.services ->  
&nbsp;   herald -- and it also put the Esquire domain on the classpath of a lab whose whole premise is loading  
&nbsp;   the messaging layer without it. Both facades import Micrometer and the JDK and nothing else, so they  
&nbsp;   move below both products and the edge disappears  

### utils/src/main/java/pro/mir0n/utils/changes.txt

mir0n java common frameworks -- pro.mir0n.utils  

**10/14/2026** mir0n  v2.0.0 -- taijitu moved out to a project of its own  
**taijitu.***  
&nbsp;- moved to armoury/taijitu as armoury-taijitu; package pro.mir0n.utils.taijitu -> pro.mir0n.taijitu.  
&nbsp;   The taijitu entries below, back to v1.2.5, stay here; armoury-taijitu keeps its own changes.txt  

**10/13/2026** mir0n  v2.0.0 -- pro.mir0n.o11y joins this project  
(project)  
&nbsp;- the three Micrometer facades (BizMeters, TraceMark, Gauges) ship in armoury-utils, under their own  
&nbsp;   package root pro.mir0n.o11y and with their own changes.txt beside them. The pom gains micrometer-core  
&nbsp;   and micrometer-observation at provided scope; nothing under pro.mir0n.utils references either  

**10/13/2026** mir0n  v2.0.0 -- the project moved into Armoury  
(project)  
&nbsp;- moved from services/mir0n-utils to armoury/utils: artifactId mir0n-utils -> armoury-utils, parent  
&nbsp;   esquire-parent -> armoury-parent, version 2.0.0. The package root pro.mir0n.utils is unchanged, so  
&nbsp;   not one source file changed. The pom still declares slf4j-api at provided scope, with the version  
&nbsp;   pinned in armoury-parent now that there is no Spring Boot BOM over it  

**10/11/2026** mir0n  v1.2.16 -- a cache read that does not take the value away  
**concurrent.ExpiringCache**  
&nbsp;- peek(K): reads the value WITHOUT taking it away, with the same age check consume() applies.  
&nbsp;   consume() is hand-off semantics; a cache reads one entry many times over its life, and through  
&nbsp;   consume() every read after the first would be a miss  

**08/26/2026** mir0n  v1.2.13 -- the queue-rig outcome seam, and the night-watch reaction  
**concurrent.IQueueRig**  
&nbsp;- outcome seam made symmetric: ISuccessListener + setSuccessListener; single-item only (a bulk worker  
&nbsp;   returns what it did not handle). NOOP on both listener contracts  
**concurrent.BoundedQueueRig**  
&nbsp;- the outcome seam made symmetric: an ISuccessListener fired per handled item (setSuccessListener, NOOP  
&nbsp;   default); setErrorListener(null) restores the LOGGING default, not NOOP  
**taijitu.AMonadY**  
&nbsp;- implements IQueueRig.IListErrorListener and registers itself on the rig; inFlightCommand carries the  
&nbsp;   command the worker is running so onError notifies its gate. RESULT_TIMEDOUT / RESULT_INTERRUPTED  
&nbsp;   replace the two literals resultCommand writes  
**taijitu.ATaijituRig**  
&nbsp;- the sweep splits a non-digest three ways: TIMEDOUT promotes the freshly loaded shadow, FAILED and  
&nbsp;   INTERRUPTED report and leave both legs, and shuttingDown (set by shutdown()) stops any promotion once  
&nbsp;   the process is going down. notADigest / unusable replace checksumFailed  

**08/11/2026** mir0n  v1.2.12 -- entity change number  
concurrent.ExpiringCache  (new)  
&nbsp;- generic hand-off cache whose entries expire by age -- a ConcurrentHashMap with a timestamp per entry,  
&nbsp;   one daemon prune thread and a lazy age check on read, so an un-started cache is still correct.  
&nbsp;   store() parks a value, consume() takes it away, storeIfGreater() parks only when the arrival compares  
&nbsp;   greater than what is there, in one atomic merge. The value type is Comparable, so the cache needs no  
&nbsp;   comparator and never learns what its callers order by  
**taijitu.ITaijituRig**  
&nbsp;- onEntityBroadcast gains a changeNo parameter, with an unnumbered default overload for producers that  
&nbsp;   report none  
**taijitu.ATaijituRig**  
&nbsp;- onEntityBroadcast takes the change number and puts it on the QueueItem  
**taijitu.ATaijituRigY**  
&nbsp;- onEntityBroadcast takes the change number and puts it on the QueueItem  
**taijitu.QueueItem**  
&nbsp;- changeNo component added, plus a traced-but-unnumbered constructor; the no-trace constructor passes  
&nbsp;   null for both  

**07/14/2026** mir0n  v1.2.11 -- BoundedQueueRig: the producer's wait on a FULL queue is its own knob, and can be endless (T10)  
**concurrent.BoundedQueueRig**  
&nbsp;- putAwaitMs + setPutAwaitMs(long) added -- the producer's own wait on a FULL queue, split off from  
&nbsp;   awaitTimeoutMs (which now only paces the worker's missed-signal re-check). <= 0 = NO TIMEOUT: put() awaits  
&nbsp;   notFull without a deadline and never discards; shutdown() / clear() already signal notFull, so a parked  
&nbsp;   producer still wakes. Default unchanged (10s, drops)  

**07/09/2026** mir0n  v1.2.11 -- pro.mir0n.utils becomes its own module (mir0n-utils); HostId  
HostId  (new)  
&nbsp;- this instance's host identity -- instanceHost() + the lazy-cached instanceNo(), moved down from esquire  
&nbsp;   common.EsqUtils (which now delegates) so the messaging bus can build its default rod-id without depending  
&nbsp;   on anything Esquire  
**concurrent.IQueueRig**  
&nbsp;- moved from the esquire-common module to mir0n-utils; package pro.mir0n.utils.concurrent unchanged  
**concurrent.BoundedQueueRig**  
&nbsp;- moved from the esquire-common module to mir0n-utils; package pro.mir0n.utils.concurrent unchanged  
**concurrent.WorkerPool**  
&nbsp;- moved from the esquire-common module to mir0n-utils; package pro.mir0n.utils.concurrent unchanged  
**taijitu.***  
&nbsp;- moved from the esquire-common module to mir0n-utils; package pro.mir0n.utils.taijitu unchanged  

**07/01/2026** mir0n  v1.2.10 -- a common bounded worker pool with three thread models (platform / virtual / virtual-per-task)  
concurrent.WorkerPool  (new)  
&nbsp;- a bounded worker pool with three thread models: PLATFORM / VIRTUAL = a FIXED pool of `size` reused workers  
&nbsp;   (OS threads, or `size` virtual threads); VIRTUAL_PER_TASK = one virtual thread per task, capped by a  
&nbsp;   Semaphore(size) (uncapped when size=0). create(name, size, mode); submit(Runnable) applies the bound  
&nbsp;   (acquire / execute / release); shutdown(awaitSeconds) drains; capacity(); Mode.of(String) parses the mode  

**06/15/2026** mir0n  v1.2.8 -- Taijitu queue item carries a parsed body map instead of the raw wire body  
**taijitu.QueueItem**  
&nbsp;- the record field pair (messageEncoding, text) replaced by a single body Map; the  
&nbsp;   constructor is now (eventType, entityId, entityKind, requestId, correlationId, body). command items  
&nbsp;   pass body=null; event items carry the parsed map, applied by the monad with no re-parse  
**taijitu.ITaijituRig**  
&nbsp;- pass(...) contract changed: the (messageEncoding, text) params replaced by Map body  
&nbsp;   (null = bodiless event, e.g. DELETE); import java.util.Map added  
**taijitu.ATaijituRig**  
&nbsp;- pass(...) signature updated to the body-map form; forwards body into the body-map QueueItem  
**taijitu.ATaijituRigY**  
&nbsp;- pass(...) signature updated to the body-map form; forwards body into the body-map QueueItem  
**taijitu.AMonadY**  
&nbsp;- CMD QueueItem construction updated to the body-map ctor (raw messageEncoding+text args dropped;  
&nbsp;   command items pass body=null)  

**06/02/2026** mir0n  v1.2.6 -- bulk processing on the queue rig + monad  
**concurrent.IQueueRig**  
&nbsp;- IQueueListWorker (extends IQueueWorker): List process(ArrayList, ISignaler) returning the unprocessed remainder  
&nbsp;- ISignaler: isRunning / isProcessing / shouldContinue (read-only run/process view)  
&nbsp;- IListErrorListener (extends IErrorListener): List onError(Throwable, ArrayList) -- null = stop the bulk  
&nbsp;- put(Collection) and setBulkThreshold(int) default methods added  
**concurrent.BoundedQueueRig**  
&nbsp;- bulk drain: when the worker is an IQueueListWorker and backlog > bulkThreshold (default 10), the whole deque  
&nbsp;   is snapshot into one ArrayList and passed to process(ArrayList, signaler); the returned remainder is re-queued  
&nbsp;   to the front; a thrown bulk is routed to the IListErrorListener (null/empty = stop, else re-run the continuation)  
&nbsp;- run / process state read through an ISignaler (processing made volatile); setBulkThreshold(int);  
&nbsp;   LoggingErrorListener now implements IListErrorListener  
**taijitu.AMonadY**  
&nbsp;- inner MonadWorker (IQueueListWorker) replaces the method-ref worker; processBatch accumulates consecutive  
&nbsp;   events and flushes _processItems(List) before any command (arrival order preserved), capped at eventBatchMax  
&nbsp;- _processItems(List) default loops _processItem; setEventBatchMax / setBulkThreshold added  

**06/02/2026** mir0n  v1.2.6 Goal 3 -- non-blocking tryPut on the queue rig  
**concurrent.IQueueRig**  
&nbsp;- tryPut(E) default method enabled (was commented out); delegates to put(E) and returns true  
**concurrent.BoundedQueueRig**  
&nbsp;- tryPut(E) override: offers under the lock; returns false when stopped or at capacity (size >= capacity),  
&nbsp;   signals the worker on success -- no wait, no silent drop  

**05/23/2026** mir0n  v1.2.5 Taijitu night-watch -- periodic sweep, mismatch reactions, readiness gate  
taijitu.MismatchAction  (new)  
&nbsp;- enum LOG | SWAP | TERMINATE: the night-watch reaction when the two monads' checksums disagree  
**taijitu.ATaijituRig**  
&nbsp;- night-watch scheduler: a single daemon ScheduledExecutorService (nightWatchExec) re-arms each  
&nbsp;   sweep sweepIntervalMs AFTER the previous one ends; a sweeping AtomicBoolean allows one at a time  
&nbsp;- sweep(): load the shadow fresh, post CHECKSUM to both legs, collect each within sweepTimeoutMs  
&nbsp;   (resultCommand), screen FAILED via checksumFailed() (inconclusive -> abandon), compare digests,  
&nbsp;   react per onMismatch (LOG keep serving / SWAP swapYinYang / TERMINATE System.exit), finally  
&nbsp;   clearMonad(yin()) back to idle  
&nbsp;- sweepAsync(): dispatch sweepGuarded onto nightWatchExec and return at once (REST force-sweep);  
&nbsp;   sweepGuarded() swallows any throw so a fault cannot cancel the periodic schedule  
&nbsp;- configurable cadence/policy: sweepIntervalMs (10s), sweepTimeoutMs (10s), onMismatch (LOG) +  
&nbsp;   setSweepIntervalMs / setSweepTimeoutMs / setOnMismatch  
&nbsp;- checksumFailed(String): true for null or FAILED -- the only inconclusive outcome to screen out  
**taijitu.AMonadY**  
&nbsp;- submit(commandId) -> submitCommand(commandId, boolean enableQueue): clears the gate, posts the  
&nbsp;   command, opens the accept-gate when enableQueue  
&nbsp;- doCommand split: doCommand = submitCommand + resultCommand; new resultCommand(timeoutMs) blocks  
&nbsp;   on the gate, cancels the registered cancelable + grace-waits on a positive timeout  
&nbsp;- removed dead NOOP_CANCEL field; unknown-command log demoted devLog.warn -> devLog.debug  
**taijitu.IMonad**  
&nbsp;- added submitCommand(String commandId, boolean enableQueue) and resultCommand(long timeoutMs)  
**taijitu.ITaijituRig**  
&nbsp;- added isReady() (readiness gate: true once loaded + serving) and the sweepAsync() default no-op  
**taijitu.ATaijituRigY**  
&nbsp;- added isReady(): true once the serving monad is LOADED  
**taijitu.MonadStatus**  
&nbsp;- dropped the String code field/constructor/code() accessor -- plain enum (IDLE/LOADING/LOADED/FAILED)  
**taijitu.AMonad**  
&nbsp;- removed unused Logger/LoggerFactory imports; javadoc: the timed-out-checksum cancel seam is wired  
**taijitu.MonadCmd**  
&nbsp;- javadoc: submit -> submitCommand; CHECKSUM is the off-queue order-independent hash  
**concurrent.IQueueRig**  
&nbsp;- dropped redundant public modifiers from the interface members  

**05/22/2026** mir0n  v1.2.5 Taijitu dark side -- two-monad director + off-queue CHECKSUM  
taijitu.AMonad  (new)  
&nbsp;- dark-side monad: extends AMonadY; handleCommand intercepts CHECKSUM and runs it on a separate  
&nbsp;   thread (checksumExec, not the queue worker) via the abstract _processItemCancellable(listener,  
&nbsp;   item); the digest is reported through the 3-arg onResult. shutdown() also stops checksumExec.  
taijitu.ATaijituRig  (new)  
&nbsp;- dark-side director: extends ATaijituRigY; holds a second monad yinMonad (AtomicReference)  
&nbsp;+ swapYinYang() (pointer flip); start() sets the shadow's gateFor listener and starts it, then  
&nbsp;   super.start() brings up + loads the serving monad; shutdown() also stops the shadow;  
&nbsp;   onEntityBroadcast fans events to both monads.  
**taijitu.AMonadY**  
&nbsp;- onResult + CommandGate carry a result String (doCommand returns it; notifyComplete uses the  
&nbsp;   result, else status.name()); _processItem returns String; handleCommand + commandGate + log/devLog  
&nbsp;   made protected; CHECKSUM stub branch removed from handleCommand  
**taijitu.ATaijituRigY**  
&nbsp;- no longer implements ICmdResponseListener; added gateFor(IMonad) building a per-monad listener,  
&nbsp;   registered in start() (was the ctor self-registration); onResult 3-arg; log/devLog protected  
**taijitu.ICmdResponseListener**  
&nbsp;- onResult(String commandId, MonadStatus status, String result) -- added the result arg  

**05/22/2026** mir0n  v1.2.5 Taijitu -- synchronous command model + IMonad control contract  
taijitu.IMonad  (new)  
&nbsp;- the monad CONTROL contract a director drives: start/shutdown, offer, setQueueEnabled/  
&nbsp;   setProcessingEnabled/queueClear, status/queueSize/id, setCmdResponseListener,  
&nbsp;   doCommand(String cmd, boolean enableQueue, long timeoutMs). AMonadY implements it  
**taijitu.AMonadY**  
&nbsp;- implements IMonad; public control methods renamed to the IMonad names: stop()->shutdown(),  
&nbsp;   clearQueue()->queueClear(), queueDepth()->queueSize(), monadId()->id() (submit() stays, used  
&nbsp;   internally by doCommand -- not on the interface)  
&nbsp;- added doCommand(cmd, enableQueue, timeoutMs): posts the command, opens the queue right after  
&nbsp;   (when enableQueue), BLOCKS on an inner CommandGate monitor until the worker signals; timeoutMs<=0  
&nbsp;   waits indefinitely, a positive timeout cancels the registered cancelable + grace-waits; returns  
&nbsp;   the result string (status name / "TIMEDOUT" / "INTERRUPTED")  
&nbsp;- new inner CommandGate implements ICmdResponseListener: holds result + cancelable; the worker  
&nbsp;   notifies it (onStarted/onResult), it forwards to the rig listener and notifies doCommand  
&nbsp;- handleCommand notifies via the gate; CLEAR wrapped in try/finally -> always IDLE + onResult  
&nbsp;- listener field renamed cmdResponseListener -> rigCmdResponseListener  
&nbsp;- instance loggers via getClass() (was static AMonadY.class)  
**taijitu.ATaijituRigY**  
&nbsp;- bootstrap() renamed start(); active monad held as AtomicReference  
&nbsp;- ctor registers the director as the monad's ICmdResponseListener (yang.setCmdResponseListener(this))  
&nbsp;- start() is a synchronous retry loop: clearMonad(active) then doCommand(LOAD, true, 0) until  
&nbsp;   LOADED, else sleep(retryDelayMs) + retry  
&nbsp;- added clearMonad(IMonad): setQueueEnabled(false) + queueClear() + setProcessingEnabled(true) +  
&nbsp;   doCommand(CLEAR, false, 0)  
&nbsp;- onStarted/onResult drive the per-command gate-flag policy (LOAD: processing off during load,  
&nbsp;   on at LOADED; FAILED / CLEAR: queue + processing off)  
&nbsp;- instance loggers via getClass()  
**taijitu.ITaijituRig**  
&nbsp;- bootstrap() renamed start()  
**taijitu.MonadStatus**  
&nbsp;- each constant carries a String code; added code() accessor  

**05/21/2026** mir0n  v1.2.5 Taijitu Step 3 generalization -- queue rig + monad framework lifted from bizTree  
concurrent.IQueueRig  (new)  
&nbsp;- generic active-object queue contract : bounded FIFO drained by one worker (IQueueWorker);  
&nbsp;   processing gate (setProcessing -- when false the worker leaves the queue UNTOUCHED, no dequeue);  
&nbsp;   put / size / clear; nested IErrorListener seam  
concurrent.BoundedQueueRig  (new)  
&nbsp;- implements IQueueRig: ArrayBlockingQueue + single daemon worker; gate parks before dequeue;  
&nbsp;   recoverable Throwable -> IErrorListener and the worker keeps running; Error propagates;  
&nbsp;   InterruptedException = shutdown; clear() bulk-drops. Lifted from bizTree MonadY  
taijitu.QueueItem  (new)  
&nbsp;- flat record(eventType, entityId, entityKind, requestId, correlationId, messageEncoding, text);  
&nbsp;   commands and events share the one record; a command is eventType==MonadCmd.CMD with  
&nbsp;   entityId=the command id -- no sealed hierarchy  
taijitu.MonadCmd  (new)  
&nbsp;- final class; interned String command vocabulary: CMD (eventType marker), LOAD, CLEAR, CHECKSUM  
taijitu.MonadStatus  (new)  
&nbsp;- enum IDLE / LOADING / LOADED / FAILED  
taijitu.AMonadY  (new)  
&nbsp;- abstract cache monad: owns a BoundedQueueRig over QueueItem, the status machine, two gates,  
&nbsp;   and command EXECUTION (LOAD/CLEAR/CHECKSUM); single abstract hook _processItem(QueueItem);  
&nbsp;   fires onStarted/onResult; only LOAD/CLEAR change status, message/CHECKSUM faults traced by the rig  
taijitu.ATaijituRigY  (new)  
&nbsp;- abstract director: implements ITaijituRig + ICmdResponseListener; holds the active AMonadY;  
&nbsp;   bootstrap (submit LOAD, enable queue + processing); DRIVES the processing gate off the monad  
&nbsp;   callbacks (onStarted LOAD -> processing off; onResult LOADED -> on; FAILED -> setQueueEnabled(false)+clearQueue)  
taijitu.ITaijituRig  (new)  
&nbsp;- director contract: bootstrap() / onEntityBroadcast(7 raw fields) / shutdown(); bean-blind, REST-free  
taijitu.ICmdResponseListener  (new)  
&nbsp;- onStarted(commandId, ICancelable) + onResult(commandId, MonadStatus); NOOP default  
taijitu.ICancelable  (new)  
&nbsp;- in-flight command abort handle: cancel()  

---

## Commits

```

-- 2026-10-14 | commit: f0ec13f | mir0n.the.programmer | v2.0 Initial --
A	.gitignore
A	LICENSE
M	README.md
A	doc/Armoury.Haubergeon.md
A	doc/Armoury.Taijitu.md
A	doc/Armoury.Utils.md
A	doc/logo/armoury.jpg
A	doc/logo/hauberk.svg
A	doc/logo/taijitu.svg
A	doc/logo/utils.png
A	doc/release_notes.txt
A	doc/reports/report_2026_10_14_.md
A	hauberk/hauberk.cmd
A	hauberk/hauberk.properties
A	hauberk/pom.xml
A	hauberk/src/main/java/pro/mir0n/hauberk/auth/KcTokenClient.java
A	hauberk/src/main/java/pro/mir0n/hauberk/auth/RefreshableToken.java
A	hauberk/src/main/java/pro/mir0n/hauberk/chain/Cmd.java
A	hauberk/src/main/java/pro/mir0n/hauberk/changes.txt
A	hauberk/src/main/java/pro/mir0n/hauberk/cli/CsvSnapshot.java
A	hauberk/src/main/java/pro/mir0n/hauberk/cli/DiffCommand.java
A	hauberk/src/main/java/pro/mir0n/hauberk/cli/HauberkCli.java
A	hauberk/src/main/java/pro/mir0n/hauberk/cli/ListCommand.java
A	hauberk/src/main/java/pro/mir0n/hauberk/cli/RunCommand.java
A	hauberk/src/main/java/pro/mir0n/hauberk/cli/SimulationCatalog.java
A	hauberk/src/main/java/pro/mir0n/hauberk/cli/SummaryCommand.java
A	hauberk/src/main/java/pro/mir0n/hauberk/config/AuthConfig.java
A	hauberk/src/main/java/pro/mir0n/hauberk/config/HauberkConfig.java
A	hauberk/src/main/java/pro/mir0n/hauberk/health/HealthPreCheck.java
A	hauberk/src/main/java/pro/mir0n/hauberk/perf/PerformanceMatrix.java
A	hauberk/src/main/java/pro/mir0n/hauberk/simulations/AHauberkSimulation.java
A	hauberk/src/main/java/pro/mir0n/hauberk/simulations/SimulationInfo.java
A	hauberk/src/test/java/pro/mir0n/hauberk/config/HauberkConfigTest.java
A	pom.xml
A	taijitu/pom.xml
A	taijitu/src/main/java/pro/mir0n/taijitu/AMonad.java
A	taijitu/src/main/java/pro/mir0n/taijitu/AMonadY.java
A	taijitu/src/main/java/pro/mir0n/taijitu/ATaijituRig.java
A	taijitu/src/main/java/pro/mir0n/taijitu/ATaijituRigY.java
A	taijitu/src/main/java/pro/mir0n/taijitu/ICancelable.java
A	taijitu/src/main/java/pro/mir0n/taijitu/ICmdResponseListener.java
A	taijitu/src/main/java/pro/mir0n/taijitu/IMonad.java
A	taijitu/src/main/java/pro/mir0n/taijitu/ITaijituRig.java
A	taijitu/src/main/java/pro/mir0n/taijitu/MismatchAction.java
A	taijitu/src/main/java/pro/mir0n/taijitu/MonadCmd.java
A	taijitu/src/main/java/pro/mir0n/taijitu/MonadStatus.java
A	taijitu/src/main/java/pro/mir0n/taijitu/QueueItem.java
A	taijitu/src/main/java/pro/mir0n/taijitu/changes.txt
A	taijitu/src/test/java/pro/mir0n/taijitu/AMonadYBulkTest.java
A	taijitu/src/test/java/pro/mir0n/taijitu/AMonadYOutcomeTest.java
A	taijitu/src/test/java/pro/mir0n/taijitu/ATaijituRigTest.java
A	taijitu/src/test/java/pro/mir0n/taijitu/ATaijituRigYTest.java
A	utils/pom.xml
A	utils/src/main/java/pro/mir0n/o11y/BizMeters.java
A	utils/src/main/java/pro/mir0n/o11y/Gauges.java
A	utils/src/main/java/pro/mir0n/o11y/TraceMark.java
A	utils/src/main/java/pro/mir0n/o11y/changes.txt
A	utils/src/main/java/pro/mir0n/utils/HostId.java
A	utils/src/main/java/pro/mir0n/utils/changes.txt
A	utils/src/main/java/pro/mir0n/utils/concurrent/BoundedQueueRig.java
A	utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
A	utils/src/main/java/pro/mir0n/utils/concurrent/IQueueRig.java
A	utils/src/main/java/pro/mir0n/utils/concurrent/WorkerPool.java
A	utils/src/test/java/pro/mir0n/o11y/BizMetersTest.java
A	utils/src/test/java/pro/mir0n/o11y/TraceMarkTest.java
A	utils/src/test/java/pro/mir0n/utils/HostIdTest.java
A	utils/src/test/java/pro/mir0n/utils/concurrent/BoundedQueueRigTest.java
A	utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheStoreIfGreaterTest.java
A	utils/src/test/java/pro/mir0n/utils/concurrent/ExpiringCacheTest.java
A	utils/src/test/java/pro/mir0n/utils/concurrent/WorkerPoolTest.java
A	utils/src/test/resources/logback-test.xml
 71 files changed, 9567 insertions(+), 2 deletions(-)
```


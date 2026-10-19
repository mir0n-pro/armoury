# Release Report: 0.0.0 → HEAD

**Repo:** `armoury/develop`  
**Top commit:** `8e0d1c3`

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

---

## Commits

```

-- 2026-10-18 | commit: 8e0d1c3 | mir0n.the.programmer | v2.0.0 finalization --
A	.github/workflows/publish.yml
M	README.md
M	doc/Armoury.Utils.md
M	doc/logo/taijitu.svg
D	doc/logo/utils.png
A	doc/logo/utils.svg
D	doc/reports/report_2026_10_14_.md
A	doc/reports/report_v2.0.0.md
M	pom.xml
 9 files changed, 919 insertions(+), 29 deletions(-)

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

---

## Files Modified

```
A	.github/workflows/publish.yml
A	.gitignore
A	LICENSE
M	README.md
A	doc/Armoury.Haubergeon.md
A	doc/Armoury.Taijitu.md
A	doc/Armoury.Utils.md
A	doc/logo/armoury.jpg
A	doc/logo/hauberk.svg
A	doc/logo/taijitu.svg
A	doc/logo/utils.svg
A	doc/release_notes.txt
A	doc/reports/report_v2.0.0.md
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
 72 files changed, 10457 insertions(+), 2 deletions(-)
```

---

*From `0.0.0` till `HEAD`*

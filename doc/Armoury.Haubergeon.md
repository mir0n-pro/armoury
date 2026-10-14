<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="logo/hauberk.svg" alt="hauberk" align="right" width="64" height="64"></td>
    <td style="width: 88%;"><h1>Armoury Haubergeon</h1></td>
  </tr>
</table>

**Harness for the Gatling engine.** Haubergeon drives HTTP load against a running deployment from
outside, and records the time of every request, split by the layers it passed through.

The harness holds no test of its own. A product writes its simulations on top of it, names their
package in its properties file, and runs them with the hauberk command line.

## The name

A **haubergeon** is a shorter version of a **hauberk**, the chainmail coat. It reaches mid-thigh,
not past the knee, and has short sleeves. A mail shirt, not a full suit of armor.

- A haubergeon is built from many small rings linked together. A Gatling simulation is built the
  same way: from many small reusable `ChainBuilder` blocks, linked into scenarios.
- The name keeps the scope narrow. Haubergeon covers one concern: smoke, load, soak, and
  race-condition repros. Not unit tests, not contract tests, not browser tests. One piece of the
  testing set, not the whole armor.

In code, configuration and system properties you see *hauberk*: `hauberk.cmd`, `pro.mir0n.hauberk`,
`armoury-hauberk`, `hauberk.metrics`. It is the shorter name for the same thing, still the
haubergeon.

## Gatling vocabulary

Gatling's own terms, used as they are across the harness:

| Term | Meaning |
|---|---|
| **Simulation** | One Java class (extends `Simulation`), the top-level test run. One simulation = one `hauberk run <sim>`. |
| **Scenario** (`ScenarioBuilder`) | The sequence of actions a virtual user performs, built by chaining `.exec(...)` calls. |
| **Chain** (`ChainBuilder`) | A reusable block: one logical command. Chains compose into scenarios. |
| **Virtual user (VU)** | An asynchronous actor that runs a scenario. One JVM runs thousands of them. |
| **Injection profile** | How VUs start: `atOnceUsers(N)`, `rampUsers(N).during(...)`, `constantUsersPerSec(N).during(...)`. |
| **`setUp(...)`** | One call per simulation. It wires scenarios to their injection profiles. Several populations in one `setUp` run in parallel. |
| **`andThen(...)`** | Populations in sequence: the next one starts when the previous one ends. |
| **`before()` / `after()`** | Hooks that run once per simulation, for shared setup and cleanup. |
| **Session** | The state of one VU. `.exec(session -> session.set("k", v))` writes it; `${k}` reads it. |
| **Feeder** | A data source (CSV, JSON, in memory) that puts values into sessions. |
| **Check** | An assertion on a response: `check(status().is(200))`, `check(jsonPath("$.id").saveAs("entityId"))`. |

## Build

```
mvn clean install            (from the armoury root)
```

Output: `hauberk/target/armoury-hauberk.jar`, one jar with Gatling, picocli and every dependency in
it. The same build installs `pro.mir0n:armoury-hauberk:2.0.0` into the local Maven repository, for a
product that builds its simulations against it.

## Run

`hauberk.cmd <subcommand> [options]`. The launcher is one line: it adds the `--add-opens` flags
Gatling needs and passes every argument to the jar.

```
java --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
     -jar target/armoury-hauberk.jar %*
```

A product ships its own launcher next to its own jar, in the same shape.

### Subcommands

| Subcommand | What it does |
|---|---|
| `list` | Prints the simulations found in the configured package (class name + short alias) |
| `run <sim> [opts]` | Starts a Gatling simulation |
| `summary <csv>` | Prints the per-URL percentile summary of a saved performance-matrix CSV |
| `diff <csv-a> <csv-b>` | Compares two CSVs per URL, side by side, with the deltas |
| `help [command]` | The picocli help, for all commands or for one |

### Options of `run`

| Option | Effect |
|---|---|
| `--metrics` | Turns on the performance matrix (per-request CSV + per-URL summary) |
| `--output <FOLDER>` | Output base. Each run goes to `<output>/<sim>-<timestamp>/` (default `./output`), with Gatling's report and the matrices side by side |
| `--config <FILE>` | Loads a properties overlay on top of `hauberk.properties` |
| `--set <key=value>` | Overrides one setting for this run; repeatable |
| `--times <N>`, `-n <N>` | Runs the simulation N times in a row; each run gets its own report |
| `--no-health-check` | Skips the health check before the run |

A product adds its own options by extending the `run` command.

## Configuration

Everything that changes between runs comes from properties: `hauberk.properties` at the module root,
an overlay file, `--set`, or a `-D` system property. Nothing is hard-coded in Java.

| Property | What it sets |
|---|---|
| `simulations.package` | The package `list` and `run` search for simulations |
| `kc.base`, `kc.realm`, `kc.client.id`, `kc.client.secret` | The Keycloak realm and the client the run signs in as |
| `tokenRelay.type` | `plain`, `vanilla` or `phantom`: the gateway's token pattern this run targets |
| `health.probe.kc`, `health.probe.gw`, `health.auth.probe` | The three URLs of the health check |
| `perf.header.capture`, `perf.header.gw.outer`, `perf.header.gw.inner`, `perf.header.srv.outer`, `perf.header.srv.inner` | The five header names the performance matrix reads |

The harness does not know which client it runs as. The loaded file tells it: `kc.client.id`,
`kc.client.secret` and `tokenRelay.type`. One file per run.

`tokenRelay.type=vanilla` sends **HTTP Basic** (`client_id:client_secret`), and the gateway fetches
the token. `plain` and `phantom` send a **Bearer** token from Keycloak. The choice is
`AuthConfig.isBasicAuth()`, read by the simulations and by the health check alike.

### Health check

Before a run, `run` calls the three `health.probe.*` URLs: the Keycloak discovery document, the
gateway health, and one call with a real token, which tests the whole sign-in path. If any fails, the
run stops at once with a clear message. Without it, an offline service shows up as thousands of
failed requests. `--no-health-check` skips it.

### Token refresh

`KcTokenClient` gets an access token by the client-credentials grant. `RefreshableToken` wraps it
and gets a new one when 30 seconds remain. Every simulation reads it per request
(`(session) -> "Bearer " + TOKEN.value()`), so a long run does not fail when a token expires.

## Performance matrix

Turned on with `--metrics`. It reads up to four timing headers from every response, names given by
the `perf.header.*` properties, and writes one CSV per Gatling scenario, plus a summary at the end.

### Output

```
./output/<sim-class>-<yyyyMMddHHmmssSSS>/
    index.html              -- Gatling's HTML report
    simulation.log          -- Gatling's raw run log
    req_*.html              -- per-request pages
    <scenario-1>.csv        -- performance matrix (only with --metrics)
    <scenario-2>.csv        -- one CSV per Gatling scenario
    ...
```

Everything from one run is in one folder under `--output`. Each CSV is named after
`Session.scenario()`, the string passed to `scenario("...")`. A simulation that runs N scenarios in
parallel writes N CSVs.

### CSV layout

```
# perf-matrix YAML preamble (commented; CSV body starts at next non-# line)
# simulation:    <SimulationClass>
# scenario:      <scenario>
# client:        <kc.client.id>
# kc_base:       <kc.base>
# gw_base:       <gw.base>
# start_utc:     <run start>
timestamp,reqName,httpStatus,clientMs,gwOuterMs,gwInnerMs,srvOuterMs,srvInnerMs
...
```

The preamble lines start with `#`. Drop them (`grep -v '^#'`) and the rest is plain CSV for any
spreadsheet or `pandas.read_csv`.

### Five measured values

Each value is a median (or any percentile you choose) of one request URL's samples, in
milliseconds.

| Abbreviation | Source | What it measures |
|---|---|---|
| `c` | Gatling's `responseTimeInMillis` | clientMs: send to receive, at the client |
| `gO` | `perf.header.gw.outer` | gwOuterMs: the gateway's whole handling of the request |
| `gI` | `perf.header.gw.inner` | gwInnerMs: the gateway's call to the service only |
| `sO` | `perf.header.srv.outer` | srvOuterMs: the service's whole handling of the request |
| `sI` | `perf.header.srv.inner` | srvInnerMs: the service's inner work, for example the database |

### Five derived bands

Each band is one value minus the next. It gives each part of the request's path its own time:

```
client -----> gw_outer -----> gw_inner -----> srv_outer -----> srv_inner
       net             gw_self        in_cluster       srv_self        (srv_inner)
```

| Band | Formula | What lies in it |
|---|---|---|
| `net` | `c - gO` | The network between client and gateway |
| `gw_self` | `gO - gI` | The gateway's own work: security checks, token handling, routing |
| `in_cluster` | `gI - sO` | The network between gateway and service |
| `srv_self` | `sO - sI` | The service's own logic, without its inner work |
| `srv_inner` | `sI` | The inner work itself |

### Per URL only

The summary and `diff` show one row per URL and no total. A median taken across different kinds of
request describes the simulation's mix of requests, not the system. A cached read of 7 ms and a
database write of 30 ms do not make one useful number.

### `diff`: A/B comparison

`hauberk.cmd diff output/<run-a>/<scn>.csv output/<run-b>/<scn>.csv` prints the two runs per URL,
with the deltas. A URL that appears in only one run is listed under "only in A" or "only in B",
not dropped.

### Sample size

Each row shows its `n`. Trust a median when `n` is large. A row with `n=1` is a single warm-up
sample. For an A/B comparison, use a load simulation, where each URL collects thousands of samples.

## Architecture

The harness in `pro.mir0n.hauberk`:

| Package | What is in it |
|---|---|
| `cli` | The picocli command line: `HauberkCli`, `RunCommand`, `ListCommand`, `SummaryCommand`, `DiffCommand`, `CsvSnapshot`, `SimulationCatalog` |
| `config` | `HauberkConfig` (the properties file and its overlay), `AuthConfig` (client and token pattern) |
| `auth` | `KcTokenClient`, `RefreshableToken` |
| `health` | `HealthPreCheck` |
| `perf` | `PerformanceMatrix` |
| `simulations` | `AHauberkSimulation`, the base class every simulation extends; `SimulationInfo` |
| `chain` | `Cmd` |

A product brings its own `chain` blocks and `simulations`, and extends the `run` command for its
own options.

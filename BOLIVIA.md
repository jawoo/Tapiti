# Tapiti — Bolivia ENSO crop-impact tool (a fork of Tokki)

**Name.** IFPRI's gridded-DSSAT tools are named after small animals: *Tokki* (토끼, rabbit in
Korean) was built under Korean government funding. The Bolivian successor is **Tapiti**, after
the *tapití* (*Sylvilagus brasiliensis*), the one wild rabbit native to Bolivia's lowlands —
the word is Guaraní. Written without the accent everywhere in code, paths and config.
Renamed from `Tokki-BO` on 2026-10-03: directory `Tapiti`, Maven `org.cgiar:tapiti`, Java
package `org.cgiar.tapiti`, merged output `tapiti_combinedOutput_*.csv`. Repository:
[github.com/jawoo/Tapiti](https://github.com/jawoo/Tapiti) (`origin`, branch `bolivia`); the US code
stays reachable as the `tokki-upstream` remote.


A clone of [jawoo/Tokki](https://github.com/jawoo/Tokki) `master` on branch `bolivia`,
repurposed and renamed for the IFPRI Bolivia El Niño crop-impact assessment. The upstream US production
workspace at `~/Codebase/Tokki` is untouched by anything here.

Plan, data inventory and analysis live in the companion project folder
`~/Claude/bolivia-enso` (`PLAN.md`, `DATA-INVENTORY.md`).

## How this diverges from upstream

### Code — country parameterisation

`US.SOL` was hardcoded in two places. The functional one (`Utility.getUnitInfo`, which actually
loads the profiles) was easy to miss; only the startup check in `App.validateEnvironment` is
obvious from a grep of `App.java`.

- **`config.yml` gains `soilFileName`.** Defaults to `US.SOL` when absent, so existing
  configs keep working. Set explicitly rather than derived from `countryCode`, because the
  first two letters of an ISO3 code are not a reliable ISO2 (`CHL` and `CHN` both give `CH`).
- `TokkiConfig` / `ConfigLoader` / `App` thread it through; `Utility.getUnitInfo` takes it as
  a parameter. `App` now echoes `> Soil file:` at startup.
- The soil file's base name **must** match the two-character prefix of every `soilProfileId`,
  because `ThreadSeasonalRuns` and `ThreadFloweringRuns` derive the per-thread copy's filename
  from `soilProfileID.substring(0,2)`.

### Code — which nitrogen rate is applied

With `useRecommendedNitrogenFertilizerRateOverride: 1` (the US setting) upstream Tokki applies
each record's **`nFertRateRec`**, not `nFertRateAct` — `ThreadSeasonalRuns` reads
`cultivarOption[6]`, which `App.buildCultivarOptionFromUnit` fills from `nFertRateRec`; the
actual rate is built into slot 7 and never used. A hindcast against observed yields wants the
farmer rate, so `config.yml` gains **`nitrogenRateSource: actual | recommended`** (default
`recommended`, so upstream behaviour is unchanged when the key is absent). The Bolivia config
sets `actual`; `App` echoes `> Nitrogen rate applied:` at startup so the log records which column
ran. Plumbed through `TokkiConfig` / `ConfigLoader` / `App` / `ThreadSeasonalRuns` (2026-10-03).

### Code — rice production system (`riceSystem: paddy | upland`)

Upstream writes every rice run from one template: transplanted (`PLME T`), puddled (`IR010`),
bunded 150 mm (`IR009`), 2 mm/d percolation (`IR008`), and a 100 mm flood 5 days after
planting (`IR003`, or `IR011` constant depth when irrigated), with `IRRIG R` and the flooded
initial conditions. That is Asian irrigated paddy; ~90% of Bolivian rice is direct-seeded
*secano*. `riceSystem: upland` (default `paddy` = upstream behaviour) changes both SNX writers:

| | rainfed record (`waterSupply R`) | irrigated record (`waterSupply I`) |
|---|---|---|
| Planting | `PLME S`, `PLDS R`, 20 cm rows, 3 cm deep | same |
| Treatment `MI` | 0 (no irrigation level, like other rainfed crops) | 2 |
| Water management | none | `IR008 2`, `IR010` puddled, `IR009 150` at planting; `IR011 100` constant flood from **25 DAP** |
| Initial conditions | rainfed block (`ICWD -99`, `SH2O .25`) | flooded block |
| `IRRIG` control | `D` | `R` |

Puddling stays in the irrigated variant because DSSAT (`Management/Flood_Irrig.for`) stops with
`FIRRIG: Invalid combination of flooded field parameters` if `IR011` is used without `IR009`
and `IR010`. Verified 2026-10-03 by reading the generated `TOUCAN00.SNX` and DSSAT's
`OVERVIEW.OUT`: rainfed runs show `IRCM 0` and water stress; irrigated runs show zero water
stress.

**What the test also showed, and it is not a rice problem:** on a low-carbon Santa Cruz soil
(SoilGrids SLOC 0.77%) rice with 14 kg N yields ~0.2 t/ha flooded *or* unflooded, with nitrogen
stress of 0.8–0.9 in the vegetative phase, and climbs to 3.1 t/ha at 100 kg N; on a Beni soil
(SLOC 1.84%) the same crop takes up 160–210 kg N mineralised from soil organic matter and
yields 7 t/ha with zero fertiliser. Tokki starts every season with essentially **no mineral N
in the profile** (`SNH4 = SNO3 = 0.001 ppm` in the initial conditions, inherited from the US
setup where 150+ kg N/ha of fertiliser makes it irrelevant). In Bolivia's 0–25 kg N systems,
initial mineral N and the SoilGrids carbon map drive the simulated yield more than anything
in the management table. That is a Phase 2 calibration item for **all five crops**, not just
rice (`PLAN.md` §7.3).

### Code — pre-season boundary conditions (added 2026-10-04, all default to upstream behaviour)

| `config.yml` key | Default (= upstream) | Alternative | What it changes in the SNX |
|---|---|---|---|
| `somModel` | `century` | `ceres` | `MESOM` P or G in both simulation-control blocks |
| `simulationStartDaysBeforePlanting` | `0` (1 January of the sowing year) | e.g. `60` | `ICDAT` / `SDATE` = planting DOY − N, clamped to 1 Jan |
| `initialSoilWaterFraction` | absent (one layer, 0.25 rainfed / 0.50 irrigated cm³/cm³) | `0.5` | one IC line per soil layer, `SH2O = SLLL + f·(SDUL−SLLL)` rainfed, `SDUL` irrigated/paddy, read from the cell's own profile |
| `initialSoilN: {snh4_ppm, sno3_ppm}` | `0.001 / 0.001` | e.g. `0.3 / 0.7` | `SNH4` / `SNO3` on every IC layer (ppm applies to the whole profile: 1 ppm over 2 m ≈ 27 kg N/ha) |

Why they exist: upstream's 1 January start puts ~10 months of bare fallow before a verano
sowing, during which SoilGrids organic carbon mineralises 150–250 kg N/ha that nothing takes
up. That, not the management table, made the first Bolivia run 1.7–2.5× too high for the
N-responsive crops (`PLAN.md` §7.3 has the sensitivity table). `App` echoes all four at startup.
`res/input/unit-information-sample60.jsonl` is the 60-cell stratified sample used for these
tests (36 lowland, 14 valleys, 10 Altiplano cells).

### Schema

`res/input/unit-information.schema.json` — `soilProfileId.pattern` widened from `^US[0-9]{8}$`
to `^[A-Z]{2}[0-9]{8}$`.

### Removed US inputs

`US.SOL`, `unit-information.jsonl`, `unit-information-belttest.jsonl` and `cell-gdd.csv` were
deleted from `res/input/`. They remain in upstream `master` history if needed. To be rebuilt
for Bolivia: **`BO.SOL`**, **`unit-information.jsonl`**, **`cell-gdd.csv`**.

`CO2048.csv` is kept as-is — it spans 1958–2050 and is global.

### Cultivars — inherited, NOT yet calibrated for Bolivia

`res/.csm/MZCER048.CUL` and `SBGRO048.CUL` carry the **US** calibration; the wheat, sorghum
and rice cultivars stamped by the Phase 1 builder are stock DSSAT generics. They are a starting
point, not a Bolivian calibration: the US work replaced degenerate yield coefficients in the
generic maturity cultivars with realistic donor values, which is a general improvement worth
inheriting, but the resulting levels are tuned to US yields.

The smoke run below shows exactly why this matters — maize comes out at up to **12.8 t/ha**
against a Bolivian reality nearer 3–5 t/ha. Maize is the first recalibration target.

## Inherited US material

The US input pipeline (`prep/*.py`, `config.yml.main`, the US soil and unit tables) was removed
from this repository on 2026-10-03, and the large US data files were purged from the history
as well, so Tapiti's history no longer shares commits with Tokki. The US scripts remain in
`~/Codebase/Tokki/prep/`; upstream fixes are brought across as patches rather than merges.

## Configuration

| File | Purpose |
|---|---|
| `config.yml` | Bolivia production: sowing years 1983–2024 (42 campaigns, matching the INE *Año Agrícola* series), 48 threads |
| `config.yml.smoke` | The exact config that produced the passing smoke run: 3 cells, sowing years 2015–2016, 4 threads |

Southern-hemisphere conventions, which the config comments repeat:

- A campaign is labelled by its **sowing** year — the 2026-27 verano campaign is
  `firstPlantingYear: 2026`. DSSAT starts on 1 Jan of the sowing year and bounds harvest in
  the following year.
- **Weather files must extend one full year beyond the last sowing year**, or the final season
  has nothing to mature into.
- Keep sorgo/frejol median planting dates at or below **DOY 335**. The onset scan window is
  ±30 days and is truncated at 31 December — see the year-wrap fix (upstream PR #9) and
  `~/Claude/bolivia-enso/debug/planting-date-yearwrap/`.

## Setup already done in this clone

`res/.csm/` was populated by copying from the US clone (stock DSSAT 4.8.5 data files, the
`DSCSM048.EXE` binary, and the two tracked calibrated CUL files) — so there is no need to
re-clone or recompile DSSAT. Verified: `git status res/.csm/` is clean, i.e. the CULs match
the repo-tracked calibrated versions rather than stock DSSAT.

## Phase 1 production inputs (built 2026-10-03)

| File | Built by | Content |
|---|---|---|
| `res/input/BO.SOL` | `~/Claude/bolivia-enso/prep/build_soil_bolivia.py` | 3,568 ISRIC SoilGrids + HC27 profiles, subset of the national `data/BO.SOL`; 10 nearest-neighbour fills logged in `derived/soil_fill_log.csv` |
| `res/input/unit-information.jsonl` | `prep/build_unit_information_bolivia.py` | 3,469 cells, 9,249 crop records (SB 1,610, MZ 2,671, RI 1,186, SG 1,788, WH 1,994); every record carries an explicit `cultivar` |
| `res/input/cell-gdd.csv` | same | 1991–2020 mean growing-season GDD per cell (for `prep/stamp_cultivars.py` compatibility) |
| `res/input/unit-information-smoke.jsonl` | hand-picked | 6 cells spanning Santa Cruz (irrigated and rainfed), Beni, Tarija valleys, Potosí Altiplano — all five crops |
| `res/input/unit-information-sample60.jsonl` | random, seed 7 | 60-cell stratified sample (36/14/10 by zone) for sensitivity runs; ~15 s per 6-year run |
| `res/weather/1981-2026_bol/` | `prep/gen_weather_bolivia.py` | NASA POWER daily weather per cell, 1981-01-01 .. 2026-09-25 |

Management (sowing date, density, N rates, cultivar) comes from
`~/Claude/bolivia-enso/derived/management_defaults_bolivia.csv`, one row per zone × crop,
with evidence in `derived/SOURCES-management.md`. Change a number there and re-run the builder;
nothing is hardcoded in the Java.

Three things about this table that anyone running the model should know:

1. **One season per crop per cell.** Output files are keyed `U{unit}_C{cell}_Y{year}_S{scenario}_{label}`
   where the label carries crop and cultivar but not planting date, so a second season of the
   same crop would overwrite the first. Each crop runs in its dominant campaign for the
   department (INE 2012–2024): verano for soya, rice and highland wheat/sorghum; primavera for
   maize; **invierno** for Santa Cruz lowland sorghum and wheat and for Tarija/Beni wheat.
2. **WH, SG and RI need stamped cultivars.** Their CUL files contain no `*`-flagged lines, so
   `Utility.getCultivarCodes` returns an empty list and an unstamped record silently runs nothing.
3. **Rice runs in upland mode** (`riceSystem: upland` in `config.yml`, added 2026-10-03; see
   the code section below). Rainfed rice is direct dry-seeded and otherwise treated like any
   rainfed crop; irrigated rice is direct-seeded into a puddled, bunded field and flooded from
   25 days after planting. Irrigated rice records carry 40 kg N/ha, secano 15.

To run the smoke set against production weather and soil:

```bash
cd ~/Claude/bolivia-enso/Tapiti
sed -i 's/^tableNameUnitInformation: .*/tableNameUnitInformation: unit-information-smoke/;s/^numberOfThreads: .*/numberOfThreads: 4/;s/^firstPlantingYear: .*/firstPlantingYear: 2015/;s/^numberOfYears: .*/numberOfYears: 2/' config.yml
java -jar target/tokki-1.0-SNAPSHOT-jar-with-dependencies.jar     # ~3 s, 48 seasons
git checkout -- config.yml
```

## Production run

`config.yml` as committed is the production configuration: sowing years 1983–2024 (42
campaigns), 16 threads, rainfall-onset planting, recorded water supply, table N rates.
Weather must extend one full year past the last sowing year, which `1981-2026_bol` does.

```bash
cd ~/Claude/bolivia-enso/Tapiti
nohup java -jar target/tokki-1.0-SNAPSHOT-jar-with-dependencies.jar > ../runs/prod_<tag>.log 2>&1 &
# 9,249 crop records x 42 years = 388k DSSAT seasons; ~1 h on this laptop.
# Merged output: res/result/tokki_combinedOutput_<epoch>.csv ; per-thread DSSAT errors: res/threads/T*/ERROR.OUT
```

Run logs of record: `~/Claude/bolivia-enso/runs/`.

| Log | What it is |
|---|---|
| `prod_1983-2024_v1_provisional-mgmt.log` | 2026-10-03, provisional management table, recommended-N column applied; stopped by hand at 48k seasons with no DSSAT errors — a scale test only |
| `prod_1983-2024_v2_actualN.log` | 2026-10-03, sourced management table, `nitrogenRateSource: actual`; the pre-calibration baseline |

## Smoke test (Phase 0, 2026-09-15)

`~/Claude/bolivia-enso/prep/smoke_inputs.py` builds a minimal end-to-end input set: three real
5-arcmin cells in the Santa Cruz Norte Integrado soybean/maize belt, **real NASA POWER daily
weather** for 2015–2017, soya verano + maíz manual, and placeholder soil profiles.

```bash
python3 ~/Claude/bolivia-enso/prep/smoke_inputs.py
cd ~/Claude/bolivia-enso/Tapiti && cp config.yml.smoke config.yml
./mvnw clean package && java -jar target/tokki-1.0-SNAPSHOT-jar-with-dependencies.jar
# restore afterwards: git checkout -- config.yml
```

**The soil profiles are placeholders cloned from `US.SOL` with a `BO` prefix.** They exist only
to prove the chain runs; Phase 1 rebuilds them from SoilGrids v2 + HC27.

### What the smoke run established

12 rows (3 cells × 2 crops × 2 sowing years), no DSSAT errors.

**The southern-hemisphere season crossing 1 January works.** This was the main technical risk
in the plan and it is now empirically cleared:

```
PDAT 2015296 (23 Oct 2015) -> ADAT 2015334 -> MDAT 2016064 (4 Mar 2016)
PDAT 2016336 ( 1 Dec 2016) -> ADAT 2017024 -> MDAT 2017076 (17 Mar 2017)
```

`icdat = yy001` / `harvestYear = yy+1` / `HARVS = M` handle the crossing without modification.

**The rainfall-onset scan is picking year-specific sowing dates** (DOY 296, 305, 336 across
cells and years), which is the channel that carries the ENSO signal into planting date.

**Yield sanity:** soybean 2,295–3,103 kg/ha dry matter — roughly the right order against
INE's ~2.2–2.4 t/ha at 13% moisture for 2015-16, modestly high. Maize 3,855–12,835 kg/ha,
which is far too high and confirms the US cultivar calibration does not transfer.

### One gotcha worth recording

The first smoke run failed every DSSAT call with error **5010 / `IPSOIL`**. Cause: the
placeholder soil header was written as free text, and DSSAT's soil reader is **fixed-column** —
an 11-character string in the 5-character `TEXTURE` field pushed `DEPTH` out of position. The
fix substitutes only the 10-character soil id and preserves every other column
(`smoke_inputs.py::retoken`). Note the failure was *silent at the Tokki level*: the run
reported `Done` and merged an output file; only `res/threads/T*/ERROR.OUT` revealed it. Check
that file, not just the exit status.

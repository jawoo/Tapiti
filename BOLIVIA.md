# Tokki-BO — Bolivia ENSO workspace

A clone of [jawoo/Tokki](https://github.com/jawoo/Tokki) `master` on branch `bolivia`,
repurposed for the IFPRI Bolivia El Niño crop-impact assessment. The upstream US production
workspace at `~/Codes/Tokki` is untouched by anything here.

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

### Schema

`res/input/unit-information.schema.json` — `soilProfileId.pattern` widened from `^US[0-9]{8}$`
to `^[A-Z]{2}[0-9]{8}$`.

### Removed US inputs

`US.SOL`, `unit-information.jsonl`, `unit-information-belttest.jsonl` and `cell-gdd.csv` were
deleted from `res/input/`. They remain in upstream `master` history if needed. To be rebuilt
for Bolivia: **`BO.SOL`**, **`unit-information.jsonl`**, **`cell-gdd.csv`**.

`CO2048.csv` is kept as-is — it spans 1958–2050 and is global.

### Cultivars — inherited, NOT yet calibrated for Bolivia

`res/.csm/MZCER048.CUL` and `SBGRO048.CUL` carry the **US** calibration. They are a starting
point, not a Bolivian calibration: the US work replaced degenerate yield coefficients in the
generic maturity cultivars with realistic donor values, which is a general improvement worth
inheriting, but the resulting levels are tuned to US yields.

The smoke run below shows exactly why this matters — maize comes out at up to **12.8 t/ha**
against a Bolivian reality nearer 3–5 t/ha. Maize is the first recalibration target.

## Configuration

| File | Purpose |
|---|---|
| `config.yml` | Bolivia production: sowing years 1983–2024 (42 campaigns, matching the INE *Año Agrícola* series), 48 threads |
| `config.yml.smoke` | The exact config that produced the passing smoke run: 3 cells, sowing years 2015–2016, 4 threads |
| `config.yml.main` | Inherited from upstream, unused |

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

## Smoke test

`~/Claude/bolivia-enso/prep/smoke_inputs.py` builds a minimal end-to-end input set: three real
5-arcmin cells in the Santa Cruz Norte Integrado soybean/maize belt, **real NASA POWER daily
weather** for 2015–2017, soya verano + maíz manual, and placeholder soil profiles.

```bash
python3 ~/Claude/bolivia-enso/prep/smoke_inputs.py
cd ~/Codes/Tokki-BO && cp config.yml.smoke config.yml
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

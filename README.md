# Tapiti

Gridded DSSAT crop simulation for Bolivia, built for IFPRI's El Niño crop-impact assessment.
Tapiti is a fork of [Tokki](https://github.com/jawoo/Tokki) (the US tool) and is named after
the *tapití*, Bolivia's native cottontail rabbit.

- **Start with [`BOLIVIA.md`](BOLIVIA.md)** — what differs from Tokki, how the inputs were
  built, how to run the smoke test and the production run, and the known limitations.
- Inputs live in `res/input/` (soil, unit table, GDD, CO2); per-cell weather and DSSAT binaries
  are not tracked (see `res/.gitignore`).
- The input-building, analysis and documentation workspace is the parent project folder
  (`~/Claude/bolivia-enso`: `PLAN.md`, `DATA-INVENTORY.md`, `prep/`, `analysis/`, `derived/`).

```bash
./mvnw clean package
java -jar target/tapiti-1.0-SNAPSHOT-jar-with-dependencies.jar   # reads ./config.yml
```

Requires Java 17+, Maven wrapper (bundled), and DSSAT 4.8.5 files in `res/.csm/`.

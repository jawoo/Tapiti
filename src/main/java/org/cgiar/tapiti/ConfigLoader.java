package org.cgiar.tapiti;

// Java utilities
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

// SnakeYAML utilities
import org.yaml.snakeyaml.Yaml;

// ConfigLoader class
public final class ConfigLoader 
{
    private ConfigLoader() {}

    @SuppressWarnings("unchecked")
    public static TapitiConfig load(String configPath, String separator) throws IOException {
        Yaml yaml = new Yaml();

        Map<String, Object> config;
        try (InputStream inputStream = new FileInputStream(configPath)) {
            config = yaml.load(inputStream);
        }

        String tableNameUnitInformation = (String) config.get("tableNameUnitInformation");
        String countryCode = (String) config.get("countryCode");

        // Name of the DSSAT soil file in the input directory. Its base name must match the
        // two-character prefix of every soilProfileId, because the thread writers derive the
        // per-thread copy's filename from that prefix (soilProfileID.substring(0,2)+".SOL").
        // Set explicitly rather than derived from countryCode: the first two letters of an
        // ISO3 code are not a reliable ISO2 (CHL and CHN both give "CH").
        String soilFileName = config.containsKey("soilFileName")
                ? (String) config.get("soilFileName")
                : "US.SOL";   // historical default, keeps existing configs working

        int numberOfThreads = (int) config.get("numberOfThreads");
        int limitForDebugging = (int) config.get("limitForDebugging");
        boolean scenarioCombinations = (int) config.get("scenarioCombinations") > 0;

        boolean useRecommendedNitrogenFertilizerRateOverride =
                (int) config.get("useRecommendedNitrogenFertilizerRateOverride") > 0;
        boolean useRecordedWaterSupplyOverride =
                (int) config.get("useRecordedWaterSupplyOverride") > 0;
        // Which per-record N rate the override applies: "recommended" (nFertRateRec, the
        // historical behaviour and the default) or "actual" (nFertRateAct, farmer practice,
        // what a hindcast against observed yields wants). Ignored when the override is off.
        // Rice production system written into the SNX: "paddy" (transplanted, puddled, bunded,
        // flooded 5 days after planting — the historical behaviour and the default) or
        // "upland" (direct dry-seeded in rows; rainfed records get no bund/puddle/flood and run
        // like any other rainfed crop, irrigated records are bunded and flooded from 25 days
        // after planting). Bolivia's arroz secano needs "upland".
        String riceSystem = config.containsKey("riceSystem")
                ? String.valueOf(config.get("riceSystem")).trim().toLowerCase() : "paddy";
        if (!riceSystem.equals("paddy") && !riceSystem.equals("upland"))
            throw new IllegalArgumentException("riceSystem must be 'paddy' or 'upland', got '" + riceSystem + "'");
        // Initial mineral N in the soil profile on 1 January of the sowing year (DSSAT SNH4 /
        // SNO3, g N per Mg soil = ppm, one value for the whole profile). Upstream hardcodes
        // 0.001 (i.e. none), harmless under US fertiliser rates and decisive under Bolivian ones.
        double initialSoilNH4ppm = 0.001, initialSoilNO3ppm = 0.001;
        if (config.containsKey("initialSoilN"))
        {
            Map<String, Object> isn = (Map<String, Object>) config.get("initialSoilN");
            if (isn.get("snh4_ppm") != null) initialSoilNH4ppm = ((Number) isn.get("snh4_ppm")).doubleValue();
            if (isn.get("sno3_ppm") != null) initialSoilNO3ppm = ((Number) isn.get("sno3_ppm")).doubleValue();
        }
        // Soil organic matter / N mineralisation model written to MESOM: "century" (DSSAT 'P',
        // upstream default) or "ceres" (Godwin 'G'). Both read the same soil file; they differ in
        // how SoilGrids organic carbon is turned into mineral N during the fallow and season.
        String somModel = config.containsKey("somModel")
                ? String.valueOf(config.get("somModel")).trim().toLowerCase() : "century";
        if (!somModel.equals("century") && !somModel.equals("ceres"))
            throw new IllegalArgumentException("somModel must be 'century' or 'ceres', got '" + somModel + "'");
        // Simulation start (ICDAT/SDATE). 0 = 1 January of the sowing year (upstream behaviour:
        // up to ~10 months of bare fallow before a verano sowing, during which mineralised N
        // accumulates with nothing to take it up). N > 0 = start N days before the planting date,
        // clamped to 1 January.
        int simulationStartDaysBeforePlanting = config.containsKey("simulationStartDaysBeforePlanting")
                ? ((Number) config.get("simulationStartDaysBeforePlanting")).intValue() : 0;
        // Initial soil water. Absent / negative = upstream behaviour (one layer to the rooting
        // depth at 0.25 cm3/cm3 rainfed, 0.50 flooded/irrigated, regardless of soil). 0..1 =
        // per-layer SH2O = SLLL + f * (SDUL - SLLL) for rainfed records (fraction of
        // plant-available water), SDUL (field capacity) for irrigated/paddy records.
        double initialSoilWaterFraction = config.containsKey("initialSoilWaterFraction")
                ? ((Number) config.get("initialSoilWaterFraction")).doubleValue() : -1.0;
        boolean useActualNitrogenRate = config.containsKey("nitrogenRateSource")
                && "actual".equalsIgnoreCase(String.valueOf(config.get("nitrogenRateSource")).trim());

        List<Integer> nitrogenFertilizerRatesList = (List<Integer>) config.get("nitrogenFertilizerRates");
        Object[] nitrogenFertilizerRates = nitrogenFertilizerRatesList.toArray();

        List<Integer> atmosphericCO2List = (List<Integer>) config.get("atmosphericCO2");
        Object[] atmosphericCO2Values = atmosphericCO2List.toArray();

        Map<String, String> directories = (Map<String, String>) config.get("directory");
        String working = "." + separator + directories.get("working") + separator;
        DirectoryLayout layout = new DirectoryLayout(
                working,
                working + "weather" + separator + directories.get("weather") + separator,
                working + directories.get("source") + separator,
                working + directories.get("input") + separator,
                working + directories.get("threads") + separator,
                working + directories.get("result") + separator,
                working + directories.get("temp") + separator + directories.get("summary") + separator,
                working + directories.get("temp") + separator + directories.get("flowering") + separator,
                working + directories.get("temp") + separator + directories.get("plantingDates") + separator,
                working + directories.get("temp") + separator + directories.get("errors") + separator
        );

        Map<String, Integer> scenarioSwitches = (Map<String, Integer>) config.get("scenarioSwitch");
        boolean[] switchScenarios = new boolean[7];
        switchScenarios[0] = scenarioSwitches.get("waterManagement") > 0;
        switchScenarios[1] = scenarioSwitches.get("fertilizer") > 0;
        switchScenarios[2] = scenarioSwitches.get("manure") > 0;
        switchScenarios[3] = scenarioSwitches.get("residue") > 0;
        switchScenarios[4] = scenarioSwitches.get("plantingWindow") > 0;
        switchScenarios[5] = scenarioSwitches.get("plantingDensity") > 0;
        switchScenarios[6] = scenarioSwitches.get("CO2fertilization") > 0;

        Map<String, Integer> plantingDateOptions = (Map<String, Integer>) config.get("plantingDateOptions");
        boolean useFixedPlantingDate = plantingDateOptions.get("useFixedPlantingDate") > 0;
        int fixedPlantingDate = plantingDateOptions.get("fixedPlantingDate");
        int firstPlantingYear = (int)config.get("firstPlantingYear");
        int numberOfYears = (int)config.get("numberOfYears");
        int latBandSize = config.containsKey("latBandSize") ? (int) config.get("latBandSize") : 10;

        return new TapitiConfig(
                tableNameUnitInformation,
                countryCode,
                soilFileName,
                numberOfThreads,
                limitForDebugging,
                scenarioCombinations,
                useRecommendedNitrogenFertilizerRateOverride,
                useRecordedWaterSupplyOverride,
                useActualNitrogenRate,
                riceSystem,
                initialSoilNH4ppm,
                initialSoilNO3ppm,
                somModel,
                simulationStartDaysBeforePlanting,
                initialSoilWaterFraction,
                nitrogenFertilizerRates,
                atmosphericCO2Values,
                layout,
                switchScenarios,
                useFixedPlantingDate,
                fixedPlantingDate,
                firstPlantingYear,
                numberOfYears,
                latBandSize
        );
    }
}


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


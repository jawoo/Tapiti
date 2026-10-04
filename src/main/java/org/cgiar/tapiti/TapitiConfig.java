package org.cgiar.tapiti;

public record TapitiConfig(
        String tableNameUnitInformation,
        String countryCode,
        String soilFileName,
        int numberOfThreads,
        int limitForDebugging,
        boolean scenarioCombinations,
        boolean useRecommendedNitrogenFertilizerRateOverride,
        boolean useRecordedWaterSupplyOverride,
        boolean useActualNitrogenRate,
        String riceSystem,
        double initialSoilNH4ppm,
        double initialSoilNO3ppm,
        String somModel,
        int simulationStartDaysBeforePlanting,
        double initialSoilWaterFraction,
        Object[] nitrogenFertilizerRates,
        Object[] atmosphericCO2Values,
        DirectoryLayout directories,
        boolean[] switchScenarios,
        boolean useFixedPlantingDate,
        int fixedPlantingDate,
        int firstPlantingYear,
        int numberOfYears,
        int latBandSize
)
{
}
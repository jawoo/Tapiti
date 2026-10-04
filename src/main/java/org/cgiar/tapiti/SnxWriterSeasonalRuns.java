package org.cgiar.tapiti;

// Java utilities
import java.text.DecimalFormat;
import java.util.Map;
import java.util.TreeMap;

// SnxWriterSeasonalRuns class
public class SnxWriterSeasonalRuns 
{
    static DecimalFormat dfTT    = new DecimalFormat("00");
    static DecimalFormat dfDDD   = new DecimalFormat("000");
    static DecimalFormat dfXYCRD = new DecimalFormat("+000.00;-000.00");

    /**
     * V6 (6-leaf) date for the N sidedress, expressed as a fraction of the
     * planting-to-anthesis interval (days-to-flowering). V6 is thermal-time
     * driven, so a fraction of the season length tracks it across climates
     * better than a fixed calendar offset: for a typical corn dtf of ~65-70 d
     * this lands the sidedress near 30 days after planting (~V6). Tune here;
     * the flowering-date CSV records the same value. */
    public static final double V6_FRACTION_OF_DTF = 0.45;

    /** Days from planting to the ~V6 sidedress, derived from days-to-flowering. */
    public static int daysToV6(int daysToFlowering)
    {
        return (int) Math.round(daysToFlowering * V6_FRACTION_OF_DTF);
    }

    public static void runningTreatmentPackages(
            Object[] o,
            String waterManagement,
            int nRate,
            int manureRate,
            Object[] cultivarOption,
            int daysToFlowering,
            int daysToHarvest,
            String pdensityOption,
            int residueHarvestPct,
            int co2,
            String weatherFileName,
            int pdate,
            String label,
            int simYear
            ) throws InterruptedException {

        // Thread ID?
        int threadID = Integer.parseInt(Thread.currentThread().getName());

        // YY — two-digit year for this simulation year
        String yy = String.valueOf(simYear).substring(2);

        // Unit information
        String soilProfileID = (String)o[4];
        int soilRootingDepth = (Integer)o[6];
        double x = (double)o[2];
        double y = (double)o[3];

        // Cultivar code
        String cropCode = ((String)cultivarOption[1]);

        // Boolean switches
        boolean isRice = cropCode.equals("RI");
        boolean isWheat = cropCode.equals("WH");
        boolean isIrrigated = waterManagement.equals("I");
        // Upland rice: direct dry-seeded. Rainfed upland rice is treated like any other rainfed
        // crop (no irrigation level, rainfed initial conditions); irrigated upland rice is
        // bunded and flooded from 25 days after planting. Paddy (the default) keeps the
        // transplanted/puddled template below.
        boolean upland = isRice && App.riceSystem.equals("upland");

        // Treatments
        String snxSectionTreatments = """

*TREATMENTS                        -------------FACTOR LEVELS------------
@N R O C TNAME.................... CU FL SA IC MP MI MF MR MC MT ME MH SM
""";
        String mi = "0", mf = "0", mr = "0", mh = "0";
        if (isIrrigated || (isRice && !upland)) mi = "2";
        if (nRate>0) mf = "1";
        if (manureRate>0) mr = "1";
        if (residueHarvestPct<100 || isWheat) mh = "1";

        // Fields
        String idField = cultivarOption[1]+(String)cultivarOption[2];
        String snxSectionFieldLevel1 = "\n*FIELDS\n@L ID_FIELD WSTA....  FLSA  FLOB  FLDT  FLDD  FLDS  FLST SLTX  SLDP  ID_SOIL    FLNAME\n";
        String snxSectionFieldLevel2 = "@L ...........XCRD ...........YCRD .....ELEV .............AREA .SLEN .FLWR .SLAS FLHST FHDUR\n";

        // Fertilizer. Second split is applied at the ~V6 sidedress (derived from
        // days-to-flowering) rather than at flowering, matching mainstream U.S.
        // corn practice (planting + V6 sidedress).
        int splitFertilizerDate = daysToV6(daysToFlowering);
        int splitFertilizerRate = nRate/2;
        String snxSectionFertilizer = """

*FERTILIZERS (INORGANIC)
@F FDATE  FMCD  FACD  FDEP  FAMN  FAMP  FAMK  FAMC  FAMO  FOCD FERNAME
 1     1 FE001 AP001    10   %s     0     0     0     0   -99 -99
 1   %s FE001 AP001    10   %s     0     0     0     0   -99 -99
""".formatted(dfDDD.format(splitFertilizerRate), dfDDD.format(splitFertilizerDate), dfDDD.format(splitFertilizerRate));

        // Filling the treatment and field sections
        int tn = 1;

        // Planting
        StringBuilder snxSectionPlantingDetails = new StringBuilder("""

*PLANTING DETAILS
@P PDATE EDATE  PPOP  PPOE  PLME  PLDS  PLRS  PLRD  PLDP  PLWT  PAGE  PENV  PLPH  SPRL                        PLNAME
""");

        // Irrigation
        StringBuilder snxSectionIrrigation = new StringBuilder("\n*IRRIGATION AND WATER MANAGEMENT\n");
        boolean irrigationSectionWritten = false;

        // Batch
        StringBuilder batch = new StringBuilder("""
$BATCH(SEQUENCE)

@FILEX                                                                                        TRTNO     RP     SQ     OP     CO
""");

        snxSectionTreatments +=
                dfTT.format(tn)+" 1 0 0 "+label+"                 1 "+dfTT.format(tn)+"  0  1 "+dfTT.format(tn)+"  "+mi+"  "+mf+"  "+mr+"  0  0  1  "+mh+"  1\n";

        snxSectionFieldLevel1 +=
                dfTT.format(tn)+" "+idField+" "+"WEATHERS"+"   -99     0 IB000     0     0 00000 -99    180  "+soilProfileID+" -99\n";

        snxSectionFieldLevel2 +=
                dfTT.format(tn)+"         "+dfXYCRD.format(x)+"         "+dfXYCRD.format(y)+"         0                 0     0     0     0 FH102    30\n";

        // Planting Details
        String pdt = dfDDD.format(pdate);

        // Planting density
        String plantingDensity;

        // Low vs high density. cultivarOption[4] = full recorded density (high),
        // [5] = half (low). Labels now match the values they write.
        if (String.valueOf(pdensityOption).equals("DL"))
            plantingDensity = dfDDD.format((int)cultivarOption[5]);   // DL = low = half
        else
            plantingDensity = dfDDD.format((int)cultivarOption[4]);   // DH = high = full recorded

        // Rice vs non-rice
        if (isRice && !upland)
            snxSectionPlantingDetails.append(dfTT.format(tn)).append(" ").append(yy).append(pdt).append("   -99   ").append(plantingDensity).append("   ").append(plantingDensity).append("     T     H    20     0     2     0    23    25     3     0                        -99\n");
        else if (isRice)   // upland: dry seed (PLME S) in 20 cm rows (PLDS R), 3 cm deep
            snxSectionPlantingDetails.append(dfTT.format(tn)).append(" ").append(yy).append(pdt).append("   -99   ").append(plantingDensity).append("   ").append(plantingDensity).append("     S     R    20     0     3   -99   -99   -99   -99     0                        -99\n");
        else
            snxSectionPlantingDetails.append(dfTT.format(tn)).append(" ").append(yy).append(pdt).append("   -99   ").append(plantingDensity).append("   ").append(plantingDensity).append("     S     R    61     0     7   -99   -99   -99   -99     0                        -99\n");

        // Irrigation
        if (!irrigationSectionWritten)
        {
            if (isRice && !upland)
            {
                if (daysToFlowering>10)
                {
                    snxSectionIrrigation.append("""
@I  EFIR  IDEP  ITHR  IEPT  IOFF  IAME  IAMT IRNAME
 1     1   -99   -99   -99   -99   -99   -99 -99
@I IDATE  IROP IRVAL
 1 %s%s IR008     2
 1 %s%s IR010     0
 1 %s%s IR009   150
 1 %s%s IR003    30
@I  EFIR  IDEP  ITHR  IEPT  IOFF  IAME  IAMT IRNAME
 2     1   -99   -99   -99   -99   -99   -99 -99
@I IDATE  IROP IRVAL
 2 %s%s IR008     2
 2 %s%s IR010     0
 2 %s%s IR009   150
 2 %s%s IR011    30
""".formatted(yy, pdt, yy, pdt, yy, pdt, yy, pdt, yy, pdt, yy, pdt, yy, pdt, yy, pdt));

                    // Paddy rice setting
                    /*
                    ! Irrigation Codes: IRRCOD
                    ! 1:  Furrow irrigation of specified amount (mm)
                    ! 2:  Alternating furrows; irrigation of specified amount (mm)
                    ! 3:  Flood irrigation of specified amount (mm)
                    ! 4:  Sprinkler irrigation of specified amount (mm)
                    ! 5:  Drip or trickle irrigation of specified amount (mm)
                    ! 6:  Single irrigation to specified total flood depth (mm)
                    ! 7:  Water table depth (cm)
                    ! 8:  Percolation rate (mm/d)
                    ! 9:  Bund height (mm)
                    ! 10: Puddling (Puddled if IRRCOD = 10 record is present)
                    ! 11: Maintain constant specified flood depth (mm)
                    */
                    int floodIrrigationDate = pdate + 5;
                    String irrigationYear = yy;
                    if (floodIrrigationDate>365)
                    {
                        floodIrrigationDate = floodIrrigationDate - 365;
                        irrigationYear = String.valueOf(Integer.parseInt(yy)+1);
                    }
                    if (isIrrigated)
                    {
                        snxSectionIrrigation.append(" 2 ").append(irrigationYear).append(dfDDD.format(floodIrrigationDate)).append(" IR011   100\n");   // Maintain constant specified flood depth (mm)
                    }
                    else
                    {
                        snxSectionIrrigation.append(" 2 ").append(irrigationYear).append(dfDDD.format(floodIrrigationDate)).append(" IR003   100\n");   // Flood irrigation of specified amount (mm)
                    }
                }
            }
            else if (isRice && isIrrigated)
            {
                // Upland irrigated rice: direct-seeded into a puddled, bunded field and kept under
                // a constant 100 mm flood from 25 days after planting until maturity. DSSAT's
                // Flood_Irrig.for refuses IROP 11 (constant depth) unless IROP 9 (bund) and 10
                // (puddled) are also present, so puddling stays in; what distinguishes this from
                // the paddy template is dry direct seeding and the later flood.
                int floodStart = pdate + 25;
                String floodYear = yy;
                if (floodStart>365)
                {
                    floodStart = floodStart - 365;
                    floodYear = String.valueOf(Integer.parseInt(yy)+1);
                }
                snxSectionIrrigation.append("""
@I  EFIR  IDEP  ITHR  IEPT  IOFF  IAME  IAMT IRNAME
 1     1   -99   -99   -99   -99   -99   -99 -99
@I IDATE  IROP IRVAL
 1 %s%s IR008     2
 1 %s%s IR010     0
 1 %s%s IR009   150
@I  EFIR  IDEP  ITHR  IEPT  IOFF  IAME  IAMT IRNAME
 2     1   -99   -99   -99   -99   -99   -99 -99
@I IDATE  IROP IRVAL
 2 %s%s IR008     2
 2 %s%s IR010     0
 2 %s%s IR009   150
 2 %s%s IR011   100
""".formatted(yy, pdt, yy, pdt, yy, pdt, yy, pdt, yy, pdt, yy, pdt, floodYear, dfDDD.format(floodStart)));
            }
            else
            {
                snxSectionIrrigation.append("""
@I  EFIR  IDEP  ITHR  IEPT  IOFF  IAME  IAMT IRNAME
 1     1   -99   -99   -99   -99   -99   -99 -99
@I IDATE  IROP IRVAL
 1   %s IR001   100
@I  EFIR  IDEP  ITHR  IEPT  IOFF  IAME  IAMT IRNAME
 2     1   -99   -99   -99   -99   -99   -99 -99
@I IDATE  IROP IRVAL
""".formatted(dfDDD.format(1)));

                // When to irrigate?
                TreeMap<Integer, Integer> irrigation = new TreeMap<>();
                irrigation.put(1, 10);   // Planting date

                if (daysToFlowering>10)
                {
                    irrigation.put(daysToFlowering-1, 15);
                    irrigation.put(daysToFlowering,   15);  // Irrigation through the critical flowering window
                    irrigation.put(daysToFlowering+3, 15);
                }

                // Additional supplementary irrigation after flowering
                for (int d=daysToFlowering+10; d<daysToHarvest-20; d=d+5)
                    irrigation.put(d, 10);

                for (Map.Entry<Integer, Integer> entry : irrigation.entrySet())
                    snxSectionIrrigation.append(" 2   ").append(dfDDD.format(entry.getKey())).append(" IR001   ").append(dfDDD.format(entry.getValue())).append("\n");

            }
        }

        // Batch file
        batch.append("TOUCAN").append(dfTT.format(threadID)).append(".SNX                                                                                     ").append(dfTT.format(tn)).append("      1      0      0      0\n");

        // Initial Conditions
        // Simulation / initial-conditions date: 1 Jan of the sowing year, or N days before planting.
        int startDoy = App.simulationStartDaysBeforePlanting > 0 ? Math.max(1, pdate - App.simulationStartDaysBeforePlanting) : 1;
        String icdat = yy + dfDDD.format(startDoy);
        String icbl = dfDDD.format(soilRootingDepth);
        String icwd = dfDDD.format(soilRootingDepth/2);
        String snh4 = String.format("%5s", App.initialSoilNH4ppm < 0.01 ? ".001" : String.format("%.1f", App.initialSoilNH4ppm));
        String sno3 = String.format("%5s", App.initialSoilNO3ppm < 0.01 ? ".001" : String.format("%.1f", App.initialSoilNO3ppm));
        boolean wetStart = isIrrigated || (isRice && !upland);
        String snxSectionInitialConditions = "\n*INITIAL CONDITIONS\n";
        snxSectionInitialConditions += """
                @C   PCR ICDAT  ICRT  ICND  ICRN  ICRE  ICWD ICRES ICREN ICREP ICRIP ICRID ICNAME
                 1    FA %s   100     0     1     1   %s  1000    .8     0   100    15 -99
                @C  ICBL  SH2O  SNH4  SNO3
                """.formatted(icdat, wetStart ? icwd : "-99");
        if (App.initialSoilWaterFraction >= 0)
        {
            // Per-layer initial water from the cell's own soil profile (o[5]): SLLL + f*(SDUL-SLLL)
            // for rainfed, SDUL for irrigated/paddy. Layers down to the rooting depth.
            String[] lines = ((String) o[5]).split("\\r?\\n");
            boolean inLayers = false; int written = 0;
            for (String ln : lines)
            {
                if (ln.startsWith("@  SLB")) { inLayers = true; continue; }
                if (!inLayers || ln.trim().isEmpty()) continue;
                if (ln.startsWith("@") || ln.startsWith("*")) break;
                String[] t = ln.trim().split("\\s+");
                if (t.length < 5) continue;
                int slb = (int) Double.parseDouble(t[0]);
                double slll = Double.parseDouble(t[2]), sdul = Double.parseDouble(t[3]);
                if (slb > soilRootingDepth && written > 0) break;
                double sh2o = wetStart ? sdul : slll + App.initialSoilWaterFraction * (sdul - slll);
                snxSectionInitialConditions += String.format(" 1   %3d %5.3f %s %s\n", Math.min(slb, soilRootingDepth), sh2o, snh4, sno3);
                written++;
            }
            if (written == 0)   // malformed profile: fall back to the single-layer line
                snxSectionInitialConditions += String.format(" 1   %s %5s %s %s\n", icbl, wetStart ? ".500" : ".250", snh4, sno3);
        }
        else
            snxSectionInitialConditions += String.format(" 1   %s %5s %s %s\n", icbl, wetStart ? ".500" : ".250", snh4, sno3);

        // Environment modifications
        String snxSectionEnvironmentModification = """

*ENVIRONMENT MODIFICATIONS
@E ODATE EDAY  ERAD  EMAX  EMIN  ERAIN ECO2  EDEW  EWIND ENVNAME  
 1 %s A 0.0 A 0.0 A   0 A   0 A 0.0 R %s A   0 A   0 
""".formatted(icdat, co2);

        // Harvest details
        int harvestDate = 365;
        if (harvestDate<1) harvestDate = 1;
        String hd = dfDDD.format(harvestDate);
        String hbpc = dfDDD.format(residueHarvestPct);
        String harvestYear = dfTT.format(Integer.parseInt(yy) + 1);
        String snxHarvest = """

*HARVEST DETAILS
@H HDATE  HSTG  HCOM HSIZE   HPC  HBPC HNAME
 1 %s%s GS000   -99   -99   100   %s
 2 %s%s GS000   -99   -99   100   %s
""".formatted(harvestYear, hd, hbpc, harvestYear, hd, hbpc);

        // Organic amendment
        String snxSectionManure = """

*RESIDUES AND ORGANIC FERTILIZER
@R RDATE  RCOD  RAMT  RESN  RESP  RESK  RINP  RDEP  RMET RENAME
 1 %s001 RE003  1000   1.4    .2  2.38    20    15 AP003 -99
""".formatted(yy);

        // Simulation controls
        String irrig = "D";  if (isRice && (!upland || isIrrigated))  irrig = "R";
        String harvs = "M";  if (isWheat) harvs = "R";
        String mesom = App.somModel.equals("ceres") ? "G" : "P";   // MESOM: CENTURY (P) or Godwin/CERES (G) soil organic matter
        String nyers = "01";  // one season per DSSAT call; year loop is in ThreadSeasonalRuns
        String snxSectionSimulationControls = """

*SIMULATION CONTROLS
@N GENERAL     NYERS NREPS START SDATE RSEED SNAME.................... SMODEL
 1 GE             %s     1     S %s  4537 CROP
@N OPTIONS     WATER NITRO SYMBI PHOSP POTAS DISES  CHEM  TILL   CO2
 1 OP              Y     Y     Y     N     N     N     N     N     D
@N METHODS     WTHER INCON LIGHT EVAPO INFIL PHOTO HYDRO NSWIT MESOM MESEV MESOL
 1 ME              G     M     E     R     S     C     R     1     %s     S     2
@N MANAGEMENT  PLANT IRRIG FERTI RESID HARVS
 1 MA              R     %s     D     R     %s
@N OUTPUTS     FNAME OVVEW SUMRY FROPT GROUT CAOUT WAOUT NIOUT MIOUT DIOUT VBOSE CHOUT OPOUT FMOPT
 1 OU              N     N     Y     3     N     N     N     N     N     N     0     N     N     C
@  AUTOMATIC MANAGEMENT
@N PLANTING    PFRST PLAST PH2OL PH2OU PH2OD PSTMX PSTMN
 1 PL          %s %s    40   100    30    40    10
@N IRRIGATION  IMDEP ITHRL ITHRU IROFF IMETH IRAMT IREFF
 1 IR             30    70   100 IB001 IB001    20   .75
@N NITROGEN    NMDEP NMTHR NAMNT NCODE NAOFF
 1 NI             30    50    25 IB001 IB001
@N RESIDUES    RIPCN RTIME RIDEP
 1 RE            100     1    20
@N HARVEST     HFRST HLAST HPCNP HPCNR
 1 HA              0 79065   100   %s
@N GENERAL     NYERS NREPS START SDATE RSEED SNAME.................... SMODEL
 2 GE              1     1     S %s  2150 FALLOW
@N OPTIONS     WATER NITRO SYMBI PHOSP POTAS DISES  CHEM  TILL   CO2
 2 OP              Y     Y     Y     N     N     N     N     N     D
@N METHODS     WTHER INCON LIGHT EVAPO INFIL PHOTO HYDRO NSWIT MESOM MESEV MESOL
 2 ME              G     M     E     R     S     C     R     1     %s     S     2
@N MANAGEMENT  PLANT IRRIG FERTI RESID HARVS
 2 MA              R     N     N     R     R
@N OUTPUTS     FNAME OVVEW SUMRY FROPT GROUT CAOUT WAOUT NIOUT MIOUT DIOUT VBOSE CHOUT OPOUT FMOPT
 2 OU              Y     N     A     5     N     N     N     N     N     N     N     N     N     A
@  AUTOMATIC MANAGEMENT
@N PLANTING    PFRST PLAST PH2OL PH2OU PH2OD PSTMX PSTMN
 2 PL          75169 75183    40   100    30    40    10
@N IRRIGATION  IMDEP ITHRL ITHRU IROFF IMETH IRAMT IREFF
 2 IR             30    70   100 IB001 IB001    20   .75
@N NITROGEN    NMDEP NMTHR NAMNT NCODE NAOFF
 2 NI             30    50    25 IB001 IB001
@N RESIDUES    RIPCN RTIME RIDEP
 2 RE            100     1    20
@N HARVEST     HFRST HLAST HPCNP HPCNR
 2 HA              0 79065   100   %s
""".formatted(nyers, icdat, mesom, irrig, harvs, icdat, icdat, hbpc, icdat, mesom, hbpc);

        // SNX
        String snx = "*EXP.DETAILS: TOUCAN"+dfTT.format(threadID)+"SN SEASONAL RUNS\n" +
                "\n" +
                "*GENERAL\n" +
                "\n" +
                snxSectionTreatments +
                "\n*CULTIVARS\n" +
                "@C CR INGENO CNAME\n" +
                " 1 "+cultivarOption[1]+" "+cultivarOption[2]+" "+cultivarOption[3]+"\n" +
                snxSectionFieldLevel1 +
                snxSectionFieldLevel2 +
                snxSectionInitialConditions +
                snxSectionPlantingDetails +
                snxSectionFertilizer +
                snxSectionIrrigation +
                snxSectionManure +
                snxSectionEnvironmentModification +
                snxHarvest +
                snxSectionSimulationControls;

        // Write
        String snxFile = App.directoryThreads+"T"+threadID+App.d+"TOUCAN"+dfTT.format(threadID)+".SNX";
        Utility.writeFile(snxFile, snx);

        // Write
        String batchFile = App.directoryThreads+"T"+threadID+App.d+"DSSBatch.v48";
        Utility.writeFile(batchFile, batch.toString());

    }    

}

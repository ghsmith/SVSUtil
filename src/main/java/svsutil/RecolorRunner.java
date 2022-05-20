package svsutil;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.imaging.icc.IccProfileInfo;
import org.apache.commons.imaging.icc.IccProfileParser;
import org.apache.commons.imaging.icc.IccTag;
import org.apache.commons.imaging.icc.IccTagTypes;
import org.la4j.Matrix;
import org.la4j.inversion.NoPivotGaussInverter;
import org.la4j.matrix.DenseMatrix;
import svsutil.jmist.Interpolater;

/**
 *
 * @author geoffrey.smith@emory.edu
 */
abstract class RecolorRunner implements Runnable {

    static Logger logger = Logger.getLogger(RecolorRunner.class.getName());    

    public SVSFile svsFile;
    public int threads;
    public int quality;
    public int skip;

    public RecolorRunner(SVSFile svsFile, int threads, int quality, int skip) throws InterruptedException {
        this.svsFile = svsFile;
        this.threads = threads;
        this.quality = quality;
        this.skip = skip;
        synchronized(svsFile) {

            if(svsFile.lutComputed) {
                return;
            }
            
            int[][][][] lutICC; // "raw" ICC lut for scanner (0-0xffff)
            float[][][][] lut; // scanner-to-monitor LUT (0-1)
            int cubeSize = -1; // the 3D LUT dimension in the scanner ICC
            int normalizer = -1; // the maximum RGB value at the white point in the scanner ICC file

            logger.log(Level.INFO, String.format("extracting ICC profile from SVS file to create 3D LUT for color correction using A2B1 tag and Windows standard RGB monitor profile"));
            
            // 1. extract LUT from scanner ICC profile in SVS file
            {
                logger.log(Level.INFO, String.format("IF ACTUAL VALUES DO NOT EQUAL EXPECTED VALUES, THIS THIS PROBABLY WON'T WORK!"));
                IccProfileParser ipp = new IccProfileParser();
                IccProfileInfo ipi = ipp.getICCProfileInfo(svsFile.iccBytes);
                IccTag a2b1Tag = null;
                for(int x = 0; x < ipi.getTags().length; x++) {
                    IccTag it = ipi.getTags()[x];
                    if(IccTagTypes.valueOf("A_TO_B1_TAG").equals(it.fIccTagType)) {
                        a2b1Tag = ipi.getTags()[x];
                    }
                }
                logger.log(Level.INFO, String.format("ICC CLUT header (expecting 'mft2')      : %s %s %s %s", (char)svsFile.iccBytes[a2b1Tag.offset + 0], (char)svsFile.iccBytes[a2b1Tag.offset + 1], (char)svsFile.iccBytes[a2b1Tag.offset + 2], (char)svsFile.iccBytes[a2b1Tag.offset + 3]));
                logger.log(Level.INFO, String.format("ICC CLUT input channels (expecting 3)   : %d", svsFile.iccBytes[a2b1Tag.offset + 8]));
                logger.log(Level.INFO, String.format("ICC CLUT output channels (expecting 3)  : %d", svsFile.iccBytes[a2b1Tag.offset + 9]));
                cubeSize = (int)svsFile.iccBytes[a2b1Tag.offset + 10];
                logger.log(Level.INFO, String.format("ICC CLUT cube size (expecting 103)      : %d", cubeSize));
                for(int x = 0; x < 36; x+=12) {
                    logger.log(Level.INFO, String.format("ICC matrix row %d (expecting identity)   : %f %f %f", x / 12,
                        ByteUtil.bytesToFloatMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + 12 + x, a2b1Tag.offset + 16 + x)),
                        ByteUtil.bytesToFloatMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + 16 + x, a2b1Tag.offset + 20 + x)),
                        ByteUtil.bytesToFloatMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + 20 + x, a2b1Tag.offset + 24 + x))
                    ));
                }
                logger.log(Level.INFO, String.format("ICC CLUT input table size (expecting 2) : %d", ByteUtil.bytesToIntMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + 48, a2b1Tag.offset + 50))));
                logger.log(Level.INFO, String.format("ICC CLUT output table size (expecting 2): %d", ByteUtil.bytesToIntMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + 50, a2b1Tag.offset + 52))));
                for(int x = 0; x < 12; x+=4) {
                    logger.log(Level.INFO, String.format("ICC CLUT input table row %d (expecting 0 - 65535) : %d %d", x / 4,
                        ByteUtil.bytesToIntMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + 52 + x, a2b1Tag.offset + 54 + x)),
                        ByteUtil.bytesToIntMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + 54 + x, a2b1Tag.offset + 56 + x))
                    ));
                }
                for(int x = 0; x < 12; x+=4) {
                    logger.log(Level.INFO, String.format("ICC CLUT output table row %d (expecting 0 - 65535): %d %d", x / 4,
                        ByteUtil.bytesToIntMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + ((int)Math.pow(cubeSize, 3) * 3 * 2) + 64 + x, a2b1Tag.offset + ((int)Math.pow(cubeSize, 3) * 3 * 2) + 66 + x)),
                        ByteUtil.bytesToIntMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + ((int)Math.pow(cubeSize, 3) * 3 * 2) + 66 + x, a2b1Tag.offset + ((int)Math.pow(cubeSize, 3) * 3 * 2) + 68 + x))
                    ));
                }
                int rrMax = -1;
                int ggMax = -1;
                int bbMax = -1;
                int r = 0;
                int g = 0;
                int b = 0;
                lutICC = new int[cubeSize][cubeSize][cubeSize][3];
                for(int x = 0; x < (int)Math.pow(cubeSize, 3) * 3 * 2; x += 6) {
                    int rr = ByteUtil.bytesToIntMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + 64 + x, a2b1Tag.offset + 66 + x));
                    int gg = ByteUtil.bytesToIntMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + 66 + x, a2b1Tag.offset + 68 + x));
                    int bb = ByteUtil.bytesToIntMSF(Arrays.copyOfRange(svsFile.iccBytes, a2b1Tag.offset + 68 + x, a2b1Tag.offset + 70 + x));
                    if(rr > rrMax) { rrMax = rr; }
                    if(gg > ggMax) { ggMax = gg; }
                    if(bb > bbMax) { bbMax = bb; }
                    lutICC[r][g][b][svsFile.R] = rr;
                    lutICC[r][g][b][svsFile.G] = gg;
                    lutICC[r][g][b][svsFile.B] = bb;
                    if(++b == cubeSize) { b = 0; if(++g == cubeSize) { g = 0; ++r; } }
                }
                logger.log(Level.INFO, String.format("ICC CLUT un-normalized max RGB values   : %d %d %d ", rrMax, ggMax, bbMax));
                logger.log(Level.INFO, String.format("ICC CLUT un-normalized white RGB values : %d %d %d ", lutICC[cubeSize - 1][cubeSize - 1][cubeSize - 1][svsFile.R], lutICC[cubeSize - 1][cubeSize - 1][cubeSize - 1][svsFile.G], lutICC[cubeSize - 1][cubeSize - 1][cubeSize - 1][svsFile.B]));
                normalizer = (int)Math.max(Math.max(lutICC[cubeSize - 1][cubeSize - 1][cubeSize - 1][svsFile.R], lutICC[cubeSize - 1][cubeSize - 1][cubeSize - 1][svsFile.G]), lutICC[cubeSize - 1][cubeSize - 1][cubeSize - 1][svsFile.B]);
                logger.log(Level.INFO, String.format("ICC CLUT is normalized using value : %d", normalizer));
            }
                
            // 2. apply monitor ICC profile to create a scanner-to-monitor 3D LUT
            {
                logger.log(Level.INFO, String.format("computing 3D LUT with dimensions %d x %d x %d...", cubeSize, cubeSize, cubeSize));
                // these are the rXYZ (column 1), gXYZ (column 2) and bXYZ (column 3) vectors from the default "sRGB color space profile" that comes with Windows
                double[][] windowsRGBColorMatrix = new double[][] {
                    {0.43607, 0.38515, 0.14307},
                    {0.22249, 0.71687, 0.06061},
                    {0.01392, 0.09708, 0.71410}
                };
                // this is the rTRC/gTRC/bTRC curves (they are all the same) from the "sRGB color space profile" that comes with Windows
                int[] windowsRGBColorCurve = {
                    0,5,10,15,20,25,30,35,40,45,50,55,59,64,69,74,79,84,89,94,99,104,109,114,119,124,129,134,139,144,149,154,159,164,169,174,178,183,188,193,198,203,208,213,219,224,229,235,240,246,251,257,263,269,275,281,287,293,299,306,312,318,325,332,338,345,352,359,366,373,380,387,395,402,410,417,425,433,441,449,457,465,473,481,489,498,506,515,524,532,541,550,559,568,577,587,596,605,615,625,634,644,654,664,674,684,694,705,715,725,736,747,757,768,779,790,801,813,824,835,847,858,870,882,894,906,918,930,942,954,967,979,992,1004,1017,1030,1043,1056,1069,1083,1096,1109,1123,1137,1150,1164,1178,1192,1206,1220,1235,1249,1264,1278,1293,1308,1323,1338,1353,1368,1383,1399,1414,1430,1446,1461,1477,1493,1509,1526,1542,1558,1575,1591,1608,1625,1642,1659,1676,1693,1711,1728,1745,1763,1781,1799,1817,1835,1853,1871,1889,1908,1926,1945,1964,1983,2002,2021,2040,2059,2079,2098,2118,2138,2158,2178,2198,2218,2238,2258,2279,2299,2320,2341,2362,2383,2404,2425,2447,2468,2490,2511,2533,2555,2577,2599,2621,2644,2666,2689,2712,2734,2757,2780,2803,2827,2850,2873,2897,2921,2944,2968,2992,3016,3041,3065,3090,3114,3139,3164,3189,3214,3239,3264,3289,3315,3341,3366,3392,3418,3444,3470,3497,3523,3550,3576,3603,3630,3657,3684,3711,3739,3766,3794,3822,3849,3877,3905,3934,3962,3990,4019,4047,4076,4105,4134,4163,4193,4222,4251,4281,4311,4341,4371,4401,4431,4461,4492,4522,4553,4584,4615,4646,4677,4708,4740,4771,4803,4835,4867,4899,4931,4963,4995,5028,5061,5093,5126,5159,5193,5226,5259,5293,5326,5360,5394,5428,5462,5496,5531,5565,5600,5635,5670,5705,5740,5775,5810,5846,5882,5917,5953,5989,6025,6062,6098,6135,6171,6208,6245,6282,6319,6357,6394,6432,6469,6507,6545,6583,6621,6660,6698,6737,6775,6814,6853,6892,6932,6971,7011,7050,7090,7130,7170,7210,7250,7291,7331,7372,7413,7454,7495,7536,7577,7619,7660,7702,7744,7786,7828,7870,7913,7955,7998,8041,8084,8127,8170,8213,8257,8300,8344,8388,8432,8476,8520,8565,8609,8654,8699,8743,8789,8834,8879,8925,8970,9016,9062,9108,9154,9200,9247,9293,9340,9387,9434,9481,9528,9576,9623,9671,9719,9767,9815,9863,9911,9960,10008,10057,10106,10155,10204,10253,10303,10353,10402,10452,10502,10552,10603,10653,10704,10754,10805,10856,10907,10959,11010,11062,11113,11165,11217,11269,11321,11374,11426,11479,11532,11585,11638,11691,11745,11798,11852,11906,11959,12014,12068,12122,12177,12231,12286,12341,12396,12452,12507,12562,12618,12674,12730,12786,12842,12899,12955,13012,13069,13126,13183,13240,13297,13355,13413,13470,13528,13587,13645,13703,13762,13821,13879,13938,13998,14057,14116,14176,14236,14295,14356,14416,14476,14536,14597,14658,14719,14780,14841,14902,14964,15026,15087,15149,15211,15274,15336,15399,15461,15524,15587,15650,15713,15777,15840,15904,15968,16032,16096,16161,16225,16290,16354,16419,16484,16550,16615,16681,16746,16812,16878,16944,17010,17077,17143,17210,17277,17344,17411,17479,17546,17614,17682,17749,17818,17886,17954,18023,18091,18160,18229,18299,18368,18437,18507,18577,18647,18717,18787,18857,18928,18999,19069,19140,19212,19283,19354,19426,19498,19570,19642,19714,19786,19859,19932,20005,20078,20151,20224,20297,20371,20445,20519,20593,20667,20742,20816,20891,20966,21041,21116,21191,21267,21343,21418,21494,21570,21647,21723,21800,21877,21954,22031,22108,22185,22263,22340,22418,22496,22575,22653,22731,22810,22889,22968,23047,23126,23206,23285,23365,23445,23525,23605,23686,23766,23847,23928,24009,24090,24172,24253,24335,24417,24499,24581,24663,24746,24828,24911,24994,25077,25161,25244,25328,25411,25495,25579,25664,25748,25833,25917,26002,26087,26173,26258,26344,26429,26515,26601,26687,26774,26860,26947,27034,27121,27208,27295,27383,27471,27559,27647,27735,27823,27912,28000,28089,28178,28267,28356,28446,28536,28625,28715,28806,28896,28986,29077,29168,29259,29350,29441,29533,29624,29716,29808,29900,29992,30085,30177,30270,30363,30456,30550,30643,30737,30830,30924,31018,31113,31207,31302,31397,31492,31587,31682,31777,31873,31969,32065,32161,32257,32354,32450,32547,32644,32741,32839,32936,33034,33131,33229,33328,33426,33524,33623,33722,33821,33920,34019,34119,34219,34318,34418,34519,34619,34719,34820,34921,35022,35123,35225,35326,35428,35530,35632,35734,35836,35939,36042,36145,36248,36351,36454,36558,36662,36766,36870,36974,37078,37183,37288,37393,37498,37603,37709,37814,37920,38026,38132,38239,38345,38452,38559,38666,38773,38880,38988,39096,39204,39312,39420,39528,39637,39746,39855,39964,40073,40183,40292,40402,40512,40622,40733,40843,40954,41065,41176,41287,41398,41510,41622,41734,41846,41958,42070,42183,42296,42409,42522,42635,42749,42862,42976,43090,43204,43319,43433,43548,43663,43778,43893,44009,44124,44240,44356,44472,44589,44705,44822,44939,45056,45173,45290,45408,45526,45643,45762,45880,45998,46117,46236,46355,46474,46593,46713,46832,46952,47072,47193,47313,47434,47554,47675,47797,47918,48039,48161,48283,48405,48527,48650,48772,48895,49018,49141,49264,49388,49511,49635,49759,49883,50008,50132,50257,50382,50507,50632,50758,50883,51009,51135,51261,51388,51514,51641,51768,51895,52022,52150,52277,52405,52533,52661,52790,52918,53047,53176,53305,53434,53564,53694,53823,53953,54084,54214,54345,54475,54606,54737,54869,55000,55132,55264,55396,55528,55660,55793,55926,56059,56192,56325,56458,56592,56726,56860,56994,57129,57263,57398,57533,57668,57804,57939,58075,58211,58347,58483,58620,58756,58893,59030,59167,59305,59442,59580,59718,59856,59995,60133,60272,60411,60550,60689,60828,60968,61108,61248,61388,61528,61669,61810,61951,62092,62233,62375,62516,62658,62800,62942,63085,63227,63370,63513,63656,63800,63943,64087,64231,64375,64519,64664,64809,64954,65099,65244,65389,65535
                };
                DenseMatrix colorMatrix = DenseMatrix.from2DArray(windowsRGBColorMatrix);
                // either matrix inverter appears to work
                //GaussJordanInverter inverter = new GaussJordanInverter(colorMatrix);
                NoPivotGaussInverter inverter  = new NoPivotGaussInverter(colorMatrix);
                Matrix colorMatrixInverted = inverter.inverse();
                // invert the curve by swapping the x and y coordinates in 24-bit color (8 bits per color)
                int[] colorCurveInverted = new int[0x10000];
                Arrays.fill(colorCurveInverted, -1);
                for(int x = 0; x < windowsRGBColorCurve.length; x++) {
                    colorCurveInverted[windowsRGBColorCurve[x]] = (int)Math.round((1f * x / (windowsRGBColorCurve.length - 1) * (0xffff)));
                }
                // this interpolation logic assumes that index positions 0 and 2^16-1 have a value
                for(int x0 = 0; x0 < colorCurveInverted.length - 1; x0++) {
                    int x1 = x0 + 1;
                    while(colorCurveInverted[x1] == -1) {
                        x1++;
                    }
                    for(int y = x0 + 1; y < x1; y++) {
                        colorCurveInverted[y] = (int)Math.round(Interpolater.interpolate((double)colorCurveInverted[x0], (double)colorCurveInverted[x1], 1d * (y - x0) / (x1 - x0)));
                    }
                    x0 = x1 - 1; // sorry should have used a while instead of a for
                }
                //FileWriter cubeFile = new FileWriter("c:/scanner/lut_extracted_from_svs.cube");
                //PrintWriter cubeFilePrintWriter = new PrintWriter(cubeFile);
                //cubeFilePrintWriter.print(String.format("# extracted from SVS file\n\nLUT_3D_SIZE %d\n", cubeSize));
                lut = new float[cubeSize][cubeSize][cubeSize][3];
                for(int b = 0; b < cubeSize; b++) {
                    for(int g = 0; g < cubeSize; g++) {
                        for(int r = 0; r < cubeSize; r++) { // in a 3D LUT, red changes fastest - this is the opposite of the ICC profile
                            DenseMatrix PCSXYZ = DenseMatrix.from1DArray(3, 1, new double[] { 1d * lutICC[r][g][b][svsFile.R] / normalizer, 1d * lutICC[r][g][b][svsFile.G] / normalizer, 1d * lutICC[r][g][b][svsFile.B] / normalizer });
                            Matrix mRGBLinear = colorMatrixInverted.multiply(PCSXYZ);
                            lut[r][g][b][svsFile.R] = 1f * colorCurveInverted[(int)Math.round(Math.min(Math.max(mRGBLinear.getColumn(0).get(0), 0), 1) * 0xffff)] / 0xffff;
                            lut[r][g][b][svsFile.G] = 1f * colorCurveInverted[(int)Math.round(Math.min(Math.max(mRGBLinear.getColumn(0).get(1), 0), 1) * 0xffff)] / 0xffff;
                            lut[r][g][b][svsFile.B] = 1f * colorCurveInverted[(int)Math.round(Math.min(Math.max(mRGBLinear.getColumn(0).get(2), 0), 1) * 0xffff)] / 0xffff;
                            //cubeFilePrintWriter.print(String.format("%f %f %f\n", lut[r][g][b][svsFile.R], lut[r][g][b][svsFile.B], lut[r][g][b][svsFile.B]));
                        }
                    }
                }
                //cubeFilePrintWriter.close();
                //cubeFile.close();
                logger.log(Level.INFO, String.format("...done"));
            }
                
            // 3. upsample scanner-to-monitor 3D LUT to 256x256x256 (24-bit color)
            {
                logger.log(Level.INFO, String.format("upsampling 3D LUT to dimensions 256 x 256 x 256 in %d threads...", threads));
                class IndexDownsample {
                    public int index = -1;
                    public float indexDownsampleNotional = -1;
                    public int indexDownsampleActualPrior = -1;
                    public int indexDownsampleActualNext = -1;
                    public float normalizedInterval = -1;
                    public boolean requiresInterpolation = false;
                    @Override
                    public String toString() {
                        return "IndexDownsample{" + "index=" + index + ", indexDownsampleNotional=" + indexDownsampleNotional + ", indexDownsampleActualPrior=" + indexDownsampleActualPrior + ", indexDownsampleActualNext=" + indexDownsampleActualNext + ", normalizedInterval=" + normalizedInterval + ", requiresInterpolation=" + requiresInterpolation + '}';
                    }
                }
                Map<Integer, IndexDownsample> indexDownsampleMap = new LinkedHashMap<>();
                for(int x = 0; x < 0x100; x++) {
                    IndexDownsample indexDownsample = new IndexDownsample();
                    indexDownsampleMap.put(x, indexDownsample);
                    indexDownsample.index = x;
                    indexDownsample.indexDownsampleNotional = x * (1f * (cubeSize - 1) / 0xff);
                    indexDownsample.requiresInterpolation = !(indexDownsample.indexDownsampleNotional == Math.round(indexDownsample.indexDownsampleNotional));
                    if(indexDownsample.requiresInterpolation) {
                        indexDownsample.indexDownsampleActualPrior = (int)Math.floor(indexDownsample.indexDownsampleNotional);
                        indexDownsample.indexDownsampleActualNext = (int)Math.ceil(indexDownsample.indexDownsampleNotional);
                        indexDownsample.normalizedInterval = 
                            ((1f * x) - (1f * indexDownsample.indexDownsampleActualPrior * 0xff / (cubeSize - 1)))
                            / ((1f * indexDownsample.indexDownsampleActualNext * 0xff / (cubeSize - 1)) - (1f * indexDownsample.indexDownsampleActualPrior * 0xff / (cubeSize - 1)));
                    }
                    else {
                        indexDownsample.indexDownsampleActualPrior = (int)indexDownsample.indexDownsampleNotional;
                        indexDownsample.indexDownsampleActualNext = (int)(x < 0xff ? indexDownsample.indexDownsampleNotional + 1 : indexDownsample.indexDownsampleNotional);
                        indexDownsample.normalizedInterval = 0;
                    }
                }
                class UpsampleRunner implements Runnable {
                    public int rStart = -1;
                    public int rIncrement = -1;
                    public UpsampleRunner(int rStart, int rIncrement) {
                        this.rStart = rStart;
                        this.rIncrement = rIncrement;
                    }
                    @Override
                    public void run() {
                        for(int b = 0; b < 0x100; b++) {
                            for(int g = 0; g < 0x100; g++) {
                                for(int r = rStart; r < 0x100; r += rIncrement) { // in a 3D LUT, red changes fastest - this is the opposite of the ICC profile
                                    float[] v000 = lut[indexDownsampleMap.get(r).indexDownsampleActualPrior][indexDownsampleMap.get(g).indexDownsampleActualPrior][indexDownsampleMap.get(b).indexDownsampleActualPrior];
                                    float[] v100 = lut[indexDownsampleMap.get(r).indexDownsampleActualNext ][indexDownsampleMap.get(g).indexDownsampleActualPrior][indexDownsampleMap.get(b).indexDownsampleActualPrior];
                                    float[] v010 = lut[indexDownsampleMap.get(r).indexDownsampleActualPrior][indexDownsampleMap.get(g).indexDownsampleActualNext ][indexDownsampleMap.get(b).indexDownsampleActualPrior];
                                    float[] v110 = lut[indexDownsampleMap.get(r).indexDownsampleActualNext ][indexDownsampleMap.get(g).indexDownsampleActualNext ][indexDownsampleMap.get(b).indexDownsampleActualPrior];
                                    float[] v001 = lut[indexDownsampleMap.get(r).indexDownsampleActualPrior][indexDownsampleMap.get(g).indexDownsampleActualPrior][indexDownsampleMap.get(b).indexDownsampleActualNext ];
                                    float[] v101 = lut[indexDownsampleMap.get(r).indexDownsampleActualNext ][indexDownsampleMap.get(g).indexDownsampleActualPrior][indexDownsampleMap.get(b).indexDownsampleActualNext ];
                                    float[] v011 = lut[indexDownsampleMap.get(r).indexDownsampleActualPrior][indexDownsampleMap.get(g).indexDownsampleActualNext ][indexDownsampleMap.get(b).indexDownsampleActualNext ];
                                    float[] v111 = lut[indexDownsampleMap.get(r).indexDownsampleActualNext ][indexDownsampleMap.get(g).indexDownsampleActualNext ][indexDownsampleMap.get(b).indexDownsampleActualNext ];
                                    svsFile.lutUpsampled[r][g][b][svsFile.R] = (int)(0xff * Interpolater.trilinearInterpolate(v000[svsFile.R], v100[svsFile.R], v010[svsFile.R], v110[svsFile.R], v001[svsFile.R], v101[svsFile.R], v011[svsFile.R], v111[svsFile.R], indexDownsampleMap.get(r).normalizedInterval, indexDownsampleMap.get(g).normalizedInterval, indexDownsampleMap.get(b).normalizedInterval));
                                    svsFile.lutUpsampled[r][g][b][svsFile.G] = (int)(0xff * Interpolater.trilinearInterpolate(v000[svsFile.G], v100[svsFile.G], v010[svsFile.G], v110[svsFile.G], v001[svsFile.G], v101[svsFile.G], v011[svsFile.G], v111[svsFile.G], indexDownsampleMap.get(r).normalizedInterval, indexDownsampleMap.get(g).normalizedInterval, indexDownsampleMap.get(b).normalizedInterval));
                                    svsFile.lutUpsampled[r][g][b][svsFile.B] = (int)(0xff * Interpolater.trilinearInterpolate(v000[svsFile.B], v100[svsFile.B], v010[svsFile.B], v110[svsFile.B], v001[svsFile.B], v101[svsFile.B], v011[svsFile.B], v111[svsFile.B], indexDownsampleMap.get(r).normalizedInterval, indexDownsampleMap.get(g).normalizedInterval, indexDownsampleMap.get(b).normalizedInterval));
                                    svsFile.lutUpsampledInt[((r & 0x0000ff)) << 16 | ((g & 0x0000ff) << 8) | ((b & 0x0000ff) << 0)] = ((svsFile.lutUpsampled[r][g][b][svsFile.R] & 0x0000ff)) << 16 | ((svsFile.lutUpsampled[r][g][b][svsFile.G] & 0x0000ff) << 8) | ((svsFile.lutUpsampled[r][g][b][svsFile.B] & 0x0000ff) << 0);
                                    // check to make sure that where original and upsampled LUTs intersect, they are the same
                                    //if(!indexDownsampleMap.get(r).requiresInterpolation && !indexDownsampleMap.get(g).requiresInterpolation && !indexDownsampleMap.get(b).requiresInterpolation) {
                                    //    if(
                                    //           !(svsFile.lutUpsampled[r][g][b][svsFile.R] == lut[(int)indexDownsampleMap.get(r).indexDownsampleNotional][(int)indexDownsampleMap.get(g).indexDownsampleNotional][(int)indexDownsampleMap.get(b).indexDownsampleNotional][svsFile.R])
                                    //        || !(svsFile.lutUpsampled[r][g][b][svsFile.G] == lut[(int)indexDownsampleMap.get(r).indexDownsampleNotional][(int)indexDownsampleMap.get(g).indexDownsampleNotional][(int)indexDownsampleMap.get(b).indexDownsampleNotional][svsFile.G])
                                    //        || !(svsFile.lutUpsampled[r][g][b][svsFile.B] == lut[(int)indexDownsampleMap.get(r).indexDownsampleNotional][(int)indexDownsampleMap.get(g).indexDownsampleNotional][(int)indexDownsampleMap.get(b).indexDownsampleNotional][svsFile.B])
                                    //    ) {
                                    //        throw new RuntimeException(String.format("interpolation error: original=(%f, %f, %f) upsample=(%f, %f, %f) indexDownsample=(%s, %s, %s)",
                                    //            svsFile.lutUpsampled[r][g][b][svsFile.R],
                                    //            svsFile.lutUpsampled[r][g][b][svsFile.G],
                                    //            svsFile.lutUpsampled[r][g][b][svsFile.B],
                                    //            lut[(int)indexDownsampleMap.get(r).indexDownsampleNotional][(int)indexDownsampleMap.get(g).indexDownsampleNotional][(int)indexDownsampleMap.get(b).indexDownsampleNotional][svsFile.R],
                                    //            lut[(int)indexDownsampleMap.get(r).indexDownsampleNotional][(int)indexDownsampleMap.get(g).indexDownsampleNotional][(int)indexDownsampleMap.get(b).indexDownsampleNotional][svsFile.G],
                                    //            lut[(int)indexDownsampleMap.get(r).indexDownsampleNotional][(int)indexDownsampleMap.get(g).indexDownsampleNotional][(int)indexDownsampleMap.get(b).indexDownsampleNotional][svsFile.B],
                                    //            indexDownsampleMap.get(r),
                                    //            indexDownsampleMap.get(g),
                                    //            indexDownsampleMap.get(b)
                                    //        ));
                                    //    }
                                    //}
                                }
                            }
                        }
                    }
                }
                Thread[] upsampleThreads = new Thread[threads];
                for(int x = 0; x < threads; x++) {
                    upsampleThreads[x] = new Thread(new UpsampleRunner(x, threads));
                    upsampleThreads[x].start();
                }
                for(int x = 0; x < threads; x++) {
                    upsampleThreads[x].join();
                }
                logger.log(Level.INFO, String.format("...done"));
            }
            
            svsFile.lutComputed = true;
            
        }
    }

    @Override
    public void run() {
    }

}

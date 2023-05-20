/**
 * MIT License
 *
 * Copyright (c) 2022 Geoffrey H. Smith
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package svsutil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 *
 * @author geoffrey.smith@emory.edu
 */
public class ColorUtil {
    
    static final Logger logger = Logger.getLogger(ColorUtil.class.getName());    
    
    public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {

        byte[] dummyTileBytes;
        try(InputStream is = ColorUtil.class.getClassLoader().getResourceAsStream("dummy_tile.jpg")) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[16384];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
              buffer.write(data, 0, nRead);
            }
            dummyTileBytes = buffer.toByteArray();
        }
        
        int threads = 4;
        int quality = 87;
        int skip = 0;
        boolean noRecolor = false;
        boolean resizeFile = false;
        boolean annotate = false;
        int startWithTiffDirIndex = 0; // set to higher numbers for troubleshooting (runs faster)
        boolean dummyTile = false;

        Options options = new Options();

        Option optionThreads = new Option("t", "threads", true, String.format("number of parallel threads, integer (default = %d)", threads));
        optionThreads.setRequired(false);
        optionThreads.setType(Number.class);
        options.addOption(optionThreads);

        Option optionQuality = new Option("q", "quality", true, String.format("JPEG compression quality, integer 0 - 100 (default = %d)", quality));
        optionQuality.setRequired(false);
        optionQuality.setType(Number.class);
        options.addOption(optionQuality);

        Option optionSkip = new Option("s", "skip", true, String.format("skip this many tiles when recoloring; used to create interesting patterns of raw/recolored tiles, integer (default = %d)", skip));
        optionSkip.setRequired(false);
        optionSkip.setType(Number.class);
        options.addOption(optionSkip);

        Option optionNoRecolor = new Option("n", "norecolor", false, String.format("if specified, tiles are rewritten at specified quality, but not recolored (default = do recolor)"));
        optionNoRecolor.setRequired(false);
        options.addOption(optionNoRecolor);

        Option optionResize = new Option("r", "resize", false, String.format("if specified, allow program to resize the SVS file (default = do not resize file)"));
        optionResize.setRequired(false);
        options.addOption(optionResize);

        Option optionAnnotate = new Option("a", "annotate", false, String.format("if specified, annotate every tile with its tile ID and microns per pixel (default = do not annotate)"));
        optionAnnotate.setRequired(false);
        options.addOption(optionAnnotate);

        Option optionDummyTile = new Option("d", "dummytile", false, String.format("if specified, white tiles a replaced with a dummy tile (GT450 only)"));
        optionDummyTile.setRequired(false);
        options.addOption(optionDummyTile);
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null; //not a good practice, it serves it purpose 

        try {
            cmd = parser.parse(options, args);
            if(cmd.hasOption(optionThreads)) { threads = ((Long)cmd.getParsedOptionValue(optionThreads)).intValue(); }
            if(cmd.hasOption(optionQuality)) { quality = ((Long)cmd.getParsedOptionValue(optionQuality)).intValue(); }
            if(cmd.hasOption(optionSkip)) { skip = ((Long)cmd.getParsedOptionValue(optionSkip)).intValue(); }
            if(cmd.hasOption(optionNoRecolor)) { noRecolor = true; }
            if(cmd.hasOption(optionResize)) { resizeFile = true; }
            if(cmd.hasOption(optionAnnotate)) { annotate = true; }
            if(cmd.hasOption(optionDummyTile)) { dummyTile = true; }
            if(cmd.getArgs().length != 1) { throw new ParseException("no file specified"); }
            if(!cmd.getArgs()[0].toLowerCase().endsWith(".svs")) { throw new ParseException("file name must have a 'svs' extension"); }
        }
        catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("java -jar svsutil.jar colorutil [options] svs_file_name", options);
            System.exit(1);
        }
        
        final SVSFile svsFile = new SVSFile(cmd.getArgs()[0]);

        svsFile.computeLut(threads);
        
        logger.log(Level.INFO, String.format("recoloring tiles in %d threads", threads));

        Thread statusThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int tileCount = svsFile.tiffDirList.stream().mapToInt(x -> x.tilesInSVSOrder != null ? x.tilesInSVSOrder.length : 0).sum();
                try {
                    while(true) {
                        logger.log(Level.INFO, String.format("%d of %d tiles recolored (%4.1f%% complete)", svsFile.nextTileNo, tileCount, 100f * svsFile.nextTileNo / tileCount));
                        Thread.sleep(10000);
                    }
                }
                catch(InterruptedException e) {
                    logger.log(Level.INFO, String.format("%d of %d tiles recolored (%4.1f%% complete)", svsFile.nextTileNo, tileCount, 100f * svsFile.nextTileNo / tileCount));
                }
                catch(Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        });
        statusThread.start();

        Thread[] recolorThreads = new Thread[threads];
        for(int x = 0; x < threads; x++) {
            if(svsFile.tiffDirList.get(0).description.startsWith("Aperio Leica Biosystems GT450 v1.0.1")) {
                recolorThreads[x] = new Thread(new RecolorRunnerGT450(svsFile, quality, skip, noRecolor, annotate, startWithTiffDirIndex, dummyTile));
            }
            else if(svsFile.tiffDirList.get(0).description.startsWith("Aperio Image Library v12.0.15")) {
                recolorThreads[x] = new Thread(new RecolorRunnerAT2(svsFile, quality, skip, noRecolor, annotate, startWithTiffDirIndex, dummyTile));
            }
            else {
                System.err.println("unknown scanner");
                System.exit(1);
            }
            recolorThreads[x].start();
        }
        for(int x = 0; x < threads; x++) {
            recolorThreads[x].join();
        }
        
        statusThread.interrupt();
        statusThread.join();

        {
// ^^ resize logic ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            List<SVSFile.ResizeSegment> resizeSegmentList = new ArrayList<>();
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            for(int x = startWithTiffDirIndex; x < svsFile.tiffDirList.size(); x++) {
                TIFFDir tiffDir = svsFile.tiffDirList.get(x);
                if(tiffDir.tagTileOffsetsInSvs == null) {
                    continue;
                }
                long bytesAvailable = Arrays.stream(tiffDir.tilesInSVSOrder).mapToLong(y -> y.length).sum();
                long bytesRequired = (dummyTile ? dummyTileBytes.length : 0) + Arrays.stream(tiffDir.tilesInSVSOrder).mapToLong(y -> svsFile.recoloredTileBytesMap.get(y.id).length).sum();
                if(bytesRequired > bytesAvailable && !resizeFile) {
                    logger.log(Level.SEVERE, String.format("error writing TIFF directory %d image tiles: %d bytes are available but %d bytes are required - use resize option or reduce JPEG compression quality", x, bytesAvailable, bytesRequired));
                    System.exit(1);
                }
// ^^ resize logic ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                // negative length = compress file (remove these bytes)
                // positive length = expand file
                if(resizeFile) {
                    SVSFile.ResizeSegment resizeSegment = new SVSFile.ResizeSegment(0, bytesRequired - bytesAvailable);
                    resizeSegmentList.add(resizeSegment);
                    if(bytesRequired < bytesAvailable) {
                        resizeSegment.start = tiffDir.tileDataContigOffsetInSVS + bytesRequired;
                    }
                    else if(bytesRequired > bytesAvailable) {
                        resizeSegment.start = tiffDir.tileDataContigOffsetInSVS + bytesAvailable;
                    }
                }
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            }    
// ^^ resize logic ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            // do segment expansions before writing the new image tiles...
            if(resizeFile) { svsFile.resize(resizeSegmentList.stream().filter(x -> x.length > 0).collect(Collectors.toList())); }
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            for(int x = startWithTiffDirIndex; x < svsFile.tiffDirList.size(); x++) {
                TIFFDir tiffDir = svsFile.tiffDirList.get(x);
                if(tiffDir.tagTileOffsetsInSvs == null) {
                    continue;
                }
                logger.log(Level.INFO, String.format("writing TIFF directory %d image tiles", x));
                long tileOffsetInSVS = tiffDir.tilesInSVSOrder[0].offsetInSVS;
                long dummyTileOffset = tiffDir.tilesInSVSOrder[0].offsetInSVS + Arrays.stream(tiffDir.tilesInSVSOrder).mapToLong(y -> svsFile.recoloredTileBytesMap.get(y.id).length).sum();
                for(int y = 0; y < tiffDir.tilesInSVSOrder.length; y ++) {
                    byte[] recoloredTileBytes = svsFile.recoloredTileBytesMap.get(tiffDir.tilesInSVSOrder[y].id);
                    if(dummyTile && recoloredTileBytes.length == 0) {
                        svsFile.setBytesToLong(tiffDir.tagTileOffsetsInSvsOffsetInSVS[tiffDir.tilesInSVSOrder[y].indexInTiffDir], dummyTileOffset);
                        svsFile.setBytesToLong(tiffDir.tagTileLengthsOffsetInSVS[tiffDir.tilesInSVSOrder[y].indexInTiffDir], dummyTileBytes.length);
                    }
                    else {
                        svsFile.setBytesToLong(tiffDir.tagTileOffsetsInSvsOffsetInSVS[tiffDir.tilesInSVSOrder[y].indexInTiffDir], tileOffsetInSVS);
                        svsFile.setBytesToLong(tiffDir.tagTileLengthsOffsetInSVS[tiffDir.tilesInSVSOrder[y].indexInTiffDir], recoloredTileBytes.length);
                        svsFile.setBytes(tileOffsetInSVS, tileOffsetInSVS + recoloredTileBytes.length, recoloredTileBytes);
                    }
                    tileOffsetInSVS += recoloredTileBytes.length;
                }
                // add dummy tile to end of this TIFF directory's tile contig
                if(dummyTile) { svsFile.setBytes(tileOffsetInSVS, tileOffsetInSVS + dummyTileBytes.length, dummyTileBytes); }
            }
// ^^ resize logic ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            // ...and do segment compressions after writing the new image tiles
            if(resizeFile) { svsFile.resize(resizeSegmentList.stream().filter(x -> x.length < 0).collect(Collectors.toList())); }
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            // clobber ICC in the TIFF directory by changing its tag name to garbage
            if(!noRecolor) {
                for(int x = startWithTiffDirIndex; x < svsFile.tiffDirList.size(); x++) {
                    TIFFDir tiffDir = svsFile.tiffDirList.get(x);
                    if(tiffDir.tagICCOffsetInSvs != -1) {
                        svsFile.setByte(tiffDir.offsetInSvs + tiffDir.tagICCNameOffsetInHeader + 0, (byte)0xff);
                        svsFile.setByte(tiffDir.offsetInSvs + tiffDir.tagICCNameOffsetInHeader + 1, (byte)0xff);
                    }
                }
            }
            svsFile.write((new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_retiled.svs"));
            logger.log(Level.INFO, String.format("recolored slide written to %s in current directory", (new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_recolored.svs")));
        }
        
        System.exit(0);

    }
    
}

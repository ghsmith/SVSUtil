package svsutil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    
    static Logger logger = Logger.getLogger(ColorUtil.class.getName());    
    
    public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {
        
        int threads = 4;
        int quality = 87;
        int skip = 0;

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

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null; //not a good practice, it serves it purpose 

        try {
            cmd = parser.parse(options, args);
            if(cmd.hasOption(optionThreads)) { threads = ((Long)cmd.getParsedOptionValue(optionThreads)).intValue(); }
            if(cmd.hasOption(optionQuality)) { quality = ((Long)cmd.getParsedOptionValue(optionQuality)).intValue(); }
            if(cmd.hasOption(optionSkip)) { skip = ((Long)cmd.getParsedOptionValue(optionSkip)).intValue(); }
            if(cmd.getArgs().length != 1) { throw new ParseException("no file specified"); }
            if(!cmd.getArgs()[0].toLowerCase().endsWith(".svs")) { throw new ParseException("file name must have a 'svs' extension"); }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("java -jar svsutil.jar [options] file-to-recolor.svs", options);
            System.exit(1);
        }
        
        final SVSFile svsFile = new SVSFile(cmd.getArgs()[0]);

        logger.log(Level.INFO, String.format("recoloring tiles in %d threads...", threads));

        Thread statusThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int tileCount = 0;
                for(int x = 0; x < svsFile.tiffDirList.size(); x++) {
                    TIFFDir tiffDir = svsFile.tiffDirList.get(x);
                    for(int y = 0; y < tiffDir.tileContigList.size(); y++) {
                        TiffTileContig tileContig = tiffDir.tileContigList.get(y);
                        for(int z = 0; z < tileContig.tagTileOffsetsInSvs.length; z++) {
                            tileCount++;
                        }
                    }
                }
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
                    throw new RuntimeException(e);
                }
            }
        });
        statusThread.start();

        Thread writerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for(int x = 0; x < svsFile.tiffDirList.size(); x++) {
                        TIFFDir tiffDir = svsFile.tiffDirList.get(x);
                        for(int y = 0; y < tiffDir.tileContigList.size(); y++) {
                            TiffTileContig tileContig = tiffDir.tileContigList.get(y);
                            while(tileContig.recoloredTileBytesMap.size() < tileContig.tagTileOffsetsInSvs.length) {
                                Thread.sleep(50);
                            }
                            int totalLength = 0;
                            for(int z = 0; z < tileContig.tagTileOffsetsInSvs.length; z++) {
                                totalLength += tileContig.recoloredTileBytesMap.get(z).length;
                            }
                            if(totalLength > tileContig.length) {
                                logger.log(Level.SEVERE, String.format("error writing TIFF directory %d contig %d: %d bytes are available but %d bytes are required - reduce JPEG compression quality", x, y, tileContig.length, totalLength));
                                System.exit(1);
                            }
                            logger.log(Level.INFO, String.format("writing TIFF directory %d contig %d: %d bytes are available and %d bytes are required.", x, y, tileContig.length, totalLength));
                            int offsetInTileContig = 0;
                            for(int z = 0; z < tileContig.tagTileOffsetsInSvs.length; z++) {
                                byte[] tileBytes = tileContig.recoloredTileBytesMap.get(z);
                                svsFile.setByte(tileContig.tagTileOffsetsInSvsOffsetInSvs[z] + 0, (byte)(((tileContig.offsetInSvs + offsetInTileContig) & 0x000000ff) >>  0));
                                svsFile.setByte(tileContig.tagTileOffsetsInSvsOffsetInSvs[z] + 1, (byte)(((tileContig.offsetInSvs + offsetInTileContig) & 0x0000ff00) >>  8));
                                svsFile.setByte(tileContig.tagTileOffsetsInSvsOffsetInSvs[z] + 2, (byte)(((tileContig.offsetInSvs + offsetInTileContig) & 0x00ff0000) >> 16));
                                svsFile.setByte(tileContig.tagTileOffsetsInSvsOffsetInSvs[z] + 3, (byte)(((tileContig.offsetInSvs + offsetInTileContig) & 0xff000000) >> 24));
                                svsFile.setByte(tileContig.tagTileLengthsOffsetInSvs[z] + 0, (byte)(((tileBytes.length) & 0x000000ff) >>  0));
                                svsFile.setByte(tileContig.tagTileLengthsOffsetInSvs[z] + 1, (byte)(((tileBytes.length) & 0x0000ff00) >>  8));
                                svsFile.setByte(tileContig.tagTileLengthsOffsetInSvs[z] + 2, (byte)(((tileBytes.length) & 0x00ff0000) >> 16));
                                svsFile.setByte(tileContig.tagTileLengthsOffsetInSvs[z] + 3, (byte)(((tileBytes.length) & 0xff000000) >> 24));
                                svsFile.setBytes(tileContig.offsetInSvs + offsetInTileContig, tileContig.offsetInSvs + offsetInTileContig + tileBytes.length, tileBytes);
                                offsetInTileContig += tileBytes.length;
                            }
                            tileContig.recoloredTileBytesMap = null;
                        }
                    }
                    logger.log(Level.INFO, String.format("writing SVS"));
                    svsFile.write();
                }
                catch(Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        writerThread.start();
        
        Thread[] recolorThreads = new Thread[threads];
        for(int x = 0; x < threads; x++) {
            recolorThreads[x] = new Thread(new RecolorRunner(svsFile, quality, skip));
            recolorThreads[x].start();
        }
        for(int x = 0; x < threads; x++) {
            recolorThreads[x].join();
        }
        
        statusThread.interrupt();
        
        writerThread.join();

        logger.log(Level.INFO, String.format("...done"));

        System.exit(0);

    }
    
}

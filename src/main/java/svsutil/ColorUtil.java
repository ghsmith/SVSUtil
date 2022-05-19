package svsutil;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
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
    
    public static void main(String[] args) throws ParseException, FileNotFoundException, IOException, InterruptedException {
        
        int threads = 4;
        final int[] quality = {87}; // array because I need this final for an inner class
        final int[] skip = {0}; // array because I need this final for an inner class

        Options options = new Options();

        Option optionThreads = new Option("t", "threads", true, String.format("number of parallel threads, integer (default = %d)", threads));
        optionThreads.setRequired(false);
        optionThreads.setType(Number.class);
        options.addOption(optionThreads);

        Option optionQuality = new Option("q", "quality", true, String.format("JPEG compression quality, integer 0 - 100 (default = %d)", quality[0]));
        optionQuality.setRequired(false);
        optionQuality.setType(Number.class);
        options.addOption(optionQuality);

        Option optionSkip = new Option("s", "skip", true, String.format("skip this many tiles when recoloring; used to create interesting patterns of raw/recolored tiles, integer (default = %d)", skip[0]));
        optionSkip.setRequired(false);
        optionSkip.setType(Number.class);
        options.addOption(optionSkip);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null; //not a good practice, it serves it purpose 

        try {
            cmd = parser.parse(options, args);
            if(cmd.hasOption(optionThreads)) { threads = ((Long)cmd.getParsedOptionValue(optionThreads)).intValue(); }
            if(cmd.hasOption(optionQuality)) { quality[0] = ((Long)cmd.getParsedOptionValue(optionQuality)).intValue(); }
            if(cmd.getArgs().length != 1) { throw new ParseException("no file specified"); }
            if(!cmd.getArgs()[0].toLowerCase().endsWith(".svs")) { throw new ParseException("file name must have a 'svs' extension"); }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("java -jar svscolorutil.jar [options] file-to-recolor.svs", options);
            System.exit(1);
        }
        
        final SVSFile svsFile = new SVSFile(cmd.getArgs()[0], threads);

        class RecolorRunner implements Runnable {
            public static Integer nextTileNo = 0;
            @Override
            public void run() {
                try {
                    Iterator iter = ImageIO.getImageWritersByFormatName("jpeg");            
                    ImageWriter writer = (ImageWriter)iter.next();
                    ImageWriteParam iwp = writer.getDefaultWriteParam();
                    iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    iwp.setCompressionQuality(quality[0] / 100f);
                    int tileNo = -1;
                    for(int x = 0; x < svsFile.tiffDirList.size(); x++) {
                        TIFFDir tiffDir = svsFile.tiffDirList.get(x);
                        for(int y = 0; y < tiffDir.tileContigList.size(); y++) {
                            TiffTileContig tileContig = tiffDir.tileContigList.get(y);
                            for(int z = 0; z < tileContig.tagTileOffsetsInSvs.length; z++) {
                                tileNo++;
                                synchronized(nextTileNo) {
                                    if(tileNo < nextTileNo) {
                                        continue;
                                    }
                                    for(int skipTileNo = 0; skipTileNo < skip[0]; skipTileNo++) {
                                        tileContig.recoloredTileBytesMap.put(z, svsFile.getBytes(tileContig.tagTileOffsetsInSvs[z], tileContig.tagTileOffsetsInSvs[z] + tileContig.tagTileLengths[z]));
                                    }
                                    nextTileNo += skip[0] + 1;
                                }
                                InputStream is = new ByteArrayInputStream(svsFile.getBytes(tileContig.tagTileOffsetsInSvs[z], tileContig.tagTileOffsetsInSvs[z] + tileContig.tagTileLengths[z]));
                                BufferedImage image = ImageIO.read(is);
                                is.close();
                                for(int xx = 0; xx < image.getWidth(); xx++) {
                                    for(int yy = 0; yy < image.getHeight(); yy++) {
                                        int pixelIn = image.getRGB(xx, yy);
                                        int r = (pixelIn & 0x00ff0000) >> 16;
                                        int g = (pixelIn & 0x0000ff00) >>  8;
                                        int b = (pixelIn & 0x000000ff) >>  0;
                                        image.setRGB(xx, yy, (pixelIn & 0xff000000) | ((svsFile.lutUpsampled[r][g][b][SVSFile.R] << 16) & 0x00ff0000) | ((svsFile.lutUpsampled[r][g][b][SVSFile.G] <<  8) & 0x0000ff00) | ((svsFile.lutUpsampled[r][g][b][SVSFile.B] <<  0) & 0x000000ff));
                                    }
                                }
                                ByteArrayOutputStream baos = new ByteArrayOutputStream(50000);
                                ImageOutputStream ios = new MemoryCacheImageOutputStream(baos);
                                writer.setOutput(ios);
                                IIOImage newImage = new IIOImage(image, null, null);
                                writer.write(null, newImage, iwp);
                                tileContig.recoloredTileBytesMap.put(z, baos.toByteArray());
                                ios.close();
                                baos.close();
                            }
                        }
                    }
                }
                catch(Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        logger.log(Level.INFO, String.format("recoloring tiles in %d threads...", threads));

        Thread statusThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
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
                    while(true) {
                        logger.log(Level.INFO, String.format("%d of %d tiles recolored (%4.1f%% complete)", RecolorRunner.nextTileNo, tileCount, 100f * RecolorRunner.nextTileNo / tileCount));
                        Thread.sleep(10000);
                    }
                }
                catch(InterruptedException e) {
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
                                Thread.sleep(100);
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
            recolorThreads[x] = new Thread(new RecolorRunner());
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

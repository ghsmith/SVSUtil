package svsutil;

import com.twelvemonkeys.io.enc.Decoder;
import com.twelvemonkeys.io.enc.Encoder;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
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
public class LabelUtil {
    
    static final Logger logger = Logger.getLogger(ColorUtil.class.getName());    

    public static void main(String[] args) throws IOException, InterruptedException {
        
        boolean extract = false;
        String annotation = null;
        String replacement = null;
                
        Options options = new Options();

        Option optionExtract = new Option("x", "extract", false, String.format("extract label to JPG"));
        optionExtract.setRequired(false);
        options.addOption(optionExtract);

        Option optionAnnotate = new Option("a", "annotate", true, String.format("add string annotation to label (e.g., -a \"test annotation\")"));
        optionAnnotate.setRequired(false);
        options.addOption(optionAnnotate);
        
        Option optionReplace = new Option("r", "replace", true, String.format("replace label entirely (e.g., -r \"study set #1<br/>case#2\")"));
        optionReplace.setRequired(false);
        options.addOption(optionReplace);
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null; //not a good practice, it serves it purpose 

        try {
            cmd = parser.parse(options, args);
            if(cmd.hasOption(optionExtract)) { extract = true; }
            if(cmd.hasOption(optionAnnotate)) { annotation = cmd.getOptionValue(optionAnnotate); }
            if(cmd.hasOption(optionReplace)) { replacement = cmd.getOptionValue(optionReplace); }
            if(cmd.getArgs().length != 1) { throw new ParseException("no file specified"); }
            if(!cmd.getArgs()[0].toLowerCase().endsWith(".svs")) { throw new ParseException("file name must have a 'svs' extension"); }
        }
        catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("java -jar svsutil.jar labelutil [options] svs_file_name", options);
            System.exit(1);
        }
        
        final SVSFile svsFile = new SVSFile(cmd.getArgs()[0]);

        if(extract) {

            for(TIFFDir tiffDir : svsFile.tiffDirList) {

                if(tiffDir.subfileType == 1) {
                    ByteArrayInputStream bis = new ByteArrayInputStream(svsFile.getBytes(tiffDir.imageDataOffsetInSvs, tiffDir.imageDataOffsetInSvs + tiffDir.imageDataLength));
                    ByteBuffer bb = ByteBuffer.allocate(1024 * 1024 * 10);
                    Decoder lzwDecoder = LZWDecoder.create(false);
                    lzwDecoder.decode(bis, bb);
                    bb.flip();
                    BufferedImage image = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_3BYTE_BGR);
                    for(int y = 0; y < tiffDir.height; y++){
                        byte r = bb.get();
                        byte g = bb.get();
                        byte b = bb.get();
                        image.setRGB(0, y, 0xff000000 | (r & 0x000000ff) << 16 | (g & 0x000000ff) << 8 | (b & 0x000000ff) << 0);
                        for(int x = 0; x < tiffDir.width; x++) {
                            // horizontal differencing
                            if(x > 0) {
                                r += bb.get();
                                g += bb.get();
                                b += bb.get();
                            }
                            image.setRGB(x, y, 0xff000000 | (r & 0x000000ff) << 16 | (g & 0x000000ff) << 8 | (b & 0x000000ff) << 0);
                        }
                    }
                    ImageIO.write(image, "jpg", new File((new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_label.jpg")));
                    logger.log(Level.INFO, String.format("label written to %s in current directory", (new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_label.jpg")));
                   
                    break;

                }
                
            }

        }
        
        if(annotation != null) {

            for(TIFFDir tiffDir : svsFile.tiffDirList) {

                if(tiffDir.subfileType == 1) {

                    ByteArrayInputStream bis = new ByteArrayInputStream(svsFile.getBytes(tiffDir.imageDataOffsetInSvs, tiffDir.imageDataOffsetInSvs + tiffDir.imageDataLength));
                    ByteBuffer bb = ByteBuffer.allocate(1024 * 1024 * 20);
                    Decoder lzwDecoder = LZWDecoder.create(false);
                    lzwDecoder.decode(bis, bb);
                    bb.flip();
                    BufferedImage image = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_3BYTE_BGR);
                    for(int y = 0; y < tiffDir.height; y++){
                        byte r = bb.get();
                        byte g = bb.get();
                        byte b = bb.get();
                        image.setRGB(0, y, 0xff000000 | (r & 0x000000ff) << 16 | (g & 0x000000ff) << 8 | (b & 0x000000ff) << 0);
                        for(int x = 0; x < tiffDir.width; x++) {
                            // undo horizontal differencing
                            if(x > 0) {
                                r += bb.get();
                                g += bb.get();
                                b += bb.get();
                            }
                            image.setRGB(x, y, 0xff000000 | (r & 0x000000ff) << 16 | (g & 0x000000ff) << 8 | (b & 0x000000ff) << 0);
                        }
                    }
                    // using monochrome for the label to keep the size small,
                    // otherwise it might not fit in the available space and
                    // most labels are monochrome, anyway
                    BufferedImage imageAnnotated = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_BYTE_BINARY);
                    //BufferedImage imageAnnotated = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D graphics = imageAnnotated.createGraphics();
                    graphics.drawImage(image, 0, 0, (int)(tiffDir.width * 0.75f), (int)(tiffDir.height * 0.75f), null);
                    graphics.setColor(Color.WHITE);
                    graphics.setFont(new Font("TimesRoman", Font.PLAIN, 50));
                    graphics.drawString(annotation, 5, tiffDir.height - 20);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bb.clear();
                    for(int y = 0; y < tiffDir.height; y++){
                        byte r  = (byte)((imageAnnotated.getRGB(0, y) & 0x00ff0000) >> 16);
                        byte g  = (byte)((imageAnnotated.getRGB(0, y) & 0x0000ff00) >>  8);
                        byte b  = (byte)((imageAnnotated.getRGB(0, y) & 0x000000ff) >>  0);
                        bb.put(r);
                        bb.put(g);
                        bb.put(b);
                        for(int x = 0; x < tiffDir.width; x++) {
                            // create horizontal differencing
                            if(x > 0) {
                                bb.put((byte)(((imageAnnotated.getRGB(x, y) & 0x00ff0000) >> 16) - r));
                                bb.put((byte)(((imageAnnotated.getRGB(x, y) & 0x0000ff00) >>  8) - g));
                                bb.put((byte)(((imageAnnotated.getRGB(x, y) & 0x000000ff) >>  0) - b));
                                r  = (byte)((imageAnnotated.getRGB(x, y) & 0x00ff0000) >> 16);
                                g  = (byte)((imageAnnotated.getRGB(x, y) & 0x0000ff00) >>  8);
                                b  = (byte)((imageAnnotated.getRGB(x, y) & 0x000000ff) >>  0);
                            }
                        }
                    }
                    bb.flip();
                    Encoder lzwEncoder = new LZWEncoder(tiffDir.width * tiffDir.height * 3);
                    lzwEncoder.encode(bos, bb);
                    byte[] lzwBytes = bos.toByteArray();
                    if(lzwBytes.length > tiffDir.imageDataLength) {
                        logger.log(Level.SEVERE, "annotated label exceeds bytes available in SVS for label");
                        System.exit(1);
                    }
                    svsFile.setByte(tiffDir.offsetInSvs + tiffDir.imageDataLengthOffsetInHeader + 0, (byte)(((lzwBytes.length) & 0x000000ff) >>  0));
                    svsFile.setByte(tiffDir.offsetInSvs + tiffDir.imageDataLengthOffsetInHeader + 1, (byte)(((lzwBytes.length) & 0x0000ff00) >>  8));
                    svsFile.setByte(tiffDir.offsetInSvs + tiffDir.imageDataLengthOffsetInHeader + 2, (byte)(((lzwBytes.length) & 0x00ff0000) >> 16));
                    svsFile.setByte(tiffDir.offsetInSvs + tiffDir.imageDataLengthOffsetInHeader + 3, (byte)(((lzwBytes.length) & 0xff000000) >> 24));
                    svsFile.setBytes(tiffDir.imageDataOffsetInSvs, tiffDir.imageDataOffsetInSvs + lzwBytes.length, lzwBytes);
                    svsFile.write((new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_label_annotated.svs"));
                    logger.log(Level.INFO, String.format("slide with annotated label written to %s in current directory", (new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_label_annotated.svs")));
                    break;

                }
                
            }
            
        }
        
        if(replacement != null) {

            for(TIFFDir tiffDir : svsFile.tiffDirList) {

                if(tiffDir.subfileType == 1) {

                    ByteBuffer bb = ByteBuffer.allocate(1024 * 1024 * 20);
                    // using monochrome for the label to keep the size small,
                    // otherwise it might not fit in the available space and
                    // most labels are monochrome, anyway
                    BufferedImage imageAnnotated = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_BYTE_BINARY);
                    //BufferedImage imageAnnotated = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D graphics = imageAnnotated.createGraphics();
                    graphics.setColor(Color.WHITE);
                    graphics.setFont(new Font("TimesRoman", Font.PLAIN, 80));
                    int yStart = graphics.getFontMetrics().getHeight() + 20;
                    for(String replacementLine : replacement.split("<br/>")) {
                        graphics.drawString(replacementLine, 5, yStart);
                        yStart += graphics.getFontMetrics().getHeight() + 10;
                    }
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    for(int y = 0; y < tiffDir.height; y++){
                        byte r  = (byte)((imageAnnotated.getRGB(0, y) & 0x00ff0000) >> 16);
                        byte g  = (byte)((imageAnnotated.getRGB(0, y) & 0x0000ff00) >>  8);
                        byte b  = (byte)((imageAnnotated.getRGB(0, y) & 0x000000ff) >>  0);
                        bb.put(r);
                        bb.put(g);
                        bb.put(b);
                        for(int x = 0; x < tiffDir.width; x++) {
                            // create horizontal differencing
                            if(x > 0) {
                                bb.put((byte)(((imageAnnotated.getRGB(x, y) & 0x00ff0000) >> 16) - r));
                                bb.put((byte)(((imageAnnotated.getRGB(x, y) & 0x0000ff00) >>  8) - g));
                                bb.put((byte)(((imageAnnotated.getRGB(x, y) & 0x000000ff) >>  0) - b));
                                r  = (byte)((imageAnnotated.getRGB(x, y) & 0x00ff0000) >> 16);
                                g  = (byte)((imageAnnotated.getRGB(x, y) & 0x0000ff00) >>  8);
                                b  = (byte)((imageAnnotated.getRGB(x, y) & 0x000000ff) >>  0);
                            }
                        }
                    }
                    bb.flip();
                    Encoder lzwEncoder = new LZWEncoder(tiffDir.width * tiffDir.height * 3);
                    lzwEncoder.encode(bos, bb);
                    byte[] lzwBytes = bos.toByteArray();
                    if(lzwBytes.length > tiffDir.imageDataLength) {
                        logger.log(Level.SEVERE, "annotated label exceeds bytes available in SVS for label");
                        System.exit(1);
                    }
                    svsFile.setByte(tiffDir.offsetInSvs + tiffDir.imageDataLengthOffsetInHeader + 0, (byte)(((lzwBytes.length) & 0x000000ff) >>  0));
                    svsFile.setByte(tiffDir.offsetInSvs + tiffDir.imageDataLengthOffsetInHeader + 1, (byte)(((lzwBytes.length) & 0x0000ff00) >>  8));
                    svsFile.setByte(tiffDir.offsetInSvs + tiffDir.imageDataLengthOffsetInHeader + 2, (byte)(((lzwBytes.length) & 0x00ff0000) >> 16));
                    svsFile.setByte(tiffDir.offsetInSvs + tiffDir.imageDataLengthOffsetInHeader + 3, (byte)(((lzwBytes.length) & 0xff000000) >> 24));
                    svsFile.setBytes(tiffDir.imageDataOffsetInSvs, tiffDir.imageDataOffsetInSvs + lzwBytes.length, lzwBytes);
                    svsFile.write((new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_label_replaced.svs"));
                    logger.log(Level.INFO, String.format("slide with annotated label written to %s in current directory", (new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_label_replaced.svs")));
                    break;

                }
                
            }
            
        }

    }
    
}

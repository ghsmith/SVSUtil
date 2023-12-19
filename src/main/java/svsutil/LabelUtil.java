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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import static javax.imageio.ImageWriteParam.MODE_COPY_FROM_METADATA;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.krysalis.barcode4j.impl.datamatrix.DataMatrixBean;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;

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
        boolean monochrome = false;
        boolean resizeFile = false;
        boolean clobberMacro = false;
        boolean barCode = false;
                
        Options options = new Options();

        Option optionExtract = new Option("x", "extract", false, String.format("extract label to JPG"));
        optionExtract.setRequired(false);
        options.addOption(optionExtract);

        Option optionAnnotate = new Option("a", "annotate", true, String.format("add string annotation to label (e.g., -a \"test annotation\")"));
        optionAnnotate.setRequired(false);
        options.addOption(optionAnnotate);
        
        Option optionReplace = new Option("s", "string", true, String.format("replace label entirely with a string (e.g., -r \"study set #1<br/>case#2\")"));
        optionReplace.setRequired(false);
        options.addOption(optionReplace);

        Option optionMonochrome = new Option("m", "monochrome", false, String.format("if specified, any label written to SVS is converted to monochrome (default = do not use monochrome)"));
        optionMonochrome.setRequired(false);
        options.addOption(optionMonochrome);
        
        Option optionResize = new Option("r", "resize", false, String.format("if specified, allow program to resize the SVS file (default = do not resize file)"));
        optionResize.setRequired(false);
        options.addOption(optionResize);
        
        Option optionClobberMacro = new Option("c", "clobber", false, String.format("if specified, the program will clobber the macro image, which can include a portion of the label (default = do not clobber)"));
        optionClobberMacro.setRequired(false);
        options.addOption(optionClobberMacro);

        Option optionBarCode = new Option("b", "barcode", false, String.format("if specified, the program will add a Data Matrix bar code to the label that encodes the replacement string (default = no bar code)"));
        optionBarCode.setRequired(false);
        options.addOption(optionBarCode);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null; //not a good practice, it serves it purpose 

        try {
            cmd = parser.parse(options, args);
            if(cmd.hasOption(optionExtract)) { extract = true; }
            if(cmd.hasOption(optionAnnotate)) { annotation = cmd.getOptionValue(optionAnnotate); }
            if(cmd.hasOption(optionReplace)) { replacement = cmd.getOptionValue(optionReplace); }
            if(cmd.hasOption(optionMonochrome)) { monochrome = true; }
            if(cmd.hasOption(optionResize)) { resizeFile = true; }
            if(cmd.hasOption(optionClobberMacro)) { clobberMacro = true; }
            if(cmd.hasOption(optionBarCode)) { barCode = true; }
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

                    BufferedImage image = null;
                    // using monochrome for the label to keep the size small,
                    // otherwise it might not fit in the available space and
                    // most labels are monochrome, anyway
                    if(monochrome) {
                        image = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_BYTE_BINARY);
                    }
                    else {
                        image = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_3BYTE_BGR);
                    }
                    for(int stripIndex = 0; stripIndex < tiffDir.stripOffsetsInSVS.length; stripIndex++) {
                        ByteArrayInputStream bis = new ByteArrayInputStream(svsFile.getBytes(tiffDir.stripOffsetsInSVS[stripIndex], tiffDir.stripOffsetsInSVS[stripIndex] + tiffDir.stripLengths[stripIndex]));
                        ByteBuffer bb = ByteBuffer.allocate(1024 * 1024 * 10);
                        Decoder lzwDecoder = LZWDecoder.create(false);
                        lzwDecoder.decode(bis, bb);
                        ((Buffer)bb).flip();
                        for(int y = tiffDir.rowsPerStrip * stripIndex; y < (int)Math.min(tiffDir.rowsPerStrip * (stripIndex + 1), tiffDir.height); y++){
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

                    BufferedImage image = null;
                    // using monochrome for the label to keep the size small,
                    // otherwise it might not fit in the available space and
                    // most labels are monochrome, anyway
                    if(monochrome) {
                        image = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_BYTE_BINARY);
                    }
                    else {
                        image = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_3BYTE_BGR);
                    }
                    for(int stripIndex = 0; stripIndex < tiffDir.stripOffsetsInSVS.length; stripIndex++) {
                        ByteArrayInputStream bis = new ByteArrayInputStream(svsFile.getBytes(tiffDir.stripOffsetsInSVS[stripIndex], tiffDir.stripOffsetsInSVS[stripIndex] + tiffDir.stripLengths[stripIndex]));
                        ByteBuffer bb = ByteBuffer.allocate(1024 * 1024 * 10);
                        Decoder lzwDecoder = LZWDecoder.create(false);
                        lzwDecoder.decode(bis, bb);
                        bb.flip();
                        for(int y = tiffDir.rowsPerStrip * stripIndex; y < (int)Math.min(tiffDir.rowsPerStrip * (stripIndex + 1), tiffDir.height); y++){
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
                    }
                    // using monochrome for the label to keep the size small,
                    // otherwise it might not fit in the available space and
                    // most labels are monochrome, anyway
                    BufferedImage imageAnnotated = null;
                    if(monochrome) {
                        imageAnnotated = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_BYTE_BINARY);
                    }
                    else {
                        imageAnnotated = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_3BYTE_BGR);
                    }
                    Graphics2D graphics = imageAnnotated.createGraphics();
                    graphics.drawImage(image, 0, 0, (int)(tiffDir.width * 1.00f), (int)(tiffDir.height * 1.00f), null); // scaling the original is possible
                    graphics.setColor(Color.WHITE);
                    graphics.setFont(new Font("TimesRoman", Font.PLAIN, 50));
                    graphics.drawString(annotation, 5, graphics.getFontMetrics().getHeight() + 20);
                    List<byte[]> stripByteList = new ArrayList<>();
                    for(int stripIndex = 0; stripIndex < tiffDir.stripOffsetsInSVS.length; stripIndex++) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ByteBuffer bb = ByteBuffer.allocate(1024 * 1024 * 10);
                        int rowsActuallyWritten = 0;
                        for(int y = tiffDir.rowsPerStrip * stripIndex; y < (int)Math.min(tiffDir.rowsPerStrip * (stripIndex + 1), tiffDir.height); y++){
                            rowsActuallyWritten++;
                            byte r = (byte)((imageAnnotated.getRGB(0, y) & 0x00ff0000) >> 16);
                            byte g = (byte)((imageAnnotated.getRGB(0, y) & 0x0000ff00) >>  8);
                            byte b = (byte)((imageAnnotated.getRGB(0, y) & 0x000000ff) >>  0);
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
                        Encoder lzwEncoder = new LZWEncoder(tiffDir.width * rowsActuallyWritten * 3);
                        lzwEncoder.encode(bos, bb);
                        stripByteList.add(bos.toByteArray());
                    }
                    int bytesAvailable = Arrays.stream(tiffDir.stripLengths).sum();
                    int bytesRequired = stripByteList.stream().mapToInt(x -> x.length).sum();
                    if(bytesRequired > bytesAvailable && !resizeFile) {
                        logger.log(Level.SEVERE, "annotated label exceeds bytes available in SVS for label - use resize option");
                        System.exit(1);
                    }
// ^^ resize logic ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    List<SVSFile.ResizeSegment> resizeSegmentList = new ArrayList<>();
                    if(resizeFile) {
                        SVSFile.ResizeSegment resizeSegment = new SVSFile.ResizeSegment(0, bytesRequired - bytesAvailable);
                        resizeSegmentList.add(resizeSegment);
                        if(bytesRequired < bytesAvailable) {
                            resizeSegment.start = tiffDir.stripOffsetsInSVS[0] + bytesRequired;
                        }
                        else if(bytesRequired > bytesAvailable) {
                            resizeSegment.start = tiffDir.stripOffsetsInSVS[0] + bytesAvailable;
                        }
                    }
                    if(resizeFile) { svsFile.resize(resizeSegmentList.stream().filter(x -> x.length > 0).collect(Collectors.toList())); }
                    tiffDir = svsFile.tiffDirList.get(Integer.valueOf(tiffDir.id)); // SVS reparsed
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    long offsetInSVS = tiffDir.stripOffsetsInSVS[0];
                    for(int stripIndex = 0; stripIndex < tiffDir.stripOffsetsInSVS.length; stripIndex++) {
                        svsFile.setBytesToLong(tiffDir.stripOffsetsInSVSOffsetInSVS[stripIndex], offsetInSVS);
                        svsFile.setBytesToLong(tiffDir.stripLengthsOffsetInSVS[stripIndex], stripByteList.get(stripIndex).length);
                        svsFile.setBytes(offsetInSVS, offsetInSVS + stripByteList.get(stripIndex).length, stripByteList.get(stripIndex));
                        offsetInSVS += stripByteList.get(stripIndex).length;
                    }
// ^^ resize logic ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    if(resizeFile) { svsFile.resize(resizeSegmentList.stream().filter(x -> x.length < 0).collect(Collectors.toList())); }
                    tiffDir = svsFile.tiffDirList.get(Integer.valueOf(tiffDir.id)); // SVS reparsed
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    svsFile.write((new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_label_annotated.svs"));
                    logger.log(Level.INFO, String.format("slide with annotated label written to %s in current directory", (new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_label_annotated.svs")));

                    break;

                }
                
            }
            
        }
        
        if(replacement != null) {

            for(TIFFDir tiffDir : svsFile.tiffDirList) {

                if(tiffDir.subfileType == 1) {

                    // using monochrome for the label to keep the size small,
                    // otherwise it might not fit in the available space and
                    // most labels are monochrome, anyway
                    BufferedImage imageReplaced = null;
                    if(monochrome) {
                        imageReplaced = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_BYTE_BINARY);
                    }
                    else {
                        imageReplaced = new BufferedImage(tiffDir.width, tiffDir.height, BufferedImage.TYPE_3BYTE_BGR);
                    }
                    Graphics2D graphics = imageReplaced.createGraphics();
                    graphics.setColor(Color.WHITE);
                    graphics.setFont(new Font("TimesRoman", Font.PLAIN, 70));
                    int yStart = graphics.getFontMetrics().getHeight() + 20;
                    for(String replacementLine : replacement.split("<br/>")) {
                        graphics.drawString(replacementLine, 5, yStart);
                        yStart += graphics.getFontMetrics().getHeight() + 10;
                    }
                    if(barCode) {
                        DataMatrixBean dataMatrixBean = new DataMatrixBean();
                        BitmapCanvasProvider canvas = new BitmapCanvasProvider(1000, BufferedImage.TYPE_BYTE_GRAY, true, 0);
                        dataMatrixBean.generateBarcode(canvas, replacement);
                        canvas.finish();
                        graphics.drawImage(canvas.getBufferedImage(), 10, 180, null);
                        //graphics.setFont(new Font("TimesRoman", Font.PLAIN, 60));
                        //graphics.drawString("TESTING", 280, 230);
                        //graphics.drawString("TESTING", 280, 300);
                        //graphics.drawString("TESTING", 280, 370);
                    }
                    List<byte[]> stripByteList = new ArrayList<>();
                    for(int stripIndex = 0; stripIndex < tiffDir.stripOffsetsInSVS.length; stripIndex++) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ByteBuffer bb = ByteBuffer.allocate(1024 * 1024 * 10);
                        int rowsActuallyWritten = 0;
                        for(int y = tiffDir.rowsPerStrip * stripIndex; y < (int)Math.min(tiffDir.rowsPerStrip * (stripIndex + 1), tiffDir.height); y++){
                            rowsActuallyWritten++;
                            byte r = (byte)((imageReplaced.getRGB(0, y) & 0x00ff0000) >> 16);
                            byte g = (byte)((imageReplaced.getRGB(0, y) & 0x0000ff00) >>  8);
                            byte b = (byte)((imageReplaced.getRGB(0, y) & 0x000000ff) >>  0);
                            bb.put(r);
                            bb.put(g);
                            bb.put(b);
                            for(int x = 0; x < tiffDir.width; x++) {
                                // create horizontal differencing
                                if(x > 0) {
                                    bb.put((byte)(((imageReplaced.getRGB(x, y) & 0x00ff0000) >> 16) - r));
                                    bb.put((byte)(((imageReplaced.getRGB(x, y) & 0x0000ff00) >>  8) - g));
                                    bb.put((byte)(((imageReplaced.getRGB(x, y) & 0x000000ff) >>  0) - b));
                                    r  = (byte)((imageReplaced.getRGB(x, y) & 0x00ff0000) >> 16);
                                    g  = (byte)((imageReplaced.getRGB(x, y) & 0x0000ff00) >>  8);
                                    b  = (byte)((imageReplaced.getRGB(x, y) & 0x000000ff) >>  0);
                                }
                            }
                        }
                        ((Buffer)bb).flip();
                        Encoder lzwEncoder = new LZWEncoder(tiffDir.width * rowsActuallyWritten * 3);
                        lzwEncoder.encode(bos, bb);
                        stripByteList.add(bos.toByteArray());
                    }
                    int bytesAvailable = Arrays.stream(tiffDir.stripLengths).sum();
                    int bytesRequired = stripByteList.stream().mapToInt(x -> x.length).sum();
                    if(bytesRequired > bytesAvailable && !resizeFile) {
                        logger.log(Level.SEVERE, "annotated label exceeds bytes available in SVS for label - use resize option");
                        System.exit(1);
                    }
// ^^ resize logic ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    List<SVSFile.ResizeSegment> resizeSegmentList = new ArrayList<>();
                    if(resizeFile) {
                        SVSFile.ResizeSegment resizeSegment = new SVSFile.ResizeSegment(0, bytesRequired - bytesAvailable);
                        resizeSegmentList.add(resizeSegment);
                        if(bytesRequired < bytesAvailable) {
                            resizeSegment.start = tiffDir.stripOffsetsInSVS[0] + bytesRequired;
                        }
                        else if(bytesRequired > bytesAvailable) {
                            resizeSegment.start = tiffDir.stripOffsetsInSVS[0] + bytesAvailable;
                        }
                    }
                    else {
                        logger.log(Level.INFO, String.format("clobbering %d label bytes", bytesAvailable - bytesRequired));
                        for(int zz = 0; zz < bytesAvailable - bytesRequired; zz++) {
                            svsFile.setByte(tiffDir.stripOffsetsInSVS[0] + bytesRequired + zz, (byte)0x00);
                        }
                    }
                    if(resizeFile) { svsFile.resize(resizeSegmentList.stream().filter(x -> x.length > 0).collect(Collectors.toList())); }
                    tiffDir = svsFile.tiffDirList.get(Integer.valueOf(tiffDir.id)); // SVS reparsed
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    long offsetInSVS = tiffDir.stripOffsetsInSVS[0];
                    for(int stripIndex = 0; stripIndex < tiffDir.stripOffsetsInSVS.length; stripIndex++) {
                        svsFile.setBytesToLong(tiffDir.stripOffsetsInSVSOffsetInSVS[stripIndex], offsetInSVS);
                        svsFile.setBytesToLong(tiffDir.stripLengthsOffsetInSVS[stripIndex], stripByteList.get(stripIndex).length);
                        svsFile.setBytes(offsetInSVS, offsetInSVS + stripByteList.get(stripIndex).length, stripByteList.get(stripIndex));
                        offsetInSVS += stripByteList.get(stripIndex).length;
                    }
// ^^ resize logic ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    if(resizeFile) { svsFile.resize(resizeSegmentList.stream().filter(x -> x.length < 0).collect(Collectors.toList())); }
                    tiffDir = svsFile.tiffDirList.get(Integer.valueOf(tiffDir.id)); // SVS reparsed
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    //svsFile.write((new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_" + replacement + ".svs"));
                    //logger.log(Level.INFO, String.format("slide with replaced label written to %s in current directory", (new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_" + replacement + ".svs")));
                    if(!clobberMacro) {
                        svsFile.write((new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_relabeled.svs"));
                        logger.log(Level.INFO, String.format("slide with replaced label written to %s in current directory", (new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_label_replaced.svs")));
                    }
                    
                    break;
                    
                }
                
            }
            
        }

        // this is quickie and needs to be improved
        if(clobberMacro) {

            // the macro always seems to be last
            TIFFDir tiffDir = svsFile.tiffDirList.get(svsFile.tiffDirList.size() - 1);

// I need to make this work for classic TIFF strips. Right now, this only works
// for the GT450, which doesn't use classic TIFF strips. See "LabelUtil,"
// which does work with classic TIFF strips to see how to do this.

            byte[] imageBytes = svsFile.getBytes(tiffDir.stripOffsetsInSVS[0], tiffDir.stripOffsetsInSVS[0] + tiffDir.stripLengths[0]);
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            
            // GT450 uses APP 14 Adobe (not JFIF)!
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
            ImageReader reader = readers.next();
            ImageInputStream imageInputStream = new MemoryCacheImageInputStream(bais);
            reader.setInput(imageInputStream);
            BufferedImage image = reader.read(0);
            Graphics2D graphics = image.createGraphics();
            graphics.drawImage(image, 0, 0, (int)(tiffDir.width * 1.00f), (int)(tiffDir.height * 1.00f), null); // scaling the original is possible
            graphics.fillRect(0, 0, 250, tiffDir.height);
            IIOMetadata imageMetadata = reader.getImageMetadata(0);
            IIOImage iioImage = new IIOImage(image, null, null);
            iioImage.setMetadata(imageMetadata);
            Iterator iter = ImageIO.getImageWritersByFormatName("jpeg");            
            ImageWriter writer = (ImageWriter)iter.next();
            JPEGImageWriteParam iwp = (JPEGImageWriteParam)writer.getDefaultWriteParam();
            iwp.setCompressionMode(MODE_COPY_FROM_METADATA);
            ByteArrayOutputStream imageOutputStreamByteStream = new ByteArrayOutputStream(10000000);
            ImageOutputStream imageOutputStream = new MemoryCacheImageOutputStream(imageOutputStreamByteStream);
            writer.setOutput(imageOutputStream);
            writer.write(null, iioImage, iwp);
            imageOutputStream.flush();
            byte[] imageBytesClobbered = imageOutputStreamByteStream.toByteArray();
            
            int bytesAvailable = imageBytes.length;
            int bytesRequired = imageBytesClobbered.length;
            if(bytesRequired > bytesAvailable && !resizeFile) {
                logger.log(Level.SEVERE, "annotated macro exceeds bytes available in SVS for macro - use resize option");
                System.exit(1);
            }
// ^^ resize logic ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            List<SVSFile.ResizeSegment> resizeSegmentList = new ArrayList<>();
            if(resizeFile) {
                SVSFile.ResizeSegment resizeSegment = new SVSFile.ResizeSegment(0, bytesRequired - bytesAvailable);
                resizeSegmentList.add(resizeSegment);
                if(bytesRequired < bytesAvailable) {
                    resizeSegment.start = tiffDir.stripOffsetsInSVS[0] + bytesRequired;
                }
                else if(bytesRequired > bytesAvailable) {
                    resizeSegment.start = tiffDir.stripOffsetsInSVS[0] + bytesAvailable;
                }
            }
            else {
                logger.log(Level.INFO, String.format("clobbering %d macro bytes", bytesAvailable - bytesRequired));
                for(int zz = 0; zz < bytesAvailable - bytesRequired; zz++) {
                    svsFile.setByte(tiffDir.stripOffsetsInSVS[0] + bytesRequired + zz, (byte)0x00);
                }
            }
            if(resizeFile) { svsFile.resize(resizeSegmentList.stream().filter(x -> x.length > 0).collect(Collectors.toList())); }
            tiffDir = svsFile.tiffDirList.get(Integer.valueOf(tiffDir.id)); // SVS reparsed
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            long offsetInSVS = tiffDir.stripOffsetsInSVS[0];
            //svsFile.setBytesToLong(tiffDir.stripOffsetsInSVSOffsetInSVS[0], offsetInSVS);
            svsFile.setBytesToLong(tiffDir.stripLengthsOffsetInSVS[0], imageBytesClobbered.length);
            svsFile.setBytes(offsetInSVS, offsetInSVS + imageBytesClobbered.length, imageBytesClobbered);
// ^^ resize logic ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            if(resizeFile) { svsFile.resize(resizeSegmentList.stream().filter(x -> x.length < 0).collect(Collectors.toList())); }
            tiffDir = svsFile.tiffDirList.get(Integer.valueOf(tiffDir.id)); // SVS reparsed
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            svsFile.write((new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_relabeled.svs"));
            logger.log(Level.INFO, String.format("slide with replaced label written to %s in current directory", (new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_relabeled.svs")));
            
        }
        
    }
    
}

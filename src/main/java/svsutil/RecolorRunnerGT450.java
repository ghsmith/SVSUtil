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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Features of GT450 SVS files:
 * 
 * The GT450 SVS files are easy-peesy compared to the AT2 SVS files. Each tile
 * in the GT450 SVS file is a completely atomic JFIF. I can't believe how
 * much harder the AT2 SVS files are to work with than the GT450 SVS files.
 * In particular, you don't have to worry about separate JPEG tables or
 * "natitive RGB" (application segment 14 "Adobe") JPEG encoding with the
 * GT450. I am liking the GT450 more and more than the AT2 every day.
 * 
 * @author geoffrey.smith@emory.edu
 */
public class RecolorRunnerGT450 extends RecolorRunner {

    static final Logger logger = Logger.getLogger(RecolorRunnerGT450.class.getName());    

    public RecolorRunnerGT450(SVSFile svsFile, int quality, int skip, boolean noRecolor, boolean annotate, int startWithTiffDirIndex) {
        super(svsFile, quality, skip, noRecolor, annotate, startWithTiffDirIndex);
    }

    @Override
    public void run() {
        try {
            
            final PipedOutputStream outputStream = new PipedOutputStream();
            final PipedInputStream inputStream = new PipedInputStream(outputStream, 1000000);
            final Queue<String> tileIdQueue = new ConcurrentLinkedQueue<>();
            final int tileWidth = svsFile.tiffDirList.get(0).tileWidth;
            final int tileHeight = svsFile.tiffDirList.get(0).tileHeight;

            // doing the JPEG encoding and decoding in a separate thread
            // allows me to use use Java ImageIO streaming with piped streams
            // and use a single image reader and single image writer for all
            // of the tiles; I think this might be the fastest way to do JPEG
            // decoding and encoding versus using ImageIO in a more conventional
            // fashion
            Thread jpegStreamThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    
                    try {

                        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
                        ImageReader reader = readers.next();
                        reader.addIIOReadWarningListener((ImageReader source, String warning) -> {
                            System.err.println(warning);
                            System.exit(1);
                        });
                        ImageInputStream imageInputStream = new MemoryCacheImageInputStream(inputStream);
                        reader.setInput(imageInputStream);

                        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");            
                        ImageWriter writer = writers.next();
                        JPEGImageWriteParam iwp = (JPEGImageWriteParam)writer.getDefaultWriteParam();
                        iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        iwp.setCompressionQuality(quality / 100f);
                        ByteArrayOutputStream imageOutputStreamByteStream = new ByteArrayOutputStream(10000000);
                        ImageOutputStream imageOutputStream = new MemoryCacheImageOutputStream(imageOutputStreamByteStream);
                        writer.setOutput(imageOutputStream);

                        int imageIndex = 0;

                        while(true) {

                            BufferedImage image = null;
                            try {
                                image = reader.read(imageIndex);
                            }
                            catch(IndexOutOfBoundsException e) {
                                break; // this will happen when the pipe closes
                            }
                            
                            String tileId = tileIdQueue.remove();

                            final int[] imagePixels = new int[tileWidth * tileHeight];
                            image.getRGB(0, 0, tileWidth, tileHeight, imagePixels, 0, tileWidth);
                            if(!noRecolor) {
                                Arrays.parallelSetAll(imagePixels, i -> svsFile.lutUpsampledInt[imagePixels[i] & 0x00ffffff]);
                            }
                            image.setRGB(0, 0, tileWidth, tileHeight, imagePixels, 0, tileWidth);
                            
                            if(annotate) {
                                TIFFDir tiffDir = svsFile.getTIFFDirForTileId(tileId);
                                TIFFTileContig tileContig = svsFile.getTileContigForTileId(tileId);
                                int tileIndex = svsFile.getTileIndexForTileId(tileId);
                                int tileX = (tileIndex) % (int)Math.ceil(1f * tiffDir.width / tiffDir.tileWidth);
                                int tileY = (tileIndex) / (int)Math.ceil(1f * tiffDir.width / tiffDir.tileHeight);
                                Graphics2D graphics = image.createGraphics();
                                graphics.setColor(Color.BLACK);
                                graphics.setStroke(new BasicStroke(5f));
                                graphics.drawLine(0, 0, 10, 10);
                                graphics.drawLine(0, tiffDir.tileHeight - 1, 10, tiffDir.tileHeight - 11);
                                graphics.drawLine(tiffDir.tileWidth - 1, tiffDir.tileHeight - 1, tiffDir.tileWidth - 1, tiffDir.tileHeight - 11);
                                graphics.drawLine(tiffDir.tileWidth - 1, 0, tiffDir.tileWidth - 11, 10);
                                graphics.setFont(new Font("TimesRoman", Font.BOLD, 30));
                                FontMetrics metrics = graphics.getFontMetrics();
                                graphics.drawString(tileId, 20, 1 * (metrics.getHeight() + 20));
                                graphics.drawString(String.format("%d x %d", tileX, tileY), 20, 2 * (metrics.getHeight() + 20));                            
                                graphics.drawString(String.format("%4.2f mpp", tiffDir.mpp), 20, 3 * (metrics.getHeight() + 20));                            
                            }
                            
                            IIOMetadata imageMetadata = reader.getImageMetadata(imageIndex);
                            IIOImage iioImage = new IIOImage(image, null, null);
                            iioImage.setMetadata(imageMetadata);
                            imageOutputStreamByteStream.reset();
                            writer.write(null, iioImage, iwp);
                            imageOutputStream.flush();
                            svsFile.setRecoloredTileBytesForTileId(tileId, imageOutputStreamByteStream.toByteArray());

                            imageIndex++;

                        }

                    }
                    catch(IOException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                    
                }    
                    
            });
            jpegStreamThread.start();
            
            int tileNo = -1;
            for(int x = startWithTiffDirIndex; x < svsFile.tiffDirList.size(); x++) {
                TIFFDir tiffDir = svsFile.tiffDirList.get(x);
                for(int y = 0; y < tiffDir.tileContigList.size(); y++) {
                    TIFFTileContig tileContig = tiffDir.tileContigList.get(y);
                    for(int z = 0; z < tileContig.tagTileOffsetsInSvs.length; z++) {
                        tileNo++;
                        synchronized(svsFile.nextTileNo) {
                            if(tileNo < svsFile.nextTileNo) {
                                continue;
                            }
                            for(int a = 0; a < skip; a++) {
                                tileContig.recoloredTileBytesMap.put(z + a + 1, svsFile.getBytes(tileContig.tagTileOffsetsInSvs[z + a + 1], tileContig.tagTileOffsetsInSvs[z + a + 1] + tileContig.tagTileLengths[z + a + 1]));
                            }
                            svsFile.nextTileNo += skip + 1;
                        }
                        tileIdQueue.add(tileContig.id + "." + String.valueOf(tileContig.firstTileIndexInTIFFDir + z));
                        outputStream.write(svsFile.getBytes(tileContig.tagTileOffsetsInSvs[z], tileContig.tagTileOffsetsInSvs[z] + tileContig.tagTileLengths[z]));
                    }
                }
            }
            outputStream.flush();
            outputStream.close();
            jpegStreamThread.join(); // mucho important, the decoder/encoder thread might be lagging
                
        }
        catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        
    }

}

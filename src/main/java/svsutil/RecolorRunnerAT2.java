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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import static javax.imageio.ImageWriteParam.MODE_COPY_FROM_METADATA;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.plugins.jpeg.JPEGHuffmanTable;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.plugins.jpeg.JPEGQTable;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.w3c.dom.NodeList;

/**
 * Features of AT2 SVS files:
 * 
 * 1. The JPG tiles in the SVS file do not have an application segment.
 * 2. The JPG tiles in the SVS file to not have quantization or Huffman tables.
 * 3. The tiles are JPG-encoded RGB, not JPG encoded YCrCb, so don't use an
 *    "APP0 JFIF" segment, use an "APP14 Adobe" segment if you want to view
 *    them.
 * 4. The JPG tables are stored separately in the TIFF. This recolor routine
 *    takes special pains to write the recolored image tiles using the original
 *    JPG tables.
 * 5. Because I am using the original JPG tables to encode the recolored tiles,
 *    the image quality setting the program is run with is ignored.
 * 
 * @author geoffrey.smith@emory.edu
 */
public class RecolorRunnerAT2 extends RecolorRunner {

    static final Logger logger = Logger.getLogger(RecolorRunnerAT2.class.getName());    

    public RecolorRunnerAT2(SVSFile svsFile, int quality, int skip, boolean noRecolor, boolean annotate, int startWithTiffDirIndex) {
        super(svsFile, quality, skip, noRecolor, annotate, startWithTiffDirIndex);
    }

    @Override
    public void run() {
        try {
            
            final PipedOutputStream outputStream = new PipedOutputStream();
            final PipedInputStream inputStream = new PipedInputStream(outputStream, 1000000);
            final Queue<String> tileIdQueue = new ConcurrentLinkedQueue<>();

            // doing the JPEG encoding and decoding in a separate thread
            // allows me to use use Java ImageIO streaming with piped streams
            // and use a single image reader and single image writer for all
            // of the tiles; I think this might be the fastest way to do JPEG
            // decoding and encoding versus using ImageIO in a more conventional
            // fashion
            Thread jpgetStreamThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    
                    try {

                        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
                        ImageReader reader = readers.next();
                        reader.addIIOReadWarningListener((ImageReader source, String warning) -> {
                            System.out.println(warning);
                            System.exit(1);
                        });
                        ImageInputStream imageInputStream = new MemoryCacheImageInputStream(inputStream);
                        reader.setInput(imageInputStream);

                        Iterator iter = ImageIO.getImageWritersByFormatName("jpeg");            
                        ImageWriter writer = (ImageWriter)iter.next();
                        JPEGImageWriteParam iwp = (JPEGImageWriteParam)writer.getDefaultWriteParam();
                        // Unless MODE_COPY_FROM_METADATA, tables will be created! With
                        // the AT2 SVS files, the JPEG tables are stored separately from
                        // tile bytes and the same JPEG tables are used for all tiles,
                        // to we don't want the JPEG tables in the tile data with an
                        // AT2 SVS.
                        iwp.setCompressionMode(MODE_COPY_FROM_METADATA);
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

                            // THE JPEG TABLES IN THE SVS MUST BE THE FIRST
                            // THING IN THE JPEG STREAM!
                            if(iwp.getQTables() == null || iwp.getDCHuffmanTables() == null || iwp.getACHuffmanTables() == null) {

                                IIOMetadataNode node = (IIOMetadataNode)reader.getStreamMetadata().getAsTree("javax_imageio_jpeg_stream_1.0");

                                List<JPEGQTable> jpegQTableList = new java.util.ArrayList<>();
                                {
                                    NodeList nodeList = node.getElementsByTagName("dqt");
                                    for(int x = 0; x < nodeList.getLength(); x ++) {
                                        jpegQTableList.add((JPEGQTable)((IIOMetadataNode)nodeList.item(x).getFirstChild()).getUserObject());
                                    }
                                }

                                List<JPEGHuffmanTable> jpegHuffmanTableListClass0 = new java.util.ArrayList<>();
                                List<JPEGHuffmanTable> jpegHuffmanTableListClass1 = new java.util.ArrayList<>();
                                {
                                    NodeList nodeList = node.getElementsByTagName("dht");
                                    for(int x = 0; x < nodeList.getLength(); x ++) {
                                        if(nodeList.item(x).getFirstChild().getAttributes().getNamedItem("class").getNodeValue().equals("0")) {
                                            jpegHuffmanTableListClass0.add((JPEGHuffmanTable)((IIOMetadataNode)nodeList.item(x).getFirstChild()).getUserObject());
                                        }
                                        else if(nodeList.item(x).getFirstChild().getAttributes().getNamedItem("class").getNodeValue().equals("1")) {
                                            jpegHuffmanTableListClass1.add((JPEGHuffmanTable)((IIOMetadataNode)nodeList.item(x).getFirstChild()).getUserObject());
                                        }
                                    }
                                }

                                iwp.setEncodeTables(
                                    jpegQTableList.toArray(new JPEGQTable[2]),
                                    jpegHuffmanTableListClass0.toArray(new JPEGHuffmanTable[2]),
                                    jpegHuffmanTableListClass1.toArray(new JPEGHuffmanTable[2])
                                );

                            }


                            final int[] imagePixels = new int[0x100 * 0x100];
                            image.getRGB(0, 0, 0x100, 0x100, imagePixels, 0, 0x100);
                            if(!noRecolor) {
                                Arrays.parallelSetAll(imagePixels, i -> svsFile.lutUpsampledInt[imagePixels[i] & 0x00ffffff]);
                            }
                            image.setRGB(0, 0, 0x100, 0x100, imagePixels, 0, 0x100);
                            
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
                                graphics.drawLine(tiffDir.width - 1, tiffDir.tileHeight - 1, tiffDir.width - 1, tiffDir.tileHeight - 11);
                                graphics.drawLine(tiffDir.width - 1, 0, tiffDir.width - 11, 10);
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
            jpgetStreamThread.start();
            
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
                            tileIdQueue.add(tileContig.id + "." + String.valueOf(tileContig.firstTileIndexInTIFFDir + z));
                        }
                        outputStream.write(svsFile.getBytes(tileContig.tagTileOffsetsInSvs[z], tileContig.tagTileOffsetsInSvs[z] + tileContig.tagTileLengths[z]));
                    }
                }
            }
            outputStream.flush();
            outputStream.close();
            jpgetStreamThread.join(); // mucho important, the decoder/encoder thread might be lagging
                
        }
        catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        
    }

}

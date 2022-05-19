package svsutil;

import svsutil.jmist.Interpolater;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 *
 * @author geoff
 */
public class SVSUtil {

    public static final int LD = 4; // LUTS_DOWNSAMPLE
    public static final float LDf = 4; // LUTS_DOWNSAMPLE
    public static final int R = 0;
    public static final int G = 1;
    public static final int B = 2;

    public static short[][][][] LUT = new short[256][256][256][3];
    public static byte[] svsIn = null;
    public static List<TiffTags> tiffs = new ArrayList<>();
    public static List<TileRow> tileRows = new ArrayList<>();

    //public static byte[] hack = null;
    
    public static void main(String[] args) throws IOException, InterruptedException {

        System.out.println("Loading LUT...");
        {

            File file = new File("c:/scanner/GT450_64.cube"); 
            BufferedReader br = new BufferedReader(new FileReader(file));
            br.readLine();
            br.readLine();
            br.readLine();
            for(int bx = 0; bx < 256; bx+=LD) {
                for(int gx = 0; gx < 256; gx+=LD) {
                    for(int rx = 0; rx < 256; rx+=LD) {
                        String[] lineParsed = br.readLine().split(" ");
                        LUT[rx][gx][bx][R] = (short)Math.round(Float.valueOf(lineParsed[R]) * 255);
                        LUT[rx][gx][bx][G] = (short)Math.round(Float.valueOf(lineParsed[G]) * 255);
                        LUT[rx][gx][bx][B] = (short)Math.round(Float.valueOf(lineParsed[B]) * 255);
                    }
                }
            }
            if(br.readLine() != null) {
                throw new RuntimeException("The LUT file has more lines than expected.");
            }
            br.close();

	}
        System.out.println(" done.");
        
        System.out.println("Interpolating LUT...");
        {
            for(int bx = 0; bx < 256; bx++) {
                for(int gx = 0; gx < 256; gx++) {
                    for(int rx = 0; rx < 256; rx++) {
                        if(rx % LD != 0 || gx % LD != 0 || bx % LD != 0) {
                            int rxCapped = rx < 256 - LD ? rx : 256 - LD - 1;
                            int gxCapped = gx < 256 - LD ? gx : 256 - LD - 1;
                            int bxCapped = bx < 256 - LD ? bx : 256 - LD - 1;
                            short[] v000 = LUT[((rxCapped / LD) * LD) +  0][((gxCapped / LD) * LD) +  0][((bxCapped / LD) * LD) +  0];
                            short[] v100 = LUT[((rxCapped / LD) * LD) + LD][((gxCapped / LD) * LD) +  0][((bxCapped / LD) * LD) +  0];
                            short[] v010 = LUT[((rxCapped / LD) * LD) +  0][((gxCapped / LD) * LD) + LD][((bxCapped / LD) * LD) +  0];
                            short[] v110 = LUT[((rxCapped / LD) * LD) + LD][((gxCapped / LD) * LD) + LD][((bxCapped / LD) * LD) +  0];
                            short[] v001 = LUT[((rxCapped / LD) * LD) +  0][((gxCapped / LD) * LD) +  0][((bxCapped / LD) * LD) + LD];
                            short[] v101 = LUT[((rxCapped / LD) * LD) + LD][((gxCapped / LD) * LD) +  0][((bxCapped / LD) * LD) + LD];
                            short[] v011 = LUT[((rxCapped / LD) * LD) +  0][((gxCapped / LD) * LD) + LD][((bxCapped / LD) * LD) + LD];
                            short[] v111 = LUT[((rxCapped / LD) * LD) + LD][((gxCapped / LD) * LD) + LD][((bxCapped / LD) * LD) + LD];
                            LUT[rx][gx][bx][R] = (short)Math.round(Interpolater.trilinearInterpolate
                                (v000[R], v100[R], v010[R], v110[R], v001[R], v101[R], v011[R], v111[R], (rx % LD) / LDf, (gx % LD) / LDf, (bx % LD) / LDf));
                            LUT[rx][gx][bx][G] = (short)Math.round(Interpolater.trilinearInterpolate
                                (v000[G], v100[G], v010[G], v110[G], v001[G], v101[G], v011[G], v111[G], (rx % LD) / LDf, (gx % LD) / LDf, (bx % LD) / LDf));
                            LUT[rx][gx][bx][B] = (short)Math.round(Interpolater.trilinearInterpolate
                                (v000[B], v100[B], v010[B], v110[B], v001[B], v101[B], v011[B], v111[B], (rx % LD) / LDf, (gx % LD) / LDf, (bx % LD) / LDf));
                        }
                    }
                }
            }
        }
        System.out.println(" done.");
        
        System.out.println("Parsing TIFF tags in SVS file... ");
        {

            svsIn = Files.readAllBytes(Paths.get("c:/scanner/test_slide_small.svs"));                  
            
            int fileOffset = (int)bytesToLong(Arrays.copyOfRange(svsIn, 0x00000008, 0x00000010));
            while(fileOffset != 1919250497) {
                TiffTags tiffTags = new TiffTags();
                tiffTags.headerOffset = fileOffset;
    //System.out.println(fileOffset);
                int numberOfTags = (int)bytesToLong(Arrays.copyOfRange(svsIn, fileOffset, fileOffset + 0x000000f));
                tiffTags.tagNumberOfTagsVal = numberOfTags;
                tiffTags.tagNumberOfTagsRelOffset = 0;
    //System.out.println(numberOfTags);
                int tagOffset = 0x00000008;
                for(int x = 0; x < numberOfTags; x++) {
                    int tagName = bytesToInt(Arrays.copyOfRange(svsIn, fileOffset + tagOffset, fileOffset + tagOffset + 0x00000002));
                    if(tagName == 34675) {
                        tiffTags.tagICCRelOffset = tagOffset;
    svsIn[fileOffset + tagOffset + 0] = (byte)0xff;
    svsIn[fileOffset + tagOffset + 1] = (byte)0xff;
                    }
                    tagOffset+=0x00000002;
    //System.out.println("tag: " + tagName);
                    int tagDataType = bytesToInt(Arrays.copyOfRange(svsIn, fileOffset + tagOffset, fileOffset + tagOffset + 0x00000002));
                    tagOffset+=0x00000002;
    //System.out.println("type: " + tagDataType);
                    int tagValueCount = (int)bytesToLong(Arrays.copyOfRange(svsIn, fileOffset + tagOffset, fileOffset + tagOffset + 0x00000008));
                    tagOffset+=0x00000008;
    //System.out.println("count: " + tagValueCount);
                    if(tagDataType == 2) {
                        int stringOffset = (int)bytesToLong(Arrays.copyOfRange(svsIn, fileOffset + tagOffset, fileOffset + tagOffset + 0x00000008));
                        tagOffset+=0x00000008;
                        String tagValue = bytesToString(Arrays.copyOfRange(svsIn, stringOffset, stringOffset + tagValueCount));
    //System.out.println("value: " + tagValue);
                    }
                    else if(tagDataType == 16) {
                        int longOffset = (int)bytesToLong(Arrays.copyOfRange(svsIn, fileOffset + tagOffset, fileOffset + tagOffset + 0x00000008));
                        tagOffset+=0x00000008;
                        if(tagName == 324) {
                            tiffs.add(tiffTags);
                            tiffTags.tagTileOffsetsVal = new int[tagValueCount];
                            tiffTags.tagTileOffsetsOffset = new int[tagValueCount];
                        }
                        else if(tagName == 325) {
                            tiffTags.tagTileLengthsVal = new int[tagValueCount];
                            tiffTags.tagTileLengthsOffset = new int[tagValueCount];
                        }
                        for(int y = 0; y < tagValueCount; y++) {
                            int tagValue = (int)bytesToLong(Arrays.copyOfRange(svsIn, longOffset, longOffset + 0x00000008));
                            if(tagName == 324) {
                                tiffTags.tagTileOffsetsVal[y] = tagValue;
                                tiffTags.tagTileOffsetsOffset[y] = longOffset;
                            }
                            else if(tagName == 325) {
                                tiffTags.tagTileLengthsVal[y] = tagValue;
                                tiffTags.tagTileLengthsOffset[y] = longOffset;
                            }
                            longOffset+=0x00000008;
    //if(y < 10) { System.out.println("value: " + tagValue); }
                        }
                    }
                    else if(tagDataType == 7) {
                        int undefinedOffset = (int)bytesToLong(Arrays.copyOfRange(svsIn, fileOffset + tagOffset, fileOffset + tagOffset + 0x00000008));
                        tagOffset+=0x00000008;
    //System.out.println("the data is at offset " + undefinedOffset + " length " + tagValueCount);
                    }
                    else {
                        for(int y = 0; y < tagValueCount; y++) {
                            if(tagDataType == 4) {
                                int tagValue = (int)bytesToLong(Arrays.copyOfRange(svsIn, fileOffset + tagOffset, fileOffset + tagOffset + 0x00000008));
                                tagOffset+=0x00000008;
    //if(y < 10) { System.out.println("value: " + tagValue); }
                            }
                            else if(tagDataType == 3) {
                                int tagValue = (int)bytesToInt(Arrays.copyOfRange(svsIn, fileOffset + tagOffset, fileOffset + tagOffset + 0x00000002));
                                tagOffset+=0x00000002;
    //System.out.println("value: " + tagValue);
                            }
                        }
                    }
                    while(bytesToInt(Arrays.copyOfRange(svsIn, fileOffset + tagOffset, fileOffset + tagOffset + 0x00000002)) == 0) {
                        tagOffset+=0x00000002;
                    }
                }
                int nextFileOffset = (int)bytesToLong(Arrays.copyOfRange(svsIn, fileOffset + tagOffset, fileOffset + tagOffset + 0x00000008));
                tiffTags.tagNextHeaderOffsetVal = nextFileOffset;
                tiffTags.tagNextHeaderOffsetRelOffset = tagOffset;
                tagOffset+=0x00000008;
                tiffTags.headerLength = tagOffset;
    //System.out.println("next file offset: " + nextFileOffset);
                fileOffset = nextFileOffset;

            }

        }
        System.out.println(" done.");
    
    //tiffs.remove(0);
        
        System.out.println("Checking to see if tile data is contiguous...");
        {
            int fileNo = 0;
            for(TiffTags tiff : tiffs) {
                tiff.dataOffset = svsIn.length;
                int maxDataOffset = 0;
                for(int x = 0; x < tiff.tagTileOffsetsVal.length; x++) {
                    tiff.dataOffset = tiff.tagTileOffsetsVal[x] < tiff.dataOffset ? tiff.tagTileOffsetsVal[x] : tiff.dataOffset;
                    maxDataOffset = tiff.tagTileOffsetsVal[x] + tiff.tagTileLengthsVal[x] > maxDataOffset ? tiff.tagTileOffsetsVal[x] + tiff.tagTileLengthsVal[x] : maxDataOffset;
                }
                tiff.bytesAvailable = maxDataOffset - tiff.dataOffset;
    System.out.println(String.format("%d %d %d", tiff.dataOffset, maxDataOffset, tiff.bytesAvailable));
                int start = tiff.tagTileOffsetsVal[0];
                int end = tiff.tagTileOffsetsVal[0] + tiff.tagTileLengthsVal[0];
                int totalBytes = tiff.tagTileLengthsVal[0];
                int x = 1;
                while(x < tiff.tagTileOffsetsVal.length) {
                    while(x < tiff.tagTileOffsetsVal.length && tiff.tagTileOffsetsVal[x - 1] + tiff.tagTileLengthsVal[x - 1] == tiff.tagTileOffsetsVal[x]) {
                        end = tiff.tagTileOffsetsVal[x - 1] + tiff.tagTileLengthsVal[x - 1];
                        totalBytes += tiff.tagTileLengthsVal[x];
                        x++;
                    }
                    if(x < tiff.tagTileOffsetsVal.length) {
                        end = tiff.tagTileOffsetsVal[x - 1] + tiff.tagTileLengthsVal[x - 1];
                        System.out.println(String.format("File %d: tile count = %d total bytes = %d starts %d ends %d", fileNo, x, totalBytes, start, end));
                        if(fileNo == 0) {
                            tileRows.add(new TileRow(start, end-start));
                        }
                        start = tiff.tagTileOffsetsVal[x];
                        end = tiff.tagTileOffsetsVal[x] + tiff.tagTileLengthsVal[x];
                        totalBytes += tiff.tagTileLengthsVal[x];
                        x++;
                    }
                }
                end = tiff.tagTileOffsetsVal[x - 1] + tiff.tagTileLengthsVal[x - 1];
                System.out.println(String.format("File %d: tile count = %d total bytes = %d starts %d ends %d", fileNo, x, totalBytes, start, end));
                if(fileNo == 0) {
                    tileRows.add(new TileRow(start, end-start));
                    for(int z = 0; z < tiff.tagTileOffsetsVal.length; z++) {
                        for(TileRow tileRow : tileRows) {
                            if(tiff.tagTileOffsetsVal[z] >= tileRow.offset && tiff.tagTileOffsetsVal[z] < tileRow.offset + tileRow.length) {
                                if(tileRow.indexStart == -1 || z < tileRow.indexStart) {
                                    tileRow.indexStart = z;
                                }
                                else if(tileRow.indexEnd == -1 || z > tileRow.indexEnd) {
                                    tileRow.indexEnd = z;
                                }
                            }
                        }
                    }
                    for(TileRow tileRow : tileRows) {
                        System.out.println(tileRow);
                    }
                }
                fileNo++;
            }
        }
        System.out.println(" done.");

        System.out.println("Performing color transform... ");
        {
            Thread[] transformerThreads = new Thread[6];
            for(int x = 0; x < transformerThreads.length; x++) {
                transformerThreads[x] = new Thread(new Transformer(x, transformerThreads.length));
                transformerThreads[x].start();
            }
            for(int x = 0; x < transformerThreads.length; x++) {
                transformerThreads[x].join();
            }
        }
        System.out.println(" done.");
        
        System.out.println("Updating SVS file...");
        {

            int fileNo = 0;

            for(TiffTags tiff : tiffs) {

                int bytesRequired = 0;
                for(byte[] convertedTile : tiff.convertedTiles.values()) { bytesRequired += convertedTile.length; }
                System.out.println(String.format("file %d: bytes required = %d bytes available = %d", fileNo, bytesRequired, tiff.bytesAvailable));
                if(bytesRequired > tiff.bytesAvailable) {
                    //throw new rte
                    System.out.println("warning!!!");
                }
                if(fileNo == 0) {
                    for(TileRow tileRow : tileRows) {
                        int bytesRequiredRow = 0;
                        for(int x = tileRow.indexStart; x <= tileRow.indexEnd; x++) { bytesRequiredRow += tiff.convertedTiles.get(x).length; }
                        System.out.println(String.format("file %d / row %d-%d: bytes required = %d bytes available = %d", fileNo, tileRow.offset, tileRow.offset + tileRow.length, bytesRequiredRow, tileRow.length));
                        if(bytesRequiredRow > tiff.bytesAvailable) {
                            //throw new rte
                            System.out.println("warning!!!");
                        }
                        int offset = 0;
                        for(int x = tileRow.indexStart; x < tileRow.indexEnd; x++) {
                            //svsIn[tiff.headerOffset + tiff.tagICCRelOffset + 0] = (byte)0xff;
                            //svsIn[tiff.headerOffset + tiff.tagICCRelOffset + 1] = (byte)0xff;
                            byte[] convertedTileBytes = tiff.convertedTiles.get(x);
                            svsIn[tiff.tagTileOffsetsOffset[x] + 0] = (byte)(((tileRow.offset + offset) & 0x000000ff) >>  0);
                            svsIn[tiff.tagTileOffsetsOffset[x] + 1] = (byte)(((tileRow.offset + offset) & 0x0000ff00) >>  8);
                            svsIn[tiff.tagTileOffsetsOffset[x] + 2] = (byte)(((tileRow.offset + offset) & 0x00ff0000) >> 16);
                            svsIn[tiff.tagTileOffsetsOffset[x] + 3] = (byte)(((tileRow.offset + offset) & 0xff000000) >> 24);
                            svsIn[tiff.tagTileLengthsOffset[x] + 0] = (byte)((convertedTileBytes.length & 0x000000ff) >>  0);
                            svsIn[tiff.tagTileLengthsOffset[x] + 1] = (byte)((convertedTileBytes.length & 0x0000ff00) >>  8);
                            svsIn[tiff.tagTileLengthsOffset[x] + 2] = (byte)((convertedTileBytes.length & 0x00ff0000) >> 16);
                            svsIn[tiff.tagTileLengthsOffset[x] + 3] = (byte)((convertedTileBytes.length & 0xff000000) >> 24);
                            for(int y = 0; y < convertedTileBytes.length; y++) {
                                svsIn[tileRow.offset + offset + y] = convertedTileBytes[y];
                            }
                            offset += convertedTileBytes.length;
                        }
                    }
                }
                else {
                    int offset = 0;
                    for(int x = 0; x < tiff.tagTileOffsetsOffset.length; x++) {
                        //svsIn[tiff.headerOffset + tiff.tagICCRelOffset + 0] = (byte)0xff;
                        //svsIn[tiff.headerOffset + tiff.tagICCRelOffset + 1] = (byte)0xff;
                        byte[] convertedTileBytes = tiff.convertedTiles.get(x);
                        svsIn[tiff.tagTileOffsetsOffset[x] + 0] = (byte)(((tiff.tagTileOffsetsVal[0] + offset) & 0x000000ff) >>  0);
                        svsIn[tiff.tagTileOffsetsOffset[x] + 1] = (byte)(((tiff.tagTileOffsetsVal[0] + offset) & 0x0000ff00) >>  8);
                        svsIn[tiff.tagTileOffsetsOffset[x] + 2] = (byte)(((tiff.tagTileOffsetsVal[0] + offset) & 0x00ff0000) >> 16);
                        svsIn[tiff.tagTileOffsetsOffset[x] + 3] = (byte)(((tiff.tagTileOffsetsVal[0] + offset) & 0xff000000) >> 24);
                        svsIn[tiff.tagTileLengthsOffset[x] + 0] = (byte)((convertedTileBytes.length & 0x000000ff) >>  0);
                        svsIn[tiff.tagTileLengthsOffset[x] + 1] = (byte)((convertedTileBytes.length & 0x0000ff00) >>  8);
                        svsIn[tiff.tagTileLengthsOffset[x] + 2] = (byte)((convertedTileBytes.length & 0x00ff0000) >> 16);
                        svsIn[tiff.tagTileLengthsOffset[x] + 3] = (byte)((convertedTileBytes.length & 0xff000000) >> 24);
                        for(int y = 0; y < convertedTileBytes.length; y++) {
                            svsIn[tiff.dataOffset + offset + y] = convertedTileBytes[y];
                        }
                        offset += convertedTileBytes.length;
                    }
                }
                
                fileNo++;

            }
            
            Files.write(Paths.get("c:/scanner/out.svs"), svsIn);

        }
        System.out.println(" done.");
        
        
    }
    
    public static long bytesToLong(byte[] bs) {
        long val = 0;
        val = (long)bs[0] <<  0 & (long)0x000000ff
            | (long)bs[1] <<  8 & (long)0x0000ff00
            | (long)bs[2] << 16 & (long)0x00ff0000
            | (long)bs[3] << 24 & (long)0xff000000
            | (long)bs[4] << 32 & (long)0x000000ff <<  8
            | (long)bs[5] << 40 & (long)0x0000ff00 << 16
            | (long)bs[6] << 48 & (long)0x00ff0000 << 24;
        return val;
    }

    public static int bytesToInt(byte[] bs) {
        int val = 0;
        val = (int)bs[0] <<  0 & (int)0x000000ff
            | (int)bs[1] <<  8 & (int)0x0000ff00;
        return val;
    }
    
    public static String bytesToString(byte[] bs) {
        String val = new String(bs);
        return val;
    }

    public static class TiffTags {
        
        public int headerOffset;
        public int headerLength;

        public int[] tagTileOffsetsVal;
        public int[] tagTileOffsetsOffset;
        public int[] tagTileLengthsVal;
        public int[] tagTileLengthsOffset;

        public int tagNumberOfTagsVal;
        public int tagNumberOfTagsRelOffset;

        public int tagICCRelOffset;

        public int tagNextHeaderOffsetVal;
        public int tagNextHeaderOffsetRelOffset;
        
        public int dataOffset;
        public int bytesAvailable;
        
        public Map<Integer, byte[]> convertedTiles = new ConcurrentHashMap<>();
        
    }

    public static class TileRow {
        
        public int offset;
        public int length;

        public int indexStart = -1;
        public int indexEnd = -1;

        public TileRow(int offset, int length) {
            this.offset = offset;
            this.length = length;
        }

        @Override
        public String toString() {
            return String.format("{ offset=%s, length=%s, indexStart=%s, indexEnd=%s }", offset, length, indexStart, indexEnd);
        }
        
        
        
    }
    
    public static class Transformer implements Runnable {

        public int threadNo;
        public int tileNoNext;
        public int tileNoSkip;

        public Transformer(int tileNoNext, int tileNoSkip) {
            this.threadNo = tileNoNext;
            this.tileNoNext = tileNoNext;
            this.tileNoSkip = tileNoSkip;
        }
        
        public void run() {
            
            try {

                Iterator iter = ImageIO.getImageWritersByFormatName("jpeg");            
                ImageWriter writer = (ImageWriter)iter.next();
                ImageWriteParam iwp = writer.getDefaultWriteParam();
                iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                iwp.setCompressionQuality(0.88f);

                int fileNo = 0;
                int tileNo = 0;

                for(TiffTags tiff : tiffs) {

    //System.out.println("file: " + fileNo);

                    int fileTileNo = 0;
    
                    for(int tileX = 0; tileX < tiff.tagTileOffsetsVal.length; tileX++) {

    //System.out.println("tile: " + tileNo);

                        if(tileNo != tileNoNext) {
                            tileNo++;
                            fileTileNo++;
                            continue;
                        }
                        tileNoNext = tileNo + tileNoSkip;
    System.out.println(threadNo + "/" + tileNo);
    //if(tileNo > 20000) { break; }

                        InputStream is = new ByteArrayInputStream(Arrays.copyOfRange(svsIn, tiff.tagTileOffsetsVal[tileX], tiff.tagTileOffsetsVal[tileX] + tiff.tagTileLengthsVal[tileX]));
                        BufferedImage image = ImageIO.read(is);
                        for(int x = 0; x < image.getWidth(); x++) {
                            for(int y = 0; y < image.getHeight(); y++) {
                                int pixelIn = image.getRGB(x, y);
                                int rx = (pixelIn & 0x00ff0000) >> 16;
                                int gx = (pixelIn & 0x0000ff00) >>  8;
                                int bx = (pixelIn & 0x000000ff) >>  0;
                                image.setRGB(x, y, (pixelIn & 0xff000000) | (((int)LUT[rx][gx][bx][R] << 16) & 0x00ff0000) | (((int)LUT[rx][gx][bx][G] <<  8) & 0x0000ff00) | (((int)LUT[rx][gx][bx][B] <<  0) & 0x000000ff));
    //if(fileTileNo == 0) {
    //    Graphics2D g2d = image.createGraphics();
    //    g2d.setPaint(Color.red);
    //    g2d.setFont(new Font("Serif", Font.BOLD, 30));
    //    FontMetrics fm = g2d.getFontMetrics();
    //    g2d.drawString("GT450", 10, 40);
    //    g2d.drawString("color", 10, 70);
    //    g2d.drawString("adjusted", 10, 100);
    //    g2d.drawString("ghsmith@emory.edu", 10, 130);
    //    g2d.dispose();        
    //}
                             }
                        }

                        ByteArrayOutputStream baos = new ByteArrayOutputStream(50000);
                        ImageOutputStream ios = new MemoryCacheImageOutputStream(baos);
                        writer.setOutput(ios);
                        IIOImage newImage = new IIOImage(image, null, null);
                        writer.write(null, newImage, iwp);

    //if(tileNo == 0) {
    //    hack = baos.toByteArray();
    //}

                        tiff.convertedTiles.put(fileTileNo, baos.toByteArray());
    
                        ios.close();
                        baos.close();
                        
                        tileNo++;
                        fileTileNo++;

                    }

                    fileNo++;

                }

                writer.dispose();

            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }
        
        }
            
    }
    
}









        /*short[] p;
        p = LUT[0][0][0];
        System.out.println(String.format("%d %d %d", p[R], p[G], p[B]));
        p = LUT[1][0][0];
        System.out.println(String.format("%d %d %d", p[R], p[G], p[B]));
        p = LUT[2][0][0];
        System.out.println(String.format("%d %d %d", p[R], p[G], p[B]));
        p = LUT[24LD][24LD][24LD];
        System.out.println(String.format("%d %d %d", p[R], p[G], p[B]));
        for(int rx = 0; rx < 256; rx++) {
            p = LUT[rx][0][0];
            System.out.println(String.format("%d: %d %d %d", rx, p[R], p[G], p[B]));
        }
        p = LUT[100][0][0];
        System.out.println(String.format("%d %d %d", p[R], p[G], p[B]));*/
        





        //ICC_Profile scnr = ICC_Profile.getInstance("c:/scanner/GT450_scnr.icc");
        //ICC_Profile mntr = ICC_Profile.getInstance("c:/scanner/default_mntr.icc");
        //ICC_Profile xxx = ICC_Profile.getInstance("c:/users/geoff/desktop/GT450.icc");
        //ICC_ColorSpace ics = new ICC_ColorSpace(ip);
        //ColorConvertOp cco = new ColorConvertOp(new ICC_Profile[] { xxx }, null);
        //BufferedImage result = cco.filter(ImageIO.read(new File("c:/scanner/asdf.jpg")), null);
        //ImageIO.write(result, "jpg", new File("c:/scanner/asdf_converted.jpg"));

        //BufferedImage image = ImageIO.read(new File("c:/scanner/asdf.jpg"));
        //ImageFilter filter = new LUTS3DRGBImageFilter();
        //ImageProducer producer = new FilteredImageSource(image.getSource(), filter);
        //Image toolkitImage = Toolkit.getDefaultToolkit().createImage(producer);
        //BufferedImage newImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        //Graphics2D g = newImage.createGraphics();
        //g.drawImage(toolkitImage, 0, 0, null);
        //ImageIO.write(newImage, "jpg", new File("c:/scanner/asdf_converted.jpg"));
        //g.dispose();
        

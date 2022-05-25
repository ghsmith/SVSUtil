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

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author geoffrey.smith@emory.edu
 */
public class SVSFile {

    static final Logger logger = Logger.getLogger(SVSFile.class.getName());    

    public static final int BUFFER_SIZE = 500000000;
    
    public static final int R = 0;
    public static final int G = 1;
    public static final int B = 2;

    public static final long NO_MORE_TIFF_DIRECTORIES_OFFSET = 0x72657041;
    
    public String svsFileName;
    public long length = -1;
    public long firstHeaderOffset = -1;
    
    public List<byte[]> svsBytesList = new ArrayList<>();
    public List<TIFFDir> tiffDirList = new ArrayList<>();
    public byte[] iccBytes = null;

    public boolean lutComputed = false;
    public int[][][][] lutUpsampled = new int[0x100][0x100][0x100][3];
    public int[] lutUpsampledInt = new int[0x100 * 0x100 * 0x100];

    public Integer nextTileNo = 0;
    
    public SVSFile(String svsFileName) throws FileNotFoundException, IOException, InterruptedException {

        this.svsFileName = svsFileName;

        FileInputStream fis = new FileInputStream(svsFileName);

        byte[] svsBytes = new byte[BUFFER_SIZE];
        int bytesRead = 0;
        length = 0;
        while((bytesRead = fis.read(svsBytes)) != -1) {
            svsBytesList.add(svsBytes);
            length += bytesRead;
            svsBytes = new byte[BUFFER_SIZE];
        }
        
        fis.close();
        
        logger.log(Level.INFO, String.format("read %d Mb from %s into %d buffers", length / (1024 * 1024), svsFileName, svsBytesList.size()));
        
        parseTIFFDirTags();

    }

    public void parseTIFFDirTags() {
        firstHeaderOffset = getBytesAsLong(0x00000008);
        long offset = firstHeaderOffset;
        while(offset != NO_MORE_TIFF_DIRECTORIES_OFFSET) {
            logger.log(Level.INFO, String.format("========== parsing TIFF directory #%d tags (%d) ==========", tiffDirList.size(), offset));
            TIFFDir tiffDir = new TIFFDir(this, offset);
            tiffDirList.add(tiffDir);
            logger.log(Level.INFO, String.format("width=%d height=%d", tiffDir.width, tiffDir.height));
            if(!tiffDir.tileContigList.isEmpty() && tiffDir.tagICCNameOffsetInHeader != -1 && iccBytes == null ) {
                iccBytes = getBytes(tiffDir.tagICCOffsetInSvs, tiffDir.tagICCOffsetInSvs + tiffDir.tagICCLength);
            }
            offset = tiffDir.tagNextDirOffsetInSvs;
        }
    }
    
    public void computeLut(int threads) throws InterruptedException {
        logger.log(Level.INFO, String.format("computing 256x256x256 color lookup table (CLUT) in %d threads", threads));
        class ComputeRunner implements Runnable {
            public int start = -1;
            public int skip = -1;
            public ComputeRunner(int start, int skip) {
                this.start = start;
                this.skip = skip;
            }
            @Override
            public void run() {
                ColorSpace colorSpace = new ICC_ColorSpace(ICC_Profile.getInstance(iccBytes));
                for(int r = start; r < 0x100; r += skip) {
                    for(int g = 0; g < 0x100; g++) {
                        for(int b = 0; b < 0x100; b++) {
                            float[] rgbTransformed = colorSpace.toRGB(new float[] { 1f * r / 0xff, 1f * g / 0xff, 1f * b / 0xff });
                            lutUpsampled[r][g][b][R] = (int)(rgbTransformed[0] * 0xff);
                            lutUpsampled[r][g][b][G] = (int)(rgbTransformed[1] * 0xff);
                            lutUpsampled[r][g][b][B] = (int)(rgbTransformed[2] * 0xff);
                            lutUpsampledInt[((r & 0x0000ff)) << 16 | ((g & 0x0000ff) << 8) | ((b & 0x0000ff) << 0)] = ((lutUpsampled[r][g][b][R] & 0x0000ff)) << 16 | ((lutUpsampled[r][g][b][G] & 0x0000ff) << 8) | ((lutUpsampled[r][g][b][B] & 0x0000ff) << 0);
                        }
                    }
                }
            }
        }
        Thread[] computeThreads = new Thread[threads];
        for(int x = 0; x < threads; x++) {
            computeThreads[x] = new Thread(new ComputeRunner(x, threads));
            computeThreads[x].start();
        }
        for(int x = 0; x < threads; x++) {
            computeThreads[x].join();
        }
    }

    public void collapse(long[] starts, long[] lengths) {

        logger.log(Level.INFO, String.format("collapsing SVS file"));
        
        // 1. update offsets in TIFF headers and referenced tile offset arrays
        {
            for(int collapseIndex = 0; collapseIndex < starts.length; collapseIndex++) {
                long collapseStart = starts[collapseIndex];
                long collapseLength = lengths[collapseIndex];
                long collapseEnd = collapseStart + collapseLength;
                if(firstHeaderOffset > collapseEnd) {
                    setBytesToLong(0x00000008, getBytesAsLong(0x00000008) - collapseLength);
                }
            }
            for(int x = 0; x < tiffDirList.size(); x++) {
                TIFFDir tiffDir = tiffDirList.get(x);
                for(int collapseIndex = 0; collapseIndex < starts.length; collapseIndex++) {
                    long collapseStart = starts[collapseIndex];
                    long collapseLength = lengths[collapseIndex];
                    long collapseEnd = collapseStart + collapseLength;
                    if(tiffDir.tagNextDirOffsetInSvs != NO_MORE_TIFF_DIRECTORIES_OFFSET && tiffDir.tagNextDirOffsetInSvs >= collapseEnd) {
                        setBytesToLong(tiffDir.tagNextDirOffsetInSvsOffsetInSvs, getBytesAsLong(tiffDir.tagNextDirOffsetInSvsOffsetInSvs) - collapseLength);
                    }
                    for(TIFFDir.TIFFTag tiffTag : tiffDir.tiffTagMap.values()) {
                        if(tiffTag instanceof TIFFDir.TIFFTagLong) {
                            TIFFDir.TIFFTagLong tiffTagLong = (TIFFDir.TIFFTagLong)tiffTag;
                            if(tiffTagLong.name == 273) { // StripOffsets (non-tiled image data)
                                if(tiffTagLong.elementValues[0] >= collapseEnd) {
                                    setBytesToLong(tiffTagLong.osElementValues[0], getBytesAsLong(tiffTagLong.osElementValues[0]) - collapseLength);
                                }
                            }
                        }
                        else if(tiffTag instanceof TIFFDir.TIFFTagASCIIReference) {
                            TIFFDir.TIFFTagASCIIReference tiffTagASCIIReference = (TIFFDir.TIFFTagASCIIReference)tiffTag;
                            if(tiffTagASCIIReference.osElementValueDereferenced >= collapseEnd) {
                                setBytesToLong(tiffTagASCIIReference.osElementValue, getBytesAsLong(tiffTagASCIIReference.osElementValue) - collapseLength);
                            }
                        }
                        else if(tiffTag instanceof TIFFDir.TIFFTagLongArrayReference) {
                            TIFFDir.TIFFTagLongArrayReference tiffTagLongArrayReference = (TIFFDir.TIFFTagLongArrayReference)tiffTag;
                            if(tiffTagLongArrayReference.osElementValuesDereferenced[0] >= collapseEnd) {
                                setBytesToLong(tiffTagLongArrayReference.osElementValue, getBytesAsLong(tiffTagLongArrayReference.osElementValue) - collapseLength);
                            }
                            if(tiffTagLongArrayReference.name == 324) { // TileOffsets (tiled image data)
                                for(int y = 0; y < tiffTagLongArrayReference.elementValuesDereferenced.length; y++) {
                                    if(tiffTagLongArrayReference.elementValuesDereferenced[y] >= collapseEnd) {
                                        setBytesToLong(tiffTagLongArrayReference.osElementValuesDereferenced[y], getBytesAsLong(tiffTagLongArrayReference.osElementValuesDereferenced[y]) - collapseLength);
                                    }
                                }
                            }
                        }
                        else if(tiffTag instanceof TIFFDir.TIFFTagUndefinedReference) {
                            TIFFDir.TIFFTagUndefinedReference tiffTagUndefinedReference = (TIFFDir.TIFFTagUndefinedReference)tiffTag;
                            if(tiffTagUndefinedReference.osElementValueDereferenced >= collapseEnd) {
                                setBytesToLong(tiffTagUndefinedReference.osElementValue, getBytesAsLong(tiffTagUndefinedReference.osElementValue) - collapseLength);
                            }
                        }
                    }
                }
            }
        }

        // 2. remove bytes from buffers and reparse TIFF
        {
            long lengthCollapsed = length - Arrays.stream(lengths).sum();
            List<byte[]> svsBytesCollapsedList = new ArrayList<>();
            for(int x = 0; x < lengthCollapsed / BUFFER_SIZE; x++) {
                svsBytesCollapsedList.add(new byte[BUFFER_SIZE]);
            }
            svsBytesCollapsedList.add(new byte[(int)lengthCollapsed % BUFFER_SIZE]);
            long indexCollapsed = 0;
            byte[] svsBytesCollapsed = null;
            for(long x = 0; x < length; x++) {
                if(indexCollapsed % BUFFER_SIZE == 0) {
                    svsBytesCollapsed = svsBytesCollapsedList.get((int)indexCollapsed / BUFFER_SIZE);
                }
                for(int y = 0; y < starts.length; y++) {
                    if(x == starts[y]) {
                        logger.log(Level.INFO, String.format("skipping %d bytes at position %d", lengths[y], starts[y]));
                        x += lengths[y];
                    }
                }
                svsBytesCollapsed[(int)(indexCollapsed % BUFFER_SIZE)] = getByte(x);
                indexCollapsed++;
            }
            length = lengthCollapsed;
            svsBytesList = svsBytesCollapsedList;
            tiffDirList = new ArrayList<>();
            parseTIFFDirTags();
        }
        
    }

    public void expand(long[] starts, long[] lengths) {
    }
    
    public void write(String svsFileNameNew) throws FileNotFoundException, IOException {
        FileOutputStream fos = new FileOutputStream(svsFileNameNew);
        long bytesLeftToWrite = length;
        for(byte[] svsBytes : svsBytesList) {
            if(bytesLeftToWrite > BUFFER_SIZE) {
                fos.write(svsBytes, 0, BUFFER_SIZE);
                bytesLeftToWrite -= BUFFER_SIZE;
            }
            else {
                fos.write(svsBytes, 0, (int)bytesLeftToWrite);
            }
        }
        fos.close();
    }
    
    public byte[] getBytes(long indexStart, long indexEnd) {
        if(indexStart / BUFFER_SIZE == (indexEnd - 1) / BUFFER_SIZE) {
            return Arrays.copyOfRange(svsBytesList.get((int)(indexStart / BUFFER_SIZE)), (int)(indexStart % BUFFER_SIZE), (int)(indexEnd % BUFFER_SIZE));
        }
        else {
            byte[] vals = new byte[(int)(indexEnd - indexStart)];
            for(long x = indexStart; x < indexEnd; x++) {
                vals[(int)(x - indexStart)] = svsBytesList.get((int)(x / BUFFER_SIZE))[(int)(x % BUFFER_SIZE)];
            }
            return vals;
        }
    }
    
    public void setBytes(long indexStart, long indexEnd, byte[] vals) {
        for(long x = indexStart; x < indexEnd; x++) {
            svsBytesList.get((int)(x / BUFFER_SIZE))[(int)(x % BUFFER_SIZE)] = vals[(int)(x - indexStart)];
        }
    }

    public byte getByte(long index) {
        return svsBytesList.get((int)(index / BUFFER_SIZE))[(int)(index % BUFFER_SIZE)];
    }
    
    public void setByte(long index, byte val) {
        svsBytesList.get((int)(index / BUFFER_SIZE))[(int)(index % BUFFER_SIZE)] = val;
    }

    // this needs to be adjusted for 64-bits
    public long getBytesAsLong(long index) {
        return (long)
              ((getByte(index + 0) & 0x000000ff) <<  0)
            | ((getByte(index + 1) & 0x000000ff) <<  8)
            | ((getByte(index + 2) & 0x000000ff) << 16)
            | ((getByte(index + 3) & 0x000000ff) << 24);
    }
    
    // this needs to be adjusted for 64-bits
    public void setBytesToLong(long index, long val) {
        setByte(index + 0, (byte)(((val) & 0x000000ff) >>  0));
        setByte(index + 1, (byte)(((val) & 0x0000ff00) >>  8));
        setByte(index + 2, (byte)(((val) & 0x00ff0000) >> 16));
        setByte(index + 3, (byte)(((val) & 0xff000000) >> 24));
    }
    
}

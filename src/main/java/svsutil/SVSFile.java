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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author geoffrey.smith@emory.edu
 */
public class SVSFile {

    static final Logger logger = Logger.getLogger(SVSFile.class.getName());
    
    static public class ResizeSegment {
        public long start = -1;
        public long length = -1; // negative = compress / positive = expand
        public long end = -1;
        public ResizeSegment(long start, long length) {
            this.start = start;
            this.length = length;
        }
        public void setEnd() {
            if(length < 0) {
                end = start + Math.abs(length);
            }
            else {
                end = start;
            }
        }
    }

    public static final int BUFFER_SIZE = 250000000;
    
    public static final int R = 0;
    public static final int G = 1;
    public static final int B = 2;

    public static final long NO_MORE_TIFF_DIRECTORIES_OFFSET = 0x0000000000000000L;
    
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
        int x = 0;
        while(offset != NO_MORE_TIFF_DIRECTORIES_OFFSET) {
            logger.log(Level.INFO, String.format("========== parsing TIFF directory #%d tags (%d) ==========", tiffDirList.size(), offset));
            TIFFDir tiffDir = new TIFFDir(String.valueOf(x), this, offset);
            tiffDirList.add(tiffDir);
            logger.log(Level.INFO, String.format("width=%d height=%d tileWidth=%d tileHeight=%d mpp=%4.2f", tiffDir.width, tiffDir.height, tiffDir.tileWidth, tiffDir.tileHeight, tiffDir.mpp));
            if(!tiffDir.tileContigList.isEmpty() && tiffDir.tagICCNameOffsetInHeader != -1 && iccBytes == null ) {
                iccBytes = getBytes(tiffDir.tagICCOffsetInSvs, tiffDir.tagICCOffsetInSvs + tiffDir.tagICCLength);
            }
            offset = tiffDir.tagNextDirOffsetInSvs;
            x++;
        }
    }
    
    public void computeLut(int threads) throws InterruptedException {
        // if you save the 3D LUT to a file (e.g., ".cube"), remember that the R
        // component should change quickly and the B should change slowly; this
        // uses the color management system built into the JVM, but if you want
        // to compute the 3D LUT totally manually, look back at prior versions
        // of this project in GitHub (this is far fewer lines of code and the
        // 3D LUTs between the two approaches are basically identical)
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

    public void resize(List<ResizeSegment> resizeSegmentList) {

        if(resizeSegmentList.isEmpty()) {
            return;
        }
        
        logger.log(Level.INFO, String.format("resizing SVS file"));
        
        // 1. update offsets in TIFF headers and referenced tile offset arrays
        {
            for(ResizeSegment resizeSegment : resizeSegmentList) {
                resizeSegment.setEnd();
                if(firstHeaderOffset >= resizeSegment.end) {
                    setBytesToLong(0x00000008, getBytesAsLong(0x00000008) + resizeSegment.length);
                }
            }
            for(int x = 0; x < tiffDirList.size(); x++) {
                TIFFDir tiffDir = tiffDirList.get(x);
                for(ResizeSegment resizeSegment : resizeSegmentList) {
                    resizeSegment.setEnd();
                    if(tiffDir.tagNextDirOffsetInSvs != NO_MORE_TIFF_DIRECTORIES_OFFSET && tiffDir.tagNextDirOffsetInSvs >= resizeSegment.end) {
                        setBytesToLong(tiffDir.tagNextDirOffsetInSvsOffsetInSvs, getBytesAsLong(tiffDir.tagNextDirOffsetInSvsOffsetInSvs) + resizeSegment.length);
                    }
                    for(TIFFDir.TIFFTag tiffTag : tiffDir.tiffTagMap.values()) {
                        if(tiffTag instanceof TIFFDir.TIFFTagLong) {
                            TIFFDir.TIFFTagLong tiffTagLong = (TIFFDir.TIFFTagLong)tiffTag;
                            if(tiffTagLong.name == 273) { // StripOffsets (non-tiled image data)
                                if(tiffTagLong.elementValues[0] >= resizeSegment.end) {
                                    setBytesToLong(tiffTagLong.osElementValues[0], getBytesAsLong(tiffTagLong.osElementValues[0]) + resizeSegment.length);
                                }
                            }
                        }
                        else if(tiffTag instanceof TIFFDir.TIFFTagASCIIReference) {
                            TIFFDir.TIFFTagASCIIReference tiffTagASCIIReference = (TIFFDir.TIFFTagASCIIReference)tiffTag;
                            if(tiffTagASCIIReference.osElementValueDereferenced >= resizeSegment.end) {
                                setBytesToLong(tiffTagASCIIReference.osElementValue, getBytesAsLong(tiffTagASCIIReference.osElementValue) + resizeSegment.length);
                            }
                        }
                        else if(tiffTag instanceof TIFFDir.TIFFTagLongArrayReference) {
                            TIFFDir.TIFFTagLongArrayReference tiffTagLongArrayReference = (TIFFDir.TIFFTagLongArrayReference)tiffTag;
                            if(tiffTagLongArrayReference.osElementValuesDereferenced[0] >= resizeSegment.end) {
                                setBytesToLong(tiffTagLongArrayReference.osElementValue, getBytesAsLong(tiffTagLongArrayReference.osElementValue) + resizeSegment.length);
                            }
                            if(tiffTagLongArrayReference.name == 324) { // TileOffsets (tiled image data)
                                for(int y = 0; y < tiffTagLongArrayReference.elementValuesDereferenced.length; y++) {
                                    if(tiffTagLongArrayReference.elementValuesDereferenced[y] >= resizeSegment.end) {
                                        setBytesToLong(tiffTagLongArrayReference.osElementValuesDereferenced[y], getBytesAsLong(tiffTagLongArrayReference.osElementValuesDereferenced[y]) + resizeSegment.length);
                                    }
                                }
                            }
                        }
                        else if(tiffTag instanceof TIFFDir.TIFFTagUndefinedReference) {
                            TIFFDir.TIFFTagUndefinedReference tiffTagUndefinedReference = (TIFFDir.TIFFTagUndefinedReference)tiffTag;
                            if(tiffTagUndefinedReference.osElementValueDereferenced >= resizeSegment.end) {
                                setBytesToLong(tiffTagUndefinedReference.osElementValue, getBytesAsLong(tiffTagUndefinedReference.osElementValue) + resizeSegment.length);
                            }
                        }
                    }
                }
            }
        }

        // 2. remove/add bytes from/to buffers and reparse TIFF
        {
            long lengthResized = length + resizeSegmentList.stream().mapToLong(x -> x.length).sum();
            long startMinimum = resizeSegmentList.stream().mapToLong(x -> x.start).min().getAsLong();
            List<byte[]> svsBytesResizedList = new ArrayList<>();
            for(int x = 0; x < (int)(startMinimum / BUFFER_SIZE); x++) {
                svsBytesResizedList.add(svsBytesList.get(x));
            }
            for(int x = (int)(startMinimum / BUFFER_SIZE); x <= (int)(lengthResized / BUFFER_SIZE); x++) {
                svsBytesResizedList.add(new byte[BUFFER_SIZE]);
            }
            long indexResized = (startMinimum / BUFFER_SIZE) * BUFFER_SIZE;
            for(long x = (startMinimum / BUFFER_SIZE) * BUFFER_SIZE; x < length; x++) {
                for(ResizeSegment resizeSegment : resizeSegmentList) {
                    if(x == resizeSegment.start) {
                        if(resizeSegment.length < 0) {
                            logger.log(Level.INFO, String.format("collapsing %d bytes at position %d", Math.abs(resizeSegment.length), resizeSegment.start));
                            x += Math.abs(resizeSegment.length);
                            break;
                        }
                        else {
                            logger.log(Level.INFO, String.format("expanding %d bytes at position %d", Math.abs(resizeSegment.length), resizeSegment.start));
                            indexResized += resizeSegment.length;
                            break;
                        }
                    }
                }
                svsBytesResizedList.get((int)(indexResized / BUFFER_SIZE))[(int)(indexResized % BUFFER_SIZE)] = getByte(x);
                indexResized++;
            }
            length = lengthResized;
            svsBytesList = svsBytesResizedList;
            Map<String, Map<Integer, byte[]>> recoloredTileBytesMapMap = new HashMap<>();
            // save recolored image tiles...
            for(TIFFDir tiffDir : tiffDirList) {
                for(TIFFTileContig tileContig : tiffDir.tileContigList) {
                    recoloredTileBytesMapMap.put(tileContig.id, tileContig.recoloredTileBytesMap);
                }
            }
            tiffDirList = new ArrayList<>();
            parseTIFFDirTags();
            // ...and restore recolored image tiles
            for(TIFFDir tiffDir : tiffDirList) {
                for(TIFFTileContig tileContig : tiffDir.tileContigList) {
                    tileContig.recoloredTileBytesMap = recoloredTileBytesMapMap.get(tileContig.id);
                }
            }
        }
        
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

    public long getBytesAsLong(long index) {
        byte[] bytes = getBytes(index, index + 7);
        return
              ((((long)bytes[0]) & 0x00000000000000ffL) <<  0)
            | ((((long)bytes[1]) & 0x00000000000000ffL) <<  8)
            | ((((long)bytes[2]) & 0x00000000000000ffL) << 16)
            | ((((long)bytes[3]) & 0x00000000000000ffL) << 24)
            | ((((long)bytes[4]) & 0x00000000000000ffL) << 32)
            | ((((long)bytes[5]) & 0x00000000000000ffL) << 40)
            | ((((long)bytes[6]) & 0x00000000000000ffL) << 48);
    }
    
    public void setBytesToLong(long index, long val) {
        byte[] bytes = new byte[] {
            (byte)(((val) & 0x00000000000000ffL) >>  0),
            (byte)(((val) & 0x000000000000ff00L) >>  8),
            (byte)(((val) & 0x0000000000ff0000L) >> 16),
            (byte)(((val) & 0x00000000ff000000L) >> 24),
            (byte)(((val) & 0x000000ff00000000L) >> 32),
            (byte)(((val) & 0x0000ff0000000000L) >> 40),
            (byte)(((val) & 0x00ff000000000000L) >> 48)
        };
        setBytes(index, index + 7, bytes);
    }

    // the tile ID consists of x.y.z
    // x = TIFF directory index
    // y = TIFF contig index
    // z = TIFF tile index in TIFF directory (not in the contig, subtract TIFFContig.firstTileIndexInTIFFDir to get the index in the contig)
    public void setRecoloredTileBytesForTileId(String tileId, byte[] vals) {
        String[] tileIdSplit = tileId.split("\\.");
        int tiffDirIndex = Integer.valueOf(tileIdSplit[0]);
        int tileContigIndex = Integer.valueOf(tileIdSplit[1]);
        int tileIndex = Integer.valueOf(tileIdSplit[2]);
        TIFFTileContig tileContig = tiffDirList.get(tiffDirIndex).tileContigList.get(tileContigIndex);
        tileContig.recoloredTileBytesMap.put(tileIndex - tileContig.firstTileIndexInTIFFDir, vals);
    }

    public TIFFDir getTIFFDirForTileId(String tileId) {
        String[] tileIdSplit = tileId.split("\\.");
        int tiffDirIndex = Integer.valueOf(tileIdSplit[0]);
        return tiffDirList.get(tiffDirIndex);
    }

    public TIFFTileContig getTileContigForTileId(String tileId) {
        String[] tileIdSplit = tileId.split("\\.");
        int tiffDirIndex = Integer.valueOf(tileIdSplit[0]);
        int tileContigIndex = Integer.valueOf(tileIdSplit[1]);
        return tiffDirList.get(tiffDirIndex).tileContigList.get(tileContigIndex);
    }

    public int getTileIndexInContigForTileId(String tileId) {
        String[] tileIdSplit = tileId.split("\\.");
        int tiffDirIndex = Integer.valueOf(tileIdSplit[0]);
        int tileContigIndex = Integer.valueOf(tileIdSplit[1]);
        int tileIndex = Integer.valueOf(tileIdSplit[2]);
        TIFFTileContig tileContig = tiffDirList.get(tiffDirIndex).tileContigList.get(tileContigIndex);
        return tileIndex - tileContig.firstTileIndexInTIFFDir;
    }

    public int getTileIndexForTileId(String tileId) {
        String[] tileIdSplit = tileId.split("\\.");
        int tileIndex = Integer.valueOf(tileIdSplit[2]);
        return tileIndex;
    }
    
}

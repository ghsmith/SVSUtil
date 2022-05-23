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

    public String svsFileName;
    public long length = -1;
    
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

        long offset = ByteUtil.bytesToLong(getBytes(0x00000008, 0x00000010));
        while(offset != 0x72657041) {
            logger.log(Level.INFO, String.format("========== parsing TIFF directory #%d header ==========", tiffDirList.size()));
            TIFFDir tiffDir = new TIFFDir(this, offset);
            tiffDirList.add(tiffDir);
            if(!tiffDir.tileContigList.isEmpty()) {
                //logger.log(Level.INFO, String.format("this directory has %d tile contigs", tiffDir.tileContigList.size()));
                //for(int x = 0; x < tiffDir.tileContigList.size(); x++) {
                //    logger.log(Level.INFO, String.format("contig %d consists of %d tiles and %d bytes (starts %d ends %d)", x, tiffDir.tileContigList.get(x).tagTileOffsetsInSvs.length, tiffDir.tileContigList.get(x).length, tiffDir.tileContigList.get(x).offsetInSvs, tiffDir.tileContigList.get(x).offsetInSvs + tiffDir.tileContigList.get(x).length));
                //}
                if(iccBytes == null && tiffDir.tagICCNameOffsetInHeader != -1) {
                    if(iccBytes == null) {
                        iccBytes = getBytes(tiffDir.tagICCOffsetInSvs, tiffDir.tagICCOffsetInSvs + tiffDir.tagICCLength);
                    }
                }
            }
            offset = tiffDir.tagNextDirOffsetInSvs;
        }

    }
    
    public void computeLut() {
        ColorSpace colorSpace = new ICC_ColorSpace(ICC_Profile.getInstance(iccBytes));
        for(int b = 0; b < 0x100; b++) {
            for(int g = 0; g < 0x100; g++) {
                for(int r = 0; r < 0x100; r++) {
                    float[] rgbTransformed = colorSpace.toRGB(new float[] { 1f * r / 0xff, 1f * g / 0xff, 1f * b / 0xff });
                    lutUpsampled[r][g][b][R] = (int)(rgbTransformed[0] * 0xff);
                    lutUpsampled[r][g][b][G] = (int)(rgbTransformed[1] * 0xff);
                    lutUpsampled[r][g][b][B] = (int)(rgbTransformed[2] * 0xff);
                    lutUpsampledInt[((r & 0x0000ff)) << 16 | ((g & 0x0000ff) << 8) | ((b & 0x0000ff) << 0)] = ((lutUpsampled[r][g][b][R] & 0x0000ff)) << 16 | ((lutUpsampled[r][g][b][G] & 0x0000ff) << 8) | ((lutUpsampled[r][g][b][B] & 0x0000ff) << 0);
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

    public void setByte(long index, byte val) {
        svsBytesList.get((int)(index / BUFFER_SIZE))[(int)(index % BUFFER_SIZE)] = val;
    }

}

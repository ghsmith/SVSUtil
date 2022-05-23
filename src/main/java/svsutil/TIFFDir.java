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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author geoffrey.smith@emory.edu
 */
public class TIFFDir {

    static final Logger logger = Logger.getLogger(TIFFDir.class.getName());    
    
    public SVSFile svsFile;
    
    public long offsetInSvs = -1;
    public long tagNextDirOffsetInSvs = -1;
    public int tagNumberOfTags = -1;
    public int tagICCNameOffsetInHeader = -1;
    public long tagICCOffsetInSvs = -1;
    public int tagICCLength = -1;
    
    public long imageDataOffsetInSvs = -1;
    public long imageDataLength = -1;
    public int imageDataLengthOffsetInHeader = -1;
    public int subfileType = -1;
    public int width = -1;
    public int height = -1;
    
    List<TiffTileContig> tileContigList = new ArrayList<>();

    public TIFFDir(SVSFile svsFile, long offsetInSvs) {

        long[] tagTileOffsetsInSvs = null;
        long[] tagTileOffsetsInSvsOffsetInSvs = null;
        int[] tagTileLengths = null;
        long[] tagTileLengthsOffsetInSvs = null;
        
        {
            this.svsFile = svsFile;
            this.offsetInSvs = offsetInSvs;
            long currentOffsetInHeader = 0;
            tagNumberOfTags = (int)ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000008));
            currentOffsetInHeader += 0x00000008;
            for(int x = 0; x < tagNumberOfTags; x++) {
                int tagName = ByteUtil.bytesToInt(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000002));
                if(tagName == 34675) {
                    tagICCNameOffsetInHeader = (int)currentOffsetInHeader;
                }
                currentOffsetInHeader += 0x00000002;
                int tagDataType = ByteUtil.bytesToInt(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000002));
                currentOffsetInHeader += 0x00000002;
                int tagValueCount = (int)ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000008));
                if(tagName == 34675) {
                    tagICCLength = tagValueCount;
                }
                currentOffsetInHeader += 0x00000008;
                if(tagName == 256) {
                    width = (int)ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000008));
                    currentOffsetInHeader += 0x00000008;
                }
                else if(tagName == 257) {
                    height = (int)ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000008));
                    currentOffsetInHeader += 0x00000008;
                }
                else if(tagName == 254) {
                    subfileType = (int)ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000008));
                    currentOffsetInHeader += 0x00000008;
                }
                else if(tagName == 273) {
                    imageDataOffsetInSvs = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000008));
                    currentOffsetInHeader += 0x00000008;
                }
                else if(tagName == 279) {
                    imageDataLength = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000008));
                    imageDataLengthOffsetInHeader = (int)currentOffsetInHeader;
                    currentOffsetInHeader += 0x00000008;
                }
                else if(tagDataType == 2) {
                    long dataOffsetInSvs = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000008));
                    currentOffsetInHeader += 0x00000008;
                    String tagValue = ByteUtil.bytesToString(svsFile.getBytes(dataOffsetInSvs, dataOffsetInSvs + tagValueCount));
                    logger.log(Level.INFO, String.format("%s", tagValue.substring(0, Math.min(tagValue.length(), 10000)).replaceAll("\\n", " ")));
                }
                else if(tagDataType == 16) {
                    long currentDataOffsetInSvs = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000008));
                    if(tagName == 324) {
                        tagTileOffsetsInSvs = new long[tagValueCount];
                        tagTileOffsetsInSvsOffsetInSvs = new long[tagValueCount];
                    }
                    else if(tagName == 325) {
                        tagTileLengths = new int[tagValueCount];
                        tagTileLengthsOffsetInSvs = new long[tagValueCount];
                    }
                    currentOffsetInHeader += 0x00000008;
                    for(int y = 0; y < tagValueCount; y++) {
                        long tagValue = ByteUtil.bytesToLong(svsFile.getBytes(currentDataOffsetInSvs, currentDataOffsetInSvs + 0x00000008));
                        if(tagName == 324) {
                            tagTileOffsetsInSvs[y] = tagValue;
                            tagTileOffsetsInSvsOffsetInSvs[y] = currentDataOffsetInSvs;
                        }
                        else if(tagName == 325) {
                            tagTileLengths[y] = (int)tagValue;
                            tagTileLengthsOffsetInSvs[y] = currentDataOffsetInSvs;
                        }
                        currentDataOffsetInSvs += 0x00000008;
                    }
                }
                else if(tagDataType == 7) { // undefined data type
                    long dataOffsetInSvs = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000008));
                    if(tagName == 34675) {
                        tagICCOffsetInSvs = dataOffsetInSvs;
                    }
                    currentOffsetInHeader += 0x00000008;
                }
                else {
                    for(int y = 0; y < tagValueCount; y++) {
                        if(tagDataType == 4) {
                            //long tagValue = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000008));
                            currentOffsetInHeader += 0x00000008;
                        }
                        else if(tagDataType == 3) {
                            //int tagValue = ByteUtil.bytesToInt(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000002));
                            currentOffsetInHeader += 0x00000002;
                        }
                    }
                }
                while(ByteUtil.bytesToInt(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000002)) == 0) {
                    currentOffsetInHeader += 0x00000002;
                }
            }
            tagNextDirOffsetInSvs = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000008));
            currentOffsetInHeader += 0x00000008;
        }

        if(tagTileOffsetsInSvs != null) {
            int contigStartIndex = 0;
            for(int x = 0; x < tagTileOffsetsInSvs.length; x++) {
                if(x == tagTileOffsetsInSvs.length - 1 || tagTileOffsetsInSvs[x] + tagTileLengths[x] != tagTileOffsetsInSvs[x + 1]) {
                    long contigEnd = tagTileOffsetsInSvs[x] + tagTileLengths[x];
                    TiffTileContig tileContig = new TiffTileContig();
                    tileContigList.add(tileContig);
                    tileContig.offsetInSvs = tagTileOffsetsInSvs[contigStartIndex];
                    tileContig.length = (int)(tagTileOffsetsInSvs[x] + tagTileLengths[x] - tagTileOffsetsInSvs[contigStartIndex]);
                    tileContig.tagTileOffsetsInSvs = Arrays.copyOfRange(tagTileOffsetsInSvs, contigStartIndex, x + 1);
                    tileContig.tagTileOffsetsInSvsOffsetInSvs = Arrays.copyOfRange(tagTileOffsetsInSvsOffsetInSvs, contigStartIndex, x + 1);
                    tileContig.tagTileLengths = Arrays.copyOfRange(tagTileLengths, contigStartIndex, x + 1);
                    tileContig.tagTileLengthsOffsetInSvs = Arrays.copyOfRange(tagTileLengthsOffsetInSvs, contigStartIndex, x + 1);
                    contigStartIndex = x + 1;
                }
            }
        }
        
    }
    
}

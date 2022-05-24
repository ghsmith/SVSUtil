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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 
 * @author geoffrey.smith@emory.edu
 */
public class TIFFDir {
    
    static final Logger logger = Logger.getLogger(TIFFDir.class.getName());    

    public class TIFFTag {
        int name = -1;
        int dataType = -1;
        int length = -1;
        long osName = -1;
        long osDataType = -1;
        long osLength =-1;
    }

    public class TIFFTagLong extends TIFFTag {
        long[] elementValues = null;
        long[] osElementValues = null;
    }
    
    public class TIFFTagShort extends TIFFTag {
        int[] elementValues = null;
        long[] osElementValues = null;
    }
    
    public class TIFFTagASCIIReference extends TIFFTag {
        String elementValueDereferenced = null;
        long osElementValueDereferenced = -1;
        long osElementValue;
    }
    
    public class TIFFTagLongArrayReference extends TIFFTag {
        long[] elementValuesDereferenced = null;
        long[] osElementValuesDereferenced = null;
        long osElementValue;
    }
    
    public class TIFFTagUndefinedReference extends TIFFTag {
        byte[] elementValuesDereferenced = null;
        long osElementValueDereferenced = -1;
        long osElementValue;
    }
    
    public Map<Integer, TIFFTag> tiffTagMap = new LinkedHashMap<>();
    
    // these are in all TIFF directories
    public long offsetInSvs = -1;
    public int tagNumberOfTags = -1;
    public long tagNextDirOffsetInSvs = -1;
    public int subfileType = -1;
    public int width = -1;
    public int height = -1;

    // this is needed to extract the ICC color profile bytes
    public long tagICCOffsetInSvs = -1;
    public int tagICCLength = -1;

    // these are for the non-tiled images (thumbnail, label, macro)
    public long imageDataOffsetInSvs = -1;
    public long imageDataLength = -1;
    public int imageDataLengthOffsetInHeader = -1;

    // this is needed to clobber the ICC profile in the SVS to prevent double-
    // color-correction (i.e., by this utility and then by a client rendering
    // the SVS)
    public int tagICCNameOffsetInHeader = -1;

    // presevering the relationship of tile contigs in the high-resolution
    // TIFF directory (the first TIFF directory) is important for proper SVS
    // rendering; there is one tile-contig per row of tiles in the high-
    // resolution TIFF directory and they appear in the SVS file in the order
    // bottom-row-to-top-row and then left-to-right (i.e., the reverse of the
    // order the contigs appear in this list); all of the other TIFF directories
    // use a single tile-contig and this isn't an issue
    public List<TiffTileContig> tileContigList = new ArrayList<>();

    public TIFFDir(SVSFile svsFile, long offsetInSvs) {

        long[] tagTileOffsetsInSvs = null;
        long[] tagTileOffsetsInSvsOffsetInSvs = null;
        int[] tagTileLengths = null;
        long[] tagTileLengthsOffsetInSvs = null;
        
        {
            this.offsetInSvs = offsetInSvs;
            long currentOffsetInHeader = 0;
            tagNumberOfTags = (int)ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000008));
            currentOffsetInHeader += 0x00000008;
            for(int x = 0; x < tagNumberOfTags; x++) {
                int tagName = ByteUtil.bytesToInt(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000002));
                long osTagName = offsetInSvs + currentOffsetInHeader;
                currentOffsetInHeader += 0x00000002;
                int tagDataType = ByteUtil.bytesToInt(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000002));
                long osTagDataType = offsetInSvs + currentOffsetInHeader;
                currentOffsetInHeader += 0x00000002;
                int tagLength = (int)ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000008));
                long osTagLength = offsetInSvs + currentOffsetInHeader;
                currentOffsetInHeader += 0x00000008;
                switch(tagDataType) {
                    case 4: {
                        TIFFTagLong tiffTag = new TIFFTagLong();
                        tiffTagMap.put(tagName, tiffTag);
                        tiffTag.name = tagName;
                        tiffTag.osName = osTagName;
                        tiffTag.length = tagLength;
                        tiffTag.dataType = tagDataType;
                        tiffTag.osDataType = osTagDataType;
                        tiffTag.osLength = osTagLength;
                        tiffTag.elementValues = new long[tagLength];
                        tiffTag.osElementValues = new long[tagLength];
                        for(int y = 0; y < tagLength; y++) {
                            tiffTag.elementValues[y] = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000008));
                            tiffTag.osElementValues[y] = offsetInSvs + currentOffsetInHeader;
                            currentOffsetInHeader += 0x00000008;
                        }
                        break;
                    }
                    case 3: {
                        TIFFTagShort tiffTag = new TIFFTagShort();
                        tiffTagMap.put(tagName, tiffTag);
                        tiffTag.name = tagName;
                        tiffTag.osName = osTagName;
                        tiffTag.length = tagLength;
                        tiffTag.dataType = tagDataType;
                        tiffTag.osDataType = osTagDataType;
                        tiffTag.osLength = osTagLength;
                        tiffTag.elementValues = new int[tagLength];
                        tiffTag.osElementValues = new long[tagLength];
                        for(int y = 0; y < tagLength; y++) {
                            tiffTag.elementValues[y] = ByteUtil.bytesToInt(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000002));
                            tiffTag.osElementValues[y] = offsetInSvs + currentOffsetInHeader;
                            currentOffsetInHeader += 0x00000002;
                        }
                        break;
                    }
                    case 2: {
                        TIFFTagASCIIReference tiffTag = new TIFFTagASCIIReference();
                        tiffTagMap.put(tagName, tiffTag);
                        tiffTag.name = tagName;
                        tiffTag.osName = osTagName;
                        tiffTag.length = tagLength;
                        tiffTag.dataType = tagDataType;
                        tiffTag.osDataType = osTagDataType;
                        tiffTag.osLength = osTagLength;
                        tiffTag.osElementValueDereferenced = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000008));
                        tiffTag.elementValueDereferenced = new String(svsFile.getBytes(tiffTag.osElementValueDereferenced, tiffTag.osElementValueDereferenced + tiffTag.length));
                        tiffTag.osElementValue = offsetInSvs + currentOffsetInHeader;
                        currentOffsetInHeader += 0x00000008;
                        break;
                    }
                    case 16: {
                        if(tagLength == 1) {
                            TIFFTagLong tiffTag = new TIFFTagLong();
                            tiffTagMap.put(tagName, tiffTag);
                            tiffTag.name = tagName;
                            tiffTag.osName = osTagName;
                            tiffTag.length = tagLength;
                            tiffTag.dataType = tagDataType;
                            tiffTag.osDataType = osTagDataType;
                            tiffTag.osLength = osTagLength;
                            tiffTag.elementValues = new long[tagLength];
                            tiffTag.osElementValues = new long[tagLength];
                            for(int y = 0; y < tagLength; y++) {
                                tiffTag.elementValues[y] = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000008));
                                tiffTag.osElementValues[y] = offsetInSvs + currentOffsetInHeader;
                                currentOffsetInHeader += 0x00000008;
                            }
                            break;
                        }
                        else {
                            TIFFTagLongArrayReference tiffTag = new TIFFTagLongArrayReference();
                            tiffTagMap.put(tagName, tiffTag);
                            tiffTag.name = tagName;
                            tiffTag.osName = osTagName;
                            tiffTag.length = tagLength;
                            tiffTag.dataType = tagDataType;
                            tiffTag.osDataType = osTagDataType;
                            tiffTag.osLength = osTagLength;
                            tiffTag.elementValuesDereferenced = new long[tagLength];
                            tiffTag.osElementValuesDereferenced = new long[tagLength];
                            for(int y = 0; y < tagLength; y++) {
                                tiffTag.osElementValuesDereferenced[y] = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000008)) + (0x00000008 * y);
                                tiffTag.elementValuesDereferenced[y] = ByteUtil.bytesToLong(svsFile.getBytes(tiffTag.osElementValuesDereferenced[y], tiffTag.osElementValuesDereferenced[y] + 0x00000008));
                            }
                            tiffTag.osElementValue = offsetInSvs + currentOffsetInHeader;
                            currentOffsetInHeader += 0x00000008;
                        }
                        break;
                    }
                    case 7: {
                        TIFFTagUndefinedReference tiffTag = new TIFFTagUndefinedReference();
                        tiffTagMap.put(tagName, tiffTag);
                        tiffTag.name = tagName;
                        tiffTag.osName = osTagName;
                        tiffTag.length = tagLength;
                        tiffTag.dataType = tagDataType;
                        tiffTag.osDataType = osTagDataType;
                        tiffTag.osLength = osTagLength;
                        tiffTag.osElementValueDereferenced = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000008));
                        tiffTag.elementValuesDereferenced = svsFile.getBytes(tiffTag.osElementValueDereferenced, tiffTag.osElementValueDereferenced + tiffTag.length);
                        tiffTag.osElementValue = offsetInSvs + currentOffsetInHeader;
                        currentOffsetInHeader += 0x00000008;
                        break;
                    }
                    default: {
                        throw new RuntimeException(String.format("error paring TIFF directory header tag #%d", x));
                    }
                }
                while(ByteUtil.bytesToInt(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000002)) == 0) {
                    currentOffsetInHeader += 0x00000002;
                }
            }
            tagNextDirOffsetInSvs = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000008));
            currentOffsetInHeader += 0x00000008;
        }

        {
            subfileType = (int)((TIFFTagLong)tiffTagMap.get(254)).elementValues[0];
            width = tiffTagMap.get(256) instanceof TIFFTagLong ? (int)((TIFFTagLong)tiffTagMap.get(256)).elementValues[0] : ((TIFFTagShort)tiffTagMap.get(256)).elementValues[0];
            height = tiffTagMap.get(257) instanceof TIFFTagLong ? (int)((TIFFTagLong)tiffTagMap.get(257)).elementValues[0] : ((TIFFTagShort)tiffTagMap.get(257)).elementValues[0];
            tagICCOffsetInSvs = tiffTagMap.get(34675) != null ? ((TIFFTagUndefinedReference)tiffTagMap.get(34675)).osElementValueDereferenced : -1;
            tagICCLength = tiffTagMap.get(34675) != null ? ((TIFFTagUndefinedReference)tiffTagMap.get(34675)).length : -1;
            tagICCNameOffsetInHeader = tiffTagMap.get(34675) != null ? (int)(((TIFFTagUndefinedReference)tiffTagMap.get(34675)).osName - offsetInSvs) : -1;
            imageDataOffsetInSvs = tiffTagMap.get(273) != null ? (((TIFFTagLong)tiffTagMap.get(273)).elementValues[0]) : -1;
            imageDataLength = tiffTagMap.get(279) != null ? (((TIFFTagLong)tiffTagMap.get(279)).elementValues[0]) : -1;
            imageDataLengthOffsetInHeader = tiffTagMap.get(279) != null ? (int)(((TIFFTagLong)tiffTagMap.get(279)).osElementValues[0] - offsetInSvs) : -1;
            tagTileOffsetsInSvs = tiffTagMap.get(324) != null ? ((TIFFTagLongArrayReference)tiffTagMap.get(324)).elementValuesDereferenced : null;
            tagTileOffsetsInSvsOffsetInSvs = tiffTagMap.get(324) != null ? ((TIFFTagLongArrayReference)tiffTagMap.get(324)).osElementValuesDereferenced : null;
            tagTileLengths = tiffTagMap.get(325) != null ? Arrays.stream(((TIFFTagLongArrayReference)tiffTagMap.get(325)).elementValuesDereferenced).mapToInt(i -> (int)i).toArray() : null;
            tagTileLengthsOffsetInSvs = tiffTagMap.get(325) != null ? ((TIFFTagLongArrayReference)tiffTagMap.get(325)).osElementValuesDereferenced : null;
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

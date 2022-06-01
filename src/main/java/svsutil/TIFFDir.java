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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * @author geoffrey.smith@emory.edu
 */
public class TIFFDir {
    
    static final Logger logger = Logger.getLogger(TIFFDir.class.getName());    

    String id = null;
        
    public Map<Integer, TIFFTag> tiffTagMap = new LinkedHashMap<>();
    
    // these are in all TIFF directories
    public long offsetInSvs = -1;
    public int tagNumberOfTags = -1;
    public long tagNextDirOffsetInSvs = -1;
    public long tagNextDirOffsetInSvsOffsetInSvs = -1;
    public int subfileType = -1;
    public int width = -1;
    public int height = -1;
    public int tileWidth = -1;
    public int tileHeight = -1;
    public int widthInTiles = -1;
    public int heightInTiles = -1;
    public String description = null;
    public float mpp = -1;

    // this is needed to extract the ICC color profile bytes
    public long tagICCOffsetInSvs = -1;
    public int tagICCLength = -1;

    // this is needed for AT2 JPEG decoding/encoding
    public long tagJPEGTablesOffsetInSvs = -1;
    public int tagJPEGTablesLength = -1;
    
    // these are for the non-tiled images (thumbnail, label, macro)
    public long[] stripOffsetsInSVS = null;
    public long[] stripOffsetsInSVSOffsetInSVS = null;
    public int[] stripLengths = null;
    public long[] stripLengthsOffsetInSVS = null;
    public int rowsPerStrip = -1;

    // this is needed to clobber the ICC profile in the SVS to prevent double-
    // color-correction (i.e., by this utility and then by a client rendering
    // the SVS)
    public int tagICCNameOffsetInHeader = -1;

    // here are the raw tile bytes
    public long[] tagTileOffsetsInSvs = null;
    public long[] tagTileOffsetsInSvsOffsetInSVS = null;
    public int[] tagTileLengths = null;
    public long[] tagTileLengthsOffsetInSVS = null;
    public long tileDataContigOffsetInSVS = -1;
    public long tileDataContigLength = -1;
    
    // here are objectified representations of the tiles
    Tile[] tilesInTIFFOrder = null;
    Tile[] tilesInSVSOrder = null;

    public TIFFDir(String id, SVSFile svsFile, long offsetInSvs) {

        this.id = id;
        
        // 1. parse the TIFF tags
        {
            int numberOfTagsLength = 0x00000008; // GT450 BigTIFF
            boolean allArraysAreDereferenced = false; // GT450 BigTIFF
            if(svsFile.longLength == 0x00000004) {
                numberOfTagsLength = 0x00000002; // AT2 TIFF
                allArraysAreDereferenced = true;// AT2 TIFF
            }
            this.offsetInSvs = offsetInSvs;
            long currentOffsetInHeader = 0;
            tagNumberOfTags = (int)ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + numberOfTagsLength));
            currentOffsetInHeader += numberOfTagsLength;
            for(int x = 0; x < tagNumberOfTags; x++) {
                int tagName = ByteUtil.bytesToShort(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000002));
                long osTagName = offsetInSvs + currentOffsetInHeader;
                currentOffsetInHeader += 0x00000002;
                int tagDataType = ByteUtil.bytesToShort(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x00000002));
                long osTagDataType = offsetInSvs + currentOffsetInHeader;
                currentOffsetInHeader += 0x00000002;
                int tagLength = (int)ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + svsFile.longLength));
                long osTagLength = offsetInSvs + currentOffsetInHeader;
                currentOffsetInHeader += svsFile.longLength;
                switch(tagDataType) {
                    case 4: {
                        if(tagLength == 1 || !allArraysAreDereferenced) {
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
                                tiffTag.elementValues[y] = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + svsFile.longLength));
                                tiffTag.osElementValues[y] = offsetInSvs + currentOffsetInHeader;
                                currentOffsetInHeader += svsFile.longLength;
                            }
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
                                tiffTag.osElementValuesDereferenced[y] = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + svsFile.longLength)) + (svsFile.longLength * y);
                                tiffTag.elementValuesDereferenced[y] = ByteUtil.bytesToLong(svsFile.getBytes(tiffTag.osElementValuesDereferenced[y], tiffTag.osElementValuesDereferenced[y] + svsFile.longLength));
                            }
                            tiffTag.osElementValue = offsetInSvs + currentOffsetInHeader;
                            currentOffsetInHeader += svsFile.longLength;
                        }
                        break;
                    }
                    case 3: {
                        if(tagLength == 1 || !allArraysAreDereferenced) {
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
                            int bytesRead = 0;
                            for(int y = 0; y < tagLength; y++) {
                                tiffTag.elementValues[y] = ByteUtil.bytesToShort(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + 0x0000002));
                                tiffTag.osElementValues[y] = offsetInSvs + currentOffsetInHeader;
                                currentOffsetInHeader += 0x00000002;
                                bytesRead += 2;
                            }
                            while(bytesRead % svsFile.longLength != 0) {
                                currentOffsetInHeader += 0x00000002;
                                bytesRead += 2;
                            }
                        }
                        else {
                            TIFFTagShortArrayReference tiffTag = new TIFFTagShortArrayReference();
                            tiffTagMap.put(tagName, tiffTag);
                            tiffTag.name = tagName;
                            tiffTag.osName = osTagName;
                            tiffTag.length = tagLength;
                            tiffTag.dataType = tagDataType;
                            tiffTag.osDataType = osTagDataType;
                            tiffTag.osLength = osTagLength;
                            tiffTag.elementValuesDereferenced = new int[tagLength];
                            tiffTag.osElementValuesDereferenced = new long[tagLength];
                            for(int y = 0; y < tagLength; y++) {
                                tiffTag.osElementValuesDereferenced[y] = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + svsFile.longLength)) + (0x00000002 * y);
                                tiffTag.elementValuesDereferenced[y] = (int)ByteUtil.bytesToLong(svsFile.getBytes(tiffTag.osElementValuesDereferenced[y], tiffTag.osElementValuesDereferenced[y] + 0x00000002));
                            }
                            tiffTag.osElementValue = offsetInSvs + currentOffsetInHeader;
                            currentOffsetInHeader += svsFile.longLength;
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
                        tiffTag.osElementValueDereferenced = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + svsFile.longLength));
                        tiffTag.elementValueDereferenced = new String(svsFile.getBytes(tiffTag.osElementValueDereferenced, tiffTag.osElementValueDereferenced + tiffTag.length));
                        tiffTag.osElementValue = offsetInSvs + currentOffsetInHeader;
                        currentOffsetInHeader += svsFile.longLength;
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
                                tiffTag.elementValues[y] = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + svsFile.longLength));
                                tiffTag.osElementValues[y] = offsetInSvs + currentOffsetInHeader;
                                currentOffsetInHeader += svsFile.longLength;
                            }
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
                                tiffTag.osElementValuesDereferenced[y] = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + svsFile.longLength)) + (svsFile.longLength * y);
                                tiffTag.elementValuesDereferenced[y] = ByteUtil.bytesToLong(svsFile.getBytes(tiffTag.osElementValuesDereferenced[y], tiffTag.osElementValuesDereferenced[y] + svsFile.longLength));
                            }
                            tiffTag.osElementValue = offsetInSvs + currentOffsetInHeader;
                            currentOffsetInHeader += svsFile.longLength;
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
                        tiffTag.osElementValueDereferenced = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + svsFile.longLength));
                        tiffTag.elementValuesDereferenced = svsFile.getBytes(tiffTag.osElementValueDereferenced, tiffTag.osElementValueDereferenced + tiffTag.length);
                        tiffTag.osElementValue = offsetInSvs + currentOffsetInHeader;
                        currentOffsetInHeader += svsFile.longLength;
                        break;
                    }
                    default: {
                        System.err.println(String.format("error parsing TIFF directory header tag #%d (%d)", x, tagDataType));
                        System.exit(1);
                    }
                }
            }
            tagNextDirOffsetInSvs = ByteUtil.bytesToLong(svsFile.getBytes(offsetInSvs + currentOffsetInHeader, offsetInSvs + currentOffsetInHeader + svsFile.longLength));
            tagNextDirOffsetInSvsOffsetInSvs = offsetInSvs + currentOffsetInHeader;
            currentOffsetInHeader += svsFile.longLength;
        }

        // 2. put the tags we care about into more convenient representations
        {
            subfileType = (int)((TIFFTagLong)tiffTagMap.get(254)).elementValues[0];
            width = tiffTagMap.get(256) instanceof TIFFTagLong ? (int)((TIFFTagLong)tiffTagMap.get(256)).elementValues[0] : ((TIFFTagShort)tiffTagMap.get(256)).elementValues[0];
            height = tiffTagMap.get(257) instanceof TIFFTagLong ? (int)((TIFFTagLong)tiffTagMap.get(257)).elementValues[0] : ((TIFFTagShort)tiffTagMap.get(257)).elementValues[0];
            tileWidth = tiffTagMap.get(322) != null ? ((TIFFTagShort)tiffTagMap.get(322)).elementValues[0] : -1;
            tileHeight = tiffTagMap.get(323) != null ? ((TIFFTagShort)tiffTagMap.get(323)).elementValues[0] : -1;
            description = ((TIFFTagASCIIReference)tiffTagMap.get(270)).elementValueDereferenced;
            {
                Pattern p = Pattern.compile(".*\\|MPP = ([\\.0-9]*)\\|.*", Pattern.DOTALL); // DOTALL b/c the description is multi-line
                Matcher m = p.matcher(description);
                if(m.matches()) {
                    mpp = Float.valueOf(m.group(1));
                }
            }
            tagICCOffsetInSvs = tiffTagMap.get(34675) != null ? ((TIFFTagUndefinedReference)tiffTagMap.get(34675)).osElementValueDereferenced : -1;
            tagICCLength = tiffTagMap.get(34675) != null ? ((TIFFTagUndefinedReference)tiffTagMap.get(34675)).length : -1;
            tagICCNameOffsetInHeader = tiffTagMap.get(34675) != null ? (int)(((TIFFTagUndefinedReference)tiffTagMap.get(34675)).osName - offsetInSvs) : -1;
            tagJPEGTablesOffsetInSvs = tiffTagMap.get(347) != null ? ((TIFFTagUndefinedReference)tiffTagMap.get(347)).osElementValueDereferenced : -1;
            tagJPEGTablesLength = tiffTagMap.get(347) != null ? ((TIFFTagUndefinedReference)tiffTagMap.get(347)).length : -1;
            tagTileOffsetsInSvs = tiffTagMap.get(324) != null ? ((TIFFTagLongArrayReference)tiffTagMap.get(324)).elementValuesDereferenced : null;
            tagTileOffsetsInSvsOffsetInSVS = tiffTagMap.get(324) != null ? ((TIFFTagLongArrayReference)tiffTagMap.get(324)).osElementValuesDereferenced : null;
            tagTileLengths = tiffTagMap.get(325) != null ? Arrays.stream(((TIFFTagLongArrayReference)tiffTagMap.get(325)).elementValuesDereferenced).mapToInt(i -> (int)i).toArray() : null;
            tagTileLengthsOffsetInSVS = tiffTagMap.get(325) != null ? ((TIFFTagLongArrayReference)tiffTagMap.get(325)).osElementValuesDereferenced : null;
            // at the moment, for the non-tiled TIFF directories like the label, etc., I am only
            // interested in the non-striped ones that the GT450 does
            if(tiffTagMap.get(273) != null) {
                stripOffsetsInSVS = tiffTagMap.get(273) instanceof TIFFTagLong ? ((TIFFTagLong)tiffTagMap.get(273)).elementValues : ((TIFFTagLongArrayReference)tiffTagMap.get(273)).elementValuesDereferenced;
                stripOffsetsInSVSOffsetInSVS = tiffTagMap.get(273) instanceof TIFFTagLong ? ((TIFFTagLong)tiffTagMap.get(273)).osElementValues : ((TIFFTagLongArrayReference)tiffTagMap.get(273)).osElementValuesDereferenced;
                stripLengths = tiffTagMap.get(279) instanceof TIFFTagLong ? Arrays.stream(((TIFFTagLong)tiffTagMap.get(279)).elementValues).mapToInt(i -> (int)i).toArray() : Arrays.stream(((TIFFTagLongArrayReference)tiffTagMap.get(279)).elementValuesDereferenced).mapToInt(i -> (int)i).toArray();
                stripLengthsOffsetInSVS = tiffTagMap.get(279) instanceof TIFFTagLong ? ((TIFFTagLong)tiffTagMap.get(279)).osElementValues : ((TIFFTagLongArrayReference)tiffTagMap.get(279)).osElementValuesDereferenced;
                rowsPerStrip = ((TIFFTagShort)tiffTagMap.get(278)).elementValues[0];
            }
            widthInTiles = (int)Math.ceil(1f * width / tileWidth);
            heightInTiles = (int)Math.ceil(1f * height / tileHeight);
        }

        // 3. put the tiles into a more convenient representation
        if(tagTileOffsetsInSvs != null) {
            List<Tile> tileList = new ArrayList<>();
            for(int x = 0; x < tagTileOffsetsInSvs.length; x++) {
                Tile tile = new Tile();
                tileList.add(tile);
                tile.indexInTiffDir = x;
                tile.offsetInSVS = tagTileOffsetsInSvs[x];
                tile.length = tagTileLengths[x];
                tile.tileX = x % widthInTiles;
                tile.tileY = x / widthInTiles;
                tile.id = String.format("%s.%d.%d", id, tile.tileX, tile.tileY);
                svsFile.tileMap.put(tile.id, tile);
            }
            tilesInTIFFOrder = tileList.toArray(new Tile[tileList.size()]);
            Collections.sort(tileList, new Comparator<Tile>() {
                @Override
                public int compare(Tile o1, Tile o2) {
                    if(o1.offsetInSVS > o2.offsetInSVS) { return 1; }
                    else if(o1.offsetInSVS < o2.offsetInSVS) { return -1; }
                    return 0;
                }
            });
            int x = 0;
            for(Tile tile : tileList) {
                tile.indexInSVS = x;
                x++;
            }
            tilesInSVSOrder = tileList.toArray(new Tile[tileList.size()]);
            tileDataContigOffsetInSVS = tilesInSVSOrder[0].offsetInSVS;
            tileDataContigLength = tilesInSVSOrder[tilesInSVSOrder.length - 1].offsetInSVS + tilesInSVSOrder[tilesInSVSOrder.length - 1].length;
        }
        
    }
    
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
    
    public class TIFFTagLongArrayReference extends TIFFTag {
        long[] elementValuesDereferenced = null;
        long[] osElementValuesDereferenced = null;
        long osElementValue;
    }
    
    public class TIFFTagShort extends TIFFTag {
        int[] elementValues = null;
        long[] osElementValues = null;
    }
    
    public class TIFFTagShortArrayReference extends TIFFTag {
        int[] elementValuesDereferenced = null;
        long[] osElementValuesDereferenced = null;
        long osElementValue;
    }

    public class TIFFTagASCIIReference extends TIFFTag {
        String elementValueDereferenced = null;
        long osElementValueDereferenced = -1;
        long osElementValue;
    }
    
    public class TIFFTagUndefinedReference extends TIFFTag {
        byte[] elementValuesDereferenced = null;
        long osElementValueDereferenced = -1;
        long osElementValue;
    }

}

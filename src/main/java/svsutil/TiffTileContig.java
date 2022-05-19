package svsutil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author geoffrey.smith@emory.edu
 */
public class TiffTileContig {
    
    public long offsetInSvs;
    public int length;
    public long[] tagTileOffsetsInSvs;
    public long[] tagTileOffsetsInSvsOffsetInSvs;
    public int[] tagTileLengths;
    public long[] tagTileLengthsOffsetInSvs;
    
    Map<Integer, byte[]> recoloredTileBytesMap = new ConcurrentHashMap<>();
    
}

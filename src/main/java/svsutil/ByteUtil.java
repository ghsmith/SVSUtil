package svsutil;

/**
 *
 * @author geoffrey.smith@emory.edu
 */
public class ByteUtil {

    // all of these methods need error handling
    
    // least significant byte first (TIFF)
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

    // least significant byte first (TIFF)
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
    
}

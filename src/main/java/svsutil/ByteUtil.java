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
    
    // most significant byte first (ICC color profile)
    static float bytesToFloatMSF(byte[] bs) {
        float val;
        int val1 = (bs[0] & 0xff) << 8 | (bs[1] & 0xff) << 0;
        int val2 = (bs[2] & 0xff) << 8 | (bs[3] & 0xff) << 0;
        val = 1f * val1 + (1f * val2 / 0xffff);
        return val;
    }

    // most significant byte first (ICC color profile)
    static int bytesToIntMSF(byte[] bs) {
        int val;
        if(bs.length == 4){
            val = (bs[0] & 0xff) << 24 | (bs[1] & 0xff) << 16 | (bs[2] & 0xff) << 8 | (bs[3] & 0xff) << 0;
        }
        else if(bs.length == 2) {
            val = (bs[0] & 0xff) << 8 | (bs[1] & 0xff) << 0;
        }
        else {
            val = -1;
        }
        return val;
    }
    
    public static String bytesToString(byte[] bs) {
        String val = new String(bs);
        return val;
    }
    
}

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

/**
 * 
 * @author geoffrey.smith@emory.edu
 */
public class ByteUtil {

    // all of these methods need error handling
    
    // least significant byte first (TIFF)
    // Java doesn't have unsigned long, so this ignores the most significant
    // byte, fortunately 2^48 addressing is adequante for our purposes, even
    // with WSI
    public static long bytesToLong(byte[] bs) {
        if(bs.length > 4) {
            long val = -1;
            val = ((long)bs[0]) <<  0 & 0x00000000000000ffL
                | ((long)bs[1]) <<  8 & 0x000000000000ff00L
                | ((long)bs[2]) << 16 & 0x0000000000ff0000L
                | ((long)bs[3]) << 24 & 0x00000000ff000000L
                | ((long)bs[4]) << 32 & 0x000000ff00000000L
                | ((long)bs[5]) << 40 & 0x0000ff0000000000L
                | ((long)bs[6]) << 48 & 0x00ff000000000000L;
            return val;
        }
        else if(bs.length > 2) {
            long val = -1;
            val = ((long)bs[0]) <<  0 & 0x00000000000000ffL
                | ((long)bs[1]) <<  8 & 0x000000000000ff00L
                | ((long)bs[2]) << 16 & 0x0000000000ff0000L
                | ((long)bs[3]) << 24 & 0x00000000ff000000L;
            return val;
        }
        else {
            long val = -1;
            val = ((long)bs[0]) <<  0 & 0x00000000000000ffL
                | ((long)bs[1]) <<  8 & 0x000000000000ff00L;
            return val;
        }
    }

    // least significant byte first (TIFF)
    // Java doesn't have unsigned short, so this must be a Java int to avoid
    // sign problems
    public static int bytesToShort(byte[] bs) {
        int val = -1;
        val = (int)bs[0] <<  0 & (int)0x000000ff
            | (int)bs[1] <<  8 & (int)0x0000ff00;
        return val;
    }
    
    public static String bytesToString(byte[] bs) {
        String val = new String(bs);
        return val;
    }
    
}

package svsutil;

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

    static Logger logger = Logger.getLogger(SVSFile.class.getName());    

    public static final int BUFFER_SIZE = 500000000;
    
    public static final int R = 0;
    public static final int G = 1;
    public static final int B = 2;

    public String svsFileName;
    public long length = -1;
    public int threads = -1;
    
    public List<byte[]> svsBytesList = new ArrayList<>();
    public List<TIFFDir> tiffDirList = new ArrayList<>();
    public byte[] iccBytes = null;

    public boolean lutComputed = false;
    public int[][][][] lutUpsampled = new int[0x100][0x100][0x100][3];
    public int[] lutUpsampledInt = new int[0x100 * 0x100 * 0x100];

    public Integer nextTileNo = 0;
    
    public SVSFile(String svsFileName, int threads) throws FileNotFoundException, IOException, InterruptedException {

        this.svsFileName = svsFileName;
        this.threads = threads;

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
            TIFFDir tiffDir = new TIFFDir(this, offset);
            tiffDirList.add(tiffDir);
            logger.log(Level.INFO, String.format("TIFF directory #%d header parsed with %d tags", tiffDirList.size(), tiffDir.tagNumberOfTags));
            if(!tiffDir.tileContigList.isEmpty()) {
                logger.log(Level.INFO, String.format("this directory has %d tile contigs", tiffDir.tileContigList.size()));
                for(int x = 0; x < tiffDir.tileContigList.size(); x++) {
                    logger.log(Level.INFO, String.format("...contig %d consists of %d tiles and %d bytes (starts %d ends %d)", x, tiffDir.tileContigList.get(x).tagTileOffsetsInSvs.length, tiffDir.tileContigList.get(x).length, tiffDir.tileContigList.get(x).offsetInSvs, tiffDir.tileContigList.get(x).offsetInSvs + tiffDir.tileContigList.get(x).length));
                }
                if(tiffDir.tagICCNameOffsetInHeader != -1) {
                    setByte(tiffDir.offsetInSvs + tiffDir.tagICCNameOffsetInHeader + 0, (byte)0xff); // clobber ICC in the TIFF directory
                    setByte(tiffDir.offsetInSvs + tiffDir.tagICCNameOffsetInHeader + 1, (byte)0xff); // clobber ICC in the TIFF directory
                    logger.log(Level.INFO, String.format("this directory has an ICC color profile and will be color-corrected"));
                    if(iccBytes == null) {
                        iccBytes = getBytes(tiffDir.tagICCOffsetInSvs, tiffDir.tagICCOffsetInSvs + tiffDir.tagICCLength);
                    }
                }
            }
            offset = tiffDir.tagNextDirOffsetInSvs;
        }

    }

    public void write() throws FileNotFoundException, IOException {
        FileOutputStream fos = new FileOutputStream(svsFileName.replace(".", "_recolored."));
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

package svsutil;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
/*import org.libjpegturbo.turbojpeg.TJ;
import org.libjpegturbo.turbojpeg.TJCompressor;
import org.libjpegturbo.turbojpeg.TJDecompressor;*/

/**
 *
 * @author geoffrey.smith@emory.edu
 */
class RecolorLibJpegTurboRunner extends RecolorRunner {

    public RecolorLibJpegTurboRunner(SVSFile svsFile, int threads, int quality, int skip) throws InterruptedException {
        super(svsFile, threads, quality, skip);
    }

    @Override
    public void run() {
        /*try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(500000);
            ImageOutputStream ios = new MemoryCacheImageOutputStream(baos);
            BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR);
            TJDecompressor decomp = new TJDecompressor();
            TJCompressor comp = new TJCompressor();
            comp.setJPEGQuality(quality);
            comp.setSubsamp(TJ.SAMP_444);
            int[] imagePixels = new int[0x100 * 0x100];
            int tileNo = -1;
            for(int x = 0; x < svsFile.tiffDirList.size(); x++) {
                TIFFDir tiffDir = svsFile.tiffDirList.get(x);
                for(int y = 0; y < tiffDir.tileContigList.size(); y++) {
                    TiffTileContig tileContig = tiffDir.tileContigList.get(y);
                    for(int z = 0; z < tileContig.tagTileOffsetsInSvs.length; z++) {
                        tileNo++;
                        synchronized(svsFile.nextTileNo) {
                            if(tileNo < svsFile.nextTileNo) {
                                continue;
                            }
                            for(int a = 0; a < skip; a++) {
                                tileContig.recoloredTileBytesMap.put(z + a + 1, svsFile.getBytes(tileContig.tagTileOffsetsInSvs[z + a + 1], tileContig.tagTileOffsetsInSvs[z + a + 1] + tileContig.tagTileLengths[z + a + 1]));
                            }
                            svsFile.nextTileNo += skip + 1;
                        }
                        decomp.setSourceImage(svsFile.getBytes(tileContig.tagTileOffsetsInSvs[z], tileContig.tagTileOffsetsInSvs[z] + tileContig.tagTileLengths[z]), tileContig.tagTileLengths[z]);
                        decomp.decompress(image, 0);
                        imagePixels = image.getRGB(0, 0, 0x100, 0x100, imagePixels, 0, 0x100);
                        for(int xy = 0; xy < 0x100 * 0x100; xy++) {
                            imagePixels[xy] = (imagePixels[xy] & 0xff000000) | (svsFile.lutUpsampledInt[imagePixels[xy] & 0x00ffffff]);
                        }
                        image.setRGB(0, 0, 0x100, 0x100, imagePixels, 0, 0x100);
                        comp.setSourceImage(image, 0, 0, 0, 0);
                        byte[] imageBytes = comp.compress(0);
                        int imageLength = comp.getCompressedSize();
                        tileContig.recoloredTileBytesMap.put(z, Arrays.copyOfRange(imageBytes, 0, imageLength));
                    }
                }
            }
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }*/
    }

}

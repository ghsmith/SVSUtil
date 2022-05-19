package svsutil;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 *
 * @author geoffrey.smith@emory.edu
 */
class RecolorRunner implements Runnable {

    public static Integer nextTileNo = 0;
    
    public SVSFile svsFile;
    public int quality;
    public int skip;

    public RecolorRunner(SVSFile svsFile, int quality, int skip) {
        this.svsFile = svsFile;
        this.quality = quality;
        this.skip = skip;
    }

    @Override
    public void run() {
        try {
            Iterator iter = ImageIO.getImageWritersByFormatName("jpeg");            
            ImageWriter writer = (ImageWriter)iter.next();
            ImageWriteParam iwp = writer.getDefaultWriteParam();
            iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            iwp.setCompressionQuality(quality / 100f);
            int tileNo = -1;
            for(int x = 0; x < svsFile.tiffDirList.size(); x++) {
                TIFFDir tiffDir = svsFile.tiffDirList.get(x);
                for(int y = 0; y < tiffDir.tileContigList.size(); y++) {
                    TiffTileContig tileContig = tiffDir.tileContigList.get(y);
                    for(int z = 0; z < tileContig.tagTileOffsetsInSvs.length; z++) {
                        tileNo++;
                        synchronized(nextTileNo) {
                            if(tileNo < nextTileNo) {
                                continue;
                            }
                            for(int a = 0; a < skip; a++) {
                                tileContig.recoloredTileBytesMap.put(z + a + 1, svsFile.getBytes(tileContig.tagTileOffsetsInSvs[z + a + 1], tileContig.tagTileOffsetsInSvs[z + a + 1] + tileContig.tagTileLengths[z + a + 1]));
                            }
                            nextTileNo += skip + 1;
                        }
                        InputStream is = new ByteArrayInputStream(svsFile.getBytes(tileContig.tagTileOffsetsInSvs[z], tileContig.tagTileOffsetsInSvs[z] + tileContig.tagTileLengths[z]));
                        BufferedImage image = ImageIO.read(is);
                        is.close();
                        for(int xx = 0; xx < image.getWidth(); xx++) {
                            for(int yy = 0; yy < image.getHeight(); yy++) {
                                int pixelIn = image.getRGB(xx, yy);
                                int r = (pixelIn & 0x00ff0000) >> 16;
                                int g = (pixelIn & 0x0000ff00) >>  8;
                                int b = (pixelIn & 0x000000ff) >>  0;
                                image.setRGB(xx, yy, (pixelIn & 0xff000000) | ((svsFile.lutUpsampled[r][g][b][SVSFile.R] << 16) & 0x00ff0000) | ((svsFile.lutUpsampled[r][g][b][SVSFile.G] <<  8) & 0x0000ff00) | ((svsFile.lutUpsampled[r][g][b][SVSFile.B] <<  0) & 0x000000ff));
                            }
                        }
                        ByteArrayOutputStream baos = new ByteArrayOutputStream(50000);
                        ImageOutputStream ios = new MemoryCacheImageOutputStream(baos);
                        writer.setOutput(ios);
                        IIOImage newImage = new IIOImage(image, null, null);
                        writer.write(null, newImage, iwp);
                        tileContig.recoloredTileBytesMap.put(z, baos.toByteArray());
                        ios.close();
                        baos.close();
                    }
                }
            }
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

}

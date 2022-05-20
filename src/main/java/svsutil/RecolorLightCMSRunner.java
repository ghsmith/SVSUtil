package svsutil;

/*import com.gmail.etordera.jcms.IccProfile;
import com.gmail.etordera.jcms.IccTransformer;
import com.gmail.etordera.jcms.JCMS;*/
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Iterator;
import java.util.logging.Logger;
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
class RecolorLightCMSRunner extends RecolorRunner {

    static Logger logger = Logger.getLogger(RecolorLightCMSRunner.class.getName());    

    public RecolorLightCMSRunner(SVSFile svsFile, int threads, int quality, int skip) throws InterruptedException {
        super(svsFile, threads, quality, skip);
    }

    @Override
    public void run() {
        /*try {
            Iterator iter = ImageIO.getImageWritersByFormatName("jpeg");            
            ImageWriter writer = (ImageWriter)iter.next();
            ImageWriteParam iwp = writer.getDefaultWriteParam();
            iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            iwp.setCompressionQuality(quality / 100f);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(500000);
            ImageOutputStream ios = new MemoryCacheImageOutputStream(baos);
            writer.setOutput(ios);
            ICC_Profile sourceProfile = (new IccProfile(svsFile.iccBytes)).getICC_Profile();
            ICC_Profile destProfile = (new IccProfile(new File("sRGB_Color_Space_Profile.icm"))).getICC_Profile();
            IccTransformer iccTransformer = new IccTransformer(destProfile, JCMS.INTENT_PERCEPTUAL, false);
            iccTransformer.setJpegQuality(quality / 100f); // I don't think this matters
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
                        InputStream is = new ByteArrayInputStream(svsFile.getBytes(tileContig.tagTileOffsetsInSvs[z], tileContig.tagTileOffsetsInSvs[z] + tileContig.tagTileLengths[z]));
                        BufferedImage image = ImageIO.read(is);
                        is.close();
                        iccTransformer.transform(image, sourceProfile);
                        IIOImage outputImage = new IIOImage(image, null, null);
                        baos.reset();
                        writer.write(null, outputImage, iwp);
                        tileContig.recoloredTileBytesMap.put(z, baos.toByteArray());
                    }
                }
            }
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }*/
    }

}

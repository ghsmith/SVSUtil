package svsutil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import org.apache.commons.cli.ParseException;

/**
 *
 * @author geoffrey.smith@emory.edu
 */
public class SVSUtil {

    public static void main(String[] args) throws IOException, FileNotFoundException, InterruptedException {

        if(args.length == 0 || !"colorutil".equals(args[0])) {
            System.err.println("USAGE: java -jar svsutil.jar [application]");
            System.err.println();
            System.err.println("available applications: colorutil");
            System.exit(1);
        }

        ColorUtil.main(Arrays.copyOfRange(args, 1, args.length));
        
    }
    
}        

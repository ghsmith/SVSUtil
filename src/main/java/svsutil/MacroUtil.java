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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * 
 * @author geoffrey.smith@emory.edu
 */
public class MacroUtil {
    
    static final Logger logger = Logger.getLogger(MacroUtil.class.getName());    

    public static void main(String[] args) throws IOException, InterruptedException {
        
        boolean extract = false;
                
        Options options = new Options();

        Option optionExtract = new Option("x", "extract", false, String.format("extract macro to JPG"));
        optionExtract.setRequired(false);
        options.addOption(optionExtract);
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null; //not a good practice, it serves it purpose 

        try {
            cmd = parser.parse(options, args);
            if(cmd.hasOption(optionExtract)) { extract = true; }
            if(cmd.getArgs().length != 1) { throw new ParseException("no file specified"); }
            if(!cmd.getArgs()[0].toLowerCase().endsWith(".svs")) { throw new ParseException("file name must have a 'svs' extension"); }
        }
        catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("java -jar svsutil.jar macroutil [options] svs_file_name", options);
            System.exit(1);
        }
        
        final SVSFile svsFile = new SVSFile(cmd.getArgs()[0]);

        if(extract) {

            for(TIFFDir tiffDir : svsFile.tiffDirList) {


// I need to make this work for classic TIFF strips. Right now, this only works
// for the GT450, which doesn't use classic TIFF strips. See "LabelUtil,"
// which does work with classic TIFF strips to see how to do this.

                if(tiffDir.description.contains("macro")) {

                    byte[] imageBytes = svsFile.getBytes(tiffDir.stripOffsetsInSVS[0], tiffDir.stripOffsetsInSVS[0] + tiffDir.stripLengths[0]);

                    Files.write(Paths.get(svsFile.svsFileName.replaceAll(".svs$", "_macro.jpg")), imageBytes);
                    
                    logger.log(Level.INFO, String.format("macro written to %s in current directory", (new File(svsFile.svsFileName)).getName().replaceAll(".svs$", "_label.jpg")));
                   
                    break;

                }
                
            }

        }

    }
    
}

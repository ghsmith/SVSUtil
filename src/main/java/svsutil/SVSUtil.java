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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

/**
 * 
 * @author geoffrey.smith@emory.edu
 */
public class SVSUtil {

    public static void main(String[] args) throws IOException, FileNotFoundException, InterruptedException {

        if(args.length == 0 || !("colorutil".equals(args[0]) || "labelutil".equals(args[0]) || "macroutil".equals(args[0]))) {
            System.err.println("USAGE: java -jar svsutil.jar [application]");
            System.err.println();
            System.err.println("available applications: colorutil, labelutil, macroutil");
            System.exit(1);
        }

        if("colorutil".equals(args[0])) {
            ColorUtil.main(Arrays.copyOfRange(args, 1, args.length));
        }
        
        if("labelutil".equals(args[0])) {
            LabelUtil.main(Arrays.copyOfRange(args, 1, args.length));
        }
        
        if("macroutil".equals(args[0])) {
            MacroUtil.main(Arrays.copyOfRange(args, 1, args.length));
        }

    }
    
}        

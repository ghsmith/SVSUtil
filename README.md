# SVSUtil
This utility recolors the image tiles in an SVS file using the ICC profile embedded in the SVS metadata.
This might be useful when you need to render an SVS using an application that is not color-managed.

This utility has only been tested with SVS files acquired on a Leica/Aperio GT450 scanner.

Build with Maven (i.e., "mvn package").

usage: java -jar svsutil.jar [options] file-to-recolor.svs
 -q,--quality <arg>   JPEG compression quality, integer 0 - 100 (default =
                      87)
 -s,--skip <arg>      skip this many tiles when recoloring; used to create
                      interesting patterns of raw/recolored tiles, integer
                      (default = 0)
 -t,--threads <arg>   number of parallel threads, integer (default = 4)

The following SVS was created using this command line:

`java -jar uber-SVSUtil-1.0-SNAPSHOT.jar -q80 -s1 -t24 test_slide_small.svs`
  
![example of a recolored SVS in ImageScope](recolor_example.png)

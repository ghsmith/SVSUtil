# SVS Utilities

These utility has only been tested with SVS files acquired on a Leica/Aperio GT450 scanner.

This is a Java project that builds with Maven (i.e., "mvn package").

## Recolor Utility

This utility recolors the image tiles in an SVS file using the ICC profile embedded in the SVS metadata.
This might be useful when you need to render an SVS file using an application that is not color-managed.

The positions of the TIFF headers and contiguous blocks of tiles in the SVS file are maintained.
The recolored SVS file is the same size as the original SVS file.
If the recolored tiles do not fit into the space avaialble in the SVS file, reducing the JPEG compression quality by one (e.g., from 87 to 86) usually allows the recolored tiles to fit into the space available.

```
usage: java -jar svsutil.jar colorutil [options] svs_file_name  
-q,--quality <arg>   JPEG compression quality, integer 0 - 100 (default = 87)  
-s,--skip <arg>      skip this many tiles when recoloring; used to create interesting patterns of raw/recolored tiles, integer (default = 0)  
-t,--threads <arg>   number of parallel threads, integer (default = 4)
```

The following SVS was created using this command line:

`java -jar svsutil.jar colorutil -q80 -s1 -t24 test_slide_small.svs`
  
![example of a recolored SVS in ImageScope](recolor_example.png)

## Label Utility

This utility manipulates the label in an SVS file.

```
usage: java -jar svsutil.jar labelutil [options] svs_file_name
 -a,--annotate <arg>   add string annotation to label (e.g., -a "test annotation)"
 -r,--replace <arg>    replace label entirely (e.g., -r "study set #1<br/>case#2)"
 -x,--extract          extract label to JPG
 ```
The following SVS was created using this command line:

`java -jar svsutil.jar labelutil -a "WARN: IQ_FOCUS" test_slide_small.svs`
  
![example of an annotated SVS label in ImageScope](annotate_example.png)

`java -jar svsutil.jar labelutil -r "Dr. Schiznits<br/>Study Set #1<br/>Case #3" test_slide_small.svs`
  
![example of a replaced SVS label in ImageScope](relabel_example.png)

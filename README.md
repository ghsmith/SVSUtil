# SVS Utilities

These utilities have been tested with ScanScope Virtual Slide (SVS) files acquired on a Leica/Aperio GT450 scanner in one focal plane and on a Leica/Aperio AT2 scanner in one focal plane. I have tested with files up to 3GB in size. See "Technical Details" below.

If you try these utilities on an SVS file created by another Leica/Aperio scanner model and/or something doesn't work, let me know (geoffrey.smith@emory.edu) and I'll work on it!

This project uses the J2SE API and does not rely on any native libraries (e.g. OpenSlide). The standard Java ImageIO API and color management system (I believe the OpenJDK uses Little-CMS) included with modern releases of the OpenJDK virutal machine provide excellent performance for working with JPEG content in SVS files. An LZW encoder/decoder from the https://haraldk.github.io/TwelveMonkeys/ project is used (slide labels are LZW encoded and there is no LZW encoder/decoder in the J2SE API). This is a Java project that builds an executable JAR with Maven (i.e., "mvn package").

```
git clone https://github.com/ghsmith/SVSUtil
mvn package
```

## [Re]color Utility

This utility recolors the image tiles in an SVS file using the International Color Consortium (ICC) profile embedded in the SVS metadata.
This might be useful when you need to render an SVS file using an application that is not color-managed.
To be clear, any software rendering an SVS file should respect the ICC profile in the SVS metadata and perform these color corrections automatically, but I have found that some software that renders SVS files does not respect the ICC profile in the SVS metadata and renders the compressed color gamut of the scanner instead of the full sRGB color gamut you'd like to see.

The positions of the TIFF directory headers and contiguous blocks of tiles in the SVS file are maintained unless the "resize" option is specified.
If the resize option is specified, the positions of the TIFF directory headers and contiguous blocks of tiles in the SVS file may shift.
Regardless of whether the resize option is specified, the order of tiles in the SVS file is preserved.

```
usage: java -jar svsutil.jar colorutil [options] svs_file_name
 -a,--annotate        if specified, annotate every tile with its tile ID
                      and microns per pixel (default = do not annotate)
 -n,--norecolor       if specified, tiles are rewritten at specified
                      quality, but not recolored (default = do recolor)
 -q,--quality <arg>   JPEG compression quality, integer 0 - 100 (default =
                      87)
 -r,--resize          if specified, allow program to resize the SVS file
                      (default = do not resize file)
 -s,--skip <arg>      skip this many tiles when recoloring; used to create
                      interesting patterns of raw/recolored tiles, integer
                      (default = 0)
 -t,--threads <arg>   number of parallel threads, integer (default = 4)
```

The following SVS was created from a GT450 scan using this command line:

`java -jar svsutil.jar colorutil -q80 -s1 -t24 test_slide_small.svs`
  
![example of a recolored SVS in ImageScope](recolor_example.png)

The following SVS was created from a GT450 scan using this command line:

`java -jar svsutil.jar colorutil -t24 -r -a test_slide_small.svs`
  
![example of a tile-annotated SVS in ImageScope](tile_annotate_example.png)

Although it is obvious, I have found that the best performance is achieved with more recent OpenJDK releases on CPUs with many cores. All SVS file operations are performed in-memory, so it may be necessary to increase your Java heap size for large files. For example, the following command line uses OpenJDK 17 with a 4GB heap and runs in 24 concurrent threads:

`/usr/lib/jvm/java-17-openjdk-amd64/bin/java -Djava.awt.headless=true -Dawt.toolkit=sun.awt.HToolkit -Xms4G -Xmx4G -jar svsutil.jar colorutil -t24 test_slide.svs`

## Label Utility

This utility manipulates the label in an SVS file.

```
usage: java -jar svsutil.jar labelutil [options] svs_file_name
 -a,--annotate <arg>   add string annotation to label (e.g., -a "test
                       annotation")
 -m,--monochrome       if specified, any label written to SVS is converted
                       to monochrome (default = do not use monochrome)
 -r,--resize           if specified, allow program to resize the SVS file
                       (default = do not resize file)
 -s,--string <arg>     replace label entirely with a string (e.g., -r
                       "study set #1<br/>case#2")
 -x,--extract          extract label to JPG
```
The following SVS was created using this command line:

`java -jar svsutil.jar labelutil -a "WARN: IQ_FOCUS" test_slide_small.svs`
  
![example of an annotated SVS label in ImageScope](annotate_example.png)

The following SVS was created using this command line:

`java -jar svsutil.jar labelutil -s "Dr. Schiznits<br/>Study Set #1<br/>Case #3" test_slide_small.svs`
  
![example of a replaced SVS label in ImageScope](relabel_example.png)

## Technical Details

### Leica/Aperio GT450 operated at 40x / one focal plane

The SVS files generated by our GT450 scanners have a TIFF image description that starts with "Aperio Leica Biosystems GT450 v1.0.1" and contain 7 TIFF directories in this order:

- TIFF Directory #0 - High-resolution (0.26 microns/pixel) histology. 256x256 pixel 24-bit color JPEG image tiles. This directory includes an ICC profile. These image tiles are recolored by "colorutil."
- TIFF Directory #1 - Thumbnail histology. 24-bit color JPEG image (not tiled). This directory includes an ICC profile. This image is NOT recolored by "colorutil."
- TIFF Directory #2 - Medium-high-resolution (1.1 microns/pixel) histology. 256x256 pixel 24-bit color JPEG image tiles. This directory includes an ICC profile. These image tiles are recolored by "colorutil."
- TIFF Directory #3 - Medium-low-resolution (4.2 microns/pixel) histology. 256x256 pixel 24-bit color JPEG image tiles. This directory includes an ICC profile. These image tiles are recolored by "colorutil."
- TIFF Directory #4 - Low-resolution (17 microns/pixel) histology. 256x256 pixel 24-bit color JPEG image tiles. This directory includes an ICC profile. These image tiles are recolored by "colorutil."
- TIFF Directory #5 - Label. Horizontally-differenced LZW-encoded 24-bit color bitmap (not tiled). This directory does not include an ICC profile. This image is not recolored by "colorutil" but is manipulated by "labelutil."
- TIFF Directory #6 - Low-resolution "macro" image of entire slide, including a portion of the label, annotated with scan area, focus points, and white balance point. 24-bit color JPEG image (not tiled). This directory does not include an ICC profile. This image is not recolored by "colorutil."

The ICC profiles in #0 - #4 are identical and primarily consist of a 103 x 103 x 103 color lookup table (CLUT) that is approximately 13 Mb in size. Since the ICC profiles are identical, "colorutil" uses the ICC profile in #0 for all color correction operations. In order to prevent double-color transformation, the ICC profiles in the generated SVS file are clobbered by changing the TIFF tag name from 0x8773 (34675) to 0xffff.

The tile bytes in the GT450 SVS files consist of JPEG tiles with JFIF application segment (APP0) and JPEG quantization and Huffman table segments that can be rendered without any manipulation in any JPEG viewer. For example, if you know the offset and length of a tile (e.g., from "tiffdump") then the following "dd" command will extract a JPG you can view in any JPEG viewer:

`dd if=file.svs of=tile.jpg skip={offset-to-tile} count={tile-length} iflag=skip_bytes,count_bytes`

The tile bytes in #0 are ordered in the SVS files in rows from bottom-to-top and then left-to-right within each row (i.e., the first tile in the SVS file is the tile at the bottom-left corner, the second tile is the tile to the right of that one, etc.). The tile bytes for #2, #3 and #4 are ordered in the SVS files from left-to-right and then top-to-bottom (i.e., the first tile in the SVS file is the tile at the top-left corner, the second tile is the tile to the right of that one, etc.).

### Leica/Aperio AT2 operated at 20x / one focal plane

The SVS files generated by our AT2 scanners have a TIFF image description that starts with "Aperio Image Library v12.0.15" and contain 5 TIFF directories in this order:

- TIFF Directory #0 - High-resolution (0.50 microns/pixel) histology. 240x240 pixel 24-bit color JPEG image tiles. This directory includes an ICC profile. These image tiles are recolored by "colorutil."
- TIFF Directory #1 - Thumbnail histology. 24-bit color JPEG image (not tiled). This directory does not include an ICC profile. This image is NOT recolored by "colorutil." Although this image is not tiled, it does use "classic" TIFF strips.
- TIFF Directory #2 - Medium-high-resolution (2.0 microns/pixel) histology. 240x240 pixel 24-bit color JPEG image tiles. This directory does not include an ICC profile. These image tiles are recolored by "colorutil."
- TIFF Directory #3 - Low-resolution (8.0 microns/pixel) histology. 240x240 pixel 24-bit color JPEG image tiles. This directory does not include an ICC profile. These image tiles are recolored by "colorutil."
- TIFF Directory #4 - Label. Horizontally-differenced LZW-encoded 24-bit color bitmap (not tiled). This directory does not include an ICC profile. This image is not recolored by "colorutil." Although this image is not tiled, it does use "classic" TIFF strips.
- TIFF Directory #5 - Low-resolution "macro" image of entire slide, including a portion of the label, annotated with scan area, focus points, and white balance point. 24-bit color JPEG image (not tiled). This directory does not include an ICC profile. This image is not recolored by "colorutil." Although this image is not tiled, it does use "classic" TIFF strips.

The ICC profile in #0 primarily consists of a 25 x 25 x 25 color lookup table (CLUT). This is considerably smaller than the CLUT used by the GT450. Presumably, this ICC profile is supposed to be used with all images in the SVS file, even though it is only included in the first TIFF directory in the SVS file. In order to prevent double-color transformation, the ICC profile in the generated SVS file is clobbered by changing the TIFF tag name from 0x8773 (34675) to 0xffff.

The JPEG tiles in the AT2 SVS files are unusual and much more difficult to work with than those in the GT450 SVS files:

- Most images are converted to YCbCr colorspace before JPEG encoding. The AT2 does not perform this colorspace conversion prior to JPEG encoding, so the JPEG tile bytes in the AT2 SVS files represent JPEG-encoded RGB colorspace. I have seen this called a "native RGB JPEG" by some sources. Care must be taken to ensure that the client rendering a tile knows this, or the client will assume it needs to perform a YCbCr-to-RGB colorspace conversion after JPEG decoding that will mangle the colors. I think the easiest way to make sure these tiles are rendered properly is to inclue an "Adobe" application segment (APP14) that specifies no-color-transform (i.e., "0xff 0xfe 0x00 0x0e A d o b e 0x00 0x00 0x00 0x00 0x00 0x00 0x00"). Additionaly, do not include a JFIF application segment (APP0), or that will override the APP14 segment.

- The raw tile bytes are not renderable JPEG images, so the "dd" trick showed above with the GT450 doesn't work with AT2 tiles. I have seen this called "abbreviated JPEG" by some sources. Specifically, the tiles do not include JPEG segments for the quantization and Huffman tables (i.e., the JPEG tables required to decode the image) and the JPEG segmens do not include an application segment (i.e., as discussed in the previous paragraph). The JPEG tables are included in the TIFF directory tags. Each TIFF directory has its own JPEG tables and it is important to render tiles using the JPEG tables for the correct TIFF directory. You must provide the application segment.

- Any AT2 image tiles rewritten by this utility preserve all of these features in the tile bytes. Specifically, JPEG encoding is performed with the same JPEG quantization and Huffman tables that are in the original SVS file and the tiles are written as "abreviated" JPEGs. Because the same quantization and Huffman tables are used, the JPEG "quality" setting is ignored with AT2 SVS files.

The tile bytes in #0 are ordered in the SVS files in columns (the GT450 uses rows, not columns) from left-to-right and then top-to-bottom within each column (i.e., the first tile in the SVS file is the tile at the top-left corner, the second tile is the tile below that one, etc.). The tile bytes for #2 and #3 are ordered in the SVS files from left-to-right and then top-to-bottom, similarly to the GT450 SVS files (i.e., the first tile in the SVS file is the tile at the top-left corner, the second tile is the tile to the right of that one, etc.).

Finally, the label image uses "classic" TIFF strips, where the horizontally-differenced LZW-encoded 24-bit bitmap image is divided into horizontal strips. "Labelutil" is currently not compatible with TIFF strips and does not work with AT2 SVS files. I will work on fixing this soon.

### Leica/Aperio AT2 operated at 40x / one focal plane

...coming soon...
 

//
// ChannelMerger.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-2006 Melissa Linkert, Curtis Rueden and Eric Kjellman.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.*;

/** Logic to automatically merge channels in a file. */
public class ChannelMerger extends FormatReader {

  // -- Fields --

  /** FormatReader used to read the file. */
  private FormatReader reader;

  /** The dimension order. */
  private String order;

  /** The Z, C, and T dimensions. */
  private int[] dimensions;

  // -- Constructors --

  /** Constructs a ChannelMerger with the given reader. */
  public ChannelMerger(FormatReader r) throws FormatException {
    super("any", "*");
    if (r == null) throw new FormatException("FormatReader cannot be null");
    reader = r;
  }

  // -- FormatReader API methods --

  /** Checks if the given block is a valid header for this file format. */
  public boolean isThisType(byte[] block) {
    return reader.isThisType(block);
  }

  /** Determines the number of images in the given file. */
  public int getImageCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);

    int originalImages = reader.getImageCount(id);

    if (canMerge(id)) {
      int channels = getChannelCount(id);
      if (channels > 3) originalImages /= 3;
      else originalImages /= channels;
    }
    return originalImages;
  }

  /** Checks if the images in the file are RGB. */
  public boolean isRGB(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);

    int channels = getChannelCount(id);
    boolean multipleChannels = channels > 1;
    return multipleChannels && (channels == 3 && reader.isRGB(id)) &&
      !separated;
  }

  /** Returns the number of channels in the file. */
  public int getChannelCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return reader.getChannelCount(id);
  }

  /** Obtains the specified image from the given file. */
  public BufferedImage openImage(String id, int no)
    throws FormatException, IOException
  {
    int[] nos = translateVirtual(no, id);

    int size = nos.length;
    if (size != 1 && size != 3 && size != 4) size = 3;
    BufferedImage[] img = new BufferedImage[size];

    int width = 1;
    int height = 1;

    for (int i=0; i<img.length; i++) {
      if (i < nos.length) {
        img[i] = reader.openImage(id, nos[i]);
        width = img[i].getWidth();
        height = img[i].getHeight();
      }
      else {
        Object o = ImageTools.getPixels(img[nos.length - 1]);
        int mul = 1;
        if (o instanceof short[][]) mul = 2;
        else if (o instanceof int[][]) mul = 4;
        else if (o instanceof float[][]) mul = 4;
        else mul = 8;

        byte[] b = new byte[width * height * mul];
        img[i] = ImageTools.makeImage(b, width, height, 1,
          false, mul, true);
      }
    }
    return ImageTools.mergeChannels(img);
  }

  /**
   * Obtains the specified image from the given file as a byte array.
   * For convenience, the channels are sequential, i.e. "RRR...GGG...BBB".
   */
  public byte[] openBytes(String id, int no)
    throws FormatException, IOException
  {
    int[] nos = translateVirtual(no, id);

    ByteVector v = new ByteVector(1000);
    for (int i=0; i<nos.length; i++) {
      v.add(reader.openBytes(id, nos[i]));
    }

    return v.toByteArray();
  }

  /** Closes the currently open file. */
  public void close() throws FormatException, IOException {
    if (reader != null) reader.close();
    reader = null;
    currentId = null;
  }

  // -- ChannelMerger API methods --

  /** Determines whether the channels in the file can be merged. */
  public boolean canMerge(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);

    int channels = getChannelCount(id);
    boolean multipleChannels = channels > 1;
    return multipleChannels && !(channels == 3 && reader.isRGB(id)) &&
      !separated;
  }

  // -- Helper methods --

  /** Initializes the given file. */
  protected void initFile(String id) throws FormatException, IOException
  {
    reader.initFile(id);

    Object root = reader.getOMENode(id);
    order = OMETools.getDimensionOrder(root);

    dimensions = new int[3];
    dimensions[0] = ((Integer) OMETools.getSizeZ(root)).intValue();
    dimensions[1] = ((Integer) OMETools.getSizeC(root)).intValue();
    dimensions[2] = ((Integer) OMETools.getSizeT(root)).intValue();
  }

  /**
   * Translate a virtual plane number in a k-tuple of "real" plane numbers,
   * one per channel.
   */
  private int[] translateVirtual(int plane, String id)
    throws FormatException, IOException
  {
    int[] real = new int[reader.getImageCount(id) / getImageCount(id)];

    while (order.length() > 3) order = order.substring(1);

    if (order.charAt(0) == 'C') {
      for (int i=0; i<real.length; i++) {
        real[i] = plane * real.length + i;
      }
    }
    else {
      // determine the number of planes between channels

      int between = 1;
      if (order.charAt(0) == 'Z' || order.charAt(1) == 'Z') {
        between *= dimensions[0];
      }
      if (order.charAt(0) == 'T' || order.charAt(1) == 'T') {
        between *= dimensions[2];
      }

      for (int i=0; i<real.length; i++) {
        real[i] = plane + between * i;
      }
    }

    return real;
  }

}

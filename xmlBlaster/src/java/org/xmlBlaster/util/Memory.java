/*------------------------------------------------------------------------------
Name:      Memory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: Memory.java,v 1.1 1999/12/21 12:02:55 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.*;


/**
 * Some helper methods for memory handling.
 * May be used for test clients etc.
 */
public class Memory
{
   private final static String ME = "Memory";


  /**
   * Build a nice, human readable string for the size in MB/KB/Bytes.
   * <br><b>Example:</b><br>
   * <code>System.out.println(Memory.DataLenStr(136000));</code><br>
   *  -> "136 KB"
   * @param size is the size in bytes
   * @return a nice readable memory string
   */
   public static final String byteString(long size)
   {
      long tBytes = size / 1000000000L;
      long mBytes = size % 1000000000L / 1000000L;
      long kBytes = size % 1000000L / 1000L;
      long  bytes = size % 1000L;

      String str;
      if (tBytes != 0)
         str = "" + tBytes + "." + Math.abs(mBytes) + " TBytes";
      else if (mBytes != 0)
         str = "" + mBytes + "." + Math.abs(kBytes) + " MBytes";
      else if (kBytes != 0)
         str = "" + kBytes + "." + Math.abs(bytes) + " KBytes";
      else
         str = "" + bytes + " Bytes";

      return str;
   }


  /**
   * Access a nice, human readable string with the current RAM memory situation.
   * @return a nice readable memory statistic string
   */
   public static final String getStatistic()
   {
      StringBuffer statistic = new StringBuffer();
      statistic.append("Total memory available = ");
      statistic.append(byteString(Runtime.getRuntime().totalMemory()));
      statistic.append(" Free memory available = ");
      statistic.append(byteString(Runtime.getRuntime().freeMemory()));
      return statistic.toString();
   }


   /**
    * Testsuite for Memory class. 
    * <p />
    * Invoke: java org.xmlBlaster.util.Memory
    */
   public static void main( String[] args )
   {
      final String ME="Memory";
      long size;

      size=10L;
      Log.info(ME, "Converting " + size + " to " + Memory.byteString(size));

      size=999L;
      Log.info(ME, "Converting " + size + " to " + Memory.byteString(size));

      size=1999L;
      Log.info(ME, "Converting " + size + " to " + Memory.byteString(size));

      size=-1999L;
      Log.info(ME, "Converting " + size + " to " + Memory.byteString(size));

      size=19998907L;
      Log.info(ME, "Converting " + size + " to " + Memory.byteString(size));

      size=-779998907L;
      Log.info(ME, "Converting " + size + " to " + Memory.byteString(size));

      size=19998907000L;
      Log.info(ME, "Converting " + size + " to " + Memory.byteString(size));
   }
}



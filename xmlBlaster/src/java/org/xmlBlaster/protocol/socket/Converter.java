/*------------------------------------------------------------------------------
Name:      Converter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Converter class for raw socket messages
Version:   $Id: Converter.java,v 1.3 2002/02/13 15:38:22 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

/**
 * Converter class for raw socket messages, use as a base class
 * @author ruff@swand.lake.de
 */
public class Converter
{
   public static final int NUM_FIELD_LEN = 10;
   public static final int FLAG_FIELD_LEN = 6;
   public static final int MAX_STRING_LEN = Integer.MAX_VALUE;

   protected long index = 0L;

   // create only once
   private ByteArray byteArray = new ByteArray(126);

   /**
    * Use this method instead of in.read() directly, as we add
    * an index counter here
    */
   public final int readNext(InputStream in) throws IOException {
      index++;
      return in.read();
   }

   /**
    * Converts 10 bytes from InputStream to a long
    */
   public final long toLong(InputStream in) throws IOException {
      byteArray.reset();
      for (int ii=0; ii<NUM_FIELD_LEN; ii++) {
         int val = readNext(in);
         if (val == 0)
            break;
         if (val == -1)
            throw new IOException("Can't read expected " + NUM_FIELD_LEN + " bytes from socket");
         byteArray.write(val);
      }
      try {
         return Long.parseLong(byteArray.toString().trim());
      }
      catch (NumberFormatException e) {
         throw new IOException("Format is corrupted '" + byteArray.toString() + "', expected integral value");
      }
   }

   /**
    * Converts 10 bytes from InputStream to a long
    */
   public final byte[] toByte(InputStream in) throws IOException {
      byteArray.reset();
      long len = toLong(in);
      if (len >= Integer.MAX_VALUE) throw new IllegalArgumentException("Length of data is bigger " + Integer.MAX_VALUE + " bytes");
      byte[] b = new byte[(int)len];
      {
         in.read(b, 0, (int)len);
         index += len;
      }
      return b;
   }

   /**
    * Converts bytes from InputStream until \0 to a long
    */
   public final long toLong0(InputStream in, long defaultVal) throws IOException {
      String tmp = toString(in);
      if (tmp == null || tmp.length() < 1)
         return defaultVal;
      try {
         return Long.parseLong(tmp.trim());
      }
      catch (NumberFormatException e) {
         throw new IOException("Format is corrupted '" + byteArray.toString() + "', expected integral value");
      }
   }

   /**
    * Extracts string until next null byte '\0'
    */
   public final String toString(InputStream in) throws IOException {
      byteArray.reset();
      for (int ii=0; ii<MAX_STRING_LEN; ii++) {
         int val = readNext(in);
         if (val == 0)
            break;
         if (val == -1)
            throw new IOException("Can't read expected string '" + byteArray.toString()+ "' to its end");
         byteArray.write(val);
      }
      //System.out.println("Reading string '" + byteArray.toString() + "'");
      return byteArray.toString();
   }
}

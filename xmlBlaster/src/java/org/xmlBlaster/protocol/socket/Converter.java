/*------------------------------------------------------------------------------
Name:      Converter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Converter class for raw socket messages
Version:   $Id: Converter.java,v 1.1 2002/02/12 21:40:47 ruff Exp $
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
   public static int NUM_FIELD_LEN = 10;
   public static int FLAG_FIELD_LEN = 6;
   public static int MAX_STRING_LEN = Integer.MAX_VALUE;

   // create only once
   private StringBuffer buf = new StringBuffer();
   private byte[] msgLenB = new byte[NUM_FIELD_LEN];


   /**
    * Converts 10 bytes from InputStream to a long
    */
   public final long toLong(InputStream in) throws IOException {
      for (int ii=0; ii<NUM_FIELD_LEN; ii++) {
         int val = in.read();
         if (val == -1) throw new IOException("Can't read expected " + NUM_FIELD_LEN + " bytes from socket");
         msgLenB[ii] = (byte)val;
         //System.out.println("Reading '" + msgLenB[ii] + "'");

      }
      try {
         return Long.parseLong(new String(msgLenB).trim());
      }
      catch (NumberFormatException e) {
         throw new IOException("Format is corrupted '" + new String(msgLenB) + "', expected integral value");
      }
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
         throw new IOException("Format is corrupted '" + new String(msgLenB) + "', expected integral value");
      }
   }

   /**
    * Extracts string until next null byte '\0'
    */
   public final String toString(InputStream in) throws IOException {
      buf.setLength(0);
      for (int ii=0; ii<MAX_STRING_LEN; ii++) {
         int val = in.read();
         if (val == -1) throw new IOException("Can't read expected string '" + buf.toString()+ "' to its end");
         buf.append(val);
      }
      System.out.println("Reading string '" + buf.toString() + "'");
      return buf.toString();
   }
}

// kObjects 
//
// Copyright (C) 2001 Stefan Haustein, Oberhausen (Rhld.), Germany
//
// Contributors: 
//
// License: LGPL
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public License
// as published by the Free Software Foundation; either version 2.1 of
// the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
// USA


package org.xmlBlaster.util;
import java.io.*;

/*
 * You can now use also javax.mail.internet.MimeUtility
 * and sun.misc.BASE64Encoder.encode.
 * There is a non-public class in Java 1.4+ called java.util.prefs.Base64
 * 
 * CAUTION: Fails on ARM BigEndian like Android Phones, works fine for Intels Little Endian
 */
public class Base64 {

    static final char[] charTab = 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray (); 

    
    public static String encode (byte [] data) {
        return encode (data, 0, data.length, null).toString ();
    }


    /** Encodes the part of the given byte array denoted by start and
        len to the Base64 format.  The encoded data is appended to the
        given StringBuffer. If no StringBuffer is given, a new one is
        created automatically. The StringBuffer is the return value of
        this method. */
 

    public static StringBuffer encode (byte [] data, int start, int len, StringBuffer buf) {

        if (buf == null) 
            buf = new StringBuffer (data.length * 3 / 2);

        int end = len - 3;
        int i = start;
        int n = 0;

        while (i <= end) {
            int d = (((data [i]) & 0x0ff) << 16) 
                | (((data [i+1]) & 0x0ff) << 8)
                | ((data [i+2]) & 0x0ff);

            buf.append (charTab [(d >> 18) & 63]);
            buf.append (charTab [(d >> 12) & 63]);
            buf.append (charTab [(d >> 6) & 63]);
            buf.append (charTab [d & 63]);

            i += 3;

            if (n++ >= 14) {
                n = 0;
                buf.append ("\r\n");
            }
        }


        if (i == start + len - 2) {
            int d = (((data [i]) & 0x0ff) << 16) 
                | (((data [i+1]) & 255) << 8);

            buf.append (charTab [(d >> 18) & 63]);
            buf.append (charTab [(d >> 12) & 63]);
            buf.append (charTab [(d >> 6) & 63]);
            buf.append ("=");
        }
        else if (i == start + len - 1) {
            int d = ((data [i]) & 0x0ff) << 16;

            buf.append (charTab [(d >> 18) & 63]);
            buf.append (charTab [(d >> 12) & 63]);
            buf.append ("==");
        }

        return buf;
    }


    static int decode (char c) {
        if (c >= 'A' && c <= 'Z') 
            return c - 65;
        else if (c >= 'a' && c <= 'z') 
            return c - 97 + 26;
        else if (c >= '0' && c <= '9')
            return c - 48 + 26 + 26;
        else switch (c) {
        case '+': return 62;
        case '/': return 63;
        case '=': return 0;
        default:
            throw new RuntimeException (new StringBuffer("unexpected code: ").append(c).toString());
        }
    }

    public static byte [] decode (byte[] b) {
    	try {
			return decode(new String(b, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return decode(new String(b));
		}
    }

    /** Decodes the given Base64 encoded String to a new byte array. 
     *  The byte array holding the decoded data is returned.
     *  @return never null
     */
    public static byte [] decode (String s) {
    	if (s == null)
    		return new byte[0];
        int i = 0;
        int len = s.length ();
        ByteArrayOutputStream bos = new ByteArrayOutputStream (len);
        
        while (true) { 
            while (i < len && s.charAt (i) <= ' ') i++;

            if (i == len) break;

            int tri = (decode (s.charAt (i)) << 18)
                + (decode (s.charAt (i+1)) << 12)
                + (decode (s.charAt (i+2)) << 6)
                + (decode (s.charAt (i+3)));
            
            bos.write ((tri >> 16) & 255);
            if (s.charAt (i+2) == '=') break;
            bos.write ((tri >> 8) & 255);
            if (s.charAt (i+3) == '=') break;
            bos.write (tri & 255);

            i += 4;
        }
        return bos.toByteArray ();
    }

   /**
    * java org.xmlBlaster.util.Base64 -file MyFile.jpg
    * java org.xmlBlaster.util.Base64 HelloWorld -> SGVsbG9Xb3JsZA==
    * java org.xmlBlaster.util.Base64 -decodeFile MyFile.base64
    * java org.xmlBlaster.util.Base64 -decode Q2lBOGEyVjVJRzlwWkQwblNHVnNiRzhuSUdOdmJuUmxiblJOYVcxbFBTZDBaWGgwTDNodGJDY2dZMjl1ZEdWdWRFMXBiV1ZGZUhSbGJtUmxaRDBuTVM0d0p6NEtJQ0E4YjNKbkxuaHRiRUpzWVhOMFpYSStQR1JsYlc4dE16NDhMMlJsYlc4dE16NDhMMjl5Wnk1NGJXeENiR0Z6ZEdWeVBnb2dQQzlyWlhrKw==
 * @throws XmlBlasterException 
    */
   public static void main(String[] args) throws XmlBlasterException {
      if (args.length == 2) {
         if (args[0].equals("-decode")) {
            String base64 = args[1];
            byte[] back = Base64.decode(base64);
            System.out.println("Decoded to '" + new String(back) + "'");
            return;
         }
         else if (args[0].equals("-decodeFile")) {
             String fileName = args[1];
             byte[] base64 = FileLocator.readFile(fileName);
             byte[] back = Base64.decode(base64);
             String fn = fileName+".bin";
             FileLocator.writeFile(fn, back);
             System.out.println("Decoded to file '" + fn + "'");
             return;
          }
         else if (args[0].equals("-file")) {
             String fileName = args[1];
             byte[] bytes = FileLocator.readFile(fileName);
             String base64 = Base64.encode(bytes);
             System.out.print(base64);
             return;
          }
         /* FileLocator is not known in J2ME:
         if (args[0].equals("-fn")) {
            try {
               byte[] bb = FileLocator.readFile(args[1]);
               String base64 = Base64.encode(bb);
               System.out.println("Content of '" + args[1] + "' encoded to base64 '" + base64 + "'");
               return;
            }
            catch (Exception e) {
               e.printStackTrace();
            }
         }
         */
      }
      {
         String hello = args.length > 0 ? args[0] : "Hello World";
         String base64 = Base64.encode(hello.getBytes());
         byte[] back = Base64.decode(base64);
         System.out.println("Before Base64 '" + hello + "' base64='" + (new String(base64)) + "' after '" + new String(back) + "'");
      }
      {
    	  //javax.mail.internet.MimeUtility.decode(arg0, arg1)
    	  //sun.misc.BASE64Encoder.encode.
    	  //java.util.prefs.Base64
      }
   }
}



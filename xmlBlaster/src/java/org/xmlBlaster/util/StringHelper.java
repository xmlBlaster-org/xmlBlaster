/*------------------------------------------------------------------------------
Name:      StringHelper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster, see xmlBlaster.property
Version:   $Id: StringHelper.java,v 1.1 2000/05/09 14:56:49 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.*;
import java.util.Properties;
import java.util.Enumeration;


/**
 * Some utilities to manipulate strings. 
 */
public class StringHelper
{
   private final static String ME = "StringHelper";


   /**
    * Replace exactly one occurrence of "from" with to "to"
    */
   public final static String replace(String str, String from, String to)
   {
      if (str == null || str.length() < 1 || from == null || to == null) return str;
      int index = str.indexOf(from);
      if (index >= 0) {
         StringBuffer tmp = new StringBuffer("");
         if (index > 0)
            tmp.append(str.substring(0, index));
         tmp.append(to);
         tmp.append(str.substring(index + from.length()));
         return tmp.toString();
      }
      else
         return str;
   }


   /**
    * Replace all occurrences of "from" with to "to".
    */
   public final static String replaceAll(String str, String from, String to)
   {
      if (str == null || str.length() < 1 || from == null || to == null) return str;
      if (str.indexOf(from) == -1) return str;

      StringBuffer buf = new StringBuffer("");
      String tail = str;
      while (true) {
         int index = tail.indexOf(from);
         if (index >= 0) {
            if (index > 0)
               buf.append(tail.substring(0, index));
            buf.append(to);
            tail = tail.substring(index + from.length());
         }
         else
            break;
      }
      buf.append(tail);
      return buf.toString();
   }


   /**
    * For testing only
    * <p />
    * java org.xmlBlaster.util.StringHelper
    */
   public static void main(String args[])
   {
      String ME = "StringHelper";

      String input = "Hello my friend, my dear friend";
      Log.plain(ME, "Converting '" + input + "' to (one replacement):\n" +
                    "'" + StringHelper.replace(input, "friend", "Jacqueline") + "'");
      Log.plain(ME, "Converting '" + input + "' to (all replacement):\n" +
                    "'" + StringHelper.replaceAll(input, "friend", "Jacqueline") + "'");

      input = "Hello my friend, my dear friend";
      Log.plain(ME, "Converting '" + input + "' to (one replacement):\n" +
                    "'" + StringHelper.replace(input, "H", "") + "'");
      Log.plain(ME, "Converting '" + input + "' to (all replacement):\n" +
                    "'" + StringHelper.replaceAll(input, "H", "") + "'");

      input = "Hello my friend, my dear friend";
      Log.plain(ME, "Converting '" + input + "' to (one replacement):\n" +
                    "'" + StringHelper.replace(input, null, "") + "'");
      Log.plain(ME, "Converting '" + input + "' to (all replacement):\n" +
                    "'" + StringHelper.replaceAll(input, null, "") + "'");

      input = "Hello my friend, my dear friend";
      Log.plain(ME, "Converting '" + input + "' to (one replacement):\n" +
                    "'" + StringHelper.replace(input, "#", "Jacqueline") + "'");
      Log.plain(ME, "Converting '" + input + "' to (all replacement):\n" +
                    "'" + StringHelper.replaceAll(input, "#", "Jacqueline") + "'");

      input = "Hello my friend, my dear friend";
      Log.plain(ME, "Converting '" + input + "' to (one replacement):\n" +
                    "'" + StringHelper.replace(input, "l", "&l;") + "'");
      Log.plain(ME, "Converting '" + input + "' to (all replacement):\n" +
                    "'" + StringHelper.replaceAll(input, "l", "&l;") + "'");

      input = " ";
      Log.plain(ME, "Converting '" + input + "' to (one replacement):\n" +
                    "'" + StringHelper.replace(input, "l", "&l;") + "'");
      Log.plain(ME, "Converting '" + input + "' to (all replacement):\n" +
                    "'" + StringHelper.replaceAll(input, "l", "&l;") + "'");

      Log.exit(ME, "Good bye");
   }
}

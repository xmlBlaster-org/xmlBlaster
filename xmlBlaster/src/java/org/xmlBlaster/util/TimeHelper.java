/*------------------------------------------------------------------------------
Name:      TimeHelper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Some Date formatting
Version:   $Id: TimeHelper.java,v 1.3 2000/05/27 11:57:24 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


import java.text.DateFormat;
import java.util.Locale;

/**
 * Some static helper methods to get nice formatted time.
 */
public class TimeHelper
{
   /**
    * Generates a nice Date and Time string using the given look and feel.
    * <p />
    * Example:
    * <br>
    *   <code>TimeHelper.getDateTime(0, DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.GERMAN);</code><br>
    *   -> return "08.12.1999 16:30:01"
    * @param timestamp       elapsed millis since 1972<br>
    *                        or 0 to get the current date/time
    * @param lookAndFeelDate java.text.DateFormat.SHORT<br>
    *                        java.text.DateFormat.MEDIUM<br>
    *                        java.text.DateFormat.LONG<br>
    *                        java.text.DateFormat.FULL
    * @param lookAndFeelTime java.text.DateFormat.SHORT<br>
    *                        java.text.DateFormat.MEDIUM<br>
    *                        java.text.DateFormat.LONG<br>
    *                        java.text.DateFormat.FULL
    * @param country         java.util.Locale.GERMAN<br>
    *                        java.util.Locale.US
    * @return
    *        The nice formatted date/time string
    */
   public static final String getDateTime(final long timestamp, final int lookAndFeelDate, final int lookAndFeelTime, final java.util.Locale country)
   {
      java.util.Date date;
      if (timestamp == 0L)
         date = new java.util.Date();
      else
         date = new java.util.Date(timestamp);
      java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(lookAndFeelDate, lookAndFeelTime, country);
      return df.format(date);
   }


   /**
    * The default look and feel for dumping the internal date/time states
    */
   public static final String getDateTimeDump(final long timestamp)
   {
      return TimeHelper.getDateTime(timestamp, DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.US);
   }


   /**
    * Convert milliseconds to some more human readable representation. 
    * @param millis An amount of elapsed milliseconds
    * @return A human readable time string
    */
   public final static String millisToNice(long millis)
   {
      long seconds = millis / 1000;
      long sec = (seconds % 3600) % 60;
      long min = (seconds % 3600) / 60;
      long hour = seconds / 3600;

      StringBuffer strbuf = new StringBuffer(60);

      strbuf.append(" [ ");

      if (hour > 0L)
         strbuf.append(hour + " h ");
      if (min > 0L)
         strbuf.append(min + " min ");
      if (sec > 0L)
         strbuf.append(sec + " sec ");

      strbuf.append((millis % 1000) + " millis");

      strbuf.append(" ]");

      return strbuf.toString();
   }


   /**
    * Only for testing.
    * <p />
    * Invoke:    java org.xmlBlaster.util.TimeHelper
    */
   public static void main(String args[]) throws Exception
   {
      String ME = "TimeHelper-Tester";

      Log.info(ME, "Converting 12345 millis to " + TimeHelper.millisToNice(12345));

      Log.exit(ME, "Good bye");
   }
}



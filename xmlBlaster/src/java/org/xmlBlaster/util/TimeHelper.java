/*------------------------------------------------------------------------------
Name:      TimeHelper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Some Date formatting
Version:   $Id: TimeHelper.java,v 1.1 1999/11/22 09:29:24 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


import java.text.DateFormat;
import java.util.Locale;

/**
 * Measure the elapsed time
 */
public class TimeHelper
{
   /**
    * Generates a nice Date and Time string using the given look and feel
    * <br>
    * Example
    * <br>
    *   <code>TimeHelper.getDateTime(0, DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.GERMAN);</code><br>
    *   -> return "08.03.1999 15:28:19"
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
    *        The nice formatted date/tim string
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
}



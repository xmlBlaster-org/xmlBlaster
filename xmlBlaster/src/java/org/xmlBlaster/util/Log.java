/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.5 $  $Date: 1999/11/14 21:55:20 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Logging output
 */
public class Log
{
   private final static String ME = "Log";

   public static boolean HACK_POA = true;

   /**
    * Switch DEBUG mode on or of for performance reasons
    * if (Log.CALLS) Log.trace(....); -> dead code elimination
    */
   public final static boolean CALLS = true;  // trace method calls
   public final static boolean DEBUG = true;  // trace application flow

   /**
    * Adjust her your local look and feel
    */
   private final static int lookAndFeelDate = java.text.DateFormat.MEDIUM;
   private final static int lookAndFeelTime = java.text.DateFormat.MEDIUM;
   private final static java.util.Locale country = java.util.Locale.US;

   /**
    * Counter for occured warnings/errors
    */
   private static int numWarnInvocations = 0;
   private static int numErrorInvocations = 0;

   /**
    * Output text for different logging levels
    */
   private final static StringBuffer INSTANCE_SEPERATOR = new StringBuffer(":  ");
   private final static StringBuffer traceX = new StringBuffer("TRACE ");
   private final static StringBuffer plainX = new StringBuffer("      ");
   private final static StringBuffer infoX  = new StringBuffer("INFO  ");
   private final static StringBuffer warnX  = new StringBuffer("WARN  ");
   private final static StringBuffer errorX = new StringBuffer("ERROR ");
   private final static StringBuffer panicX = new StringBuffer("PANIC ");
   private final static StringBuffer exitX  = new StringBuffer("EXIT  ");

   /**
    * Colored output to xterm
    */
   private final static boolean withXtermEscapeColor = true;
   private final static StringBuffer INSTANCE_SEPERATOR_E = new StringBuffer("\033[1m:\033[0m  "); // bold
   private final static StringBuffer traceE = new StringBuffer("\033[37;40mTRACE\033[0m: ");   // white on black
   private final static StringBuffer plainE = new StringBuffer("\033[37;40m     \033[0m: ");   // white on black
   private final static StringBuffer infoE  = new StringBuffer("\033[32;40mINFO \033[0m: ");   // green
   private final static StringBuffer warnE  = new StringBuffer("\033[33;40mWARN \033[0m: ");   // yellow
   private final static StringBuffer errorE = new StringBuffer("\033[31;40mERROR\033[0m: ");   // red
   private final static StringBuffer panicE = new StringBuffer("\033[31;40mPANIC\033[0m: ");   // red
   private final static StringBuffer exitE  = new StringBuffer("\033[32;40mEXIT \033[0m: ");   // green


   /**
    * Create the Date/Time informations
    *
    * @return example: 1999-11-01 10:41:03 INFO
    */
   public final static StringBuffer logHeader(StringBuffer levelStr)
   {
      if (levelStr == null) return new StringBuffer("");

      java.util.Date currentDate = new java.util.Date();

      java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(lookAndFeelDate, lookAndFeelTime, country);

      String timeStr = df.format(currentDate);

      StringBuffer time = new StringBuffer(timeStr.length() + 4 + levelStr.length());

      return time.append(timeStr).append(" ").append(levelStr);
   }


   /**
    * Log example:
    *
    *   1999-11-01 10:41:03 INFO : xmlBlaster is ready to blast
    */
   public final static void log(final StringBuffer levelStr, String instance, String text)
   {
      System.err.println(logHeader(levelStr).append(instance).append(withXtermEscapeColor ? INSTANCE_SEPERATOR_E : INSTANCE_SEPERATOR).append(text));
   }


   /**
    * Display some statistic on exit
    */
   public final static void displayStatistics()
   {
      if (withXtermEscapeColor)
      {
         if (numErrorInvocations>0)
            Log.info(ME, "\033[31;40mThere were " + numErrorInvocations + " ERRORS and " + numWarnInvocations + " WARNINGS\033[0m");
         else if (numWarnInvocations>0)
            Log.info(ME, "\033[33;40mThere were " + numErrorInvocations + " ERRORS and " + numWarnInvocations + " WARNINGS\033[0m");
         else
            Log.info(ME, "\033[32;40mNo errors/warnings were reported\033[0m");
      }
      else
      {
         if (numErrorInvocations>0 || numWarnInvocations>0)
            Log.info(ME, "There were " + numErrorInvocations + " ERRORS and " + numWarnInvocations + " WARNINGS");
         else
            Log.info(ME, "No errors/warnings were reported");
         }
   }


   /**
    * The only way to stop the server
    * @param val exit code for operating system
    */
   private final static void exitLow(int val)
   {
      //Runtime.getRuntime().runFinalizersOnExit(true);
      Runtime.getRuntime().runFinalization();
      System.exit(val);
   }


   /**
    * Use this exit for errors
    */
   public final static void panic(String instance, String text)
   {
      log((withXtermEscapeColor) ? panicE : panicX, instance, text);
      exitLow(1);
   }


   /**
    * exit without errors
    */
   public final static void exit(String instance, String text)
   {
      log((withXtermEscapeColor) ? exitE : exitX, instance, text);
      exitLow(0);
   }


   public final static void info(String instance, String text)
   {
      log((withXtermEscapeColor) ? infoE : infoX, instance, text);
   }


   public final static void warning(String instance, String text)
   {
      numWarnInvocations++;
      log((withXtermEscapeColor) ? warnE : warnX, instance, text);
   }
   /*
   public final static void warningThrow(String instance, String text) throws org.xmlBlaster.XmlBlasterException
   {
      warning(instance, text);
      throw new org.xmlBlaster.XmlBlasterException(instance, text);
   }
   */

   public final static void error(String instance, String text)
   {
      numErrorInvocations++;
      log((withXtermEscapeColor) ? errorE : errorX, instance, text);
   }
   /*
   public final static void errorThrow(String instance, String text) throws org.xmlBlaster.XmlBlasterException
   {
      error(instance, text);
      throw new org.xmlBlaster.XmlBlasterException(instance, text);
   }
   */

   /*
    * Log without time/date
    */
   public final static void plain(String instance, String text)
   {
      log(null, instance, text);
   }

   public final static void trace(String instance, String text)
   {
      log((withXtermEscapeColor) ? traceE : traceX, instance, text);
   }


   /**
    * Only for testing
    *    java org.xmlBlaster.util.Log
    */
   public static void main(String args[]) throws Exception
   {
      String me = "Log-Tester";

      Log.trace(me, "TRACE: Entering method xy");
      Log.info(me, "Successfully loaded data");
      Log.warning(me, "your input is strange");
      Log.error(me, "given User unknown");
      Log.error(me, "another error");
      Log.panic(me, "Unrecoverable Error - good bye");
   }

}



/*------------------------------------------------------------------------------
Name:      Log.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Logging output to console/file, using org.jutils
Version:   $Id: Log.java,v 1.63 2002/04/29 09:43:31 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


import java.io.*;
import org.jutils.log.LogChannel;
import org.jutils.log.LogDeviceConsole;
import org.jutils.log.LogDeviceFile;
import org.jutils.init.Property;
import org.jutils.runtime.Memory;


/**
 * Logging output.
 * <p />
 * This wraps the jutils logging framework to xmlBlaster specific logging.
 * <p />
 * Note that the layout of this class is for optimum performance and ease of use.<br />
 * To increase performance of jutils you may choose to add final qualifiers<br />
 * to CALL/TIME/TRACE/DUMP variables to achieve dead code elimination (see code below).
 * <p />
 * To initialize the logging, include this call into your main(String[] args)
 * method at startup:
 * <pre>
 *     Log.setLogLevel(args); // initialize log level
 * or
 *     Property property;
 *     try {
 *        property = new Property("myApp.properties", true, args, true);
 *     } catch(org.jutils.JUtilsException e) { }
 *     Log.setLogLevel(property); // initialize log level with your property file and command line
 * </pre>
 * You can then invoke for example trace mode like:
 * <pre>
 * java MyApp -trace true
 * </pre>
 * <p />
 * If you want to redirect the logging into your application to handle it by yourself:
 * <pre>
 *   public class MyApp implements org.jutils.util.LogableDevice {
 *      ...
 *      void MyApp() {
 *         Log.addLogDevice(this);
 *         ...
 *      }
 *      ...
 *      // Event fired by Log.java through interface LogableDevice.
 *      public void log(int level, String source, String str) {
 *         System.out.println(str + "\n");
 *      }
 *      ...
 *   }
 * </pre>
 * @see org.jutils.init.Property
 */
public class Log
{
   private static final String ME = "Log";
   private static LogChannel lc = null;
   private static boolean first = true;

    /**
     * Counter for occurred warnings/errors
     */
   private static int numWarnInvocations = 0;
   private static int numErrorInvocations = 0;
/*
   static {
      try { XmlBlasterProperty.init(new String [0]); } catch(Throwable e) { System.out.println(e.toString()); }
      initialize(new String[0]);
   }
*/
   public static void initialize(Property property) {
   /*
      if (args != null && args.length > 0) {
         try { XmlBlasterProperty.addArgs2Props(args); } catch(Throwable e) { System.out.println(e.toString()); }
      }
   */
      setLogLevel(property);
        
      lc = new LogChannel(XmlBlasterProperty.getProperty());
      boolean bVal = XmlBlasterProperty.get("logConsole", true);
      if (bVal == true) {
         LogDeviceConsole ldc = new LogDeviceConsole(lc);
         lc.addLogDevice(ldc);
      }

      String strFilename = XmlBlasterProperty.get("logFile", (String)null);
      if (strFilename != null) {
         LogDeviceFile ldf = new LogDeviceFile(lc, strFilename);
         lc.addLogDevice(ldf);
      }

      init();

      Log.setLogChannel(lc);
      Log.info(ME, "XmlBlaster logging configured");
   }

   /**
    * Produce logging output on important method calls.
    * <p />
    * Logging output in the xmlBlaster code looks typically like this:<br />
    * <code>if (Log.TRACE) Log.trace(....); </code><br />
    * <p />
    * Switch CALL mode to <code>false</code> for performance reasons<br />
    * or switch it to <code>true</code> to allow debugging output.<br />
    * Every logging output is checked with this boolean comparison, which
    * is not CPU intense.
    * <p />
    * Dead code elimination with final qualifier:<br />
    * Note that you need to uncomment the final declaration, set all booleans to false.
    * Then recompile to achieve real dead code elimination.
    * <p />
    * The same applies for TIME, TRACE, DUMP and PLAIN
    */
   public /*final*/ static boolean CALL = false;  // trace method calls
   /**
    * Performance logging output true/false
    */
   public /*final*/ static boolean TIME  = false;  // trace performance
   /**
    * Fine grained code logging output true/false
    */
   public /*final*/ static boolean TRACE = false;  // trace application flow
   /**
    * Dump internal state in xml format, true/false
    */
   public /*final*/ static boolean DUMP  = false;  // dump internal state
   /**
    * Plain output without formatting
    */
   public /*final*/ static boolean PLAIN  = false;  // dump internal state


   /**
    * Method for setting the LogChannel object.
    */
   public static final void setLogChannel(LogChannel p_lc) {
      if(p_lc != null) {
         lc = p_lc;
      }
   }


   /**
    * Method for retrieving the LogChannel object.
    */
   public static final LogChannel getLogChannel() {
      return lc;
   }


   /**
    * Method for retrieving the LogChannel object.
    */
   public static final boolean isLoglevelEnabled(int level) {
      if (lc == null) return false;
      return lc.isLoglevelEnabled(level);
   }


   public static final void setLogLevel(int level) {
      if (lc != null) {
         lc.setLogLevel(level);
         init();
      }
   }


   public static final void setLogLevel(String[] args) {
      if (lc != null) {
         lc.initialize(args);
         init();
      }
   }


   public static final void setLogLevel(Property props) {
      if (lc != null) {
         lc.initialize(props);
         init();
      }
   }

   public static final void addLogLevel(String logLevel) {
      if (lc == null) return;
      lc.addLogLevel(logLevel);
      init();
   }

   public static void removeLogLevel(String logLevel) {
      if (lc == null) return;
      lc.removeLogLevel(logLevel);
      init();
   }

   public static int getLogLevel() {
      if (lc == null) return 0;
      return lc.getLogLevel();
   }

   public static final String bitToLogLevel(int level) {
      if (lc == null) return "";
      return lc.bitToLogLevel(level);
   }

   public static final void setDefaultLogLevel() {
      if (lc == null) return;
      lc.setDefaultLogLevel();
      init();
   }


   private static final void init()
   {
      if (lc == null) return;
      if (lc.isLoglevelEnabled(LogChannel.LOG_CALL))
         CALL = true;
      else
         CALL = false;

      if (lc.isLoglevelEnabled(LogChannel.LOG_TIME))
         TIME = true;
      else
         TIME = false;

      if (lc.isLoglevelEnabled(LogChannel.LOG_TRACE))
         TRACE = true;
      else
         TRACE = false;

      if (lc.isLoglevelEnabled(LogChannel.LOG_DUMP))
         DUMP = true;
      else
         DUMP = false;

      if (lc.isLoglevelEnabled(LogChannel.LOG_PLAIN))
         PLAIN = true;
      else
         PLAIN = false;
   }


   /**
    * delegate log exeption to LogChannel object.
    */
   public static final void exception(String source, Throwable t) {
      if (lc == null) return;
      lc.exception(source, t);
   }


   /**
    * delegate log error to LogChannel object.
    */
   public static final void error(String source, String text) {
      /* Old code:
      numErrorInvocations++;
      if (lc == null) return;
      lc.error(source, text);
      */
      try {
         numErrorInvocations++;
         String location = null;
         String tmp = source;
         if (location != null && tmp != null && tmp.length() > 0)
            tmp = location + "-" + source;
         else if (location != null)
            tmp = location;
         if (lc == null)
            System.err.println(tmp + ": " + text);
         else
            lc.error(tmp, text);
         if (text != null) {
            try {
               if (text.indexOf("java.lang.NullPointerException") >= 0 ||
                   text.indexOf("java.lang.ArrayIndexOutOfBoundsException") >= 0
                  )
               {
                  Exception e = new Exception();
                  e.printStackTrace();
               }
            } catch (Throwable e) {}
         }
      }
      catch(Throwable e) {
         System.err.println("Internal error in Log class: " + e.toString() + ", nested message is: " + source + ": " + text);
      }
   }


   /**
    * You need to customize this method,
    * We return true if the stackTrace line contains
    * our own position. We will use the following line to
    * calculate the position of the code where the exception is thrown.
    */
   private static boolean stopStackPos(String line)
   {
      //System.out.println("Log.java - Checking: " + line);
      if (line.indexOf("org.xmlBlaster.util.Log.error") >= 0)
         return true;
      return false;
   }


   /**
    * Show file name and line number (a minimal stack trace).
    * @return A nice string or null on error
    */
   private static String getLocation()
   {
      String location = null;
      try {
         StringWriter stringWriter = new StringWriter();
         PrintWriter printWriter = new PrintWriter(stringWriter);
         (new Exception()).printStackTrace(printWriter);
         String trace = stringWriter.getBuffer().toString();
         StringReader stringReader = new StringReader(trace);
         BufferedReader bufferedReader = new BufferedReader(stringReader);
         for (int ii=0; ii<20; ii++) {  // ignore first lines of stack trace (not important)
            String line = bufferedReader.readLine();
            // System.out.println("Stack: " + line);
            if (line == null) return null;
            if (stopStackPos(line) == true)
               break;
         }
         String stackEntry = bufferedReader.readLine().trim();
         if (stackEntry == null) return null;
         // System.out.println("Log.java - Exception scanning:" + stackEntry);
         int space = stackEntry.indexOf(" ");
         int paren = stackEntry.indexOf("(");
         int colon = stackEntry.indexOf(":");
         if (space < 0 || paren < 0 || colon < 0) return "";
         String method = stackEntry.substring(space + 1, paren);
         String sourceName = stackEntry.substring(paren + 1, colon);
         int line = 0;
         try {
            paren = stackEntry.indexOf(")");
            if (paren >= 0) {
               String ln = stackEntry.substring(colon+1, paren);
               line = Integer.parseInt(ln);
            }
         }
         catch (NumberFormatException e) { System.out.println("NumberFormatException in Log.java"); }
         //location = sourceName + ":" + method + ":" + line;
         //location = method + ":" + line;
         location = sourceName + ":" + line;
         System.out.println("Exception found location=" + location);
      }
      catch (Throwable e) { e.printStackTrace(); }

      return location;
   }


   /**
    * delegate log warn to LogChannel object.
    */
   public static final void warn(String source, String text) {
      numWarnInvocations++;
      if (lc == null) return;
      lc.warn(source, text);
   }


   /**
    * delegate log info to LogChannel object.
    */
   public static final void info(String source, String text) {
      if (lc == null) return;
      lc.info(source, text);
   }


   /**
    * delegate log call to LogChannel object.
    */
   public static final void call(String source, String text) {
      if (lc == null) return;
      lc.call(source, text);
   }


   /**
    * delegate log time to LogChannel object.
    */
   public static final void time(String source, String text) {
      if (lc == null) return;
      lc.time(source, text);
   }


   /**
    * delegate log trace to LogChannel object.
    */
   public static final void trace(String source, String text) {
      if (lc == null) return;
      lc.trace(source, text);
   }


   /**
    * delegate log plain to LogChannel object.
    */
   public static final void plain(String source, String text) {
      if (lc == null) {
         System.out.println(source + ": " + text);
         return;
      }
      lc.plain(source, text);
   }


   /*
    * Log without time/date
    */
   public static final void dump(String instance, String text)
   {
      if (lc == null) return;
      lc.dump(instance, text);
      lc.dump(instance, Memory.getStatistic());
   }

   /**
    * delegate log plain to LogChannel object.
    */
   public static final void plain(String text) {
      if (lc == null) {
         System.out.println(text);
         return;
      }
      lc.plain("unknown", text);
   }

   /**
    * The only way to stop the server
    * @param val exit code for operating system
    */
   private static final void exitLow(int val)
   {
      //Runtime.getRuntime().runFinalizersOnExit(true);
      Runtime.getRuntime().runFinalization();
      System.exit(val);
   }


   /**
    * Use this exit for errors
    */
   public static final void panic(String instance, String text)
   {
      System.err.println(text);
      try {
         error(instance, text);
      }
      catch(Throwable e) {
         System.err.println("Internal error in Log class: " + e.toString() + ", nested message is: " + instance + ": " + text);
      }
      numErrorInvocations++;
      displayStatistics();
      exitLow(1);
   }


   /**
    * Exit without errors
    */
   public static final void exit(String instance, String text)
   {
      info(instance, text);
      displayStatistics();
      exitLow(0);
   }

    /**
    * Display some statistic on exit
    */
   public static final void displayStatistics()
   {
      System.out.println(Memory.getStatistic());
      if (numErrorInvocations>0)
         System.out.println("There were " + numErrorInvocations + " ERRORS and " + numWarnInvocations + " WARNINGS");
      else if (numWarnInvocations>0)
         System.out.println("There were " + numWarnInvocations + " WARNINGS");
      else
         System.out.println("No errors/warnings were reported");
   }

   /**
    * Command line usage.
    * <p />
    * These variables may be set in your property file as well.
    * Don't use the "-" prefix there.
    */
   public static void usage()
   {
      Log.plain(ME, "");
      Log.plain(ME, "Logging options:");
      Log.plain(ME, "   -info  false        Supress info output.");
      Log.plain(ME, "   -trace true         Show code trace.");
      Log.plain(ME, "   -dump  true         Dump internal state.");
      Log.plain(ME, "   -call  true         Show important method entries");
      Log.plain(ME, "   -time true          Display some performance data.");
      Log.plain(ME, "   -logFile <fileName> Log to given file.");
      Log.plain(ME, "   -logConsole false   Supress logging to console.");
      Log.plain(ME, "   -Log.exception true Display more info about a thrown Exception.");
      Log.plain(ME, "");
   }

}

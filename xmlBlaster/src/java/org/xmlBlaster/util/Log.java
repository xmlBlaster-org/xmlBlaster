/*------------------------------------------------------------------------------
Name:      Log.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Logging output to console/file, using org.jutils
Version:   $Id: Log.java,v 1.53 2000/09/18 06:27:47 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


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

    /**
     * Counter for occurred warnings/errors
     */
   private static int numWarnInvocations = 0;
   private static int numErrorInvocations = 0;

   static {
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
      Log.info(ME, "XmlBlaster logging subsystem configured");
      Log.setLogChannel(lc);
      Log.info(ME, "XmlBlaster logging switched");
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
   public /*final*/ static boolean CALL = true;  // trace method calls
   /**
    * Performance logging output true/false
    */
   public /*final*/ static boolean TIME  = true;  // trace performance
   /**
    * Fine grained code logging output true/false
    */
   public /*final*/ static boolean TRACE = true;  // trace application flow
   /**
    * Dump internal state in xml format, true/false
    */
   public /*final*/ static boolean DUMP  = true;  // dump internal state
   /**
    * Plain output without formatting
    */
   public /*final*/ static boolean PLAIN  = true;  // dump internal state


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
      lc.addLogLevel(logLevel);
      init();
   }

   public static void removeLogLevel(String logLevel) {
      lc.removeLogLevel(logLevel);
      init();
   }

   public static int getLogLevel() {
      return lc.getLogLevel();
   }

   public static final String bitToLogLevel(int level) {
      return lc.bitToLogLevel(level);
   }

   public static final void setDefaultLogLevel() {
      lc.setDefaultLogLevel();
      init();
   }


   private static final void init()
   {
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
      lc.exception(source, t);
   }


   /**
    * delegate log error to LogChannel object.
    */
   public static final void error(String source, String text) {
      numErrorInvocations++;
      lc.error(source, text);
   }


   /**
    * delegate log warn to LogChannel object.
    */
   public static final void warn(String source, String text) {
      numWarnInvocations++;
      lc.warn(source, text);
   }


   /**
    * delegate log info to LogChannel object.
    */
   public static final void info(String source, String text) {
      lc.info(source, text);
   }


   /**
    * delegate log call to LogChannel object.
    */
   public static final void call(String source, String text) {
      lc.call(source, text);
   }


   /**
    * delegate log time to LogChannel object.
    */
   public static final void time(String source, String text) {
      lc.time(source, text);
   }


   /**
    * delegate log trace to LogChannel object.
    */
   public static final void trace(String source, String text) {
      lc.trace(source, text);
   }


   /**
    * delegate log plain to LogChannel object.
    */
   public static final void plain(String source, String text) {
      lc.plain(source, text);
   }


   /*
    * Log without time/date
    */
   public static final void dump(String instance, String text)
   {
      lc.dump(instance, text);
      lc.dump(instance, Memory.getStatistic());
   }

   /**
    * delegate log plain to LogChannel object.
    */
   public static final void plain(String text) {
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
      info(instance, text);
      System.err.println(text);
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
      Log.plain(ME, "   -trace true         Show code trace.");
      Log.plain(ME, "   -dump  true         Dump internal state.");
      Log.plain(ME, "   -calls true         Show important method entries");
      Log.plain(ME, "   -time true          Display some performance data.");
      Log.plain(ME, "   -logFile <fileName> Log to given file.");
      Log.plain(ME, "   -logConsole false   Supress logging to console.");
      Log.plain(ME, "");
   }

}

/*------------------------------------------------------------------------------
Name:      Log.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: Log.java,v 1.44 2000/05/02 13:12:25 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;
import java.io.*;
import java.text.MessageFormat;
import java.util.Locale;


/**
 * Logging output.
 * <p />
 * Note that the layout of this class is for optimum performance and ease of use.<br />
 * To increase performance of xmlBlaster you may choose to add final qualifiers<br />
 * to CALLS/TIME/TRACE/DUMP variables to achieve dead code elimination (see code below).
 */
public class Log
{
   private static final String ME = "Log";

   private static LogListener logListener = null;

   /**
    * Produce logging output on important method calls.
    * <p />
    * Switch CALLS mode to <code>false</code> for performance reasons<br />
    * or switch it to <code>true</code> for debugging reasons.
    * <p />
    * Setting to false:<br />
    * <code>if (Log.TRACE) Log.trace(....); </code> -> dead code elimination<br />
    * Note that you need to uncomment the final declaration, set all booleans to false
    * and comment out the code in method setPreLogLevelCheck(). Then recompile
    * to achieve real dead code elimination.
    * <p />
    * The same applies for CALLS, TIME, TRACE and DUMP
    */
   public /*final*/ static boolean CALLS = true;  // trace method calls
   /**
    * Performance logging output true/false
    */
   public /*final*/ static boolean TIME  = true;  // trace performance
   /**
    * Fine grained code logging output true/false
    */
   public /*final*/ static boolean TRACE = true;  // trace application flow
   /**
    * Dump internal xmlBlaster state in xml format, true/false
    */
   public /*final*/ static boolean DUMP  = true;  // dump internal state

   /**
    * Logging levels
    */
   private static final int L_NOLOG = 0x0;   // No logs at all
   private static final int L_PANIC = 0x1;   // Do exit on error
   private static final int L_ERROR = 0x2;   // Internal error occured
   private static final int L_WARN  = 0x4;   // Warning about user actions
   private static final int L_INFO  = 0x8;   // Important informational logs only
   private static final int L_CALLS = 0x10;  // Trace entry of methods
   private static final int L_TIME  = 0x20;  // Show elapsed milliseconds
   private static final int L_TRACE = 0x40;  // Trace application flow
   private static final int L_DUMP  = 0x80;  // Dump internal state
   private static final int L_EXIT  = 0x100; // Do a normal exit
   private static final int L_PLAIN = 0x200; // Marker for plain output

   /** Default log level is  L_PANIC | L_ERROR | L_WARN | L_INFO */
   private static int LOGLEVEL = (L_PANIC | L_ERROR | L_WARN | L_INFO);
   /**
    * Adjust here your local look and feel
    */
   private static int lookAndFeelDate = java.text.DateFormat.MEDIUM;
   private static int lookAndFeelTime = java.text.DateFormat.MEDIUM;
   private static Locale country = Locale.getDefault(); // java.util.Locale.US;

   /**
    * Counter for occurred warnings/errors
    */
   private static int numWarnInvocations = 0;
   private static int numErrorInvocations = 0;

   /**
    * colors foreground/background
    */
   private static final String ESC          = "\033[0m";     // Reset color to original values
   private static final String BOLD         = "\033[1m";

   private static final String RED_BLACK    = "\033[31;40m";
   private static final String GREEN_BLACK  = "\033[32;40m";
   private static final String YELLOW_BLACK = "\033[33;40m";
   private static final String BLUE_BLACK   = "\033[34;40m";
   private static final String PINK_BLACK   = "\033[35;40m";
   private static final String LTGREEN_BLACK= "\033[36;40m";
   private static final String WHITE_BLACK  = "\033[37;40m";

   private static final String WHITE_RED    = "\033[37;41m";
   private static final String BLACK_RED    = "\033[30;41m";
   private static final String BLACK_GREEN  = "\033[40;42m";
   private static final String BLACK_PINK   = "\033[40;45m";
   private static final String BLACK_LTGREEN= "\033[40;46m";


   /**
    * Output text for different logging levels
    */
   private static final String timeX  = new String("TIME ");
   private static final String callsX = new String("CALL ");
   private static final String traceX = new String("TRACE");
   private static final String plainX = new String("     ");
   private static final String infoX  = new String("INFO ");
   private static final String warnX  = new String("WARN ");
   private static final String errorX = new String("ERROR");
   private static final String panicX = new String("PANIC");
   private static final String exitX  = new String("EXIT ");

   /**
    * Colored output to xterm
    */
   private       static boolean withXtermEscapeColor = false;

   private static final String timeE  = new String(LTGREEN_BLACK+ "TIME " + ESC);
   private static final String callsE = new String(BLACK_LTGREEN+ "CALL " + ESC);
   private static final String traceE = new String(WHITE_BLACK  + "TRACE" + ESC);
   private static final String plainE = new String(WHITE_BLACK  + "     " + ESC);
   private static final String infoE  = new String(GREEN_BLACK  + "INFO " + ESC);
   private static final String warnE  = new String(YELLOW_BLACK + "WARN " + ESC);
   private static final String errorE = new String(RED_BLACK    + "ERROR" + ESC);
   private static final String panicE = new String(BLACK_RED    + "PANIC" + ESC);
   private static final String exitE  = new String(GREEN_BLACK  + "EXIT " + ESC);


   /** format: <timestamp>:<levelStr>:<instance>:<text> */
   // public final static String completeLog = "{0}:{1}:{2}:{3}";
   // public final static String simpleLog = "{0}:{1}:{3}";       //without instanceName
   // public final static String plainLog = "{3}";
   private static String currentLogFormat = "{0} {1} {2}: {3}";
   private static boolean logFormatPropertyRead = false;

   static
   {
      setDefaultLogLevel();
      setEscape();
   }


   /**
    * Check if we may use colored escape sequences to highlight errors etc.
    * <p />
    * Unix xterm support it usually<br />
    * DOS boxes not
    */
   private static final void setEscape()
   {
      String osName = System.getProperty("os.name");     // "Linux" "Windows NT" ...
      if (osName.startsWith("Window"))
         withXtermEscapeColor = false;
      else
         withXtermEscapeColor = true;
   }


   /**
    * Sets the default loglevel L_PANIC | L_ERROR | L_WARN | L_INFO
    */
   public static final void setDefaultLogLevel()
   {
      LOGLEVEL = (L_PANIC | L_ERROR | L_WARN | L_INFO);
      setPreLogLevelCheck();
   }


   /**
    * Sets the loglevel
    */
   public static final void setLogLevel(int level)
   {
      LOGLEVEL = level;
      setPreLogLevelCheck();
   }


   /**
    * Set logging level from start parameter and initialize properties.
    * <br />
    * Example:<br />
    * <pre>jaco org.xmlBlaster.Main +trace +dump +calls +dump</pre>
    *
    * HACK:<br />
    * This method also initializes the xmlBlaster.properties file, and adds
    * the args array to the property object (see Property.java).<br />
    * This is not the correct place, but we have the args here, and we don't need
    * to remember this task somewhere else.
    * <p />
    * CHANGE:
    *  "+" parameters are a bad idea, we should change to -calls true/false<br />
    * If you now want to change the setting in xmlBlaster.properties it looks ugly:<br />
    * +calls=true      # Switch Log.calls() on<br />
    * info=true        # Switch Log.info() off (on commandline: -info)
    *
    */
   public static final void setLogLevel(String[] args)
   {
      Property.loadProps(args);  // Initialize Property object here with args array

      if (Args.getArg(args, "-?") == true || Args.getArg(args, "-h") == true) {
         usage();
         return;
      }

      initialize();
   }


   /**
    * Force setting logging properties from xmlBlaster.properties
    * <p />
    * Called from Property after reading xmlBlaster.properties
    */
   static void initialize()
   {
      logFormatPropertyRead = true;

      // Given flag -info switches off Log.info messages:
      if (Property.propertyExists("info")) {
         if (Property.getProperty("info", false)) Log.addLogLevel("INFO");
         else Log.removeLogLevel("INFO");
      }
      if (Property.propertyExists("warn")) {
         if (Property.getProperty("warn", false)) Log.addLogLevel("WARN");
         else Log.removeLogLevel("WARN");
      }
      if (Property.propertyExists("error")) {
         if (Property.getProperty("error", false)) Log.addLogLevel("ERROR");
         else Log.removeLogLevel("ERROR");
      }
      if (Property.propertyExists("calls")) {
         if (Property.getProperty("calls", false)) Log.addLogLevel("CALLS");
         else Log.removeLogLevel("CALLS");
      }
      if (Property.propertyExists("time")) {
         if (Property.getProperty("time", false)) Log.addLogLevel("TIME");
         else Log.removeLogLevel("TIME");
      }
      if (Property.propertyExists("trace")) {
         if (Property.getProperty("trace", false)) Log.addLogLevel("TRACE");
         else Log.removeLogLevel("TRACE");
      }
      if (Property.propertyExists("dump")) {
         if (Property.getProperty("dump", false)) Log.addLogLevel("DUMP");
         else Log.removeLogLevel("DUMP");
      }

      // Note: "+" parameters are a bad idea, we should change to -calls true/false
      if (Property.getProperty("+calls", false)) Log.addLogLevel("CALLS");
      if (Property.getProperty("+time", false)) Log.addLogLevel("TIME");
      if (Property.getProperty("+trace", false)) Log.addLogLevel("TRACE");
      if (Property.getProperty("+dump", false)) Log.addLogLevel("DUMP");

      // format: {0}:{1}:{2}:{3}    <timestamp>:<levelStr>:<instance>:<text>
      currentLogFormat = Property.getProperty("LogFormat", currentLogFormat);

      String tmp = Property.getProperty("LogFormat.Date", "MEDIUM").trim();
      if (tmp.equals("SHORT")) lookAndFeelDate = java.text.DateFormat.SHORT;
      else if (tmp.equals("MEDIUM")) lookAndFeelDate = java.text.DateFormat.MEDIUM;
      else if (tmp.equals("LONG")) lookAndFeelDate = java.text.DateFormat.LONG;
      else if (tmp.equals("FULL")) lookAndFeelDate = java.text.DateFormat.FULL;

      tmp = Property.getProperty("LogFormat.Time", "MEDIUM").trim();
      if (tmp.equals("SHORT")) lookAndFeelTime = java.text.DateFormat.SHORT;
      else if (tmp.equals("MEDIUM")) lookAndFeelTime = java.text.DateFormat.MEDIUM;
      else if (tmp.equals("LONG")) lookAndFeelTime = java.text.DateFormat.LONG;
      else if (tmp.equals("FULL")) lookAndFeelTime = java.text.DateFormat.FULL;

      String la = Property.getProperty("LogFormat.Language", (String)null);
      String co = Property.getProperty("LogFormat.Country", (String)null);
      if (la != null && co != null) country = new Locale(la, co);

      // Log.plain(ME, Property.toXml());

      String fileName = Property.getProperty("logFile", (String)null);
      if (fileName != null)
         Log.logToFile(fileName);
   }


   /**
    * Adds the loglevel
    * @param for example "DUMP"
    */
   public static final void addLogLevel(String logLevel)
   {
      int level = logLevelToBit(logLevel);
      LOGLEVEL = (LOGLEVEL | level);
      // System.out.println("addLogLevel("+logLevel+"):" + bitToLogLevel(LOGLEVEL));
      setPreLogLevelCheck();
   }


   /**
    * Removes the loglevel
    * @param for example "TRACE"
    */
   public static final void removeLogLevel(String logLevel)
   {
      int level = logLevelToBit(logLevel);
      LOGLEVEL = (LOGLEVEL & ~level);
      // System.out.println("removeLogLevel("+logLevel+"):" + bitToLogLevel(LOGLEVEL));
      setPreLogLevelCheck();
   }


   /**
    * Set the boolean values of CALL, TIME, TRACE, DUMP accordingly.
    * <p />
    * This allows to use 'if (Log.TRACE)' in your code, so that the following
    * Log.trace(...) is not executed if not needed (performance gain).
    */
   private static final void setPreLogLevelCheck()
   {
      CALLS = TIME = TRACE = DUMP = false;
      if((LOGLEVEL & L_CALLS) != 0) CALLS = true;
      if((LOGLEVEL & L_TIME) != 0) TIME = true;
      if((LOGLEVEL & L_TRACE) != 0) TRACE = true;
      if((LOGLEVEL & L_DUMP) != 0) DUMP = true;
   }


   /**
    * Converts the String logLevel (e.g. "ERROR") to the bit setting L_ERROR.
    * @param for example "TRACE"
    * @return bit setting L_TRACE
    */
   private static final int logLevelToBit(String logLevel)
   {
      if (logLevel.equals("PANIC"))
         return L_PANIC;
      else if (logLevel.equals("ERROR"))
         return L_ERROR;
      else if (logLevel.equals("WARN"))
         return L_WARN;
      else if (logLevel.equals("INFO"))
         return L_INFO;
      else if (logLevel.equals("CALLS"))
         return L_CALLS;
      else if (logLevel.equals("TIME"))
         return L_TIME;
      else if (logLevel.equals("TRACE"))
         return L_TRACE;
      else if (logLevel.equals("DUMP"))
         return L_DUMP;
      return L_NOLOG;
   }


   /**
    * Converts for example the bit setting L_ERROR to String "ERROR".
    * @param bit setting for example L_TRACE
    * @return for example "TRACE"
    */
   public static final String bitToLogLevel(int level)
   {
      StringBuffer sb = new StringBuffer();
      if ((level & L_PANIC) > 0) sb.append("PANIC");
      if ((level & L_ERROR) > 0) sb.append(" | ERROR");
      if ((level & L_WARN) > 0) sb.append(" | WARN");
      if ((level & L_INFO) > 0) sb.append(" | INFO");
      if ((level & L_CALLS) > 0) sb.append(" | CALLS");
      if ((level & L_TIME) > 0) sb.append(" | TIME");
      if ((level & L_TRACE) > 0) sb.append(" | TRACE");
      if ((level & L_DUMP) > 0) sb.append(" | DUMP");
      return sb.toString();
   }


   /**
    * Gets the loglevel 0,10,20,30,40,50,60
    */
   public static final int getLogLevel()
   {
      return LOGLEVEL;
   }


   /**
    * You may add a Listener here (only one is accepted).
    * <p />
    * All output is redirected to this listener.<br />
    * Adding another listener overwrites the old one.<br />
    * @parameter The new listener which wants to have the logging output<br />
    *            null: switch of the listener, output to console
    */
   public static final void addLogListener(LogListener listener)
   {
      logListener = listener;
      if (listener == null)
         setEscape();  // show nice colors for different log levels (UNIX-xterm only)
      else
         withXtermEscapeColor = false;
   }


   /**
    * Log to file instead of to console.
    * <p />
    * The inner class LogFile is used to log to file,
    * and may be used as a demo implementation of the LogListener interface
    * Use the variable "Log.maxFileLines" in xmlBlaster.properties to adjust the file size.
    * @parameter The log file name (inclusive path)
    */
   public static final void logToFile(String fileName)
   {
      // System.out.println(ME + ": Logging to file " + fileName + " ...");
      Log.addLogListener(new LogFile(fileName));
   }


   public static final void setLogFormat(String format)
   {
      currentLogFormat = format;
   }


   /**
    * Log example:
    * <p />
    * Feb 11, 2000 3:44:48 PM INFO :  [Main]  xmlBlaster is ready for requests
    *
    * @param levelStr e.g. "WARNING" or "INFO" or null
    * @param level    for performance reasons, an int for the levelStr
    * @param instance e.g. "RequestBroker" or "Authentication"
    * @param text     e.g. "Login denied"
    */
   public static final void log(final String levelStr, final int level, final String instance, final String text)
   {
      if (logFormatPropertyRead == false) {
         initialize();
      }

      String logFormat;
      if((level & L_DUMP) != 0)
         logFormat = "{3}";
      else
         logFormat = currentLogFormat;

      java.util.Date currentDate = new java.util.Date();
      java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(lookAndFeelDate, lookAndFeelTime, country);
      String timeStr = df.format(currentDate);

      Object[] arguments = {
         timeStr,
         levelStr,
         instance,
         text
      };

      String logEntry = MessageFormat.format( logFormat, arguments );

      if (logListener != null) {
         logListener.log(logEntry);
      }
      else {
         if ((level & L_ERROR) != 0 || (level & L_WARN) != 0 || (level & L_PANIC) != 0)
            System.err.println(logEntry);
         else
            System.out.println(logEntry);
      }
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
      if((LOGLEVEL & L_PANIC) != 0)
      {
         log((withXtermEscapeColor) ? panicE : panicX, L_PANIC, instance, text);
         System.err.println(text);
         numErrorInvocations++;
         // displayStatistics();
         exitLow(1);
      }
   }


   /**
    * Exit without errors
    */
   public static final void exit(String instance, String text)
   {
      log((withXtermEscapeColor) ? exitE : exitX, L_EXIT, instance, text);
      displayStatistics();
      exitLow(0);
   }


   /**
    * Use this for normal logging output
    */
   public static final void info(String instance, String text)
   {
      if((LOGLEVEL & L_INFO) != 0)
      {
         log((withXtermEscapeColor) ? infoE : infoX, L_INFO, instance, text);
      }
   }


   /**
    * Use this for logging output where the xmlBlaster administrator shall be informed<br />
    * for example a login denied event
    */
   public static final void warning(String instance, String text)
   {
      if((LOGLEVEL & L_WARN) != 0)
      {
         numWarnInvocations++;
         log((withXtermEscapeColor) ? warnE : warnX, L_WARN, instance, text);
      }
   }
   /*
   public static final void warningThrow(String instance, String text) throws org.xmlBlaster.XmlBlasterException
   {
      warning(instance, text);
      throw new org.xmlBlaster.XmlBlasterException(instance, text);
   }
   */

   /**
    * Use this for internal xmlBlaster errors reporting
    */
   public static final void error(String instance, String text)
   {
      if((LOGLEVEL & L_PANIC) != 0)
      {
         numErrorInvocations++;
         log((withXtermEscapeColor) ? errorE : errorX, L_ERROR, instance, text);
      }
   }
   /*
   public static final void errorThrow(String instance, String text) throws org.xmlBlaster.XmlBlasterException
   {
      error(instance, text);
      throw new org.xmlBlaster.XmlBlasterException(instance, text);
   }
   */

   /*
    * Log without time/date/instance
    * @param instance (not currently used)
    * @param text the string to log
    */
   public static final void plain(String instance, String text)
   {
      log(null, L_PLAIN, null, text);
   }

   /*
    * Log without time/date
    */
   public static final void dump(String instance, String text)
   {
      if((LOGLEVEL & L_DUMP) != 0) {
         log("", L_DUMP, instance, text);
         log("", L_DUMP, instance, Memory.getStatistic());
      }
   }

   /**
    * Tracing execution
    */
   public static final void trace(String instance, String text)
   {
      if((LOGLEVEL & L_TRACE) != 0)
         log((withXtermEscapeColor) ? traceE : traceX, L_TRACE, instance, text);
   }

   /**
    * Tracing when entering methods
    */
   public static final void calls(String instance, String text)
   {
      if((LOGLEVEL & L_CALLS) != 0)
         log((withXtermEscapeColor) ? callsE : callsX, L_CALLS, instance, text);
   }

   /**
    * Output of performant measurements (elapsed milliseconds)
    */
   public static final void time(String instance, String text)
   {
      if((LOGLEVEL & L_TIME) != 0)
         log((withXtermEscapeColor) ? timeE : timeX, L_TIME, instance, text);
   }


   /**
    * Display some statistic on exit
    */
   public static final void displayStatistics()
   {
      Log.info(ME, Memory.getStatistic());
      if (withXtermEscapeColor) {
         if (numErrorInvocations>0)
            Log.info(ME, BLACK_RED + "There were " + numErrorInvocations + " ERRORS and " + numWarnInvocations + " WARNINGS" + ESC);
         else if (numWarnInvocations>0)
            Log.info(ME, BLACK_PINK + "There were " + numErrorInvocations + " ERRORS and " + numWarnInvocations + " WARNINGS" + ESC);
         else
            Log.info(ME, BLACK_GREEN + "No errors/warnings were reported" + ESC);
      }
      else {
         if (numErrorInvocations>0 || numWarnInvocations>0)
            Log.info(ME, "There were " + numErrorInvocations + " ERRORS and " + numWarnInvocations + " WARNINGS");
         else
            Log.info(ME, "No errors/warnings were reported");
      }
   }


   /**
    * Display the current stack<br />
    * Very slow and not filtered for usefulness
    */
   public static final void printStack()
   {
      try {
         throw new Exception("");
      }
      catch (Exception e) {
         Log.info(ME, "Current stack trace:");
         e.printStackTrace();
      }
      /* Another way to do it:
         Throwable throwable = new Throwable();
         StringWriter stringWriter = new StringWriter();
         StringBuffer writerBuffer = stringWriter.getBuffer();
         PrintWriter out = new PrintWriter(stringWriter,false);
         throwable.fillInStackTrace();
         throwable.printStackTrace(out);
         out.flush();
         System.out.println(writerBuffer.toString());
      */
   }


   /**
    * Command line usage.
    * <p />
    * These variables may be set in xmlBlaster.properties as well.
    * Don't use the "-" or "+" prefix there.
    */
   public static void usage()
   {
      Log.plain(ME, "");
      Log.plain(ME, "Logging options:");
      Log.plain(ME, "   +trace              Show code trace.");
      Log.plain(ME, "   +dump               Dump internal state.");
      Log.plain(ME, "   +calls              Show important method entries");
      Log.plain(ME, "   +time               Display some performance data.");
      Log.plain(ME, "   -logFile <fileName> Log to given file instead to console.");
      Log.plain(ME, "");
   }


   /**
    * Only for testing
    *    java org.xmlBlaster.util.Log
    */
   public static void main(String args[]) throws Exception
   {
      Log.printStack();

      String me = "Log-Tester";
      System.out.println("LOGLEVEL : " + getLogLevel() + ": " + bitToLogLevel(getLogLevel()));
      setLogLevel(L_ERROR | L_WARN | L_INFO);
      System.out.println("LOGLEVEL : " + getLogLevel() + ": " + bitToLogLevel(getLogLevel()));
      Log.panic(me, "Panic is not shown");
      Log.error(me, "Error ...");
      setLogLevel(0xFFF);
      System.out.println("LOGLEVEL : " + getLogLevel() + ": " + bitToLogLevel(getLogLevel()));
      Log.calls(me, "Entering method main()");
      Log.time(me, "Elapsed time = 34 milli seconds");
      Log.trace(me, "TRACE: Entering method xy");
      Log.info(me, "Successfully loaded data");
      Log.warning(me, "your input is strange");
      Log.error(me, "given User unknown");
      Log.error(me, "another error");
      Log.panic(me, "Unrecoverable Error - good bye");
   }

} // end of class Log




/**
   * Write log messages to file.
   * <br />
   * Class which uses the LogListener interface to log to a file.<br />
   * You can specify to use this file logger instead of the default
   * logging to console on the command line, e.g.:<br />
   * <pre>
   *    jaco org.xmlBlaster.Main -logFile /tmp/xmlBlaster.log
   * </pre>
   * This is also a nice example, how to implement an own logging output channel
   */
class LogFile implements LogListener
{
   private String ME = "LogFile";
   private String fileName = "xmlBlaster.log";
   private int maxLogFileLines = 50000;   // lines per file
   private int numLogLines = 0;
   private java.io.File logFile = null;
   private byte[] newLine = null;

   public LogFile(String fileName)
   {
      if (fileName != null && fileName.length() > 1) this.fileName = fileName;
      String tmp = new String("\n");
      newLine = tmp.getBytes();
      maxLogFileLines = Integer.parseInt(Property.getProperty("Log.maxFileLines", "" + maxLogFileLines));   // in lines per file
      Log.info("LogFile", "Logging output is sent to '" + fileName + "' with maxFileLines=" + maxLogFileLines);
      newFile();
   }

   /**
    * Event fired by Log.java through interface LogListener.
    * <p />
    * Log output into a file<br />
    * If the number of lines is too big, save file with extension .bak
    * and start a new one
    */
   public void log(String str)
   {
      if (numLogLines > maxLogFileLines) newFile();
      numLogLines++;
      try {
         boolean append = true;
         FileOutputStream to = new FileOutputStream(fileName, true);
         to.write(str.getBytes());
         to.write(newLine);
         to.close();
      }
      catch (Exception e) {
         Log.addLogListener(null);
         Log.error(ME, "Can't write to file " + fileName + ": " + e.toString());
         Log.info(ME, "Resetting logging output to stdout");
         System.out.println(str);
      }
   }

   /**
    * Create the new logfile, save an existing with ".bak" extension.
    */
   private void newFile()
   {
      String bakFile = fileName + "bak";
      java.io.File nf;
      nf = new java.io.File(bakFile);
      if (nf.exists())
         nf.delete();
      if (logFile != null)
         logFile.renameTo(nf);
      logFile = new java.io.File(fileName);
      if (logFile.exists()) logFile.delete();
   }
} // end of class LogFile


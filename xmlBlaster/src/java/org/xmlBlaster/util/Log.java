/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: Log.java,v 1.33 2000/01/19 21:03:48 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


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

   /** Default log level is  L_PANIC | L_ERROR | L_WARN | L_INFO */
   private static int LOGLEVEL = (L_PANIC | L_ERROR | L_WARN | L_INFO);
   /**
    * Adjust here your local look and feel
    */
   private static final int lookAndFeelDate = java.text.DateFormat.MEDIUM;
   private static final int lookAndFeelTime = java.text.DateFormat.MEDIUM;
   private static final java.util.Locale country = java.util.Locale.US;

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
   private static final StringBuffer INSTANCE_SEPERATOR = new StringBuffer(":  ");
   private static final StringBuffer timeX  = new StringBuffer("TIME  ");
   private static final StringBuffer callsX = new StringBuffer("CALL  ");
   private static final StringBuffer traceX = new StringBuffer("TRACE ");
   private static final StringBuffer plainX = new StringBuffer("      ");
   private static final StringBuffer infoX  = new StringBuffer("INFO  ");
   private static final StringBuffer warnX  = new StringBuffer("WARN  ");
   private static final StringBuffer errorX = new StringBuffer("ERROR ");
   private static final StringBuffer panicX = new StringBuffer("PANIC ");
   private static final StringBuffer exitX  = new StringBuffer("EXIT  ");

   /**
    * Colored output to xterm
    */
   private       static boolean withXtermEscapeColor = false;
   private static final StringBuffer INSTANCE_SEPERATOR_E = new StringBuffer(BOLD + ":" + ESC + "  "); // bold

   private static final StringBuffer timeE  = new StringBuffer(LTGREEN_BLACK+ "TIME " + ESC + ": ");
   private static final StringBuffer callsE = new StringBuffer(BLACK_LTGREEN+ "CALL " + ESC + ": ");
   private static final StringBuffer traceE = new StringBuffer(WHITE_BLACK  + "TRACE" + ESC + ": ");
   private static final StringBuffer plainE = new StringBuffer(WHITE_BLACK  + "     " + ESC + ": ");
   private static final StringBuffer infoE  = new StringBuffer(GREEN_BLACK  + "INFO " + ESC + ": ");
   private static final StringBuffer warnE  = new StringBuffer(YELLOW_BLACK + "WARN " + ESC + ": ");
   private static final StringBuffer errorE = new StringBuffer(RED_BLACK    + "ERROR" + ESC + ": ");
   private static final StringBuffer panicE = new StringBuffer(BLACK_RED    + "PANIC" + ESC + ": ");
   private static final StringBuffer exitE  = new StringBuffer(GREEN_BLACK  + "EXIT " + ESC + ": ");


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
    * Set logging level from start parameter.
    * <br />
    * Example:<br />
    * <pre>jaco org.xmlBlaster.Main +trace +dump +calls +dump</pre>
    */
   public static final void setLogLevel(String[] args)
   {
      if (Args.getArg(args, "+calls")) Log.addLogLevel("CALLS");
      if (Args.getArg(args, "+time"))  Log.addLogLevel("TIME");
      if (Args.getArg(args, "+trace")) Log.addLogLevel("TRACE");
      if (Args.getArg(args, "+dump"))  Log.addLogLevel("DUMP");
   }


   /**
    * Adds the loglevel
    * @param for example "DUMP"
    */
   public static final void addLogLevel(String logLevel)
   {
      int level = logLevelToBit(logLevel);
      LOGLEVEL = (LOGLEVEL | level);
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
      setPreLogLevelCheck();
   }


   /**
    * Set the boolean values of CALL, TIME, TRACE, DUMP accordingly.
    * <p />
    * This allows to use if (Log.TRACE) in your code, so that the following
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
      else if (logLevel.equals("WARNING"))
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
      if ((level & L_WARN) > 0) sb.append(" | WARNING");
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
         setEscape();
      else
         withXtermEscapeColor = false;
   }


   /**
    * Create the Date/Time informations.
    *
    * @return example: 1999-11-01 10:41:03 INFO
    */
   public static final StringBuffer logHeader(StringBuffer levelStr)
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
    * <p />
    *   1999-11-01 10:41:03 INFO : xmlBlaster is ready to blast
    */
   public static final void log(final StringBuffer levelStr, String instance, String text)
   {
      StringBuffer strBuf;
      if (instance == null)
         strBuf = logHeader(levelStr).append(" ").append(text);
      else
         strBuf = logHeader(levelStr).append(" [").append(instance).append("]  ").append(text);

      if (logListener != null) {
         logListener.log(strBuf.toString());
      }
      else {
         System.err.println(strBuf);
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
         log((withXtermEscapeColor) ? panicE : panicX, instance, text);
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
      log((withXtermEscapeColor) ? exitE : exitX, instance, text);
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
         log((withXtermEscapeColor) ? infoE : infoX, instance, text);
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
         log((withXtermEscapeColor) ? warnE : warnX, instance, text);
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
         log((withXtermEscapeColor) ? errorE : errorX, instance, text);
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
      log(null, null, text);
   }

   /*
    * Log without time/date
    */
   public static final void dump(String instance, String text)
   {
      if((LOGLEVEL & L_DUMP) != 0) {
         log(null, instance, text);
         log(null, instance, Memory.getStatistic());
      }
   }

   /**
    * Tracing execution
    */
   public static final void trace(String instance, String text)
   {
      if((LOGLEVEL & L_TRACE) != 0)
         log((withXtermEscapeColor) ? traceE : traceX, instance, text);
   }

   /**
    * Tracing when entering methods
    */
   public static final void calls(String instance, String text)
   {
      if((LOGLEVEL & L_CALLS) != 0)
         log((withXtermEscapeColor) ? callsE : callsX, instance, text);
   }

   /**
    * Output of performant measurements (elapsed milliseconds)
    */
   public static final void time(String instance, String text)
   {
      if((LOGLEVEL & L_TIME) != 0)
         log((withXtermEscapeColor) ? timeE : timeX, instance, text);
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
    * Command line usage.
    */
   public static void usage()
   {
      Log.plain(ME, "");
      Log.plain(ME, "Logging options:");
      Log.plain(ME, "   +trace              Show code trace.");
      Log.plain(ME, "   +dump               Dump internal state.");
      Log.plain(ME, "   +calls              Show important method entries");
      Log.plain(ME, "   +time               Display some performance data.");
      Log.plain(ME, "");
   }


   /**
    * Only for testing
    *    java org.xmlBlaster.util.Log
    */
   public static void main(String args[]) throws Exception
   {
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

}



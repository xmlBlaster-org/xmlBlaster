/*----------------------------------------------------------------------------
Name:      Log.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: Log.h,v 1.4 2001/11/26 09:20:59 ruff Exp $
-----------------------------------------------------------------------------*/

#define _TERM_WITH_COLORS_

#ifndef _UTIL_LOG_H
#define _UTIL_LOG_H

#include <stdlib.h>
#include <util/Property.h>
#include <strstream.h>
#include <time.h>

/**
 * Logging output.
 * <p />
 * Note that the layout of this class is for optimum performance and ease of 
 * use.<br />
 * to CALL/TIME/TRACE/DUMP variables to achieve dead code elimination (see 
 * code below).
 */


namespace org { namespace xmlBlaster {
namespace util {
   
   
   class Log {
      
   private:
      
      /**
       * Produce logging output on important method calls.
       * <p />
       * Switch CALL mode to <code>false</code> for performance reasons<br />
       * or switch it to <code>true</code> for debugging reasons.
       * <p />
       * Setting to false:<br />
       * <code>if (Log.TRACE) Log.trace(....); </code> -> dead code 
       * elimination<br />
       * Note that you need to uncomment the final declaration, set all booleans 
       * to false and comment out the code in method setPreLogLevelCheck(). 
       * Then recompile to achieve real dead code elimination.
       * <p />
       * The same applies for CALL, TIME, TRACE and DUMP
       */
      
      /**
       * Logging levels
       */
      static const int L_NOLOG  = 0x0;   // No logs at all
      static const int L_PANIC  = 0x1;   // Do exit on error
      static const int L_ERROR  = 0x2;   // Internal error occured
      static const int L_WARN   = 0x4;   // Warning about user actions
      static const int L_INFO   = 0x8;   // Important informational logs only
      static const int L_CALL  = 0x10;  // Trace entry of methods
      static const int L_TIME   = 0x20;  // Show elapsed milliseconds
      static const int L_TRACE  = 0x40;  // Trace application flow
      static const int L_DUMP   = 0x80;  // Dump internal state
      static const int L_EXIT   = 0x100; // Do a normal exit
      static const int L_PLAIN  = 0x200; // Marker for plain output
//      static int lookAndFeelTime = LC_TIME;
      /**
       * colors foreground/background
       */
      static char* const ESC          = "\033[0m"; 
      static char* const BOLD         = "\033[1m";
      static char* const RED_BLACK    = "\033[31;40m";
      static char* const GREEN_BLACK  = "\033[32;40m";
      static char* const YELLOW_BLACK = "\033[33;40m";
      static char* const BLUE_BLACK   = "\033[34;40m";
      static char* const PINK_BLACK   = "\033[35;40m";
      static char* const LTGREEN_BLACK= "\033[36;40m";
      static char* const WHITE_BLACK  = "\033[37;40m";
      static char* const WHITE_RED    = "\033[37;41m";
      static char* const BLACK_RED    = "\033[30;41m";
      static char* const BLACK_GREEN  = "\033[40;42m";
      static char* const BLACK_PINK   = "\033[40;45m";
      static char* const BLACK_LTGREEN= "\033[40;46m";

      static Property *properties_;
      static int      logLevel_;

      /**
       * Counter for occurred warnings/errors
       */
      static int    numWarnInvocations;
      static int    numErrorInvocations;
      static int    numOfImplementations_;
      static string currentLogFormat;
      static bool   logFormatPropertyRead;

      string   ME;


      /**
       * Output text for different logging levels
       */
      string timeX, callX, traceX, plainX, infoX;
      string warnX, errorX, panicX, exitX;

#ifdef _TERM_WITH_COLORS_
      string timeE, callE, traceE, plainE, infoE;
      string warnE, errorE, panicE, exitE;
#endif // _TERM_WITH_COLORS_

   private:
      /**
       * Converts the String logLevel (e.g."ERROR") to the bit setting L_ERROR.
       * @param for example "TRACE"
       * @return bit setting L_TRACE
       */
      int logLevelToBit(const string &logLevel) {
	 if (logLevel == "PANIC")      return L_PANIC;
	 else if (logLevel == "ERROR") return L_ERROR;
	 else if (logLevel == "WARN" ) return L_WARN;
	 else if (logLevel == "INFO" ) return L_INFO;
	 else if (logLevel == "CALL") return L_CALL;
	 else if (logLevel == "TIME" ) return L_TIME;
	 else if (logLevel == "TRACE") return L_TRACE;
	 else if (logLevel == "DUMP" ) return L_DUMP;
	 return L_NOLOG;
      }
      
      
      /**
       * The only way to stop the server
       * @param val exit code for operating system
       */
      void exitLow(int val) {
	 std::exit(val);
      }
      
            
     public:
      static bool CALL, TIME, TRACE, DUMP;
      
      
      Log(int args=0, char *argc[]=0);


      ~Log();

      /**
       * Sets the default loglevel L_PANIC | L_ERROR | L_WARN | L_INFO
       */
      void setDefaultLogLevel();


      /**
       * Sets the loglevel
       */
      void setLogLevel(int level);

      
      /**
       * Set logging level from start parameter and initialize properties.
       * <br />
       * Example:<br />
       * <pre>jaco org.xmlBlaster.Main -trace true -dump true -call true -dump true</pre>
       *
       */
      void setLogLevel(int argc, char *args[]);


      /**
       * Removes the loglevel
       * @param for example "TRACE"
       */
      void removeLogLevel(string logLevel);


      /**
       * Adds the loglevel
       * @param for example "DUMP"
       */
      void addLogLevel(string logLevel);
            

      /**
       * Set the boolean values of CALL, TIME, TRACE, DUMP accordingly.
       * <p />
       * This allows to use 'if (Log.TRACE)' in your code, so that the 
       * following Log.trace(...) is not executed if not needed (performance 
       * gain).
       */
      void setPreLogLevelCheck();

      
      /**
       * Converts for example the bit setting L_ERROR to String "ERROR".
       * @param bit setting for example L_TRACE
       * @return for example "TRACE"
       */
      string bitToLogLevel(int level);      

      /**
       * Gets the loglevel 0,10,20,30,40,50,60
       */
      int getLogLevel() {
	 return logLevel_;
      }
      

      void setLogFormat(const string &format) {
	 currentLogFormat = format;
      }


      /**
       * Use this exit for errors
       */
      void panic(const string &instance, const string &text);


      /**
       * Exit without errors
       */
      void exit(const string &instance, const string &text);


      /**
       * Use this for normal logging output
       */
      void info(const string &instance, const string &text);
      

      /**
       * Use this for logging output where the xmlBlaster administrator shall 
       * be informed<br /> for example a login denied event
       */
      void warn(const string &instance, const string &text);
      
      
      /**
       * Use this for internal xmlBlaster errors reporting
       */
      void error(const string &instance, const string &text);


      /*
       * Log without time/date/instance
       * @param instance (not currently used)
       * @param text the string to log
       */
      void plain(const string &instance, const string &text);


      /*
       * Log without time/date
       */
      void dump(const string &instance, const string &text);


      /**
       * Tracing execution
       */
      void trace(const string &instance, const string &text);


      /**
       * Tracing when entering methods
       */
      void call(const string &instance, const string &text);

      
      /**
       * Output of performant measurements (elapsed milliseconds)
       */
      void time(const string &instance, const string &text);


      /**
       * Command line usage.
       * <p />
       * These variables may be set in xmlBlaster.properties as well.
       * Don't use the "-" or "+" prefix there.
       */
      void usage();

      /**
       * Display some statistic on exit
       */
      void displayStatistics();


      /**
       * Display the current stack<br />
       * Very slow and not filtered for usefulness
       */
      void printStack();
  

      /**
       * Force setting logging properties from xmlBlaster.properties
       * <p />
       * Called from Property after reading xmlBlaster.properties
       */
      void initialize();       


      string getTime();

 
      /**
       * Log example:
       * <p />
       * Feb 11, 2000 3:44:48 PM INFO :  [Main]  xmlBlaster is ready for 
       * requests.
       *
       * @param levelStr e.g. "WARNING" or "INFO" or null
       * @param level    for performance reasons, an int for the levelStr
       * @param instance e.g. "RequestBroker" or "Authentication"
       * @param text     e.g. "Login denied"
       */
      void log(const string &levelStr, int level, const string &instance, 
	       const string &text) {
	 if (logFormatPropertyRead == false) {
	    initialize();
	 }

	 string logFormat;
	 if(level & L_DUMP)
	    logFormat = "{3}";
	 else
	    logFormat = currentLogFormat;
	 
	 string logEntry = levelStr + " ";
	 if (level & L_TIME) logEntry += getTime() + ": ";
	 if ((level & L_ERROR) || (level & L_WARN) || (level & L_PANIC))
	    cerr << logEntry << instance << " " << text << endl;
	 else
	    cout << logEntry << instance << " " << text << endl;
      }


      Property& getProperties() {
	 return *properties_;
      }

   }; // end of class Log

}}} // end of namespace util

#endif // _UTIL_LOG_H

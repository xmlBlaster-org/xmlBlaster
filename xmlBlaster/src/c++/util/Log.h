/*----------------------------------------------------------------------------
Name:      Log.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
-----------------------------------------------------------------------------*/

#ifndef _UTIL_LOG_H
#define _UTIL_LOG_H

#include <util/xmlBlasterDef.h>
#include <util/PropertyDef.h>
#include <util/Property.h>
#include <util/I_Log.h>

/**
 * Logging output.
 * <p />
 * Note that the layout of this class is for optimum performance and ease of 
 * use.<br />
 * to CALL/TIME/TRACE/DUMP variables to achieve dead code elimination (see 
 * code below).
 */
namespace org { namespace xmlBlaster { namespace util {
   
   class Dll_Export Log : public I_Log {
      
   private:

      /**
       * private copy constructor
       */
      Log(const Log& entry);

      /**
       * private assignment constructor
       */
      Log& operator =(const Log& entry);

      bool withXtermColor_;
      
      /**
       * Produce logging output on important method calls.
       * <p />
       * Switch CALL mode to <code>false</code> for performance reasons<br />
       * or switch it to <code>true</code> for debugging reasons.
       * <p />
       * Setting to false:<br />
       * <code>if (Log.trace()) Log.trace(....); </code> -> dead code 
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
      enum {L_NOLOG= 0x0,   // No logs at all
            L_PANIC= 0x1,   // Do exit on error
            L_ERROR= 0x2,   // Internal error occured
            L_WARN = 0x4,   // Warning about user actions
            L_INFO = 0x8,   // Important informational logs only
            L_CALL = 0x10,    // Trace entry of methods
            L_TIME = 0x20,    // Show elapsed milliseconds
            L_TRACE= 0x40,   // Trace application flow
            L_DUMP = 0x80,    // Dump internal state
            L_EXIT = 0x100,    // Do a normal exit
            L_PLAIN= 0x200  // Marker for plain output
            };
//      static int lookAndFeelTime = LC_TIME;
      /**
       * colors foreground/background
       */
      static const char* const ESC          ;
      static const char* const BOLD         ;
      static const char* const RED_BLACK    ;
      static const char* const GREEN_BLACK  ;
      static const char* const YELLOW_BLACK ;
      static const char* const BLUE_BLACK   ;
      static const char* const PINK_BLACK   ;
      static const char* const LTGREEN_BLACK;
      static const char* const WHITE_BLACK  ;
      static const char* const WHITE_RED    ;
      static const char* const BLACK_RED    ;
      static const char* const BLACK_GREEN  ;
      static const char* const BLACK_PINK   ;
      static const char* const BLACK_LTGREEN;

      Property&     properties_;
      int           logLevel_;

      /**
       * Counter for occurred warnings/errors
       */
      int    numWarnInvocations;
      int    numErrorInvocations;
      std::string currentLogFormat;
      bool   logFormatPropertyRead;

      std::string   ME;
      std::string   name_;

      std::string timeE, callE, traceE, plainE, infoE;
      std::string warnE, errorE, panicE, exitE;

      /**
       * Output text for different logging levels
       * The DOS box does not know any colors
       */
      std::string timeX, callX, traceX, plainX, infoX;
      std::string warnX, errorX, panicX, exitX;

   private:
      /**
       * Converts the String logLevel (e.g."ERROR") to the bit setting L_ERROR.
       * @param for example "TRACE"
       * @return bit setting L_TRACE
       */
      int logLevelToBit(const std::string &logLevel) {
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

      void initSpecificTrace(const std::string& trace, const std::string& traceId);
      
      
      /**
       * The only way to stop the server
       * @param val exit code for operating system
       */
      void exitLow(int val);
      
     public:
      /** Return the log channel */
      inline std::string getName() { return name_; }
      
      Log(Property& properties, int args=0, const char * const argc[]=0, const std::string& name="default");


      ~Log();

      /**
       * Switch the colored logging output on or off. 
       * Defaults on UNIX to 'on' and on Windows to 'off'
       */
      void setWithXtermColor(bool val=true);

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
      void setLogLevel(int argc, const char * const args[]);


      /**
       * Removes the loglevel
       * @param for example "TRACE"
       */
      void removeLogLevel(std::string logLevel);


      /**
       * Adds the loglevel
       * @param for example "DUMP"
       */
      void addLogLevel(std::string logLevel);
            

      /**
       * Set the boolean values of CALL, TIME, TRACE, DUMP accordingly.
       * <p />
       * This allows to use 'if (Log.trace())' in your code, so that the 
       * following Log.trace(...) is not executed if not needed (performance 
       * gain).
       */
      void setPreLogLevelCheck();

      
      /**
       * Converts for example the bit setting L_ERROR to String "ERROR|WARN".
       * @param bit setting for example L_TRACE
       * @return for example "ERROR|WARN|TRACE"
       */
      std::string bitToLogLevel(int level) const;      

      /**
       * My current log level setting in human readable notation. 
       * @return for example "ERROR|WARN|TRACE"
       */
      std::string getLogLevelStr() const { return bitToLogLevel(logLevel_); }

      /**
       * Gets the loglevel 0,10,20,30,40,50,60
       */
      int getLogLevel() const {
         return logLevel_;
      }
      

      void setLogFormat(const std::string &format) {
         currentLogFormat = format;
      }


      /**
       * Use this exit for errors
       */
      void panic(const std::string &instance, const std::string &text);


      /**
       * Exit without errors
       */
      void exit(const std::string &instance, const std::string &text);


      /**
       * Use this for normal logging output
       */
      void info(const std::string &instance, const std::string &text);
      

      /**
       * Use this for logging output where the xmlBlaster administrator shall 
       * be informed<br /> for example a login denied event
       */
      void warn(const std::string &instance, const std::string &text);
      
      
      /**
       * Use this for internal xmlBlaster errors reporting
       */
      void error(const std::string &instance, const std::string &text);


      /*
       * Log without time/date/instance
       * @param instance (not currently used)
       * @param text the std::string to log
       */
      void plain(const std::string &instance, const std::string &text);


      /*
       * Log without time/date
       */
      void dump(const std::string &instance, const std::string &text);


      /**
       * Tracing execution
       */
      void trace(const std::string &instance, const std::string &text);


      /**
       * Tracing when entering methods
       */
      void call(const std::string &instance, const std::string &text);

      
      /**
       * Output of performant measurements (elapsed milliseconds)
       */
      void time(const std::string &instance, const std::string &text);


      /**
       * Command line usage.
       * <p />
       * These variables may be set in xmlBlaster.properties as well.
       * Don't use the "-" or "+" prefix there.
       */
      virtual std::string usage() const;

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


      std::string getTime();

 
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
      void log(const std::string &levelStr, int level, const std::string &instance, 
               const std::string &text);


      Property& getProperties() {
         return properties_;
      }

   }; // end of class Log



}}} // end of namespace util

#endif // _UTIL_LOG_H

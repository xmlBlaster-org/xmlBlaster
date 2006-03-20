/*----------------------------------------------------------------------------
Name:      Log.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
----------------------------------------------------------------------------*/
#include <util/Log.h>
#include <iostream>
#include <ctime>   //<time.h>
#include <cstdlib> //<stdlib.h>
#include <util/lexical_cast.h>
#include <util/PropertyDef.h>

using namespace std;

namespace org { namespace xmlBlaster {
namespace util {

const char* const Log::ESC          = "\033[0m";
const char* const Log::BOLD         = "\033[1m";
const char* const Log::RED_BLACK    = "\033[31;40m";
const char* const Log::GREEN_BLACK  = "\033[32;40m";
const char* const Log::YELLOW_BLACK = "\033[33;40m";
const char* const Log::BLUE_BLACK   = "\033[34;40m";
const char* const Log::PINK_BLACK   = "\033[35;40m";
const char* const Log::LTGREEN_BLACK= "\033[36;40m";
const char* const Log::WHITE_BLACK  = "\033[37;40m";
const char* const Log::WHITE_RED    = "\033[37;41m";
const char* const Log::BLACK_RED    = "\033[30;41m";
const char* const Log::BLACK_GREEN  = "\033[40;42m";
const char* const Log::BLACK_PINK   = "\033[40;45m";
const char* const Log::BLACK_LTGREEN= "\033[40;46m";



   /**
    * Initializes logging and Properties
    */
   Log::Log(Property& properties, int /*args*/, const char * const /*argc*/[], const string& name) 
      : withXtermColor_(true), properties_(properties), name_(name)
   {
      ME     = "Log";
#     ifdef _WIN32
         withXtermColor_ = false;
#     endif
      withXtermColor_ = properties.getBoolProperty("xmlBlaster.withXtermColor", withXtermColor_, true);
      call_  = true;
      time_  = true;
      trace_ = true;
      dump_  = true;
      numWarnInvocations     = 0;
      numErrorInvocations    = 0;
      currentLogFormat       = "{0} {1} {2}: {3}";
      logFormatPropertyRead  = false;
      logLevel_ = L_PANIC | L_ERROR | L_WARN | L_INFO;

      timeE   = string(LTGREEN_BLACK) + "TIME " + ESC;
      callE   = string(BLACK_LTGREEN) + "CALL " + ESC;
      traceE  = string(WHITE_BLACK  ) + "TRACE" + ESC;
      plainE  = string(WHITE_BLACK  ) + "     " + ESC;
      infoE   = string(GREEN_BLACK  ) + "INFO " + ESC;
      warnE   = string(YELLOW_BLACK ) + "WARN " + ESC;
      errorE  = string(RED_BLACK    ) + "ERROR" + ESC;
      panicE  = string(BLACK_RED    ) + "PANIC" + ESC;
      exitE   = string(GREEN_BLACK  ) + "EXIT " + ESC;

      timeX   = "TIME ";
      callX   = "CALL ";
      traceX  = "TRACE";
      plainX  = "     ";
      infoX   = "INFO ";
      warnX   = "WARN ";
      errorX  = "ERROR";
      panicX  = "PANIC";
      exitX   = "EXIT ";
   }


   Log::~Log() {
   }

   void Log::exitLow(int val) {
      // gcc 3.x: The functions abort, exit, _Exit and _exit are recognized and presumed not to return,
      // but otherwise are not built in.
      // _exit is not recognized in strict ISO C mode (`-ansi', `-std=c89' or `-std=c99').
      // _Exit is not recognized in strict C89 mode (`-ansi' or `-std=c89').
#     if defined(__ICC)
         ::exit(val);
#     elif  __GNUC__==3
         ::exit(val);
#     elif defined(__sun)
         ::exit(val);
#     else
         ::_exit(val);
#     endif
   }

   void Log::setWithXtermColor(bool val /* = true */) {
      withXtermColor_ = val;
   }

   void Log::setDefaultLogLevel() {
      logLevel_ = L_PANIC | L_ERROR | L_WARN | L_INFO;
      setPreLogLevelCheck();
   }


   void Log::setLogLevel(int level) {
      logLevel_ = level;
      setPreLogLevelCheck();
   }

   void Log::setLogLevel(int argc, const char * const args[]) {
      if ((properties_.findArgument(argc, args, "-?") > 0) ||
          (properties_.findArgument(argc, args, "-h") > 0)) {
         std::cout << usage() << std::endl;
         return;
      }
      initialize();
   }


   void Log::removeLogLevel(string logLevel) {
      int level = logLevelToBit(logLevel);
      logLevel_ = (logLevel_ & ~level);
      setPreLogLevelCheck();
   }


   void Log::addLogLevel(string logLevel) {
      int level = logLevelToBit(logLevel);
      logLevel_ = (logLevel_ | level);
      setPreLogLevelCheck();
      //std::cout << "DEBUG: " << "Adding logLevel '" << logLevel << "' level=" << level << " dump=" << dump_ << std::endl;
   }


   void Log::setPreLogLevelCheck() {
      call_ = time_ = trace_ = dump_  = false;
      if (logLevel_ & L_CALL)  call_  = true;
      if (logLevel_ & L_TIME ) time_  = true;
      if (logLevel_ & L_TRACE) trace_ = true;
      if (logLevel_ & L_DUMP ) dump_  = true;
   }


   string Log::bitToLogLevel(int level) const {
      string sb = "";
      if (level & L_PANIC) sb += "PANIC";
      if (level & L_ERROR) sb += " | ERROR";
      if (level & L_WARN ) sb += " | WARN";
      if (level & L_INFO ) sb += " | INFO";
      if (level & L_CALL) sb += " | CALL";
      if (level & L_TIME ) sb += " | TIME";
      if (level & L_TRACE) sb += " | TRACE";
      if (level & L_DUMP ) sb += " | DUMP";
      return sb;
   }


   void Log::panic(const string &instance, const string &text) {
      if (logLevel_ & L_PANIC) {
         if (withXtermColor_)
            log(panicE, L_PANIC, instance, text);
         else
            log(panicX, L_PANIC, instance, text);
         cerr << text << endl;
         numErrorInvocations++;
         // displayStatistics();
         exitLow(1);
      }
   }


   void Log::exit(const string &instance, const string &text) {
      if (withXtermColor_)
         log(exitE, L_EXIT, instance, text);
      else
         log(exitX, L_EXIT, instance, text);
      displayStatistics();
      exitLow(0);
   }


   void Log::info(const string &instance, const string &text) {
      if (logLevel_ & L_INFO) {
         if (withXtermColor_)
            log(infoE, L_INFO, instance, text);
         else
            log(infoX, L_INFO, instance, text);
      }
   }


   void Log::warn(const string &instance, const string &text) {
      if(logLevel_ & L_WARN) {
         numWarnInvocations++;
         if (withXtermColor_)
            log(warnE, L_WARN, instance, text);
         else
            log(warnX, L_WARN, instance, text);
      }
   }


   void Log::error(const string &instance, const string &text) {
      if(logLevel_ & L_PANIC) {
         numErrorInvocations++;
         if (withXtermColor_)
            log(errorE, L_ERROR, instance, text);
         else
            log(errorX, L_ERROR, instance, text);
      }
   }


   void Log::plain(const string &/*instance*/, const string &text) {
      log("", L_PLAIN, "", text);
   }


   void Log::dump(const string &instance, const string &text) {
      if((logLevel_ & L_DUMP) != 0) {
         log("", L_DUMP, instance, text);
//          log("", L_DUMP, instance, Memory.getStatistic());
      }
   }


   void Log::trace(const string &instance, const string &text) {
      if(logLevel_ & L_TRACE) {
         if (withXtermColor_)
            log(traceE, L_TRACE, instance, text);
         else
            log(traceX, L_TRACE, instance, text);
      }
   }


   void Log::call(const string &instance, const string &text) {
      if(logLevel_ & L_CALL) {
         if (withXtermColor_)
            log(callE, L_CALL, instance, text);
         else
            log(callX, L_CALL, instance, text);
      }
   }


   void Log::time(const string &instance, const string &text) {
         if(logLevel_ & L_TIME) {
            if (withXtermColor_)
               log(timeE, L_TIME, instance, text);
            else
               log(timeX, L_TIME, instance, text);
         }
      }

   void Log::log(const string &levelStr, int level, const string &instance,
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

   std::string Log::usage() const {
      std::string text = string("");
      text += string("\nLogging options:");
      text += string("\n   -trace true         Show code trace.");
      text += string("\n   -dump true          Dump internal state.");
      text += string("\n   -call true          Show important method entries");
      text += string("\n   -time true          Display some performance data.");
      //text += string("\n  -logFile <fileName> Log to given file instead to console.");
      return text;
   }


   void Log::displayStatistics() {
      //       Log.info(ME, Memory.getStatistic());
      if (withXtermColor_) {
         if (numErrorInvocations>0) {
            info(ME, string(BLACK_RED) + "There were " + lexical_cast<std::string>(numErrorInvocations) +
                     " ERRORS and " + lexical_cast<std::string>(numWarnInvocations) + " WARNINGS" + ESC);
         }
         else if (numWarnInvocations>0) {
            info(ME, string(BLACK_PINK) + "There were " + lexical_cast<std::string>(numErrorInvocations) +
                     " ERRORS and " + lexical_cast<std::string>(numWarnInvocations) + " WARNINGS" + ESC);
         }
         else {
            info(ME, string(BLACK_GREEN) + "No errors/warnings were reported" + ESC);
         }
      }
      else {
         if (numErrorInvocations>0 || numWarnInvocations>0) {
            info(ME, string("There were ") + lexical_cast<std::string>(numErrorInvocations) + " ERRORS and " + (lexical_cast<std::string>(numWarnInvocations)) + " WARNINGS");
         }
         else
            info(ME, "No errors/warnings were reported");
      }
   }


   void Log::printStack() {
      cerr << "sorry, no Stack aviable" << endl;
   }



   void Log::initSpecificTrace(const string& trace, const string& traceId)
   {
      if (properties_.propertyExists(trace + "[" + name_ + "]")) {
         if (properties_.getBoolProperty(trace + "[" + name_ + "]", false))
            addLogLevel(traceId);
         else removeLogLevel(traceId);
         return;
      }
      if (properties_.propertyExists(trace)) {
         if (properties_.getBoolProperty(trace, false))
            addLogLevel(traceId);
         else removeLogLevel(traceId);
      }
   }


   void Log::initialize() {
      setPreLogLevelCheck();
      logFormatPropertyRead = true;
      // Given flag -info switches off Log.info messages:
      initSpecificTrace("info", "INFO");
      initSpecificTrace("warn", "WARN");
      initSpecificTrace("error", "ERROR");
      initSpecificTrace("call", "CALL");
      initSpecificTrace("time", "TIME");
      initSpecificTrace("trace", "TRACE");
      initSpecificTrace("dump", "DUMP");

      if (properties_.getBoolProperty("+call", false))
         addLogLevel("CALL");
      if (properties_.getBoolProperty("+time", false))
         addLogLevel("TIME");
      if (properties_.getBoolProperty("+trace", false))
         addLogLevel("TRACE");
      if (properties_.getBoolProperty("+dump", false))
         addLogLevel("DUMP");

      // format: {0}:{1}:{2}:{3}    <timestamp>:<levelStr>:<instance>:<text>
      currentLogFormat = properties_.getStringProperty("LogFormat",
                                                       currentLogFormat);

      //std::cout << "DEBUG: " << "Current logLevel for [" << name_ << "] is " << bitToLogLevel(logLevel_) << std::endl;

//      string tmp = properties_.getStringProperty("LogFormat.Date","MEDIUM");
//       if (tmp == "SHORT") lookAndFeelDate = java.text.DateFormat.SHORT;
//       else if (tmp.equals("MEDIUM"))
//          lookAndFeelDate = java.text.DateFormat.MEDIUM;
//       else if (tmp.equals("LONG"))
//          lookAndFeelDate = java.text.DateFormat.LONG;
//       else if (tmp.equals("FULL"))
//          lookAndFeelDate = java.text.DateFormat.FULL;
//       tmp = properties_.getStringProperty("LogFormat.Time","MEDIUM");
//       if (tmp.equals("SHORT"))
//          lookAndFeelTime = java.text.DateFormat.SHORT;
//       else if (tmp.equals("MEDIUM"))
//          lookAndFeelTime = java.text.DateFormat.MEDIUM;
//       else if (tmp.equals("LONG"))
//          lookAndFeelTime = java.text.DateFormat.LONG;
//       else if (tmp.equals("FULL"))
//          lookAndFeelTime = java.text.DateFormat.FULL;

//      string la = properties_.getStringProperty("LogFormat.Language","");
//      string co = properties_.getStringProperty("LogFormat.Country", "");
//       if (la != null && co != null) country = new Locale(la, co);

//       String fileName = properties_.getProperty("logFile", (String)null);
//       if (fileName != null)
//          Log.logToFile(fileName);
   }


   string Log::getTime() {
      // adapt it here to the correct time format (locales) ?!
      time_t theTime;
      ::time(&theTime);
      string timeStr = ctime(&theTime), ret;
      // eliminate new lines (if any)
      string::size_type pos = timeStr.find("\n");
      if (pos == string::npos) return timeStr;
      ret.assign(timeStr, 0, pos);
      return ret;
   }

}}} // end of namespace







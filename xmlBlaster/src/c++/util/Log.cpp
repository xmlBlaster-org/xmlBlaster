/*----------------------------------------------------------------------------
Name:      Log.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: Log.cpp,v 1.2 2001/12/16 22:47:38 ruff Exp $
----------------------------------------------------------------------------*/

#include <util/Log.h>
#include <time.h>

using namespace std;

namespace org { namespace xmlBlaster {
namespace util {

char* const Log::ESC          = "\033[0m"; 
char* const Log::BOLD         = "\033[1m";
char* const Log::RED_BLACK    = "\033[31;40m";
char* const Log::GREEN_BLACK  = "\033[32;40m";
char* const Log::YELLOW_BLACK = "\033[33;40m";
char* const Log::BLUE_BLACK   = "\033[34;40m";
char* const Log::PINK_BLACK   = "\033[35;40m";
char* const Log::LTGREEN_BLACK= "\033[36;40m";
char* const Log::WHITE_BLACK  = "\033[37;40m";
char* const Log::WHITE_RED    = "\033[37;41m";
char* const Log::BLACK_RED    = "\033[30;41m";
char* const Log::BLACK_GREEN  = "\033[40;42m";
char* const Log::BLACK_PINK   = "\033[40;45m";
char* const Log::BLACK_LTGREEN= "\033[40;46m";



   Log::Log(int args, const char * const argc[]) {
      bool isNewProperty = false;
      if (numOfImplementations_ == 0) {
         properties_ = new Property(args, argc);
         isNewProperty = true;
      }
      else {
         if ((args != 0) && (argc != 0)) {
            delete properties_;
            properties_ = new Property(args, argc);
            isNewProperty = true;
         }
      }

      if (isNewProperty) { // all static stuff here ...
         CALL   = true;
         TIME    = true;
         TRACE   = true;
         DUMP    = true;
         currentLogFormat       = "{0} {1} {2}: {3}";
         logFormatPropertyRead  = false;
         logLevel_ = L_PANIC | L_ERROR | L_WARN | L_INFO;
         string path = properties_->getProperty("HOME");
         string name = "xmlBlaster.properties";
         // introduce exceptions here if needed...
         int ret = properties_->loadPropsFromFile(name, path);
         if ( ret == -1) {
            path = properties_->getProperty("XMLBLASTER_HOME");
            ret  = properties_->loadPropsFromFile(name,path);
            if (ret == -1)
               error("reading properties: ", "could not find the file");
         }
      }
      // the following stuff does not matter if reinitialized many times
      ME      = "Log";
      timeX   = "TIME ";
      callX  = "CALL ";
      traceX  = "TRACE";
      plainX  = "     ";
      infoX   = "INFO ";
      warnX   = "WARN ";
      errorX  = "ERROR";
      panicX  = "PANIC";
      exitX   = "EXIT ";
#ifdef _TERM_WITH_COLORS_
      timeE   = string(LTGREEN_BLACK) + "TIME " + ESC;
      callE   = string(BLACK_LTGREEN) + "CALL " + ESC;
      traceE  = string(WHITE_BLACK  ) + "TRACE" + ESC;
      plainE  = string(WHITE_BLACK  ) + "     " + ESC;
      infoE   = string(GREEN_BLACK  ) + "INFO " + ESC;
      warnE   = string(YELLOW_BLACK ) + "WARN " + ESC;
      errorE  = string(RED_BLACK    ) + "ERROR" + ESC;
      panicE  = string(BLACK_RED    ) + "PANIC" + ESC;
      exitE   = string(GREEN_BLACK  ) + "EXIT " + ESC;
#endif // _TERM_WITH_COLORS_
      numOfImplementations_++;
   }


   Log::~Log() {
      numOfImplementations_--;
      if (numOfImplementations_ == 0) {
         if (properties_) delete properties_;
         properties_ = 0;
      }
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
      if ((properties_->findArgument(argc, args, "-?") > 0) ||
          (properties_->findArgument(argc, args, "-h") > 0)) {
         usage();
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
   }


   void Log::setPreLogLevelCheck() {
      CALL = TIME = TRACE = DUMP = false;
      if (logLevel_ & L_CALL) CALL = true;
      if (logLevel_ & L_TIME ) TIME  = true;
      if (logLevel_ & L_TRACE) TRACE = true;
      if (logLevel_ & L_DUMP ) DUMP  = true;
   }


   string Log::bitToLogLevel(int level) {
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
#ifdef _TERM_WITH_COLORS_
         log(panicE, L_PANIC, instance, text);
#else
         log(panicX, L_PANIC, instance, text);
#endif
         cerr << text << endl;
         numErrorInvocations++;
         // displayStatistics();
         exitLow(1);
      }
   }


   void Log::exit(const string &instance, const string &text) {
#ifdef _TERM_WITH_COLORS_
      log(exitE, L_EXIT, instance, text);
#else
      log(exitX, L_EXIT, instance, text);
#endif
      displayStatistics();
      exitLow(0);
   }


   void Log::info(const string &instance, const string &text) {
      if (logLevel_ & L_INFO) {
#ifdef _TERM_WITH_COLORS_
         log(infoE, L_INFO, instance, text);
#else
         log(infoX, L_INFO, instance, text);
#endif
      }
   }


   void Log::warn(const string &instance, const string &text) {
      if(logLevel_ & L_WARN) {
         numWarnInvocations++;
#ifdef _TERM_WITH_COLORS_
         log(warnE, L_WARN, instance, text);
#else
         log(warnX, L_WARN, instance, text);
#endif
      }
   }


   void Log::error(const string &instance, const string &text) {
      if(logLevel_ & L_PANIC) {
         numErrorInvocations++;
#ifdef _TERM_WITH_COLORS_
         log(errorE, L_ERROR, instance, text);
#else
         log(errorX, L_ERROR, instance, text);
#endif
      }
   }


   void Log::plain(const string &instance, const string &text) {
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
#ifdef _TERM_WITH_COLORS_
         log(traceE, L_TRACE, instance, text);
#else
         log(traceX, L_TRACE, instance, text);
#endif
      }
   }


   void Log::call(const string &instance, const string &text) {
      if(logLevel_ & L_CALL) {
#ifdef _TERM_WITH_COLORS_
         log(callE, L_CALL, instance, text);
#else
         log(callX, L_CALL, instance, text);
#endif
      }
   }


   void Log::time(const string &instance, const string &text) {
         if(logLevel_ & L_TIME) {
#ifdef _TERM_WITH_COLORS_
            log(timeE, L_TIME, instance, text);
#else
            log(timeX, L_TIME, instance, text);
#endif
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

   void Log::usage() {
      plain(ME, "");
      plain(ME, "Logging options:");
      plain(ME, "   +trace              Show code trace.");
      plain(ME, "   +dump               Dump internal state.");
      plain(ME, "   +call               Show important method entries");
      plain(ME, "   +time               Display some performance data.");
      plain(ME, "   -logFile <fileName> Log to given file instead to console.");
      plain(ME, "");
   }


   void Log::displayStatistics() {
      char       buffer[512];
      ostrstream out(buffer, 511);
//       Log.info(ME, Memory.getStatistic());
#ifdef _TERM_WITH_COLORS_
      if (numErrorInvocations>0) {
         out << BLACK_RED << "There were " << numErrorInvocations;
         out << " ERRORS and " << numWarnInvocations << " WARNINGS";
         out << ESC << (char)0;
      }
      else if (numWarnInvocations>0) {
         out << BLACK_PINK << "There were " << numErrorInvocations;
         out << " ERRORS and " << numWarnInvocations << " WARNINGS";
         out << ESC << (char)0;
      }
      else {
         out << BLACK_GREEN << "No errors/warnings were reported";
         out << ESC << (char)0;
      }
#else
      if (numErrorInvocations>0 || numWarnInvocations>0) {
         out << "There were " << numErrorInvocations << " ERRORS and ";
         out << numWarnInvocations << " WARNINGS" << (char)0;
      }
      else
         out << "No errors/warnings were reported" << (char)0;
#endif
      info(ME, buffer);
   }


   void Log::printStack() {
      cerr << "sorry, no Stack aviable" << endl;
   }


   void Log::initialize() {
      logFormatPropertyRead = true;
      // Given flag -info switches off Log.info messages:
      if (properties_->propertyExists("info")) {
         if (properties_->getBoolProperty("info", false))
            addLogLevel("INFO");
         else removeLogLevel("INFO");
      }
      if (properties_->propertyExists("warn")) {
         if (properties_->getBoolProperty("warn", false))
            addLogLevel("WARN");
         else removeLogLevel("WARN");
      }
      if (properties_->propertyExists("error")) {
         if (properties_->getBoolProperty("error", false))
            addLogLevel("ERROR");
         else removeLogLevel("ERROR");
      }
      if (properties_->propertyExists("call")) {
         if (properties_->getBoolProperty("call", false))
            addLogLevel("CALL");
         else removeLogLevel("CALL");
      }
      if (properties_->propertyExists("time")) {
         if (properties_->getBoolProperty("time", false))
            addLogLevel("TIME");
         else removeLogLevel("TIME");
      }
      if (properties_->propertyExists("trace")) {
         if (properties_->getBoolProperty("trace", false))
            addLogLevel("TRACE");
         else removeLogLevel("TRACE");
      }
      if (properties_->propertyExists("dump")) {
         if (properties_->getBoolProperty("dump", false))
            addLogLevel("DUMP");
         else removeLogLevel("DUMP");
      }

      if (properties_->getBoolProperty("+call", false))
         addLogLevel("CALL");
      if (properties_->getBoolProperty("+time", false))
         addLogLevel("TIME");
      if (properties_->getBoolProperty("+trace", false))
         addLogLevel("TRACE");
      if (properties_->getBoolProperty("+dump", false))
         addLogLevel("DUMP");

      // format: {0}:{1}:{2}:{3}    <timestamp>:<levelStr>:<instance>:<text>
      currentLogFormat = properties_->getStringProperty("LogFormat",
                                                       currentLogFormat);
      string tmp = properties_->getStringProperty("LogFormat.Date","MEDIUM");
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

      string la = properties_->getStringProperty("LogFormat.Language","");
      string co = properties_->getStringProperty("LogFormat.Country", "");
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
      int pos = timeStr.find("\n");
      if (pos < 0) return timeStr;
      ret.assign(timeStr, 0, pos);
      return ret;
   }

   Property *Log::properties_        = 0;
   int    Log::numWarnInvocations    = 0;
   int    Log::numErrorInvocations   = 0;
   int    Log::numOfImplementations_ = 0;
   int    Log::logLevel_             = 0;
   string Log::currentLogFormat      = "";
   bool   Log::logFormatPropertyRead = false;
   bool   Log::CALL = false;
   bool   Log::TIME  = false;
   bool   Log::TRACE = false;
   bool   Log::DUMP  = false;


}}} // end of namespace







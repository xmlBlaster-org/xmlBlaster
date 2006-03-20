/*----------------------------------------------------------------------------
Name:      I_Log.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
-----------------------------------------------------------------------------*/

#ifndef _ORG_XMLBLASTER_UTIL_I_LOG_H
#define _ORG_XMLBLASTER_UTIL_I_LOG_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <iostream>

/**
 * Interface for logging. 
 * <p />
 * This allows to customize which logging library you want to use.
 * The six logging levels used by Log are (in order):
 *
 * <p> The six logging levels used by <code>Log</code> are (in order):
 * <ol>
 * <li>trace (the least serious)</li>
 * <li>call (on method entry)</li>
 * <li>info</li>
 * <li>warn</li>
 * <li>error</li>
 * <li>panic (the most serious, does an exit)</li>
 * </ol>
 * The mapping of these log levels to the concepts used by the underlying
 * logging system is implementation dependent.
 * The implemention should ensure, though, that this ordering behaves
 * as expected.</p>
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
namespace org { namespace xmlBlaster { namespace util {
   
class Dll_Export I_Log {

   protected:
      /** For better performance */
      bool call_;
      bool time_;
      bool trace_;
      bool dump_;
      bool info_;
      
   public:
      I_Log() : call_(false), time_(false), trace_(false), dump_(false), info_(true) {}
      virtual ~I_Log() {}

      /** For better performance try: if (log.call()) log.call("Me","bla"); */
      inline bool call() const { return call_; }
      inline bool time() const { return time_; }
      inline bool trace() const { return trace_; }
      inline bool dump() const { return dump_; }

      /**
       * Use this exit for errors
       * @deprecated
       */
      virtual void panic(const std::string &instance, const std::string &text) { error(instance, text); ::exit(1); }


      /**
       * Exit without errors
       * @deprecated
       */
      virtual void exit(const std::string &instance, const std::string &text) { error(instance, text); ::exit(0); }


      /**
       * Use this for normal logging output
       */
      virtual void info(const std::string &instance, const std::string &text)= 0;
      

      /**
       * Use this for logging output where the xmlBlaster administrator shall 
       * be informed<br /> for example a login denied event
       */
      virtual void warn(const std::string &instance, const std::string &text)= 0;
      
      
      /**
       * Use this for internal xmlBlaster errors reporting
       */
      virtual void error(const std::string &instance, const std::string &text)= 0;


      /*
       * Log without time/date/instance (ignoring the header is not supported with all logging frameworks)
       * @param text the std::string to log
       * @deprecated
       */
      virtual void plain(const std::string &instance, const std::string &text) { info(instance, text); }


      /*
       * Log without time/date
       * @deprecated
       */
      virtual void dump(const std::string &instance, const std::string &text) { trace(instance, text); }


      /**
       * Tracing execution
       */
      virtual void trace(const std::string &instance, const std::string &text)= 0;


      /**
       * Tracing when entering methods
       */
      virtual void call(const std::string &instance, const std::string &text)= 0;

      
      /**
       * Output of performance measurements (elapsed milliseconds)
       * @deprecated
       */
      virtual void time(const std::string &instance, const std::string &text) { trace(instance, text); }

      /**
       * My current log level setting in human readable notation. 
       * @return for example "ERROR|WARN|TRACE"
       */
      virtual std::string getLogLevelStr() const { return ""; }

      virtual std::string usage() const { return "No logging usage available, please check the logging documentation"; }
   }; // end of class I_Log



}}} // end of namespace util

#endif // _ORG_XMLBLASTER_UTIL_I_LOG_H

/*----------------------------------------------------------------------------
Name:      Log4cplus.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
-----------------------------------------------------------------------------*/

#ifndef _ORG_XMLBLASTER_UTIL_LOG4CPLUS_H
#define _ORG_XMLBLASTER_UTIL_LOG4CPLUS_H

#if XMLBLASTER_COMPILE_LOG4CPLUS_PLUGIN==1

#include <util/xmlBlasterDef.h>
#include <string>
#include <map>
#include <util/I_LogFactory.h>
#include <util/I_Log.h>
#include <log4cplus/logger.h>

namespace org { namespace xmlBlaster { namespace util {
   
/**
 * Embed log4cpp logging library in xmlBlaster. 
 * <p />
 * This embedding class replaces the xmlBlaster native logging
 * facility by log4cplus (http://log4cplus.sourceforge.net/)
 * <p />
 * You need to compile it with this define:
 * <pre>
 * build -DXMLBLASTER_COMPILE_LOG4CPLUS_PLUGIN=1 cpp
 * </pre>
 * <p>
 *  We are forwarding all <tt>xmlBlaster.properties</tt> and command line settings to log4cplus,
 *  so you could configure everything in <tt>xmlBlaster.properties</tt> instead of a
 *  separate <tt>log4cplus.properties</tt>
 *  (see the example file <tt>xmlBlaster/config/log4cplus.properties</tt>).<br />
 *  Log4cplus is extended to do ${xy} variable replacement not only from environment
 *  but from itself as well (recursion depth is one).
 * </p>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see http://log4cplus.sourceforge.net/
 * @see http://logging.apache.org/log4j/docs/manual.html
 */
class Dll_Export Log4cplusFactory : public I_LogFactory
{
   public:
      typedef std::map<std::string, org::xmlBlaster::util::I_Log *> LogMap;
   private:
     LogMap logMap_;
   public:
      Log4cplusFactory();
      virtual ~Log4cplusFactory();
      void initialize(const PropMap& propMap);
      I_Log& getLog(const std::string& name="");
      void releaseLog(const std::string& name="");
}; // end of class Log4cplus


/**
 * Embed log4cpp logging library in xmlBlaster. 
 * <p />
 * Map our internal logging calls to log4cpp logging calls.
 * <p />
 * You need to compile it with this define:
 * <pre>
 * build -DXMLBLASTER_COMPILE_LOG4CPLUS_PLUGIN=1 cpp
 * </pre>
 * <p />
 * The following command line arguments allows to specify the logging configuration file:
 * <pre>
 * -xmlBlaster/logging/configFileName log4cplus.properties
 *
 * -xmlBlaster/logging/debug true
 * </pre>
 * <p />
 * Here is an example for a <code>log4cplus.properties</code> configuration:
 * <pre>
log4cplus.rootLogger=INFO, STDOUT, R
log4cplus.logger.test.a.b.c=WARN
log4cplus.logger.filelogger=WARN, R
log4cplus.additivity.filelogger=FALSE

log4cplus.appender.STDOUT=log4cplus::ConsoleAppender
log4cplus.appender.STDOUT.layout=log4cplus::PatternLayout
log4cplus.appender.STDOUT.layout.ConversionPattern=%d{%m/%d/%y %H:%M:%S} [%t] %-5p %c{2} %%%x%% - %m [%l]%n

log4cplus.appender.R=log4cplus::RollingFileAppender
log4cplus.appender.R.File=xmlBlaster_CPP_${ENV_VAR}.log
log4cplus.appender.R.MaxFileSize=500KB
log4cplus.appender.R.MaxBackupIndex=5
log4cplus.appender.R.layout=log4cplus::TTCCLayout
 * </pre>
 * You can set in your environment a variable and reference it here with ${}, see ENV_VAR
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see http://log4cplus.sourceforge.net/
 * @see http://logging.apache.org/log4j/docs/manual.html
 */
class Dll_Export Log4cplusLog : public I_Log
{
  private:
   std::string logName_;
   log4cplus::Logger logger_;

  public:
   Log4cplusLog(std::string logName);
   void info(const std::string &instance, const std::string &text);
   void warn(const std::string &instance, const std::string &text);
   void error(const std::string &instance, const std::string &text);
   void panic(const std::string &instance, const std::string &text);
   void trace(const std::string &instance, const std::string &text);
   void call(const std::string &instance, const std::string &text);
   std::string usage() const;
};


}}} // end of namespace util

#endif // XMLBLASTER_COMPILE_LOG4CPLUS_PLUGIN

#endif // _ORG_XMLBLASTER_UTIL_LOG4CPLUS_H

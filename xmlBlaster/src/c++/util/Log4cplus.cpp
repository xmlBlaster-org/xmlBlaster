/*----------------------------------------------------------------------------
Name:      Log4cplus.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Embed logging library log4cpp http://log4cplus.sourceforge.net/
----------------------------------------------------------------------------*/

#ifdef XMLBLASTER_COMPILE_LOG4CPLUS_PLUGIN

#include <util/Log4cplus.h>
#include <log4cplus/logger.h>
#include <log4cplus/configurator.h>
#include <log4cplus/helpers/loglog.h>
#include <fstream>

using namespace std;
using namespace log4cplus;

namespace org { namespace xmlBlaster {
namespace util {

   Log4cplusFactory::Log4cplusFactory()
   {  
   }

   /**
    * Enforced by I_LogFactory, initialize the logging environment
    */
   void Log4cplusFactory::initialize(const PropMap& properties)
   {
      PropMap propMap = properties;
      if (propMap.count("xmlBlaster/logging/debug") > 0) {
         if ("true" == propMap["xmlBlaster/logging/debug"])
            log4cplus::helpers::LogLog::getLogLog()->setInternalDebugging(true);
      }

      string configFileName = "log4cplus.properties";
      if (propMap.count("xmlBlaster/logging/configFileName") > 0) {
         configFileName = propMap["xmlBlaster/logging/configFileName"];
      }
      std::ifstream file;
      file.open(configFileName.c_str());
      if(!file) {
            BasicConfigurator config;
            config.configure();
            Logger logger = Logger::getInstance("client");
            LOG4CPLUS_WARN(logger, "Couldn't find file logging configuration file \"-xmlBlaster/logging/configFileName " + configFileName + "\", logging is default configured");
      }
      else {
         PropertyConfigurator::doConfigure(configFileName);
      }

      Logger logger = Logger::getInstance("client");
      LOG4CPLUS_WARN(logger, "LOG4CPLUS: Hello, World!");
   }

   /**
    * Enforced by I_LogFactory
    */
   Log4cplusFactory::~Log4cplusFactory()
   {
      LogMap::reverse_iterator i;
      for(i = logMap_.rbegin(); i != logMap_.rend(); ++i) {
         I_Log* log = (*i).second;
         delete log;
      }
      logMap_.clear();
   }

   /**
    * Enforced by I_LogFactory
    */
   I_Log& Log4cplusFactory::getLog(const string& logName)
   {
      LogMap::iterator pos = logMap_.find(logName);
      if (pos != logMap_.end()) return *((*pos).second);
      
      Log4cplusLog *help = new Log4cplusLog(logName);
      logMap_.insert(LogMap::value_type(logName, help));
      pos = logMap_.find(logName);
      if (pos != logMap_.end()) {
         I_Log* log = (*pos).second;
         return *log;
      }

      std::cerr << "LogManager.cpp getLog(" << logName << ") is not implemented -> throwing exception" << std::endl;
      throw bad_exception();
   }
   
   /**
    * Enforced by I_LogFactory
    */
   void Log4cplusFactory::releaseLog(const string& name)
   {
      std::cerr << "Log4cplus.cpp releaseLog(" << name << ") is not implemented" << std::endl;
   }

//================== Log4cplusLog implementation ======================

   Log4cplusLog::Log4cplusLog(std::string logName) : logName_(logName), logger_(Logger::getInstance(logName)) {
   }

   void Log4cplusLog::info(const std::string &instance, const std::string &text){
      std::cout << "[INFO]  " << instance << ": " << text << std::endl;
      LOG4CPLUS_INFO(logger_, text);
   }
   
   void Log4cplusLog::warn(const std::string &instance, const std::string &text){
      std::cout << "[WARN]  " << instance << ": " << text << std::endl;
      LOG4CPLUS_WARN(logger_, text);
   }
   
   void Log4cplusLog::error(const std::string &instance, const std::string &text){
      std::cout << "[ERROR] " << instance << ": " << text << std::endl;
      LOG4CPLUS_ERROR(logger_, text);
   }
   
   void Log4cplusLog::trace(const std::string &instance, const std::string &text){
      std::cout << "[TRACE] " << instance << ": " << text << std::endl;
      LOG4CPLUS_TRACE(logger_, text);
   }
   
   void Log4cplusLog::call(const std::string &instance, const std::string &text){
      std::cout << "[CALL]  " << instance << ": " << text << std::endl;
      LOG4CPLUS_DEBUG(logger_, text);
   }
}}} // end of namespace

#endif // XMLBLASTER_COMPILE_LOG4CPLUS_PLUGIN



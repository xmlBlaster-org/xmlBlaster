/*----------------------------------------------------------------------------
Name:      Log4cplus.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Embed logging library log4cpp http://log4cplus.sourceforge.net/
----------------------------------------------------------------------------*/

#if XMLBLASTER_COMPILE_LOG4CPLUS_PLUGIN==1

#include <util/Log4cplus.h>
#include <log4cplus/logger.h>
#include <log4cplus/configurator.h>
#include <log4cplus/helpers/property.h>
#include <log4cplus/helpers/loglog.h>
#include <fstream>
#include <util/PropertyDef.h>
#include <util/lexical_cast.h>

using namespace std;
using namespace log4cplus;

namespace org { namespace xmlBlaster {
namespace util {

   Log4cplusFactory::Log4cplusFactory()
   {  
   }

   /**
    * Enforced by I_LogFactory, initialize the logging environment
    * If your code does logging initialization already you can switch this initialization off
    * by adding a property "xmlBlaster/logging/initialize=false" to propMap.
    */
   void Log4cplusFactory::initialize(const PropMap& propMap)
   {
      {
         PropMap::const_iterator pos = propMap.find("xmlBlaster/logging/debug");
         if (pos != propMap.end()) {
            log4cplus::helpers::LogLog::getLogLog()->setInternalDebugging(lexical_cast<bool>(pos->second));
         }
      }

      bool initialize = true;
      PropMap::const_iterator pos = propMap.find("xmlBlaster/logging/initialize");
      if (pos != propMap.end())
         if ("false" == pos->second)
            initialize = false;


      if (initialize) {
         // Find the configuration file name
         const char *envName = "xmlBlaster/logging/configFileName";
         string configFileName = "log4cplus.properties"; // local directory
         pos = propMap.find(envName);
         if (pos != propMap.end()) {
            configFileName = (*pos).second;
         }
         else {
            const char* envValue = getenv(envName);
            if (envValue != 0) {
               configFileName = envValue;
            }
            else {
               std::ifstream file;
               file.open(configFileName.c_str());  // local directory?
               if(!file) {
                  pos = propMap.find("user.home");
                  if (pos != propMap.end()) {
                     string tmp = (*pos).second + FILE_SEP + configFileName;
                     std::ifstream file2;
                     file2.open(tmp.c_str());
                     if(!file2) {
                     }
                     else {
                        configFileName = tmp;
                     }
                  }
               }
            }
         }

         bool inheritEnvironment = true;

         std::ifstream file;
         file.open(configFileName.c_str());
         if(!file) {
            // No configuration file
            if (inheritEnvironment) {
               // We pass all xmlBlaster.properties + command line settings to log4cplus
               log4cplus::helpers::Properties props;
               PropMap::const_iterator iter = propMap.begin();
               while (iter != propMap.end()) {
                    props.setProperty((*iter).first, (*iter).second);
                  iter++;
               }
               PropertyConfigurator tmp(props, Logger::getDefaultHierarchy());
               tmp.configure();
            }
            else {
               BasicConfigurator config;
               config.configure();
            }
            Logger logger = Logger::getInstance("org.xmlBlaster");
            LOG4CPLUS_WARN(logger, "Couldn't find file logging configuration file \"-xmlBlaster/logging/configFileName " + configFileName + "\", you can use the example in xmlBlaster" +
                                    FILE_SEP + "config" + FILE_SEP + configFileName);
            LOG4CPLUS_INFO(logger, "We continue with default logging configuration.");
         }
         else {
            // Scan configuration file
            if (inheritEnvironment) {
               // Log4Cplus can replace env variables in its config file
               // there for we pass all settings from xmlBlaster.properties to log4cplus
               log4cplus::helpers::Properties props(configFileName);
               PropMap::const_iterator iter = propMap.begin();
               while (iter != propMap.end()) {
                    props.setProperty((*iter).first, (*iter).second);
                  iter++;
               }
               PropertyConfigurator tmp(props, Logger::getDefaultHierarchy());
               tmp.configure();
            }
            else {
               PropertyConfigurator::doConfigure(configFileName);
            }

            Logger logger = Logger::getInstance("org.xmlBlaster");
            LOG4CPLUS_INFO(logger, "Configured log4cplus with configuration file xmlBlaster/logging/configFileName=" + configFileName);
         }
      }
      else {
         Logger logger = Logger::getInstance("org.xmlBlaster");
         LOG4CPLUS_INFO(logger, "Log4cplus is configured already (xmlBlaster/logging/initialize=false), no reconfiguration done.");
      }

      //Logger logger = Logger::getInstance("org.xmlBlaster");
      //LOG4CPLUS_WARN(logger, "LOG4CPLUS: Hello, World!");
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

      //Logger::getDefaultHierarchy().shutdown();
      Logger::shutdown();
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
      //Should we set this if basic configured??:
      //logger.setLogLevel(INFO_LOG_LEVEL);
      call_ = dump_ = time_ = logger_.isEnabledFor(log4cplus::DEBUG_LOG_LEVEL);
      trace_ = logger_.isEnabledFor(log4cplus::TRACE_LOG_LEVEL);
      info_ = logger_.isEnabledFor(log4cplus::INFO_LOG_LEVEL);
   }

   void Log4cplusLog::info(const std::string &instance, const std::string &text){
      //std::cout << "[INFO]  " << instance << ": " << text << std::endl;
      if (logger_.isEnabledFor(log4cplus::INFO_LOG_LEVEL)) {
         log4cplus::tostringstream _log4cplus_buf;
         _log4cplus_buf << text;
         logger_.forcedLog(log4cplus::INFO_LOG_LEVEL, _log4cplus_buf.str(), instance.c_str(), -1);
      }
      //LOG4CPLUS_INFO(logger_, text);
   }
   
   void Log4cplusLog::warn(const std::string &instance, const std::string &text){
      if (logger_.isEnabledFor(log4cplus::WARN_LOG_LEVEL)) {
         log4cplus::tostringstream _log4cplus_buf;
         _log4cplus_buf << text;
         logger_.forcedLog(log4cplus::WARN_LOG_LEVEL, _log4cplus_buf.str(), instance.c_str(), -1);
      }
   }
   
   void Log4cplusLog::error(const std::string &instance, const std::string &text){
      if (logger_.isEnabledFor(log4cplus::ERROR_LOG_LEVEL)) {
         log4cplus::tostringstream _log4cplus_buf;
         _log4cplus_buf << text;
         logger_.forcedLog(log4cplus::ERROR_LOG_LEVEL, _log4cplus_buf.str(), instance.c_str(), -1);
      }
   }

   void Log4cplusLog::panic(const std::string &instance, const std::string &text){
      if (logger_.isEnabledFor(log4cplus::FATAL_LOG_LEVEL)) {
         log4cplus::tostringstream _log4cplus_buf;
         _log4cplus_buf << text;
         logger_.forcedLog(log4cplus::FATAL_LOG_LEVEL, _log4cplus_buf.str(), instance.c_str(), -1);
      }
      ::exit(1);
   }
   
   void Log4cplusLog::trace(const std::string &instance, const std::string &text){
      if (logger_.isEnabledFor(log4cplus::TRACE_LOG_LEVEL)) {
         log4cplus::tostringstream _log4cplus_buf;
         _log4cplus_buf << text;
         logger_.forcedLog(log4cplus::TRACE_LOG_LEVEL, _log4cplus_buf.str(), instance.c_str(), -1);
      }
   }
   
   void Log4cplusLog::call(const std::string &instance, const std::string &text){
      if (logger_.isEnabledFor(log4cplus::DEBUG_LOG_LEVEL)) {
         log4cplus::tostringstream _log4cplus_buf;
         _log4cplus_buf << text;
         logger_.forcedLog(log4cplus::DEBUG_LOG_LEVEL, _log4cplus_buf.str(), instance.c_str(), -1);
      }
   }

   std::string Log4cplusLog::usage() const {
      std::string str;
      str += "\nLOG4CPLUS logging configuration, see http://log4cplus.sourceforge.net";
      str += "\n   -xmlBlaster/logging/configFileName [log4cplus.properties]";
      str += "\n                       Path to the log4cplus configuration file, for";
      str += "\n                       configuration see http://logging.apache.org/log4j/docs/manual.html";
      str += string("\n                       We provide an example file in xmlBlaster")+FILE_SEP+"config"+FILE_SEP+"log4cplus.properties";
      return str;
   }
}}} // end of namespace

#endif // XMLBLASTER_COMPILE_LOG4CPLUS_PLUGIN



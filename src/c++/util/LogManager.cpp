/*----------------------------------------------------------------------------
Name:      LogManager.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
----------------------------------------------------------------------------*/
#include <util/LogManager.h>
#include <util/Global.h>
#include <iostream>
#include <exception>

using namespace std;

namespace org { namespace xmlBlaster {
namespace util {

   /**
    * Initializes logging and Properties
    */
   LogManager::LogManager() : logFactory_(new DefaultLogFactory())
   {
   }

   LogManager::~LogManager()
   {
      delete logFactory_;
   }

   void LogManager::initialize(const PropMap& propMap)
   {
      if (logFactory_ == 0) {
         logFactory_ = new DefaultLogFactory();
      }
      logFactory_->initialize(propMap);
   }

   I_LogFactory& LogManager::getLogFactory(const std::string& /*name*/) const
   {
      return *logFactory_;
   }

   void LogManager::setLogFactory(const std::string& /*name*/, I_LogFactory* logFactory)
   {
      delete logFactory_;
      logFactory_ = logFactory;
   }

//--------------------DefaultLogFactory implementation -------------------------------------------


   //DefaultLogFactory::DefaultLogFactory() : I_LogFactory() {}

   /**
    * Enforced by I_LogFactory, we are the default implementation.
    */
   //void DefaultLogFactory::initialize(const PropMap& properties)
   //{
   //   this->propMap_ = properties;
   //}

   /**
    * Enforced by I_LogFactory, we are the default implementation.
    */
   DefaultLogFactory::~DefaultLogFactory()
   {
      LogMap::reverse_iterator i;
      for(i = logMap_.rbegin(); i != logMap_.rend(); ++i) {
         I_Log* log = (*i).second;
         delete log;
      }
      logMap_.clear();
   }

   /**
    * Enforced by I_LogFactory, we are the default implementation. 
    */
   I_Log& DefaultLogFactory::getLog(const string& logName)
   {
      LogMap::iterator pos = logMap_.find(logName);
      if (pos != logMap_.end()) return *((*pos).second);
      
#     ifndef ORG_XMLBLASTER_UTIL_LOGMANAGER   // To support standalone compilation (see main() below)
      Global& glob = Global::getInstance();
      Log *help = new Log(glob.getProperty(), 0, 0, logName);
      help->initialize();
      logMap_.insert(LogMap::value_type(logName, help));
      pos = logMap_.find(logName);
      if (pos != logMap_.end()) {
         I_Log* log = (*pos).second;
         return *log;
      }
#     endif

      std::cerr << "LogManager.cpp getLog(" << logName << ") is not implemented -> throwing exception" << std::endl;
      throw bad_exception();
      //throw bad_exception("LogManager.cpp not implemented -> throwing exception");
   }
   
   /**
    * Enforced by I_LogFactory, we are the default implementation. 
    */
   void DefaultLogFactory::releaseLog(const string& name)
   {
      std::cerr << "LogManager.cpp releaseLog(" << name << ") is not implemented -> throwing exception" << std::endl;
   }

}}} // end of namespace


#ifdef ORG_XMLBLASTER_UTIL_LOGMANAGER
using namespace std;
using namespace org::xmlBlaster::util;

class DummyLog : public I_Log
{  public:
   void info(const std::string &instance, const std::string &text){
      std::cout << "[INFO]  " << instance << ": " << text << std::endl;
   }
   void warn(const std::string &instance, const std::string &text){
      std::cout << "[WARN]  " << instance << ": " << text << std::endl;
   }
   void error(const std::string &instance, const std::string &text){
      std::cout << "[ERROR] " << instance << ": " << text << std::endl;
   }
   void trace(const std::string &instance, const std::string &text){
      std::cout << "[TRACE] " << instance << ": " << text << std::endl;
   }
   void call(const std::string &instance, const std::string &text){
      std::cout << "[CALL]  " << instance << ": " << text << std::endl;
   }
};

class DummyLogFactory : public I_LogFactory {
   private:
      DummyLog log_;
   public:
      DummyLogFactory() {}
      void initialize(const PropMap& propMap) {};
      I_Log& getLog(const std::string& name="") { return log_; }
      void releaseLog(const std::string& name="") {}
};

// g++ LogManager.cpp -Wall -g -o LogManager -DORG_XMLBLASTER_UTIL_LOGMANAGER=1 -I../
int main(int argc, char* argv[])
{
   LogManager::PropMap propMap;
   for (int ii=0; ii<argc-1; ii++) {
      propMap[argv[ii]] = argv[(ii+1)];
      ii++;
   }

   try {
      cout << endl << "Testing customized LogFactory" << endl;
      {
         LogManager logManager;
         logManager.initialize(propMap);
         logManager.setLogFactory("test", new DummyLogFactory());
         I_Log& log = logManager.getLogFactory().getLog();
         log.info(__FILE__, "A message");
         if (log.trace()) log.info(__FILE__, "ERROR: trace not expected");
      }

      cout << endl << "Testing default behaviour" << endl;
      {
         LogManager logManager;
         logManager.initialize(propMap);
         I_Log& log = logManager.getLogFactory().getLog();
         log.info(__FILE__, "A message");
      }
   }
   catch(...) {
      std::cerr << "Caught unexpected exception" << std::endl;
   }

   return 0;
}
#endif





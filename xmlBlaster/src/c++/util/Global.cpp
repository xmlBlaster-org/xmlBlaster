/*------------------------------------------------------------------------------
Name:      Global.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
Version:   $Id: Global.cpp,v 1.3 2002/12/06 19:28:14 laghi Exp $
------------------------------------------------------------------------------*/
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util {

   Global::Global() : ME("Global"), logMap_()
   {
      copy();
      isInitialized_ = false;
   }

/*
   Global::Global(const Global& global) : ME("Global"), logMap_(global.logMap_)
   {
      args_ = global.args_;
      argc_ = global.argc_;
   }
*/
   Global& Global::operator =(const Global &global)
   {
      copy();
      return *this;
   }


   Global::~Global()
   {
      try {
         logMap_.erase(logMap_.begin(), logMap_.end());
         delete property_;
      }
      catch (...) {
      }
   }

   Global& Global::getInstance(const char* instanceName)
   {
      static Global global;
      return global;
   }

   void Global::initialize(int args, const char * const argc[])
   {
      if (isInitialized_) {
         getLog("core").warn(ME, "::initialize: the global is already initialized. Ignoring this initialization");
      }
      args_     = args;
      argc_     = argc;
      if (property_ != NULL) delete property_;
      property_ = NULL;
      property_ = new Property(args, argc);
   }

   Property& Global::getProperty()
   {
      if (property_ == NULL)
        throw XmlBlasterException(USER_CONFIGURATION,
                                  "UNKNOWN NODE",
                                  ME + string("::getProperty"));
      return *property_;
   }


   Log& Global::getLog(char* logName)
   {
      LogMap::iterator pos = logMap_.find(logName);
      if (pos != logMap_.end()) return (*pos).second;

      Log help(args_, argc_);
      help.initialize();
      logMap_.insert(LogMap::value_type(logName, help));
      pos = logMap_.find(logName);
      if (pos != logMap_.end()) {
         Log* log = &(*pos).second;
         return *log;
      }

     // if it reaches this point, then a serious error occured
     throw XmlBlasterException(INTERNAL_UNKNOWN, "UNKNOWN NODE", ME + string("::getLog"));
   }

   int Global::getArgs()
   {
      return args_;
   }

   const char * const* Global::getArgc()
   {
      return argc_;
   }

   string Global::getLocalIP() const
   {
      // change this to a better way later ...
      return string("127.0.0.1");
   }

   string Global::getBootstrapHostname()
   {
      string ret = getProperty().getProperty(string("hostname"));
      if (ret == "") return getLocalIP();
      return ret;

   }

}}}; // namespace


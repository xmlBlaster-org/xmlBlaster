/*-----------------------------------------------------------------------------
Name:      TestSuite.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing helper
See:       Other C++ test tools: http://c2.com/cgi/wiki?TestingFramework
-----------------------------------------------------------------------------*/

#ifndef _TESTSUITE_H
#define _TESTSUITE_H

#include <assert.h> // windows
#include <iostream>
#include <client/XmlBlasterAccess.h>
#include <util/EmbeddedServer.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/thread/ThreadImpl.h>
#include <util/Timestamp.h>
#include <util/lexical_cast.h>

namespace org { namespace xmlBlaster { namespace test {

template <class T1, class T2> extern void assertEquals(org::xmlBlaster::util::I_Log& log, const std::string& who, const T1& should, const T2& is, const std::string& txt);
template <class T1, class T2> extern void assertDifferes(org::xmlBlaster::util::I_Log& log, const std::string& who, const T1& should, const T2& is, const std::string& txt);
#if __GNUC__ == 2
extern void assertEquals(org::xmlBlaster::util::I_Log& log, const std::string& who, const std::string& should, const std::string& is, const std::string& txt);
extern void assertDifferes(org::xmlBlaster::util::I_Log& log, const std::string& who, const std::string& should, const std::string& is, const std::string& txt);
#endif

/**
 * Supports comparing for example char[] with string. 
 * @param should const char [13]
 * @param is     std::string
 */
template <class T1, class T2> 
void assertEquals(org::xmlBlaster::util::I_Log& log, const std::string& who, const T1& should, const T2& is, const std::string& txt)
{
   //if (should != is) {
   if (org::xmlBlaster::util::lexical_cast<std::string>(should) != org::xmlBlaster::util::lexical_cast<std::string>(is)) {
      log.error(who, txt + " FAILED: value is '" + org::xmlBlaster::util::lexical_cast<std::string>(is) + "' but should be '" + org::xmlBlaster::util::lexical_cast<std::string>(should) + "'");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}

template <class T1, class T2> 
void assertDifferes(org::xmlBlaster::util::I_Log& log, const std::string& who, const T1& should, const T2& is, const std::string& txt)
{
   //if (should == is) {
   if (org::xmlBlaster::util::lexical_cast<std::string>(should) == org::xmlBlaster::util::lexical_cast<std::string>(is)) {
      log.error(who, txt + " FAILED: value is '" + org::xmlBlaster::util::lexical_cast<std::string>(is) + "' in both cases but they should be different");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}


#if __GNUC__ == 2
// for example g++ 2.95.3
// specific implementation for the string since the org::xmlBlaster::util::lexical_cast from string to string causes problems.
void assertEquals(org::xmlBlaster::util::I_Log& log, const std::string& who, const std::string& should, const std::string& is, const std::string& txt)
{
   if (should != is) {
      log.error(who, txt + " FAILED: value is '" + is + "' but should be '" + should + "'");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}
void assertDifferes(org::xmlBlaster::util::I_Log& log, const std::string& who, const std::string& should, const std::string& is, const std::string& txt)
{
   if (should == is) {
      log.error(who, txt + " FAILED: value is '" + is + "' for both cases but they should be different");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}
#endif


class TestSuite
{
protected:
   std::string           ME;
   std::string           applName_;
   org::xmlBlaster::util::Global&          global_;
   org::xmlBlaster::util::I_Log&             log_;
   bool             useEmbeddedServer_;
   org::xmlBlaster::client::XmlBlasterAccess connection_;
   org::xmlBlaster::util::EmbeddedServer*  embeddedServer_;
   bool             needsHelp_;
   bool             doEmbeddedServerCheck_;

public:

   TestSuite(int args, const char * const argv[], const std::string& name, bool doEmbeddedServerCheck=true) 
      : ME(name), 
        applName_(ME), 
        global_(org::xmlBlaster::util::Global::getInstance().initialize(args, argv)), 
        log_(global_.getLog("test")),
        connection_(global_),
        doEmbeddedServerCheck_(doEmbeddedServerCheck)
   {
      needsHelp_ = false;
      for (int ii=0; ii<args; ii++) {
         if (strcmp(argv[ii], "-?")==0 || strcmp(argv[ii], "-h")==0 || strcmp(argv[ii], "-help")==0) {
            needsHelp_ = true;
            break;
         }
      }

      if (!doEmbeddedServerCheck_)
         return;

      if ( log_.call() ) log_.call(ME, "Entering TestSuite base class, initializing XML environment");
      embeddedServer_    = NULL;
      useEmbeddedServer_ = global_.getProperty().getBoolProperty("embeddedServer", false);
      if (useEmbeddedServer_) {
         log_.info(ME, "the embedded server is switched ON (you could switch it off with '-embeddedServer false' on the command line)");
      }
      else {
         log_.warn(ME, "the embedded server is switched OFF (you will need an external xmlBlaster running), sleeping for 2 sec now ...");
         org::xmlBlaster::util::thread::Thread::sleep(2000);
     }
     if (useEmbeddedServer_) {
# ifdef XMLBLASTER_MICO
        std::cout << " !!!!! THIS TEST CAN NOT BE RUN WITH MICO SINCE AN ORB WHICH IS SHUTDOWN CAN NOT BE REUSED !!!!" << std::endl;
        std::cout << " !!!!! IT HAS BEEN TESTED AND IS PROVEN TO FAIL WITH MICO 2.3.7 AND 2.3.8                  !!!!" << std::endl;
        std::cout << " !!!!! IT IS PROVEN TO FAIL WITH MICO 2.3.7 AND 2.3.8                                      !!!!" << std::endl;
        std::cout << " !!!!! TRY IT WITH ANOTHER CORBA IMPLEMENTATION (for example TAO)                          !!!!" << std::endl;
        exit(-1);
# endif
        if ( log_.call() ) log_.call(ME, "Entering TestSuite base class, useEmbeddedServer_=true");
        std::string cmdLine = global_.getProperty().getStringProperty("embeddedServer.cmdLine", "> /dev/null");
        std::string jvmArgs = global_.getProperty().getStringProperty("embeddedServer.jvmArgs", "");
        embeddedServer_ = new org::xmlBlaster::util::EmbeddedServer(global_, jvmArgs, cmdLine, &connection_);
        embeddedServer_->start(true);
//        org::xmlBlaster::util::thread::Thread::sleepSecs(5); // let the xmlBlaster server start ...
        // don't need to wait anymore since 
     }
   }

   virtual ~TestSuite()
   {
      if (log_.call()) log_.call(ME, "destructor");
      if (doEmbeddedServerCheck_) {
         delete embeddedServer_;
         embeddedServer_ = NULL;
      }
      if (log_.trace()) log_.trace(ME, "destructor ended");
   }

   virtual void setUp(/*int args=0, char *argv[]=0*/)
   {
      if (needsHelp_) {
         usage();
         exit(0);
      }
   }

   virtual void tearDown() 
   {
   }

   void startEmbeddedServer()
   {
      if (embeddedServer_) {
         log_.trace(ME, "starting now the embedded server");
         embeddedServer_->start();
      }
      else {
         log_.warn(ME, "could not start the embedded server since you are running without it");
      }
   }

   void stopEmbeddedServer()
   {
      if (embeddedServer_) {
         embeddedServer_->stop();
      }
      else {
         log_.warn(ME, "could not stop the embedded server since you are running without it");
      }
   }

   virtual void usage() const
   {
      log_.plain(applName_, std::string("usage: ") + applName_ + "\n with the following attributes:\n");
      log_.plain(applName_, "-embeddedServer false: [default] an external xmlBlaster will be needed");
      log_.plain(applName_, "-embeddedServer true :           an internal xmlBlaster will be used, stop all external xmlBlaster");
      log_.plain(applName_, "-embeddedServer.cmdLine : defaults to \"/dev/null\"");
      log_.plain(applName_, "-embeddedServer.jvmArgs : defaults to \"\"");
      log_.plain(applName_, "");
      log_.plain(applName_, std::string("for example: ") + applName_ + " -embeddedServer true -embeddedServer.cmdLine \"call -true\" -embbededServer.jvmArgs \"-Dwhatever \"");
   }

};


}}} // namespace


#endif



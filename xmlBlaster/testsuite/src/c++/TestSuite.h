/*-----------------------------------------------------------------------------
Name:      TestSuite.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

#ifndef _TESTSUITE_H
#define _TESTSUITE_H

#include <client/XmlBlasterAccess.h>
#include <util/EmbeddedServer.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/Log.h>
#include <util/PlatformUtils.hpp>
#include <util/thread/ThreadImpl.h>
#include <util/Timestamp.h>
#include <boost/lexical_cast.hpp>

using boost::lexical_cast;
using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster;

namespace org { namespace xmlBlaster { namespace test {

template <class T> 
void assertEquals(Log& log, const string& who, const T& should, const T& is, const string& txt)
{
   if (should != is) {
      log.error(who, txt + " FAILED: value is " + lexical_cast<string>(is) + "' but should be '" + lexical_cast<string>(should) + "'");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}

template <class T> 
void assertDifferes(Log& log, const string& who, const T& should, const T& is, const string& txt)
{
   if (should == is) {
      log.error(who, txt + " FAILED: value is " + lexical_cast<string>(is) + "' in both cases but they should be different");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}


// specific implementation for the string since the lexical_cast from string to string causes problems.

void assertEquals(Log& log, const string& who, const string& should, const string& is, const string& txt)
{
   if (should != is) {
      log.error(who, txt + " FAILED: value is " + is + "' but should be '" + should + "'");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}

void assertDifferes(Log& log, const string& who, const string& should, const string& is, const string& txt)
{
   if (should == is) {
      log.error(who, txt + " FAILED: value is " + is + "' for both cases but they should be different");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}



class TestSuite
{
protected:
   string           ME;
   string           applName_;
   Global&          global_;
   Log&             log_;
   bool             useEmbeddedServer_;
   XmlBlasterAccess connection_;
   EmbeddedServer*  embeddedServer_;
   bool             needsHelp_;

public:

   TestSuite(int args, char ** argv, const string& name) 
      : ME(name), 
        applName_(ME), 
        global_(Global::getInstance().initialize(args, argv)), 
        log_(global_.getLog("test")), 
        connection_(global_)
   {
      needsHelp_ = false;
      for (int ii=0; ii<args; ii++) {
         if (strcmp(argv[ii], "-?")==0 || strcmp(argv[ii], "-h")==0 || strcmp(argv[ii], "-help")==0) {
            needsHelp_ = true;
            break;
         }
      }

      XMLPlatformUtils::Initialize();
      embeddedServer_    = NULL;
      useEmbeddedServer_ = global_.getProperty().getBoolProperty("embeddedServer", false);
      if (useEmbeddedServer_) log_.info(ME, "the embedded server is switched ON (you could switch it off with '-embeddedServer false' on the command line)");
      else {
         log_.warn(ME, "the embedded server is switched OFF (you will need an external xmlBlaster running)");
         Thread::sleep(2000);
     }
     if (useEmbeddedServer_) {
        embeddedServer_ = new EmbeddedServer(global_, "", "", &connection_);
        embeddedServer_->start();
        Thread::sleepSecs(5); // let the xmlBlaster server start ...
     }
   }

   virtual ~TestSuite()
   {
      cout << "destructor for TestSuite invoked" << endl;
      delete embeddedServer_;
   }

   virtual void setUp()
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
      log_.plain(applName_, string("usage: ") + applName_ + "\n with the following attributes:\n");
      log_.plain(applName_, "-embeddedServer false: [default] an external xmlBlaster will be needed");
      log_.plain(applName_, "-embeddedServer true :           an internal xmlBlaster will be used, stop all external xmlBlaster");
   }
};


}}} // namespace


#endif



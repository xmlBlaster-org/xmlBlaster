/*-----------------------------------------------------------------------------
Name:      TestEmbeddedServer.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing the Timeout Features
-----------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/Log.h>
#include <util/PlatformUtils.hpp>
#include <util/Timestamp.h>
#include <util/thread/Thread.h>
#include <boost/lexical_cast.hpp>

/**
 *
 */

using boost::lexical_cast;
using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::thread;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;
using namespace org::xmlBlaster;

class TestEmbeddedServer : public Thread
{
private:
   string            ME;
   Global&           global_;
   Log&              log_;
   XmlBlasterAccess* connection_;
   bool&             doStop_;
   Mutex             mutex_;
   Condition*        condition_;
   string            name_;
public:
   TestEmbeddedServer(Global& glob, const string name, bool& doStop, Condition* condition=NULL) 
      : ME("TestEmbeddedServer"), 
        global_(glob), 
	log_(glob.getLog()), 
	doStop_(doStop),
	mutex_(), 
	condition_(condition), 
	name_(name)
   {
      connection_ = NULL;
      doStop_ = false;
   }

   virtual ~TestEmbeddedServer()
   {
      delete connection_;
   }

   void starter()
   {  while (!doStop_) {
         system("java org.xmlBlaster.Main");
	 if (!condition_) return;
         Lock lock(mutex_);
         condition_->wait(lock, 1200000L);  // wait maximum 20 minutes
      }
   }

   void killer()
   {
      while (!doStop_) {
         Thread::sleep(30000);
	 if (!connection_) connection_ = new XmlBlasterAccess(global_);
	 ConnectQos connQos(global_, "killer", "secret");
	 connection_->connect(connQos, NULL);

	 PublishKey key(global_);
	 key.setOid("__cmd:?exit=-1");
	 PublishQos qos(global_);
	 MessageUnit msgUnit(key, "", qos);
	 connection_->publish(msgUnit);
	 delete connection_;
	 connection_ = NULL;
	 Thread::sleep(20000);
	 if (condition_) condition_->notify();
      }
   }

   void run()
   {
      if ( name_ == "killer" ) killer();
      else if ( name_ == "starter") starter();
      else {
         bool doStop = false;
         Condition condition;
	 TestEmbeddedServer serverStarter(global_, "starter", doStop, &condition);
	 TestEmbeddedServer serverKiller(global_, "killer", doStop, &condition);
         serverStarter.start();
	 serverKiller.start();
	 Thread::sleepSecs(180);
	 doStop = true;
	 serverStarter.join();
	 serverKiller.join();
      }
   }
};


int main(int args, char *argv[])
{
//   ServerThread* server = new ServerThread("java org.xmlBlaster.Main");
//   server->start();
   XMLPlatformUtils::Initialize();
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);
  
   bool doStop = false;
   TestEmbeddedServer server(glob, "main", doStop, NULL);
   server.start();
   server.join();

   return 0;
}

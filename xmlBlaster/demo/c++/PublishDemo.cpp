// NOTE: UNDER CONSTRUCTION Marcel 2004-01-16

/*-----------------------------------------------------------------------------
Name:      PublishDemo.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Little demo to show how a publish is done
-----------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <util/Global.h>
#include <util/lexical_cast.h>
#include <iostream>

using namespace std;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

class PublishDemo
{
private:
   string           ME;
   Global&          global_;
   Log&             log_;
   XmlBlasterAccess connection_;
	bool interactive;
	bool oneway;
	long sleep;
	int numPublish;
	string oid;
	string clientTags;
	string contentStr;
	PriorityEnum priority;
	bool persistent;
	long lifeTime;
	bool forceDestroy;
	bool readonly;
	long destroyDelay;
	bool createDomEntry;
	long historyMaxMsg;
	bool forceQueuing;
	bool subscribable;
	string destination;
	bool doErase;
	bool disconnect;
	bool eraseTailback;
	int contentSize;
	bool eraseForceDestroy;

public:
   PublishDemo(Global& glob) 
      : ME("PublishDemo"), 
        global_(glob), 
        log_(glob.getLog("demo")),
        connection_(global_)
   {
		initEnvironment();
		run();
	}

	void run() 
	{
		connect();
		publish();
		erase();
      connection_.disconnect(DisconnectQos(global_));
   }

   void initEnvironment();

   void connect();

   void publish();

   void erase()
   {
      EraseKey key(global_);
      key.setOid("c++-demo");
      EraseQos qos(global_);
      connection_.erase(key, qos);
	}
};

void PublishDemo::initEnvironment()
{
	interactive = global_.getProperty().get("interactive", true);
	oneway = global_.getProperty().get("oneway", false);
	sleep = global_.getProperty().get("sleep", 1000L);
	numPublish = global_.getProperty().get("numPublish", 1);
	oid = global_.getProperty().get("oid", string("Hello"));
	clientTags = global_.getProperty().get("clientTags", "<org.xmlBlaster><demo-%counter/></org.xmlBlaster>");
	contentStr = global_.getProperty().get("content", "Hi-%counter");
	priority = int2Priority(global_.getProperty().get("priority", NORM_PRIORITY));
	persistent = global_.getProperty().get("persistent", true);
	lifeTime = global_.getProperty().get("lifeTime", -1L);
	forceDestroy = global_.getProperty().get("forceDestroy", false);
	readonly = global_.getProperty().get("readonly", false);
	destroyDelay = global_.getProperty().get("destroyDelay", 60000L);
	createDomEntry = global_.getProperty().get("createDomEntry", true);
	historyMaxMsg = global_.getProperty().get("queue/history/maxEntries", -1L);
	forceQueuing = global_.getProperty().get("forceQueuing", true);
	subscribable = global_.getProperty().get("subscribable", true);
	destination = global_.getProperty().get("destination", "");
	doErase = global_.getProperty().get("doErase", true);
	disconnect = global_.getProperty().get("disconnect", true);
	eraseTailback = global_.getProperty().get("eraseTailback", false);
	contentSize = global_.getProperty().get("contentSize", -1); // 2000000);
	eraseForceDestroy = global_.getProperty().get("erase.forceDestroy", false);

	//Needs to be ported similar to Java
   //map clientPropertyMap = glob.getProperty().get("clientProperty", (map)null);

   if (historyMaxMsg < 1 && !global_.getProperty().propertyExists("destroyDelay"))
      destroyDelay = 24L*60L*60L*1000L; // Increase destroyDelay to one day if no history queue is used

   log_.info(ME, "Used settings are:");
   log_.info(ME, "   -interactive    " + lexical_cast<string>(interactive));
   log_.info(ME, "   -sleep          " + lexical_cast<string>(sleep)); // org.jutils.time.TimeHelper.millisToNice(sleep));
   log_.info(ME, "   -oneway         " + lexical_cast<string>(oneway));
   log_.info(ME, "   -doErase        " + lexical_cast<string>(doErase));
   log_.info(ME, "   -disconnect     " + lexical_cast<string>(disconnect));
   log_.info(ME, "   -eraseTailback  " + lexical_cast<string>(eraseTailback));
   log_.info(ME, " Pub/Sub settings");
   log_.info(ME, "   -numPublish     " + lexical_cast<string>(numPublish));
   log_.info(ME, "   -oid            " + lexical_cast<string>(oid));
   log_.info(ME, "   -clientTags     " + clientTags);
   if (contentSize >= 0) {
      log_.info(ME, "   -content        [generated]");
      log_.info(ME, "   -contentSize    " + lexical_cast<string>(contentSize));
   }
   else {
      log_.info(ME, "   -content        " + contentStr);
      log_.info(ME, "   -contentSize    " + lexical_cast<string>(contentStr.length()));
   }
   log_.info(ME, "   -priority       " + lexical_cast<string>(priority));
   log_.info(ME, "   -persistent     " + lexical_cast<string>(persistent));
   log_.info(ME, "   -lifeTime       " + lexical_cast<string>(lifeTime)); // org.jutils.time.TimeHelper.millisToNice(lifeTime));
   log_.info(ME, "   -forceDestroy   " + lexical_cast<string>(forceDestroy));
   //if (clientPropertyMap != null) {
   //   Iterator it = clientPropertyMap.keySet().iterator();
   //   while (it.hasNext()) {
   //      String key = (String)it.next();
   //      log_.info(ME, "   -clientProperty["+key+"]   " + clientPropertyMap.get(key).toString());
   //   }
   //}
   //else {
   //   log_.info(ME, "   -clientProperty[]   ");
   //}
   log_.info(ME, " Topic settings");
   log_.info(ME, "   -readonly       " + lexical_cast<string>(readonly));
   log_.info(ME, "   -destroyDelay   " + lexical_cast<string>(destroyDelay)); // org.jutils.time.TimeHelper.millisToNice(destroyDelay));
   log_.info(ME, "   -createDomEntry " + lexical_cast<string>(createDomEntry));
   log_.info(ME, "   -queue/history/maxEntries " + lexical_cast<string>(historyMaxMsg));
   log_.info(ME, " PtP settings");
   log_.info(ME, "   -subscribable  " + lexical_cast<string>(subscribable));
   log_.info(ME, "   -forceQueuing   " + lexical_cast<string>(forceQueuing));
   log_.info(ME, "   -destination    " + destination);
   log_.info(ME, " Erase settings");
   log_.info(ME, "   -erase.forceDestroy " + lexical_cast<string>(eraseForceDestroy));
   log_.info(ME, "For more info please read:");
   log_.info(ME, "   http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html");
}

void PublishDemo::connect()
{
   ConnectQos connQos(global_);
   log_.trace(ME, string("connecting to xmlBlaster. Connect qos: ") + connQos.toXml());
   ConnectReturnQos retQos = connection_.connect(connQos, NULL); // no callback
   log_.trace(ME, "successfully connected to xmlBlaster. Return qos: " + retQos.toXml());
}

void PublishDemo::publish()
{
	for(int i=0; i<numPublish; i++) {
   
      if (interactive) {
         log_.info(ME, "Hit a key to publish '" + oid + "' #" + lexical_cast<string>(i+1) + "/" + lexical_cast<string>(numPublish));
			char c;
			cin >> c;
      }
      else {
         try {
            org::xmlBlaster::util::thread::Thread::sleepSecs(1);
         }
         catch(XmlBlasterException e) {
            log_.error(ME, e.toXml());
         }

         if (sleep > 0) {
	         try {
	            org::xmlBlaster::util::thread::Thread::sleep(sleep);
	         }
	         catch(XmlBlasterException e) {
	            log_.error(ME, e.toXml());
	         }
         }
         log_.info(ME, "Publish '" + oid + "' #" + lexical_cast<string>(i+1) + "/" + lexical_cast<string>(numPublish));
      }

		PublishKey key(global_, oid, "text/xml", "1.0");
	   key.setClientTags(clientTags);
	   PublishQos pq(global_);
      pq.setPriority(priority);
      pq.setPersistent(persistent);
      pq.setLifeTime(lifeTime);
      //pq.setForceDestroy(forceDestroy);
      //pq.setSubscribable(subscribable);
		/*
            if (clientPropertyMap != null) {
               Iterator it = clientPropertyMap.keySet().iterator();
               while (it.hasNext()) {
                  String key = (String)it.next();
                  pq.addClientProperty(key, clientPropertyMap.get(key).toString());
               }
               //Example for a typed property:
               //pq.getData().addClientProperty("ALONG", (new Long(12)));
            }
            
            if (i == 0) {
               TopicProperty topicProperty = new TopicProperty(glob);
               topicProperty.setDestroyDelay(destroyDelay);
               topicProperty.setCreateDomEntry(createDomEntry);
               topicProperty.setReadonly(readonly);
               if (historyMaxMsg >= 0L) {
                  HistoryQueueProperty prop = new HistoryQueueProperty(this.glob, null);
                  prop.setMaxEntries(historyMaxMsg);
                  topicProperty.setHistoryQueueProperty(prop);
               }
               pq.setTopicProperty(topicProperty);
               log.info(ME, "Added TopicProperty on first publish: " + topicProperty.toXml());
            }
            
            if (destination != null) {
               Destination dest = new Destination(glob, new SessionName(glob, destination));
               dest.forceQueuing(forceQueuing);
               pq.addDestination(dest);
            }
		*/


	   MessageUnit msgUnit(key, contentStr, pq);
	   log_.trace(ME, string("published message unit: ") + msgUnit.toXml());
	   PublishReturnQos tmp = connection_.publish(msgUnit);
	   log_.trace(ME, string("publish return qos: ") + tmp.toXml());
	}
}

static void usage(Log& log) 
{
   log.info("PublishDemo usage:", Global::usage());
   log.info("PublishDemo", "Plus the following additional command line arguments:");
   log.info("PublishDemo", " -h (for help: this command)");
   log.info("PublishDemo", " -numOfRuns (int): the number of publishes which have to be done");
   log.info("PublishDemo", " -publishDelay (ms): the delay to wait between each publish. If negative (default) it does not wait");
   exit(0);
}


/**
 * Try
 * <pre>
 *   java TestSubXPath -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);
      Log& log  = glob.getLog("demo");

      if (glob.wantsHelp()) {
         usage(log);
      }

      int numOfRuns     = glob.getProperty().getIntProperty("numOfRuns", 10);
      long publishDelay = glob.getProperty().getIntProperty("publishDelay", -1L);
      PublishDemo demo(glob);
		/*
      demo->connect();
      for (int i=0; i < numOfRuns; i++) {
         demo->publish();
         if (publishDelay > 0) org::xmlBlaster::util::thread::Thread::sleep(publishDelay);
      }
      demo->erase();
      delete demo;
      demo = NULL;
		*/
   }
   catch (XmlBlasterException& ex) {
      std::cout << ex.toXml() << std::endl;
   }
   catch (bad_exception& ex) {
      cout << "bad_exception: " << ex.what() << endl;
   }
   catch (exception& ex) {
      cout << " exception: " << ex.what() << endl;
   }
   catch (string& ex) {
      cout << "string: " << ex << endl;
   }
   catch (char* ex) {
      cout << "char* :  " << ex << endl;
   }
   catch (...) {
      cout << "unknown exception occured" << endl;
      XmlBlasterException e(INTERNAL_UNKNOWN, "main", "main thread");
      cout << e.toXml() << endl;
   }

   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   }
   catch (...) {
      cout << "unknown exception occured in fini()" << endl;
      XmlBlasterException e(INTERNAL_UNKNOWN, "main", "main thread");
      cout << e.toXml() << endl;
   }

   return 0;
}

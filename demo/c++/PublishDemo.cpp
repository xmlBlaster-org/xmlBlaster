/*-----------------------------------------------------------------------------
Name:      PublishDemo.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Little demo to show how a publish is done
-----------------------------------------------------------------------------*/

#include <client/XmlBlasterAccess.h>
#include <util/Global.h>
#include <util/lexical_cast.h>
#include <util/qos/ClientProperty.h>
#include <authentication/SecurityQos.h>
#include <iostream>
#include <fstream>
#include <map>

using namespace std;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::util::qos::storage;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

static unsigned long filesize(ifstream &ins)
{
   unsigned long s,e,c;
   c = ins.tellg();        // save current file position
   ins.seekg(0, ios::end); // position at end
   e = ins.tellg();
   ins.seekg(0, ios::beg); // position at beginning
   s = ins.tellg();
   ins.seekg(c);           // restore file position
   return e-s;
}

static int fileRead(string &fn, string &content)
{
   unsigned char *buf;
   ifstream ins(fn.c_str(), ios_base::binary);
   if (!ins.is_open()) return -1;
   int   fs   = filesize(ins);
   buf  = new unsigned char [fs+1];
   buf[fs]    = 0; // so we can assign to string
   ins.read((char *)buf,fs);
   ins.close();
   content = (char *)buf;
   delete [] buf;
   return fs;
}


class PublishDemo
{
private:
   string ME;
   Global& global_;
   I_Log& log_;
   char ptr[2];
   XmlBlasterAccess connection_;
   bool interactive;
   bool oneway;
   long sleep;
   int numPublish;
   string oid;
   string domain;
   string clientTags;
   string contentStr;
   string contentFile;
   PriorityEnum priority;
   bool persistent;
   long lifeTime;
   bool forceUpdate;
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
   QosData::ClientPropertyMap clientPropertyMap;

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
      if (doErase) {
         if (interactive) {
            string outStr = "Hit 'e' to erase topic '" + oid + "' ('q' to exit without erase) >> "; 
            string ret = org::xmlBlaster::util::waitOnKeyboardHit(outStr);
            if (ret == "q") return;
         }
         log_.info(ME, "Erasing topic '" + oid + "'");
         EraseKey key(global_);
         key.setOid(oid);
         EraseQos eq(global_);
         eq.setForceDestroy(eraseForceDestroy);
         connection_.erase(key, eq);
      }
   }
};

void PublishDemo::initEnvironment()
{
   interactive = global_.getProperty().get("interactive", true);
   oneway = global_.getProperty().get("oneway", false);
   sleep = global_.getProperty().get("sleep", 1000L);
   numPublish = global_.getProperty().get("numPublish", 1);
   oid = global_.getProperty().get("oid", string("Hello"));
   domain = global_.getProperty().get("domain", string(""));
   clientTags = global_.getProperty().get("clientTags", ""); // "<org.xmlBlaster><demo-%counter/></org.xmlBlaster>");
   contentStr = global_.getProperty().get("content", "Hi-%counter");
   contentFile = global_.getProperty().get("contentFile", "");
   priority = int2Priority(global_.getProperty().get("priority", NORM_PRIORITY));
   persistent = global_.getProperty().get("persistent", true);
   lifeTime = global_.getProperty().get("lifeTime", -1L);
   forceUpdate = global_.getProperty().get("forceUpdate", true);
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

   //TODO: Needs to be ported similar to Java
   //map<std::string,std::string> clientPropertyMap = global_.getProperty().get("clientProperty", map<std::string,std::string>());
   string clientPropertyKey = global_.getProperty().get("clientProperty.key", string(""));
   string clientPropertyValue = global_.getProperty().get("clientProperty.value", string(""));
   string clientPropertyEncoding = global_.getProperty().get("clientProperty.encoding", ""); // Force to Constants::ENCODING_BASE64="base64"
   string clientPropertyCharset = global_.getProperty().get("clientProperty.charset", ""); // Force to e.g. "windows-1252"
   string clientPropertyType = global_.getProperty().get("clientProperty.type", ""); // Date type, see Constants::TYPE_DOUBLE, Constants::TYPE_STRING etc
   if (clientPropertyKey != "") {
      ClientProperty cp(clientPropertyKey, clientPropertyValue, clientPropertyType, clientPropertyEncoding);
      if (clientPropertyCharset != "") cp.setCharset(clientPropertyCharset);
      //
      // Returns "en_US.UTF-8" on Linux and "English_United States.1252" on WinXP
      //char *p = setlocale(LC_CTYPE, "");
      //log_.info(ME, "setlocale CTYPE returns: " + string(p));
      // But java (server on Linux or Windows) can't handle "English_United States.1252" or "1252": java.io.UnsupportedEncodingException: 1252
      // but it can handle conversion from "windows-1252" to "UTF-8"
      // Further, java does: UnsupportedEncodingException: en_US.UTF-8
      // but likes "UTF-8"
      //What else instead of setlocal() could we use for automatic charset detection of this C++ client (which is compatible to Java used names)?
      clientPropertyMap.insert(QosData::ClientPropertyMap::value_type(clientPropertyKey, cp));
   }

   if (historyMaxMsg < 1 && !global_.getProperty().propertyExists("destroyDelay"))
      destroyDelay = 24L*60L*60L*1000L; // Increase destroyDelay to one day if no history queue is used

   log_.info(ME, "You can use for example '-session.name publisher/1 -passwd secret' to pass your credentials");
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
   log_.info(ME, "   -domain         " + lexical_cast<string>(domain));
   log_.info(ME, "   -clientTags     " + clientTags);
   if (contentSize >= 0) {
      log_.info(ME, "   -content        [generated]");
      log_.info(ME, "   -contentSize    " + lexical_cast<string>(contentSize));
   }
   else if (contentFile.size() > 0) {
      log_.info(ME, "   -contentFile    " + contentFile);
   }
   else {
      log_.info(ME, "   -content        " + contentStr);
      log_.info(ME, "   -contentSize    " + lexical_cast<string>(contentStr.length()));
   }
   log_.info(ME, "   -priority       " + lexical_cast<string>(priority));
   log_.info(ME, "   -persistent     " + lexical_cast<string>(persistent));
   log_.info(ME, "   -lifeTime       " + lexical_cast<string>(lifeTime)); // org.jutils.time.TimeHelper.millisToNice(lifeTime));
   log_.info(ME, "   -forceUpdate    " + lexical_cast<string>(forceUpdate));
   log_.info(ME, "   -forceDestroy   " + lexical_cast<string>(forceDestroy));
   if (clientPropertyMap.size() > 0) {
      QosData::ClientPropertyMap::const_iterator mi;
      for (mi=clientPropertyMap.begin(); mi!=clientPropertyMap.end(); ++mi) {
         log_.info(ME, "   -clientProperty["+mi->first+"]   " + mi->second.getStringValue());
      }
   }
   else {
      log_.info(ME, "   -clientProperty[]   ");
   }
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
   //org::xmlBlaster::authentication::SecurityQos sec(global_, "jack", "secret", "htpasswd,1.0");
   //connQos.setSecurityQos(sec);
   log_.trace(ME, string("connecting to xmlBlaster. Connect qos: ") + connQos.toXml());
   ConnectReturnQos retQos = connection_.connect(connQos, NULL); // no callback
   log_.trace(ME, "successfully connected to " + connection_.getServerNodeId() + ". Return qos: " + retQos.toXml());
}

void PublishDemo::publish()
{
   for(int i=0; i<numPublish; i++) {
   
      if (interactive) {
         std::cout << "Hit a key to publish '" + oid + "' #" + lexical_cast<string>(i+1) + "/" + lexical_cast<string>(numPublish) + " ('b' to break) >> ";
         std::cin.read(ptr,1);
         if (*ptr == 'b') break;
      }
      else {
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
      if (domain != "")  key.setDomain(domain);
      if (i==0) log_.info(ME, "PublishKey: " + key.toXml());

      PublishQos pq(global_);
      pq.setPriority(priority);
      pq.setPersistent(persistent);
      pq.setLifeTime(lifeTime);
      pq.setForceUpdate(forceUpdate);
      pq.setForceDestroy(forceDestroy);
      pq.setSubscribable(subscribable);
      if (clientPropertyMap.size() > 0) {
         pq.setClientProperties(clientPropertyMap);
         //This is the correct way for a typed property:
         pq.addClientProperty("ALONG", long(12L));
      }
      
      if (i == 0) {
         TopicProperty topicProperty(global_);
         topicProperty.setDestroyDelay(destroyDelay);
         topicProperty.setCreateDomEntry(createDomEntry);
         topicProperty.setReadonly(readonly);
         if (historyMaxMsg >= 0L) {
            HistoryQueueProperty prop(global_, "");
            prop.setMaxEntries(historyMaxMsg);
            topicProperty.setHistoryQueueProperty(prop);
         }
         pq.setTopicProperty(topicProperty);
         log_.info(ME, "Added TopicProperty on first publish: " + topicProperty.toXml());
      }
      
      if (destination != "") {
         SessionName sessionName(global_, destination);
         Destination dest(global_, sessionName);
         dest.forceQueuing(forceQueuing);
         pq.addDestination(dest);
      }

      log_.info(ME, "mapSize=" + lexical_cast<string>(clientPropertyMap.size()) + " PublishQos: " + pq.toXml());

      string contentTmp = contentStr;
      if (contentSize >= 0) {
         contentTmp = "";
         for (int j=0; j<contentSize; j++)
            contentTmp += "X";
      }
      else if (contentFile.size() > 0) {
         fileRead(contentFile, contentTmp);
      }
      else {
         contentTmp = StringTrim::replaceAll(contentTmp, "%counter", lexical_cast<string>(i+1));
      }

      MessageUnit msgUnit(key, contentTmp, pq);
      if (oneway) {
         log_.trace(ME, string("publishOneway() message unit: ") + msgUnit.toXml());
         vector<MessageUnit> msgUnitArr;
         msgUnitArr.push_back(msgUnit);
         connection_.publishOneway(msgUnitArr);
         log_.trace(ME, "publishOneway() done");
      }
      else {
         log_.trace(ME, string("publish() message unit: ") + msgUnit.toXml());
         PublishReturnQos tmp = connection_.publish(msgUnit);
         log_.trace(ME, string("publish return qos: ") + tmp.toXml());
      }
   }
}


static void usage(I_Log& log) 
{
   log.plain("PublishDemo usage:", Global::usage());
   string str = "\nPlus many more additional command line arguments:";
   str += "\n -numPublish (int): the number of publishes which have to be done";
   str += "\n -sleep (ms): the delay to wait between each publish. If negative (default) it does not wait";
   str += "\n ...";
   str += "\nExample:\n";
   str += "   PublishDemo -trace true -numPublish 1000\n";
   str += "   PublishDemo -destination joe -oid Hello -content 'Hi joe'\n";
   log.plain("PublishDemo", str);
   exit(0);
}


/**
 * Try
 * <pre>
 *   PublishDemo -help
 * </pre>
 * for usage help
 * <p />Example:
 * PublishDemo -oid __sys__remoteProperties -clientProperty.key "MultiByte" -clientProperty.value "With 'Ä' multibyte" -clientProperty.charset windows-1252 -clientProperty.encoding base64
 */
int main(int args, char ** argv)
{
   try {
      org::xmlBlaster::util::Object_Lifetime_Manager::init();
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);
      I_Log& log  = glob.getLog("demo");

      if (glob.wantsHelp()) {
         usage(log);
      }

      PublishDemo demo(glob);
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

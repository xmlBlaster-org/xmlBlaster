/*-----------------------------------------------------------------------------
Name:      SubscribeDemo.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Little demo to show how a subscribe is done
-----------------------------------------------------------------------------*/
#include <client/XmlBlasterAccess.h>
#include <util/Global.h>
#include <util/lexical_cast.h>
#include <iostream>
#include <stdexcept>

namespace std {
  class UpdateException : public exception {
  public:
    explicit UpdateException(const string&  what_arg );
  };
}

using namespace std;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;

/**
 * This subscriber subscribes on the topic 'Hello' and dumps
 * the received messages. 
 * Please start the publisher demo
 * <code>
 * PublishDemo -numPublish 10
 * </code>
 * to receives some messages
 */
class SubscribeDemo : public I_Callback
{
private:
   string           ME;
   Global&          global_;
   I_Log&           log_;
   XmlBlasterAccess connection_;
   int updateCounter;
   char ptr[1];
   string subscriptionId;
   bool disconnect;
   bool interactive;
   bool interactiveUpdate;
   long updateSleep;
   string updateExceptionErrorCode;
   string updateExceptionMessage;
   string updateExceptionRuntime;
   string oid;
   //string domain;
   string xpath;
   bool multiSubscribe;
   bool persistentSubscribe;
   bool notifyOnErase;
   bool local;
   bool initialUpdate;
   bool updateOneway;
   bool wantContent;
   int historyNumUpdates;
   bool historyNewestFirst;
   string filterType;
   string filterVersion;
   string filterQuery;
   bool unSubscribe;
   int maxContentLength;

public:
   bool doContinue_;

   SubscribeDemo(Global& glob) 
      : ME("SubscribeDemo"), 
        global_(glob), 
        log_(glob.getLog("demo")),
        connection_(global_),
        updateCounter(0)
   {
      initEnvironment();
      doContinue_ = true;
      execute();
   }

   void execute()
   {
      connect();

      subscribe();
      
      log_.info(ME, "Please use PublishDemo to publish a message '"+oid+"', i'm waiting for updates ...");

      if (interactive) {
         org::xmlBlaster::util::thread::Thread::sleepSecs(1);
         bool stop = false;
         while (!stop) {
            std::cout << "(Enter 'q' to exit) >> ";
            std::cin.read(ptr,1);
            if (*ptr == 'q') stop = true;
         }
      }
      else {
         log_.plain(ME, "I will exit when the publisher destroys the topic ...");
         while (doContinue_) {
            org::xmlBlaster::util::thread::Thread::sleepSecs(2);
         }
      }
      
      unSubscribe_();
      
      disconnect_();
   }

   void initEnvironment()
   {
      disconnect = global_.getProperty().get("disconnect", true);
      interactive = global_.getProperty().get("interactive", true);
      interactiveUpdate = global_.getProperty().get("interactiveUpdate", false);
      updateSleep = global_.getProperty().get("updateSleep", 0L);
      updateExceptionErrorCode = global_.getProperty().get("updateException.errorCode", string(""));
      updateExceptionMessage = global_.getProperty().get("updateException.message", string(""));
      updateExceptionRuntime = global_.getProperty().get("updateException.runtime", string(""));
      oid = global_.getProperty().get("oid", "");
      //domain = global_.getProperty().get("domain", "");
      xpath = global_.getProperty().get("xpath", "");
      multiSubscribe = global_.getProperty().get("multiSubscribe", true);
      persistentSubscribe = global_.getProperty().get("persistentSubscribe", false);
      notifyOnErase = global_.getProperty().get("notifyOnErase", true);
      local = global_.getProperty().get("local", true);
      initialUpdate = global_.getProperty().get("initialUpdate", true);
      updateOneway = global_.getProperty().get("updateOneway", false);
      wantContent = global_.getProperty().get("wantContent", true);
      historyNumUpdates = global_.getProperty().get("historyNumUpdates", 1);
      historyNewestFirst = global_.getProperty().get("historyNewestFirst", true);
      filterType = global_.getProperty().get("filter.type", "GnuRegexFilter");// XPathFilter | ContentLenFilter
      filterVersion = global_.getProperty().get("filter.version", "1.0");
      filterQuery = global_.getProperty().get("filter.query", "");
      unSubscribe = global_.getProperty().get("unSubscribe", true);
      maxContentLength = global_.getProperty().get("maxContentLength", 250);

      if (oid == "" && xpath == "") {
         log_.warn(ME, "No -oid or -xpath given, we subscribe to oid='Hello'.");
         oid = "Hello";
      }

      if (updateSleep > 0L && interactiveUpdate == true) {
         log_.warn(ME, "You can't set 'updateSleep' and  'interactiveUpdate' simultaneous, we reset interactiveUpdate to false");
         interactiveUpdate = false;
      }

      if (updateExceptionErrorCode != "" && updateExceptionRuntime != "") {
         log_.warn(ME, "You can't throw a runtime and an XmlBlasterException simultaneous, please check your settings "
                        " -updateException.errorCode and -updateException.runtime");
         updateExceptionRuntime = "";
      }

      log_.info(ME, "Used settings are:");
      log_.info(ME, "   -interactive         " + lexical_cast<string>(interactive));
      log_.info(ME, "   -interactiveUpdate   " + lexical_cast<string>(interactiveUpdate));
      log_.info(ME, "   -updateSleep         " + lexical_cast<string>(updateSleep));
      log_.info(ME, "   -updateException.errorCode " + updateExceptionErrorCode);
      log_.info(ME, "   -updateException.message   " + updateExceptionMessage);
      log_.info(ME, "   -updateException.runtime   " + updateExceptionRuntime);
      log_.info(ME, "   -oid                 " + oid);
      //log_.info(ME, "   -domain            " + domain);
      log_.info(ME, "   -xpath               " + xpath);
      log_.info(ME, "   -multiSubscribe      " + lexical_cast<string>(multiSubscribe));
      log_.info(ME, "   -persistentSubscribe " + lexical_cast<string>(persistentSubscribe));
      log_.info(ME, "   -notifyOnErase       " + lexical_cast<string>(notifyOnErase));
      log_.info(ME, "   -local               " + lexical_cast<string>(local));
      log_.info(ME, "   -initialUpdate       " + lexical_cast<string>(initialUpdate));
      log_.info(ME, "   -updateOneway        " + lexical_cast<string>(updateOneway));
      log_.info(ME, "   -historyNumUpdates   " + lexical_cast<string>(historyNumUpdates));
      log_.info(ME, "   -historyNewestFirst  " + lexical_cast<string>(historyNewestFirst));
      log_.info(ME, "   -wantContent         " + lexical_cast<string>(wantContent));
      log_.info(ME, "   -unSubscribe         " + lexical_cast<string>(unSubscribe));
      log_.info(ME, "   -disconnect          " + lexical_cast<string>(disconnect));
      log_.info(ME, "   -filter.type         " + filterType);
      log_.info(ME, "   -filter.version      " + filterVersion);
      log_.info(ME, "   -filter.query        " + filterQuery);
      log_.info(ME, "For more info please read:");
      log_.info(ME, "   http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html");
   }

   void connect()
   {
      ConnectQos connQos(global_);
      log_.info(ME, string("connecting to xmlBlaster. Connect qos: ") + connQos.toXml());
      ConnectReturnQos retQos = connection_.connect(connQos, this);
      log_.trace(ME, "successfully connected to xmlBlaster. Return qos: " + retQos.toXml());
   }

   void subscribe()
   {
      SubscribeKey *sk = 0;
      string qStr = "";
      try {
         if (oid.length() > 0) {
            sk = new SubscribeKey(global_, oid);
            qStr = oid;
         }
         else if (xpath.length() > 0) {
            sk = new SubscribeKey(global_, xpath, Constants::XPATH);
            qStr = xpath;
         }
         //if (domain.length() > 0) {  // cluster routing information
         //   if (sk == 0) sk = new SubscribeKey(global_, "", Constants::D_O_M_A_I_N); // usually never
         //   sk->setDomain(domain);
         //   qStr = domain;
         //}
         SubscribeQos sq(global_);
         sq.setWantInitialUpdate(initialUpdate);
         sq.setWantUpdateOneway(updateOneway);
         sq.setMultiSubscribe(multiSubscribe);
         sq.setPersistent(persistentSubscribe);
         sq.setWantNotify(notifyOnErase);
         sq.setWantLocal(local);
         sq.setWantContent(wantContent);
         
         HistoryQos historyQos(global_);
         historyQos.setNumEntries(historyNumUpdates);
         historyQos.setNewestFirst(historyNewestFirst);
         sq.setHistoryQos(historyQos);

         if (filterQuery.length() > 0) {
            AccessFilterQos filter(global_, filterType, filterVersion, filterQuery);
            sq.addAccessFilter(filter);
         }

         log_.info(ME, "SubscribeKey=\n" + sk->toXml());
         log_.info(ME, "SubscribeQos=\n" + sq.toXml());

         if (interactive) {
            log_.info(ME, "Hit a key to subscribe '" + qStr + "'");
            std::cin.read(ptr,1);
         }

         SubscribeReturnQos srq = connection_.subscribe(*sk, sq);
         subscriptionId = srq.getSubscriptionId();

         log_.info(ME, "Subscribed on topic '" + ((oid.length() > 0) ? oid : xpath) +
                      "', got subscription id='" + srq.getSubscriptionId() + "'\n" + srq.toXml());
         if (log_.dump()) log_.dump("", "Subscribed: " + sk->toXml() + sq.toXml() + srq.toXml());
         delete sk;
      }
      catch (...) { // a finally would have been more appropriate
         delete sk;
         throw;
      }
   }

   void unSubscribe_()
   {
      if (unSubscribe) {
         if (interactive) {
            log_.info(ME, "Hit a key to unSubscribe");
            std::cin.read(ptr,1);
         }

         UnSubscribeKey uk(global_, subscriptionId);
         //if (domain.length() > 0)  // cluster routing information TODO!!!
         //   uk.setDomain(domain);
         UnSubscribeQos uq(global_);
         log_.info(ME, "UnSubscribeKey=\n" + uk.toXml());
         log_.info(ME, "UnSubscribeQos=\n" + uq.toXml());
         vector<UnSubscribeReturnQos> urqArr = connection_.unSubscribe(uk, uq);
         log_.info(ME, "UnSubscribe on " + lexical_cast<string>(urqArr.size()) + " subscriptions done");
      }
   }

   void disconnect_()
   {
      if (disconnect) {
         DisconnectQos dq(global_);
         connection_.disconnect(dq);
         log_.info(ME, "Disconnected");
      }
   }

   /**
    * Here we receive the asynchronous callback messages from the xmlBlaster server. 
    */
   string update(const string& sessionId, UpdateKey& updateKey, const unsigned char *content, long contentSize, UpdateQos& updateQos)
   {
      //const Global& global_ = updateKey.getGlobal();
      ++updateCounter;

      //NOTE: subscribe("anotherDummy");  -> subscribe in update does not work
      //      with single threaded 'mico' or SOCKET protocol

      log_.info(ME, "Receiving update #" + lexical_cast<string>(updateCounter) + " of a message, secret sessionId=" + sessionId + " ...");
      log_.plain(ME,"============= START #" + lexical_cast<string>(updateCounter) + " '" + updateKey.getOid() + "' =======================");
      

      string contentStr((char*)content, (char*)(content)+contentSize);
      log_.plain(ME, "<xmlBlaster>");
      log_.plain(ME, updateKey.toXml("  "));
      log_.plain(ME, " <content size='" + lexical_cast<string>(contentSize) + "'>");
      if (contentSize < maxContentLength)
         log_.plain(ME, contentStr);
      else
         log_.plain(ME, contentStr.substr(0, maxContentLength-5) + " ...");
      log_.plain(ME, " </content>");
      log_.plain(ME, updateQos.toXml("  "));
      log_.plain(ME, "</xmlBlaster>");
      log_.plain(ME,"============= END #" + lexical_cast<string>(updateCounter) + " '" + updateKey.getOid() + "' =========================");
      log_.plain(ME,"");

      // Dump the ClientProperties decoded (the above dump may contain Base64 encoding):
      const QosData::ClientPropertyMap& propMap = updateQos.getClientProperties();
      QosData::ClientPropertyMap::const_iterator mi;
      for (mi=propMap.begin(); mi!=propMap.end(); ++mi) {
         const ClientProperty& clientProperty = mi->second;
         if (clientProperty.isBase64()) {
            log_.info(ME, "ClientProperty decoded: "+mi->first+"=" + clientProperty.getStringValue());
         }
      }
      // Examples for direct access:
      if (updateQos.hasClientProperty(string("StringKey"))) {
         log_.info(ME, "ClientProperty BLA=" +updateQos.getClientProperty("BLA", string("MISSING VALUE?")));
      }
      if (updateQos.hasClientProperty(string("ALONG"))) {
         long aLongValue = updateQos.getClientProperty("ALONG", -1L);
         log_.info(ME, "ClientProperty ALONG=" + lexical_cast<string>(aLongValue));
      }
      
      if (updateSleep > 0L) {
         log_.info(ME, "Sleeping for " + lexical_cast<string>(updateSleep) + " millis in callback ...");
         org::xmlBlaster::util::thread::Thread::sleep(updateSleep);
         log_.info(ME, "Waking up.");
      }
      else if (interactiveUpdate) {
         log_.info(ME, "Hit a key to return from update() (we are blocking the server callback) ...");
         std::cin.read(ptr,1);
         log_.info(ME, "Returning update() - control goes back to server");
      }

      if (updateExceptionErrorCode != "") {
         log_.info(ME, "Throwing XmlBlasterException with errorCode='" + updateExceptionErrorCode + "' back to server ...");
         throw XmlBlasterException(updateExceptionErrorCode, ME, updateExceptionMessage); 
      }

      if (updateExceptionRuntime != "") {
         log_.info(ME, "Throwing RuntimeException '" + updateExceptionRuntime + "'");
         //throw UpdateException(updateExceptionRuntime);
         throw logic_error(updateExceptionRuntime);
      }

      if (updateQos.getState() == "ERASED" ) {
         doContinue_ = false;
         log_.info(ME, "Received topic '" + updateKey.getOid() + "' ERASED message, stopping myself ...");
         return "";
      }

      return "";
   }
};

/**
 * Try
 * <pre>
 *   SubscribeDemo -help
 * </pre>
 * for usage help
 */
int main(int args, char ** argv)
{
   org::xmlBlaster::util::Object_Lifetime_Manager::init();
   //TimestampFactory& tsFactory = TimestampFactory::getInstance();
   //Timestamp startStamp = tsFactory.getTimestamp();
   //std::cout << " start time: " << tsFactory.toXml(startStamp, "", true) << std::endl;

   try {
      Global& glob = Global::getInstance();
      glob.initialize(args, argv);

      if (glob.wantsHelp()) {
         glob.getLog().plain("", Global::usage());
         glob.getLog().plain("", "\nExample:\n");
         glob.getLog().plain("", "   SubscribeDemo -trace true -interactiveUpdate true\n");
         glob.getLog().plain("", "   SubscribeDemo -xpath '//key' -filter.query '^H.*'\n");
         org::xmlBlaster::util::Object_Lifetime_Manager::fini();
         return 1;
      }

      SubscribeDemo demo(glob);

      //Timestamp stopStamp = tsFactory.getTimestamp();
      //std::cout << " end time: " << tsFactory.toXml(stopStamp, "", true) << std::endl;
      //Timestamp diff = stopStamp - startStamp;
      //std::cout << " time used for demo: " << tsFactory.toXml(diff, "", true) << std::endl;
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

   catch (...)
   {
      cout << "unknown exception occured" << endl;
      XmlBlasterException e(INTERNAL_UNKNOWN, "main", "main thread");
      cout << e.toXml() << endl;
   }

   org::xmlBlaster::util::Object_Lifetime_Manager::fini();
   return 0;
}

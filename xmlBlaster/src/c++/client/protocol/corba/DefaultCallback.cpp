/*----------------------------------------------------------------------------
Name:      DefaultCallback.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default implementation of the POA_serverIdl::BlasterCallback.
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_PROTOCOL_CORBA_DEFAULTCALLBACK_C
#define _CLIENT_PROTOCOL_CORBA_DEFAULTCALLBACK_C

#include <client/protocol/corba/DefaultCallback.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::client;
using namespace org::xmlBlaster::client::protocol::corba;
using namespace org::xmlBlaster::client::qos;
using namespace org::xmlBlaster::client::key;


DefaultCallback::DefaultCallback(Global& global, const string &name, I_Callback *boss,
                /*BlasterCache*/ void* /*cache*/) 
:global_(global), log_(global.getLog("org.xmlBlaster.client.protocol.corba")), msgKeyFactory_(global), msgQosFactory_(global)
{
   boss_         = boss;
   loginName_    = name;
   // cache_ = cache;
   if (log_.call()) log_.call(me(),"Entering constructor with argument");
}

/**
 * This is the callback method invoked from the server
 * informing the client in an asynchronous mode about new messages.
 * <p />
 * You don't need to use this little method, but it nicely converts
 * the raw CORBA BlasterCallback.update() with raw Strings and arrays
 * in corresponding objects and calls for every received message
 * the I_Callback.update().
 * <p />
 * So you should implement in your client the I_Callback interface -
 * suppling the update() method.
 *
 * @param loginName        The name to whom the callback belongs
 * @param msgUnit      Contains a MessageUnit structs (your message)
 * @param qos              Quality of Service of the MessageUnit
 */
serverIdl::XmlTypeArr* DefaultCallback::update(const char* sessionId,
                       const serverIdl::MessageUnitArr& msgUnitArr) UPDATE_THROW_SPECIFIER
{
   serverIdl::XmlTypeArr *res = new serverIdl::XmlTypeArr(msgUnitArr.length());
   res->length(msgUnitArr.length());

   if (log_.call()) { log_.call(me(), "Receiving update of " + lexical_cast<std::string>(msgUnitArr.length()) + " message ..."); }
   
   if (msgUnitArr.length() == 0) {
      log_.warn(me(), "Entering update() with 0 messages");
      return res;
   }
   for (string::size_type i=0; i < msgUnitArr.length(); i++) {
      const serverIdl::MessageUnit &msgUnit = msgUnitArr[i];
      UpdateKey *updateKey = 0;
      UpdateQos *updateQos = 0;
      try {
         if (log_.dump()) {
            log_.dump(me(), string("update: the key: ") + string(msgUnit.xmlKey));
            log_.dump(me(), string("update: the qos: ") + string(msgUnit.qos));
         }
         updateKey = new UpdateKey(global_, msgKeyFactory_.readObject(string(msgUnit.xmlKey)));
         updateQos = new UpdateQos(global_, msgQosFactory_.readObject(string(msgUnit.qos)));
         // Now we know all about the received msg, dump it or do 
         // some checks
         if (log_.dump()) log_.dump("UpdateKey", string("\n") + updateKey->toXml());
         if (log_.dump()) {
            string msg = "\n";
            for (string::size_type j=0; j < msgUnit.content.length(); j++) 
               msg += (char)msgUnit.content[j];
            log_.dump("content", "Message received '" + msg + "' with size=" + lexical_cast<std::string>(msgUnit.content.length()));
         }
         if (log_.dump()) log_.dump("UpdateQos", "\n" + updateQos->toXml());
         if (log_.trace()) log_.trace(me(), "Received message [" + updateKey->getOid() + "] from publisher " + updateQos->getSender()->getAbsoluteName());

         //Checking whether the Update is for the Cache or for the boss
         //The boss should not be interested in cache updates
         bool forCache = false;
         //          if( cache_ != null ) {
         //             forCache = cache_.update(updateQos.getSubscriptionId(), 
         //                                      updateKey.toXml(), msgUnit.content);
         //          }
         string oneRes = "<qos><state id='OK'/></qos>";
         if (!forCache) {
            if (boss_) {
               int size = 0;
               size = msgUnit.content.length();
               const unsigned char *content = NULL;
               if (size > 0) content = (const unsigned char*)&msgUnit.content[0];
               if (log_.trace()) log_.trace(me(), "going to invoke client specific update");
               oneRes = boss_->update(sessionId, *updateKey, content, size, *updateQos); 
               // Call my boss
            }
            else log_.warn(me(), "can not update: no callback defined");
         }
         CORBA::String_var str = CORBA::string_dup(oneRes.c_str());
         (*res)[i] = str;
      } 
      catch (serverIdl::XmlBlasterException &e) {
         log_.error(me(), string(e.message) + " message is on error state: " + updateKey->toXml());
         string oneRes = "<qos><state id='ERROR'/></qos>";
         CORBA::String_var str = CORBA::string_dup(oneRes.c_str());
         (*res)[i] = str;
      }
      catch(...) {
         string tmp = "Exception caught in update() " + lexical_cast<std::string>(msgUnitArr.length()) + " messages are handled as not delivered";
         log_.error(me(), tmp);
         throw serverIdl::XmlBlasterException("user.update.error", "org.xmlBlaster.client", 
                                              "client update failed", "en",
                                              tmp.c_str(), "", "", "", "", 
                                              "", "");
      }

      delete updateKey;
      delete updateQos;
   } // for every message
   return res;
}

      /**
       * This is the oneway variant, not returning a value (no application level ACK). 
       * @see update()
       */
void DefaultCallback::updateOneway(const char* sessionId,
                      const serverIdl::MessageUnitArr& msgUnitArr) PING_THROW_SPECIFIER
{
   if (log_.call()) { log_.call(me(), "Receiving updateOneway of " + lexical_cast<std::string>(msgUnitArr.length()) + " message ..."); }
   
   if (msgUnitArr.length() == 0) {
      log_.warn(me(), "Entering updateOneway() with 0 messages");
      return;
   }

   for (string::size_type i=0; i < msgUnitArr.length(); i++) {
      UpdateKey *updateKey = 0;
      UpdateQos *updateQos = 0;
      try {
         const serverIdl::MessageUnit &msgUnit = msgUnitArr[i];
         try {
            updateKey = new UpdateKey(global_, msgKeyFactory_.readObject(string(msgUnit.xmlKey)));
            updateQos = new UpdateQos(global_, msgQosFactory_.readObject(string(msgUnit.qos)));
         } 
         catch (serverIdl::XmlBlasterException &e) {
            log_.error(me(), string(e.message) );
         }

         if (log_.trace()) log_.trace(me(), "Received oneway message [" + updateKey->getOid() + "] from publisher " + updateQos->getSender()->getAbsoluteName());

         if (boss_) {
            boss_->update(sessionId, *updateKey,
                           (const unsigned char*)&msgUnit.content[0], 
                           msgUnit.content.length(), *updateQos); 
         }
         else
            log_.warn(me(), "can not update: no callback defined");
      }
      catch (const exception& e) {
         log_.error(me(), string("Exception caught in updateOneway(), it is not transferred to server: ") + e.what());
      }
      catch(...) {
         log_.error(me(), "Exception caught in updateOneway(), it is not transferred to server");
      }

      delete updateKey;
      delete updateQos;
   } // for each message

} // updateOneway

/**
 * Check the callback server.
 * @see xmlBlaster.idl
 */
char* DefaultCallback::ping(const char *qos) PING_THROW_SPECIFIER
{
   if (log_.call()) log_.call(me(), "ping(" + string(qos) + ") ...");
   return CORBA::string_dup("");
} // ping


#endif

/*----------------------------------------------------------------------------
Name:      DefaultCallback.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default implementation of the POA_serverIdl::BlasterCallback.
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_DEFAULTCALLBACK_C
#define _CLIENT_DEFAULTCALLBACK_C

#include "DefaultCallback.h"

using namespace org::xmlBlaster;


DefaultCallback::DefaultCallback(const string &name, I_Callback *boss, 
                /*BlasterCache*/ void* /*cache*/) 
:log_() 
{
   boss_         = boss;
   loginName_    = name;
   // cache_ = cache;
   if (log_.CALL) log_.trace(me(),"Entering constructor with argument");
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
serverIdl::XmlTypeArr* 
DefaultCallback::update(const char* sessionId,
                        const serverIdl::MessageUnitArr& msgUnitArr) 
#ifdef ORBIX
            IT_THROW_DECL ((CORBA::SystemException))
#endif
{

   // typedef StringSequenceTmpl<CORBA::String_var> StringArr;
   // typedef TSeqVar<StringSequenceTmpl<CORBA::String_var> > StringArr_var;
   // typedef TSeqOut<StringSequenceTmpl<CORBA::String_var> > StringArr_out;
   // IDL: typedef sequence<string> StringArr;
   serverIdl::XmlTypeArr *res = new serverIdl::XmlTypeArr(msgUnitArr.length());
   res->length(msgUnitArr.length());

   if (log_.CALL) { log_.call(me(), "Receiving update of " + lexical_cast<string>(msgUnitArr.length()) + " message ..."); }
   
   if (msgUnitArr.length() == 0) {
      log_.warn(me(), "Entering update() with 0 messages");
      return res;
   }
   for (string::size_type i=0; i < msgUnitArr.length(); i++) {
      const serverIdl::MessageUnit &msgUnit = msgUnitArr[i];
      UpdateKey *updateKey = 0;
      UpdateQos *updateQos = 0;
      try {
         updateKey = new UpdateKey();
         updateKey->init(string(msgUnit.xmlKey));
         updateQos = new UpdateQos(string(msgUnit.qos));
         // Now we know all about the received msg, dump it or do 
         // some checks
         if (log_.DUMP) log_.dump("UpdateKey", string("\n") + updateKey->printOn());
         if (log_.DUMP) {
            string msg = "\n";
            for (string::size_type j=0; j < msgUnit.content.length(); j++) 
               msg += (char)msgUnit.content[j];
            log_.dump("content", "Message received '" + msg + "' with size=" + lexical_cast<string>(msgUnit.content.length()));
         }
         if (log_.DUMP) log_.dump("UpdateQos", "\n" + updateQos->printOn());
         if (log_.TRACE) log_.trace(me(), "Received message [" + updateKey->getUniqueKey() + "] from publisher " + updateQos->getSender());

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
               oneRes = boss_->update(sessionId, *updateKey,
                             (void*)&msgUnit.content[0], 
                             msgUnit.content.length(), *updateQos); 
               // Call my boss
            }
            else log_.warn(me(), "can not update: no callback defined");
         }
         CORBA::String_var str = CORBA::string_dup(oneRes.c_str());
         (*res)[i] = str;
      } 
      catch (serverIdl::XmlBlasterException &e) {
         log_.error(me(), string(e.reason) + " message is on error state: " + updateKey->printOn());
         string oneRes = "<qos><state id='ERROR'/></qos>";
         CORBA::String_var str = CORBA::string_dup(oneRes.c_str());
         (*res)[i] = str;
      }
      catch(...) {
         string tmp = "Exception caught in update() " + lexical_cast<string>(msgUnitArr.length()) + " messages are handled as not delivered";
         log_.error(me(), tmp);
         throw serverIdl::XmlBlasterException("UpdateFailed", tmp.c_str());
      }

      delete updateKey;
      delete updateQos;
   } // for every message

   return res; // res._retn();
}

      /**
       * This is the oneway variant, not returning a value (no application level ACK). 
       * @see update()
       */
void 
DefaultCallback::updateOneway(const char* sessionId,
                              const serverIdl::MessageUnitArr& msgUnitArr) 
#ifdef ORBIX
            IT_THROW_DECL ((CORBA::SystemException))
#endif
{
   if (log_.CALL) { log_.call(me(), "Receiving update of " + lexical_cast<string>(msgUnitArr.length()) + " message ..."); }
   
   if (msgUnitArr.length() == 0) {
      log_.warn(me(), "Entering update() with 0 messages");
      return;
   }

   for (string::size_type i=0; i < msgUnitArr.length(); i++) {
      UpdateKey *updateKey = 0;
      UpdateQos *updateQos = 0;
      try {
         const serverIdl::MessageUnit &msgUnit = msgUnitArr[i];
         try {
            updateKey = new UpdateKey();
            updateKey->init(string(msgUnit.xmlKey));
            updateQos = new UpdateQos(string(msgUnit.qos));
         } 
         catch (serverIdl::XmlBlasterException &e) {
            log_.error(me(), string(e.reason) );
         }

         if (log_.TRACE) log_.trace(me(), "Received message [" + updateKey->getUniqueKey() + "] from publisher " + updateQos->getSender());

         if (boss_) {
            boss_->update(sessionId, *updateKey,
                           (void*)&msgUnit.content[0], 
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
char*
DefaultCallback::ping(const char *qos) 
#ifdef ORBIX
            IT_THROW_DECL ((CORBA::SystemException))
#endif
{
   if (log_.CALL) log_.call(me(), "ping(" + string(qos) + ") ...");
   return CORBA::string_dup("");
} // ping


#endif

/*----------------------------------------------------------------------------
Name:      DefaultCallback.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default implementation of the POA_serverIdl::BlasterCallback.
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_DEFAULTCALLBACK_H
#define _CLIENT_DEFAULTCALLBACK_H

#include <string>
#include <boost/lexical_cast.hpp>
#include <util/Log.h>
#include <client/I_Callback.h>
#include <client/UpdateKey.h>
#include <client/UpdateQoS.h>
#define  SERVER_HEADER generated/xmlBlaster
#include <util/CompatibleCorba.h>
#include COSNAMING
#include <generated/xmlBlaster.h>

using namespace std;
using namespace boost;

namespace org { namespace xmlBlaster {
   
   /**
    * Example for a callback implementation.<p />
    * You can use this default callback handling with your clients,
    * but if you need other handling of callbacks, take a copy
    * of this Callback implementation and add your own code.
    */
   class DefaultCallback : public virtual POA_clientIdl::BlasterCallback {

   protected:
      util::Log log_;

   private:
      string me() {
         return "DefaultCallback-" + loginName_;
      }

      I_Callback *boss_;
      string loginName_;
      // BlasterCache cache_;

      
      void copy(const DefaultCallback &el) {
         boss_      = el.boss_;
         loginName_ = el.loginName_;
      }

      
      /**
       * Construct a persistently named object.
       */
   public:
      DefaultCallback(const string &name="", I_Callback *boss=0, 
                      /*BlasterCache*/ void* cache=0) : log_() {
         boss_         = boss;
         loginName_    = name;
         // cache_ = cache;
         if (log_.CALL) log_.trace(me(),"Entering constructor with argument");
      }

      DefaultCallback(const DefaultCallback &el) : log_() {
         copy(el);
      }

      DefaultCallback& operator =(const DefaultCallback &el) {
         copy(el);
         return *this;
      }

      ~DefaultCallback() {
         //      delete boss_; MUST BE DELETED OUTSIDE 
         // BECAUSE IT IS NOT OWNED BY THIS OBJECT
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
      serverIdl::StringArr* update(const char* sessionId, const serverIdl::MessageUnitArr& msgUnitArr) {

         // typedef StringSequenceTmpl<CORBA::String_var> StringArr;
         // typedef TSeqVar<StringSequenceTmpl<CORBA::String_var> > StringArr_var;
         // typedef TSeqOut<StringSequenceTmpl<CORBA::String_var> > StringArr_out;
         // IDL: typedef sequence<string> StringArr;
         serverIdl::StringArr *res = new serverIdl::StringArr(msgUnitArr.length());
         res->length(msgUnitArr.length());

         if (log_.CALL) { log_.call(me(), "Receiving update of " + lexical_cast<string>(msgUnitArr.length()) + " message ..."); }
         
         if (msgUnitArr.length() == 0) {
            log_.warn(me(), "Entering update() with 0 messages");
            return res;
         }
         for (string::size_type i=0; i < msgUnitArr.length(); i++) {
            const serverIdl::MessageUnit &msgUnit = msgUnitArr[i];
            UpdateKey *updateKey = 0;
            UpdateQoS *updateQoS = 0;
            try {
               updateKey = new UpdateKey();
               updateKey->init(string(msgUnit.xmlKey));
               updateQoS = new UpdateQoS(string(msgUnit.qos));
            } 
            catch (serverIdl::XmlBlasterException &e) {
               log_.error(me(), string(e.reason) );
            }
            // Now we know all about the received msg, dump it or do 
            // some checks
            if (log_.DUMP) log_.dump("UpdateKey", string("\n") + 
                                     updateKey->printOn());
            if (log_.DUMP) {
               string msg = "\n";
               for (string::size_type j=0; j < msgUnit.content.length(); j++) 
                  msg += (char)msgUnit.content[j];
               log_.dump("content", msg);
            }
            if (log_.DUMP) log_.dump("UpdateQoS", "\n" + updateQoS->printOn());
            if (log_.TRACE) log_.trace(me(), "Received message [" + 
                                       updateKey->getUniqueKey() + 
                                       "] from publisher " + 
                                       updateQoS->getSender());
            //Checking whether the Update is for the Cache or for the boss
            //The boss should not be interested in cache updates
            bool forCache = false;
//          if( cache_ != null ) {
//             forCache = cache_.update(updateQoS.getSubscriptionId(), 
//                                      updateKey.toXml(), msgUnit.content);
//          }
            string oneRes = "<qos><state>OK</state></qos>";
            if (!forCache) {
               if (boss_) {
                  oneRes = boss_->update(sessionId, loginName_, *updateKey,
                                (void*)&msgUnit.content[0], 
                                msgUnit.content.length(), *updateQoS); 
                  // Call my boss
               }
               else log_.warn(me(), "can not update: no callback defined");
            }
            CORBA::String_var str = CORBA::string_dup(oneRes.c_str());
            (*res)[i] = str;
            delete updateKey;
            delete updateQoS;
         }
         return res; // res._retn();
      }
   }; // class DefaultCallback
}} // namespace


#endif

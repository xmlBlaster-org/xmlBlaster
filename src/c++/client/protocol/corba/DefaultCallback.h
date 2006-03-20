/*----------------------------------------------------------------------------
Name:      DefaultCallback.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default implementation of the POA_serverIdl::BlasterCallback.
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_PROTOCOL_CORBA_DEFAULTCALLBACK_H
#define _CLIENT_PROTOCOL_CORBA_DEFAULTCALLBACK_H

#include <string>
#include <util/lexical_cast.h>
#include <client/I_Callback.h>
#include <client/key/UpdateKey.h>
#include <client/qos/UpdateQos.h>
#include <util/key/MsgKeyFactory.h>
#include <util/qos/MsgQosFactory.h>
#define  SERVER_HEADER 1 // does #include <generated/xmlBlasterS.h> with CompatibleCorba.h, OMNIORB: use -Wbh=.h to force this extension
#include <client/protocol/corba/CompatibleCorba.h>
#include COSNAMING

namespace org { 
 namespace xmlBlaster {
  namespace client {
   namespace protocol {
    namespace corba {
   
   /**
    * Example for a callback implementation.<p />
    * You can use this default callback handling with your clients,
    * but if you need other handling of callbacks, take a copy
    * of this Callback implementation and add your own code.
    */
   class Dll_Export DefaultCallback : public virtual POA_clientIdl::BlasterCallback {

   protected:
      org::xmlBlaster::util::Global&       global_;
      org::xmlBlaster::util::I_Log&          log_;
      org::xmlBlaster::util::key::MsgKeyFactory msgKeyFactory_;
      org::xmlBlaster::util::qos::MsgQosFactory msgQosFactory_;

   private:
      std::string me() {
         return "DefaultCallback-" + loginName_;
      }

      org::xmlBlaster::client::I_Callback *boss_;
      std::string loginName_;
      // BlasterCache cache_;

      
      void copy(const DefaultCallback &el) 
      {
         boss_      = el.boss_;
         loginName_ = el.loginName_;
      }

      
      /**
       * Construct a persistently named object.
       */
   public:
      DefaultCallback(org::xmlBlaster::util::Global& global, const std::string &name="", org::xmlBlaster::client::I_Callback *boss=0,
                      /*BlasterCache*/ void* /*cache*/=0);

      DefaultCallback(const DefaultCallback &el) 
         : global_(el.global_), log_(el.log_), msgKeyFactory_(el.global_), msgQosFactory_(el.global_)
      {
         copy(el);
      }

      DefaultCallback& operator =(const DefaultCallback &el) {
         copy(el);
         return *this;
      }

      ~DefaultCallback() {
         //log_.trace(me(), "DEBUG ONLY: entering ~DefaultCallback");
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
       * the org::xmlBlaster::client::I_Callback.update().
       * <p />
       * So you should implement in your client the org::xmlBlaster::client::I_Callback interface -
       * suppling the update() method.
       *
       * @param loginName        The name to whom the callback belongs
       * @param msgUnit      Contains a org::xmlBlaster::util::MessageUnit structs (your message)
       * @param qos              Quality of Service of the org::xmlBlaster::util::MessageUnit
       */
      serverIdl::XmlTypeArr* update(const char* sessionId,
                                    const serverIdl::MessageUnitArr& msgUnitArr)
                                    UPDATE_THROW_SPECIFIER;

      /**
       * This is the oneway variant, not returning a value (no application level ACK). 
       * @see update()
       */
      void updateOneway(const char* sessionId, const serverIdl::MessageUnitArr& msgUnitArr) PING_THROW_SPECIFIER;

      /**
       * Check the callback server.
       * @see xmlBlaster.idl
       */
      char *ping(const char *qos) PING_THROW_SPECIFIER;

   }; // class DefaultCallback
}}}}} // namespace


#endif

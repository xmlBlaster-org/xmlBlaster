/*----------------------------------------------------------------------------
Name:      DefaultCallback.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Default implementation of the POA_serverIdl::BlasterCallback.
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_PROTOCOL_CORBA_DEFAULTCALLBACK_H
#define _CLIENT_PROTOCOL_CORBA_DEFAULTCALLBACK_H

#include <string>
#include <boost/lexical_cast.hpp>
#include <util/Log.h>
#include <client/I_Callback.h>
#include <client/UpdateKey.h>
#include <client/UpdateQos.h>
#define  SERVER_HEADER generated/xmlBlaster
#include <client/protocol/corba/CompatibleCorba.h>
#include COSNAMING
#ifdef TAO
  #include "generated/xmlBlasterC.h"
  #include "generated/xmlBlasterS.h"
#else
  #include <generated/xmlBlaster.h>
#endif


using namespace std;
using namespace boost;
using namespace org::xmlBlaster::util;

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
      Global& global_;
      Log&    log_;

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
      DefaultCallback(Global& global, const string &name="", I_Callback *boss=0,
                      /*BlasterCache*/ void* /*cache*/=0);

      DefaultCallback(const DefaultCallback &el) : global_(el.global_), log_(el.log_)
      {
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
      serverIdl::XmlTypeArr* update(const char* sessionId,
                                    const serverIdl::MessageUnitArr& msgUnitArr)
#ifdef ORBIX
            IT_THROW_DECL ((CORBA::SystemException))
#endif
      ;

      /**
       * This is the oneway variant, not returning a value (no application level ACK). 
       * @see update()
       */
      void updateOneway(const char* sessionId, const serverIdl::MessageUnitArr& msgUnitArr)
#ifdef ORBIX
            IT_THROW_DECL ((CORBA::SystemException))
#endif
      ;

      /**
       * Check the callback server.
       * @see xmlBlaster.idl
       */
      char *ping(const char *qos)
#ifdef ORBIX
            IT_THROW_DECL ((CORBA::SystemException))
#endif
      ;

   }; // class DefaultCallback
}}}}} // namespace


#endif

/*-----------------------------------------------------------------------------
Name:      I_Callback.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_ICALLBACK_H
#define _CLIENT_ICALLBACK_H

#include <string>
#include <client/key/UpdateKey.h>
#include <client/qos/UpdateQos.h>
// #define CLIENT_HEADER generated/xmlBlaster    // xmlBlaster.h
using namespace std;
using org::xmlBlaster::client::qos::UpdateQos;
using org::xmlBlaster::client::key::UpdateKey;

namespace org { namespace xmlBlaster { namespace client {
   /**
    * This is a little helper class wraps the CORBA BlasterCallback update(),
    * and delivers the client a nicer update() method. <p>
    * You may use this, if you don't want to program with the rawer CORBA 
    * BlasterCallback.update()
    *
    * @version $Revision: 1.14 $
    * @author $Author: laghi $
    */
   class Dll_Export I_Callback {
      /**
       * This is the callback method invoked from CorbaConnection
       * informing the client in an asynchronous mode about a new message.
       * <p />The raw BlasterCallback.update() is unpacked and for each 
       * arrived message this update is called. <p />
       * So you should implement in your client the I_Callback interface -
       * suppling the update() method where you can do with the message 
       * whatever you want. <p />
       * If you do multiple logins with the same I_Callback implementation, 
       * the loginName which is delivered with this update() method may be 
       * used to dispatch the message to the correct client.
       *
       * @param sessionId The sessionId to authenticate the callback
       *                  This sessionId was passed on subscription
       *                  we can use it to decide if we trust this update()
       * @param updateKey The arrived key
       * @param content   The arrived message content
       * @param qos       Quality of Service of the MessageUnit
       * @return The status string
       */
      
   public:
      virtual string update(const string &sessionId,
                          UpdateKey &updateKey, 
                          void *content, long contentSize, 
                          UpdateQos &updateQos) = 0;
   };
}}} // namespace

#endif



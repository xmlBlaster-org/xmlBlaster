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

namespace org { namespace xmlBlaster { namespace client {
/**
 * This is a little helper class wraps the CORBA BlasterCallback update(),
 * and delivers the client a nicer update() method. <p>
 * You may use this, if you don't want to program with the rawer CORBA 
 * BlasterCallback.update()
 *
 * @author laghi
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
    * @param content   The arrived message content. If the size is 0 it is NULL
    * @param contentSize the size of the content of the message
    * @param qos       Quality of Service of the org::xmlBlaster::util::MessageUnit
    * @return The status std::string
    */
   
public:
   virtual std::string update(const std::string &sessionId,
                       org::xmlBlaster::client::key::UpdateKey &updateKey, 
                       const unsigned char *content, long contentSize, 
                       org::xmlBlaster::client::qos::UpdateQos &updateQos) = 0;

   virtual ~I_Callback() // = 0;
   {
   }


};
}}} // namespace

#endif



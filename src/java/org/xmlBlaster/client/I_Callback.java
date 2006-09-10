/*------------------------------------------------------------------------------
Name:      I_Callback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;

/**
 * Interface to receive asynchronously send callback messages from xmlBlaster. 
 * <p>
 * Please implement this to receive your messages.
 *
 * @version $Revision: 1.13 $
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/demo/HelloWorld3.java.html">HelloWorld8.java</a>
 */
public interface I_Callback
{
   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * So you should implement in your client code the I_Callback interface -
    * suppling the update() method where you can do with the message whatever you want.
    * <p />
    * The raw protocol driver specific update() method (e.g. CORBA-BlasterCallback.update())
    * is unpacked and for each arrived message this update is called.
    *
    * @param cbSessionId The session ID specified by the client which registered the callback.
    *                    You can specify a cbSessionId during connection (with ConnectQos)
    *                    and this is bounced back here so you can authenticate the message.
    * @param updateKey   The arrived key containing the topic name
    * @param content     The arrived message content. This is your payload.
    * @param qos         Quality of Service of the MsgUnit
    * @see org.xmlBlaster.client.I_XmlBlasterAccess
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException;
}


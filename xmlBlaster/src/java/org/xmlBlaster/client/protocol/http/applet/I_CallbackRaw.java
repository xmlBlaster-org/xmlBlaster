/*------------------------------------------------------------------------------
Name:      I_Callback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.http.applet;

/**
 * Here you receive callbacks from xmlBlaster in your applet. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public interface I_CallbackRaw
{
   /**
    * This is the callback method invoked from I_XmlBlasterRaw
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * So you should implement in your applet the I_CallbackRaw interface -
    * suppling the update() method where you can do with the message whatever you want.
    *
    * @param cbSessionId The session ID specified by the client which registered the callback
    * @param updateKey   The arrived key
    * @param content     The arrived message content
    * @param qos         Quality of Service of the MsgUnit
    * @see org.xmlBlaster.client.I_XmlBlasterAccess
    */
   public String update(String cbSessionId, String updateKey, byte[] content, String updateQos) throws Exception;
}


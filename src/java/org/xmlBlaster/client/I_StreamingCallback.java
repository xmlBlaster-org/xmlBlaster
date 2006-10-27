/*------------------------------------------------------------------------------
Name:      I_StreamCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages where the content is handled 
           as a stream.
Version:   $Id: I_Callback.java 15494 2006-09-10 10:59:27Z ruff $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import java.io.IOException;
import java.io.InputStream;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;

/**
 * Interface to receive asynchronously send callback messages from xmlBlaster. 
 * <p>
 * Please implement this to receive your messages.
 *
 * @version $Revision: 1.13 $
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>.
 */
public interface I_StreamingCallback
{
   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * So you should implement in your client code the I_Callback interface -
    * suppling the update() method where you can do with the message whatever you want.
    * <p />
    *
    * @param cbSessionId The session ID specified by the client which registered the callback.
    *                    You can specify a cbSessionId during connection (with ConnectQos)
    *                    and this is bounced back here so you can authenticate the message.
    * @param updateKey   The arrived key containing the topic name
    * @param content     The arrived message content to be accessed as an InputStream.
    * @param qos         Quality of Service of the MsgUnit
    * @see org.xmlBlaster.client.I_XmlBlasterAccess
    */
   public String update(String cbSessionId, UpdateKey updateKey, InputStream contentStream, UpdateQos updateQos) throws XmlBlasterException, IOException;
}


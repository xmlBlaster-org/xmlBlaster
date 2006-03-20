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
 * This is a little helper class wraps the different, protocol specific
 * update() methods, and delivers the client a nicer update() method.
 * <p>
 * You may use this, if you don't want to program with the rawer CORBA BlasterCallback.update()
 * or RMI or XMLRPC.
 *
 * @version $Revision: 1.13 $
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public interface I_Callback
{
   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * So you should implement in your client the I_Callback interface -
    * suppling the update() method where you can do with the message whatever you want.
    * <p />
    * The raw driver specific update() method (e.g. CORBA-BlasterCallback.update())
    * is unpacked and for each arrived message this update is called.
    *
    * @param cbSessionId The session ID specified by the client which registered the callback
    * @param updateKey   The arrived key
    * @param content     The arrived message content
    * @param qos         Quality of Service of the MsgUnit
    * @see org.xmlBlaster.client.I_XmlBlasterAccess
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException;
}


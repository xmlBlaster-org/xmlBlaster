/*------------------------------------------------------------------------------
Name:      I_Callback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: I_Callback.java,v 1.7 2002/03/17 13:38:44 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * This is a little helper class wraps the different, protocol specific
 * update() methods, and delivers the client a nicer update() method.
 * <p>
 * You may use this, if you don't want to program with the rawer CORBA BlasterCallback.update()
 * or RMI or XML-RPC.
 *
 * @version $Revision: 1.7 $
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public interface I_Callback
{
   /**
    * This is the callback method invoked from XmlBlasterConnection
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
    * @param qos         Quality of Service of the MessageUnit
    */
   public void update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS) throws XmlBlasterException;
}


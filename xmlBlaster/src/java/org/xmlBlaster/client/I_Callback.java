/*------------------------------------------------------------------------------
Name:      I_Callback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: I_Callback.java,v 1.6 2000/10/18 20:45:42 ruff Exp $
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
 * @version $Revision: 1.6 $
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
    * If you do multiple logins with the same I_Callback implementation, the loginName
    * which is delivered with this update() method may be used to dispatch the message
    * to the correct client.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS) throws XmlBlasterException;
}


/*------------------------------------------------------------------------------
Name:      I_Callback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: I_Callback.java,v 1.1 1999/12/13 12:18:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.*;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;


/**
 * This is a little helper class wraps the CORBA BlasterCallback update(),
 * and delivers the client a nicer update() method. 
 * <p>
 * You may use this, if you don't want to program with the rawer CORBA BlasterCallback.update()
 *
 * @version $Revision: 1.1 $
 * @author $Author: ruff $
 */
public interface I_Callback
{
   /**
    * This is the callback method invoked from CorbaConnection
    * informing the client in an asynchronous mode about a new message. 
    * <p />
    * The raw BlasterCallback.update() is unpacked and for each arrived message
    * this update is called.
    * <p />
    * So you should implement in your client the I_Callback interface -
    * suppling the update() method where you can do with the message whatever you want.
    * <p />
    * If you do multiple logins with the same I_Callback implementation, the loginName
    * which is delivered with this update() method may be used to dispatch the message
    * to the correct client.
    *
    * @param loginName The name to whom the callback belongs
    * @param keyOid    the unique message key for your convenience (redundant to updateKey.getUniqueKey())
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(String loginName, String keyOid, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS);
}


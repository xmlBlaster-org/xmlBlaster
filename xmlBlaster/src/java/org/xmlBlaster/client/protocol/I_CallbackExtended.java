/*------------------------------------------------------------------------------
Name:      I_XmlRpcCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the xml-rpc callback messages
Version:   $Id: I_CallbackExtended.java,v 1.2 2000/10/18 20:45:42 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * This is a little helper interface which which extends the I_Callback
 * and I_CallbackRaw interface to become suited for protocols like xml-rpc.
 * <p>
 * The class implementing this interface needs to support
 * the 3 update() variants, so that the protocol drivers
 * can choose the update() they like most.
 *
 * @version $Revision: 1.2 $
 * @author "Michele Laghi" <michele.laghi@attglobal.net>
 */
public interface I_CallbackExtended extends I_Callback, I_CallbackRaw
{
   /**
    * This is the callback method invoked when using certain callback protocols
    * (for example xml-rpc) which informs the client in an asynchronous
    * mode about a new message.
    * <p />
    * You can implement this interface in your client. Note that when doing
    * so, you need to implement all update() methods defined (remember the
    * one defined in I_Callback/I_CallbackRaw).
    * <p />
    * The implementation of the update method with
    * the signature specified in this interface must parse the string literals
    * passed in the argument list and call the other update method (the one
    * with the signature defined in I_Callback).
    * <p />
    * If you do multiple logins with the same implementation, the loginName
    * which is delivered with this update() method may be used to dispatch the
    * message to the correct client.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKeyLiteral The arrived key (as an xml-string)
    * @param content   The arrived message content
    * @param updateQosLiteral  Quality of Service of the MessageUnit
    *                      (as an xml-string)
    * @see I_Callback
    * @see AbstractCallbackExtended
    */
   public void update(String loginName, String updateKeyLiteral, byte[] content,
                      String updateQoSLiteral) throws XmlBlasterException;
}


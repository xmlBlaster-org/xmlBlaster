/*------------------------------------------------------------------------------
Name:      I_XmlRpcCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the xml-rpc callback messages
Version:   $Id: I_CallbackExtended.java,v 1.1 2000/08/30 00:21:58 laghi Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * This is a little helper interface which which extends the I_Callback
 * interface to become suited for protocols like xml-rpc.
 * <p>
 *
 * @version $Revision: 1.1 $
 * @author "Michele Laghi" <michele.laghi@attglobal.net>
 */
public interface I_CallbackExtended extends I_Callback
{
   /**
    * This is the callback method invoked when using certain callback protocols
    * (for example xml-rpc) which informs the client in an asynchronous 
    * mode about a new message.
    * <p />
    * You can implement this interface in your client. Note that when doing 
    * so, you need to implement both update() methods defined (remember the 
    * one defined in I_Callback). The implementation of the update method with
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


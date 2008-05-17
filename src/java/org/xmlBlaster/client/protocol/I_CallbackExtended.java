/*------------------------------------------------------------------------------
Name:      I_XmlRpcCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the xml-rpc callback messages
Version:   $Id$
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
 * @version $Revision: 1.7 $
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
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
    *
    * @param cbSessionId The session ID specified by the client which registered the callback
    * @param updateKeyLiteral The arrived key (as an xml-string)
    * @param content   The arrived message content
    * @param updateQosLiteral  Quality of Service of the MsgUnitRaw
    *                      (as an xml-string)
    * @see I_Callback
    * @see AbstractCallbackExtended
    */
   public String update(String cbSessionId, String updateKeyLiteral, byte[] content,
                      String updateQosLiteral) throws XmlBlasterException;

   /**
    * The oneway variant without a return value or exception
    */
   public void updateOneway(String cbSessionId, String updateKeyLiteral, byte[] content, String updateQosLiteral);
   
   /**
    * For example called by socket layer on EOF
    * @param xmlBlasterException
    */
   public void lostConnection(XmlBlasterException xmlBlasterException);
}


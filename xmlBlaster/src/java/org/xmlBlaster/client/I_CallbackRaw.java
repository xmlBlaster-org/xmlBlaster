/*------------------------------------------------------------------------------
Name:      I_CallbackRaw.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;


/**
 * This is a little helper class is the same as the CORBA generated
 * <pre>
 *    org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackOperations
 * </pre>
 * but it is independent of the protocol type.
 * <p>
 * Its purpose is to deliver Java clients the asynchronous message updates
 * through the update() method.
 * @author xmlBlaster@marcelruff.info
 */
public interface I_CallbackRaw
{
   /**
    * This is the callback method invoked from xmlBlaster server
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * @param msgUnitArr Array of MsgUnitRaw, containing xmlKey,content,qos
    * @return For every message a return QoS
    */
   public String[] update(String cbSessionId, org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr) throws XmlBlasterException;

   /**
    * The oneway variant without a return value or exception
    */
   public void updateOneway(String cbSessionId, org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr);
}


/*------------------------------------------------------------------------------
Name:      I_CallbackRaw.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: I_CallbackRaw.java,v 1.2 2000/10/18 20:45:42 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * This is a little helper class is the same as the CORBA generated
 * <pre>
 *    org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackOperations
 * </pre>
 * but it is independent of the protocol type.
 * <p>
 * Its purpose is to deliver Java clients the asynchronous message updates
 * through the update() method.
 * @author ruff@swand.lake.de
 */
public interface I_CallbackRaw
{
   /**
    * This is the callback method invoked from xmlBlaster server
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * @param msgUnitArr Array of MessageUnit, containing xmlKey,content,qos
    */
   public void update(String loginName, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) throws XmlBlasterException;
}


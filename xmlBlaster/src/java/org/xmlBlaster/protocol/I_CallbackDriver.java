/*------------------------------------------------------------------------------
Name:      I_CallbackDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_CallbackDriver.java,v 1.4 2000/03/18 20:33:23 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;


/**
 * This interface hides the real protocol used to send a client a callback message
 * <p>
 *
 * @version $Revision: 1.4 $
 * @author $Author: ruff $
 */
public interface I_CallbackDriver
{
   /**
    * This method sends the message update to the client.
    * <p />
    * The protocol for sending is implemented in the derived class
    * 
    * @exception On callback problems you need to throw a XmlBlasterException e.id="CallbackFailed",
    *            the message will queued until the client logs in again
    */
   public void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, String updateQoS) throws XmlBlasterException;
}


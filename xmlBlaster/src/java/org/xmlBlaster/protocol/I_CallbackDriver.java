/*------------------------------------------------------------------------------
Name:      I_CallbackDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_CallbackDriver.java,v 1.3 2000/02/24 22:19:53 ruff Exp $
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
 * @version $Revision: 1.3 $
 * @author $Author: ruff $
 */
public interface I_CallbackDriver
{
   /**
    * This method sends the message update to the client.
    * <p />
    * The protocol for sending is implemented in the derived class
    */
   public void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, String updateQoS) throws XmlBlasterException;
}


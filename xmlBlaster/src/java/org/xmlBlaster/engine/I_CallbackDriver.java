/*------------------------------------------------------------------------------
Name:      I_CallbackDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_CallbackDriver.java,v 1.1 1999/12/01 22:17:28 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.serverIdl.XmlBlasterException;


/**
 * This interface hides the real protocol used to send a client a callback message
 * <p>
 *
 * @version $Revision: 1.1 $
 * @author $Author: ruff $
 */
public interface I_CallbackDriver
{
   /**
    * This method sends the message update to the client. 
    * <p />
    * The protocol for sending is implemented in the derived class
    */
   public void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper messageUnitWrapper) throws XmlBlasterException;
}


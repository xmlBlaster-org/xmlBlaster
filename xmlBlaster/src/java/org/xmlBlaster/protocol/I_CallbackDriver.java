/*------------------------------------------------------------------------------
Name:      I_CallbackDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_CallbackDriver.java,v 1.5 2000/06/04 23:44:46 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.util.CallbackAddress;


/**
 * This interface hides the real protocol used to send a client a callback message
 * <p>
 *
 * @version $Revision: 1.5 $
 * @author $Author: ruff $
 */
public interface I_CallbackDriver
{
   /** Get a human readable name of this driver */
   public String getName();


   /**
    * Get callback reference here.
    * @param  callbackAddress Contains the stringified CORBA callback handle of the client
    */
   public void init(CallbackAddress callbackAddress) throws XmlBlasterException;


   /**
    * This method sends the message update to the client.
    * <p />
    * The protocol for sending is implemented in the derived class
    *
    * @exception On callback problems you need to throw a XmlBlasterException e.id="CallbackFailed",
    *            the message will queued until the client logs in again
    */
   public void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, String updateQoS) throws XmlBlasterException;


   /**
    * This method shuts down the driver. 
    * <p />
    */
   public void shutdown();
}


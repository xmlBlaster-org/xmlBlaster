/*------------------------------------------------------------------------------
Name:      I_CallbackDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_CallbackDriver.java,v 1.10 2001/11/24 23:15:34 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * This interface hides the real protocol used to send a client a callback message
 * <p>
 *
 * @version $Revision: 1.10 $
 * @author $Author: ruff $
 */
public interface I_CallbackDriver
{
   /** Get a human readable name of this driver */
   public String getName();


   /**
    * Intialize the driver.
    * @param  callbackAddress Contains the callback address,
    *         e.g. the stringified CORBA callback handle of the client or his email address.
    */
   public void init(CallbackAddress callbackAddress) throws XmlBlasterException;


   /**
    * Send the message update to the client.
    * <p />
    * The protocol for sending is implemented in the derived class
    *
    * @param clientInfo Data about a specific client
    * @param msgUnitWrapper For Logoutput only (deprecated?)
    * @param messageUnitArr Array of all messages to send
    * @exception On callback problems you need to throw a XmlBlasterException e.id="CallbackFailed",
    *            the message will queued until the client logs in again
    */
   public void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, MessageUnit[] messageUnitArr) throws XmlBlasterException;


   /**
    * Shut down the driver.
    * <p />
    */
   public void shutdown();
}


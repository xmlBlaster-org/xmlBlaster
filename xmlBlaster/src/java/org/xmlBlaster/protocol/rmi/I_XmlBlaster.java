/*------------------------------------------------------------------------------
Name:      I_XmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_XmlBlaster.java,v 1.4 2002/03/18 00:29:36 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

import java.rmi.RemoteException;


/**
 * RMI clients access xmlBlaster through these methods. 
 *
 * @see xmlBlaster.idl
 * @see org.xmlBlaster.engine.RequestBroker
 * @see org.xmlBlaster.protocol.I_XmlBlaster
 * @author ruff@swand.lake.de
 */
public interface I_XmlBlaster extends java.rmi.Remote
{
   /**
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String subscribe(String sessionId, String xmlKey_literal, String subscribeQoS_literal)
                           throws RemoteException, XmlBlasterException;

   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public void unSubscribe(String sessionId, String xmlKey_literal, String unSubscribeQoS_literal)
                           throws RemoteException, XmlBlasterException;

   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String publish(String sessionId, MessageUnit msgUnit)
                           throws RemoteException, XmlBlasterException;

   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String[] publishArr(String sessionId, MessageUnit[] msgUnitArr)
                           throws RemoteException, XmlBlasterException;

   /**
    * Publish messages.
    * <p />
    * @see xmlBlaster.idl
    */
   public void publishOneway(String sessionId, MessageUnit[] msgUnitArr)
                           throws RemoteException;

   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String[] erase(String sessionId, String xmlKey_literal, String eraseQoS_literal)
                           throws RemoteException, XmlBlasterException;

   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public MessageUnit[] get(String sessionId, String xmlKey_literal, String getQoS_literal)
                           throws RemoteException, XmlBlasterException;

   /**
    * Ping to check if the xmlBlaster server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    */
   public String ping(String str) throws RemoteException;
}


/*------------------------------------------------------------------------------
Name:      I_XmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;

import java.rmi.RemoteException;


/**
 * RMI clients access xmlBlaster through these methods. 
 *
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 * @see org.xmlBlaster.engine.RequestBroker
 * @see org.xmlBlaster.protocol.I_XmlBlaster
 * @author xmlBlaster@marcelruff.info
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
   public String[] unSubscribe(String sessionId, String xmlKey_literal, String unSubscribeQos_literal)
                           throws RemoteException, XmlBlasterException;

   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String publish(String sessionId, MsgUnitRaw msgUnit)
                           throws RemoteException, XmlBlasterException;

   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String[] publishArr(String sessionId, MsgUnitRaw[] msgUnitArr)
                           throws RemoteException, XmlBlasterException;

   /**
    * Publish messages.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void publishOneway(String sessionId, MsgUnitRaw[] msgUnitArr)
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
   public MsgUnitRaw[] get(String sessionId, String xmlKey_literal, String getQoS_literal)
                           throws RemoteException, XmlBlasterException;

   /**
    * Ping to check if the xmlBlaster server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    */
   public String ping(String str) throws RemoteException;
}


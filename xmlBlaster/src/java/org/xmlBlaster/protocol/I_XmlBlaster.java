/*------------------------------------------------------------------------------
Name:      I_XmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_XmlBlaster.java,v 1.3 2000/06/13 13:04:00 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;


/**
 * This is the native interface to xmlBlaster.
 * <p />
 * All protocol drivers access xmlBlaster through these methods.
 * @see xmlBlaster.idl
 * @see org.xmlBlaster.engine.RequestBroker
 * @author ruff@swand.lake.de
 */
public interface I_XmlBlaster
{
   /**
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String subscribe(String sessionId, XmlKey xmlKey, SubscribeQoS subscribeQoS) throws XmlBlasterException;
   /**
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String subscribe(String sessionId, String xmlKey_literal, String subscribeQoS_literal) throws XmlBlasterException;


   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public void unSubscribe(String sessionId, XmlKey xmlKey, UnSubscribeQoS unSubscribeQoS) throws XmlBlasterException;
   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public void unSubscribe(String sessionId, String xmlKey_literal, String unSubscribeQoS_literal) throws XmlBlasterException;


   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String publish(String sessionId, MessageUnit msgUnit, PublishQoS publishQoS) throws XmlBlasterException;
   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String publish(String sessionId, MessageUnit msgUnit, String publishQoS_literal) throws XmlBlasterException;
   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String publish(String sessionId, String xmlKey_literal, byte[] content, String publishQoS_literal) throws XmlBlasterException;


   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String[] publishArr(String sessionId, MessageUnit[] msgUnitArr, PublishQoS[] publishQosArr) throws XmlBlasterException;
   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String[] publishArr(String sessionId, MessageUnit[] msgUnitArr, String[] qos_literal_Arr) throws XmlBlasterException;


   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String[] erase(String sessionId, XmlKey xmlKey, EraseQoS qoS) throws XmlBlasterException;
   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String[] erase(String sessionId, String xmlKey_literal, String eraseQoS_literal) throws XmlBlasterException;


   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public MessageUnitContainer[] get(String sessionId, XmlKey xmlKey, GetQoS getQoS) throws XmlBlasterException;
   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public MessageUnitContainer[] get(String sessionId, String xmlKey_literal, String getQoS_literal) throws XmlBlasterException;


   public String toXml() throws XmlBlasterException;
   public String toXml(String extraOffset) throws XmlBlasterException;
}


/*------------------------------------------------------------------------------
Name:      I_XmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_XmlBlaster.java,v 1.6 2000/11/12 13:22:05 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This is the native interface to xmlBlaster.
 * <p />
 * All protocol drivers access xmlBlaster through these methods.
 * This interface is implemented by engine/XmlBlasterImpl.java
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
    * If you have parsed the xml string already, use this method.
    *
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public void unSubscribe(String sessionId, XmlKey xmlKey, UnSubscribeQoS unSubscribeQoS) throws XmlBlasterException;
   /**
    * Unsubscribe from messages.
    * <p />
    * To pass the raw xml ASCII strings, use this method.
    *
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public void unSubscribe(String sessionId, String xmlKey_literal, String unSubscribeQoS_literal) throws XmlBlasterException;


   /**
    * Publish a message.
    * <p />
    * The MessageUnit contains the literal ASCII strings of xmlKey and publishQoS only,
    * this method allows to pass the parsed objects of xmlKey/publishQoS as well (if you have them),
    * to avoid double parsing.
    *
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String publish(String sessionId, XmlKey xmlKey, MessageUnit msgUnit, PublishQoS publishQoS) throws XmlBlasterException;
   /**
    * Publish a message.
    * <p />
    * @param msgUnit The MessageUnit contains the literal ASCII strings of xmlKey and publishQoS and the binary content.
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String publish(String sessionId, MessageUnit msgUnit) throws XmlBlasterException;


   /**
    * Publish messages.
    * <p />
    * This variant allows to pass an array of MessageUnit object, for performance reasons and
    * probably in future as an entity for transactions.
    *
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String[] publishArr(String sessionId, MessageUnit[] msgUnitArr) throws XmlBlasterException;


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
   public MessageUnit[] get(String sessionId, XmlKey xmlKey, GetQoS getQoS) throws XmlBlasterException;
   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public MessageUnit[] get(String sessionId, String xmlKey_literal, String getQoS_literal) throws XmlBlasterException;


   public String toXml() throws XmlBlasterException;
   public String toXml(String extraOffset) throws XmlBlasterException;
}


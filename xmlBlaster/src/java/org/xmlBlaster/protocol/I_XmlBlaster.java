/*------------------------------------------------------------------------------
Name:      I_XmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_XmlBlaster.java,v 1.10 2002/05/19 12:55:46 ruff Exp $
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
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>" target="others">CORBA xmlBlaster.idl</a>
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
   public String subscribe(String sessionId, String xmlKey_literal, String subscribeQoS_literal) throws XmlBlasterException;

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
    * The MessageUnit contains the literal ASCII strings of xmlKey and publishQos only,
    * this method allows to pass the parsed objects of xmlKey/publishQos as well (if you have them),
    * to avoid double parsing.
    * Problem is that we can't import/export message
    *
    * @see org.xmlBlaster.engine.RequestBroker
    * @deprecated unsecure
    */
//   public String publish(String sessionId, XmlKey xmlKey, MessageUnit msgUnit, PublishQos publishQos) throws XmlBlasterException;

   /**
    * Publish a message.
    * <p />
    * @param msgUnit The MessageUnit contains the literal ASCII strings of xmlKey and publishQos and the binary content.
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
    * Publish messages.
    * <p />
    * The oneway variant may be used for better performance,
    * it is not returning a value (no application level ACK)
    * and there are no exceptions supported over the connection to the client.
    *
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public void publishOneway(String sessionId, MessageUnit[] msgUnitArr);

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
   public MessageUnit[] get(String sessionId, String xmlKey_literal, String getQoS_literal) throws XmlBlasterException;

   /**
     * Ping to check if xmlBlaster is alive. 
     * This ping checks the availability on the application level.
     * @param qos Currently an empty string ""
     * @return    Currently an empty string ""
     */
   public String ping(String qos);

   public String toXml() throws XmlBlasterException;
   public String toXml(String extraOffset) throws XmlBlasterException;
}


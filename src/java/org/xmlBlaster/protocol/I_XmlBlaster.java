/*------------------------------------------------------------------------------
Name:      I_XmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.engine.qos.AddressServer;


/**
 * This is the native interface to xmlBlaster.
 * <p />
 * All protocol drivers access xmlBlaster through these methods.
 * This interface is implemented by engine/XmlBlasterImpl.java
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 * @see org.xmlBlaster.engine.RequestBroker
 * @author xmlBlaster@marcelruff.info
 */
public interface I_XmlBlaster
{
   /**
    * Subscribe to messages.
    * <p />
    * @param xmlKey_literal Depending on the security plugin this key is encrypted
    * @param subscribeQoS_literal Depending on the security plugin this qos is encrypted
    * @param isInternal true if the subscription is internal, i.e. from a recovery
    * @see org.xmlBlaster.engine.RequestBroker
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
    */
   public String subscribe(AddressServer addressServer, String sessionId, String xmlKey_literal, String subscribeQoS_literal) throws XmlBlasterException;

   /**
    * Unsubscribe from messages.
    * <p />
    * To pass the raw xml ASCII strings, use this method.
    *
    * @param xmlKey_literal Depending on the security plugin this key is encrypted
    * @param unSubscribeQoS_literal Depending on the security plugin this qos is encrypted
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public String[] unSubscribe(AddressServer addressServer, String sessionId, String xmlKey_literal, String unSubscribeQos_literal) throws XmlBlasterException;

   /**
    * Publish a message.
    * <p />
    * @param msgUnit The MsgUnitRaw contains the literal ASCII strings of xmlKey and publishQos and the binary content.
    *                Depending on the security plugin the msgUnit is encrypted
    * @see org.xmlBlaster.engine.RequestBroker
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public String publish(AddressServer addressServer, String sessionId, MsgUnitRaw msgUnit) throws XmlBlasterException;

   /**
    * Publish messages.
    * <p />
    * This variant allows to pass an array of MsgUnitRaw object, for performance reasons and
    * probably in future as an entity for transactions.
    *
    * @see org.xmlBlaster.engine.RequestBroker
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public String[] publishArr(AddressServer addressServer, String sessionId, MsgUnitRaw[] msgUnitArr) throws XmlBlasterException;

   /**
    * Publish messages.
    * <p />
    * The oneway variant may be used for better performance,
    * it is not returning a value (no application level ACK)
    * and there are no exceptions supported over the connection to the client.
    *
    * @see org.xmlBlaster.engine.RequestBroker
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public void publishOneway(AddressServer addressServer, String sessionId, MsgUnitRaw[] msgUnitArr);

   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html">The interface.erase requirement</a>
    */
   public String[] erase(AddressServer addressServer, String sessionId, String xmlKey_literal, String eraseQoS_literal) throws XmlBlasterException;

   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html">The interface.get requirement</a>
    */
   public MsgUnitRaw[] get(AddressServer addressServer, String sessionId, String xmlKey_literal, String getQoS_literal) throws XmlBlasterException;

   /**
     * Ping to check if xmlBlaster is alive. 
     * This ping checks the availability on the application level.
     * @param qos Currently an empty string ""
     * @return qos, see StatusQosData.java
     * <pre>
     *  &lt;qos>
     *     &lt;state id='OK'/>
     *  &lt;/qos>
     * </pre>
     * Other returned id's are "RUNLEVEL_CLEANUP", "RUNLEVEL_STANDBY", "RUNLEVEL_HALTED".
     * All none "OK" values tell that the server is not willing to process messages.
     */
   public String ping(AddressServer addressServer, String qos) throws XmlBlasterException;

   public String toXml() throws XmlBlasterException;
   public String toXml(String extraOffset) throws XmlBlasterException;
}


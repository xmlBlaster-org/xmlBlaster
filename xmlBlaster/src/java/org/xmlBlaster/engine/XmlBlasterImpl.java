/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: XmlBlasterImpl.java,v 1.2 2000/06/13 13:04:00 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;
import org.xmlBlaster.authentication.Authenticate;


/**
 * This is the native implementation of the xmlBlaster interface.
 * <p />
 * All protocol drivers access xmlBlaster through these methods.
 * <br />
 * All security checks are done here, and then the call is delegated
 * to RequestBroker for processing.
 * <br />
 * Most access methods are provided with varying arguments for your convenience.
 *
 * @see org.xmlBlaster.engine.RequestBroker
 * @see org.xmlBlaster.protocol.I_XmlBlaster
 */
public class XmlBlasterImpl implements org.xmlBlaster.protocol.I_XmlBlaster
{
   private String ME = "XmlBlasterImpl";
   private RequestBroker requestBroker;
   private Authenticate authenticate;


   /**
    * One instance of this represents one xmlBlaster server.
    * @param authenticate The authentication service
    */
   public XmlBlasterImpl(Authenticate authenticate) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      this.requestBroker = new RequestBroker(authenticate);
   }

   /**
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String subscribe(String sessionId, XmlKey xmlKey, SubscribeQoS subscribeQoS) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      return requestBroker.subscribe(clientInfo, xmlKey, subscribeQoS);
   }
   /**
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String subscribe(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      SubscribeQoS subscribeQoS = new SubscribeQoS(qos_literal);
      return requestBroker.subscribe(clientInfo, xmlKey, subscribeQoS);
   }


   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void unSubscribe(String sessionId, XmlKey xmlKey, UnSubscribeQoS unSubscribeQoS) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      requestBroker.unSubscribe(clientInfo, xmlKey, unSubscribeQoS);
   }
   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void unSubscribe(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      UnSubscribeQoS unSubscribeQoS = new UnSubscribeQoS(qos_literal);
      requestBroker.unSubscribe(clientInfo, xmlKey, unSubscribeQoS);
   }


   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish(String sessionId, MessageUnit msgUnit, PublishQoS publishQoS) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      return requestBroker.publish(clientInfo, msgUnit, publishQoS);
   }
   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish(String sessionId, MessageUnit msgUnit, String qos_literal) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      PublishQoS publishQoS = new PublishQoS(qos_literal);
      return requestBroker.publish(clientInfo, msgUnit, publishQoS);
   }
   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish(String sessionId, String xmlKey_literal, byte[] content, String qos_literal) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      PublishQoS publishQoS = new PublishQoS(qos_literal);
      MessageUnit msgUnit = new MessageUnit(xmlKey_literal, content);
      return requestBroker.publish(clientInfo, msgUnit, publishQoS);
   }


   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(String sessionId, MessageUnit[] msgUnitArr, PublishQoS[] publishQoSArr) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      return requestBroker.publish(clientInfo, msgUnitArr, publishQoSArr);
   }
   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(String sessionId, MessageUnit[] msgUnitArr, String[] qos_literal_Arr) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      if (msgUnitArr == null || qos_literal_Arr==null || msgUnitArr.length != qos_literal_Arr.length) {
         Log.error(ME + ".InvalidArguments", "The arguments of method publishArr() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments", "The arguments of method publishArr() are invalid");
      }
      PublishQoS[] publishQoSArr = new PublishQoS[qos_literal_Arr.length];
      for (int ii=0; ii<qos_literal_Arr.length; ii++) {
         publishQoSArr[ii] = new PublishQoS(qos_literal_Arr[ii]);
      }
      return requestBroker.publish(clientInfo, msgUnitArr, publishQoSArr);
   }


   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase(String sessionId, XmlKey xmlKey, EraseQoS eraseQoS) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      return requestBroker.erase(clientInfo, xmlKey, eraseQoS);
   }
   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      EraseQoS eraseQoS = new EraseQoS(qos_literal);
      return requestBroker.erase(clientInfo, xmlKey, eraseQoS);
   }


   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MessageUnitContainer[] get(String sessionId, XmlKey xmlKey, GetQoS getQoS) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      return requestBroker.get(clientInfo, xmlKey, getQoS);
   }
   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MessageUnitContainer[] get(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      GetQoS getQoS = new GetQoS(qos_literal);
      return requestBroker.get(clientInfo, xmlKey, getQoS);
   }


   /**
    * Dump state of RequestBroker into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml() throws XmlBlasterException
   {
      return requestBroker.toXml();
   }


   /**
    * Dump state of RequestBroker into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      return requestBroker.toXml(extraOffset);
   }
}


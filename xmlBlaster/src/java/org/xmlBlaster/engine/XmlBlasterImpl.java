/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: XmlBlasterImpl.java,v 1.4 2000/06/25 18:32:41 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.RequestBroker;
import org.jutils.log.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
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
    * If you have in a native driver the XmlKey and PublishQoS objects already
    * use this method to avoid parsing again.
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish(String sessionId, XmlKey xmlKey, MessageUnit msgUnit, PublishQoS publishQoS) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      return requestBroker.publish(clientInfo, xmlKey, msgUnit, publishQoS);
   }
   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish(String sessionId, MessageUnit msgUnit) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      XmlKey xmlKey = new XmlKey(msgUnit.xmlKey, true);
      PublishQoS publishQoS = new PublishQoS(msgUnit.qos);
      return requestBroker.publish(clientInfo, xmlKey, msgUnit, publishQoS);
   }


   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(String sessionId, MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      if (msgUnitArr == null) {
         Log.error(ME + ".InvalidArguments", "The argument of method publishArr() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments", "The argument of method publishArr() are invalid");
      }
      // How to guarantee complete transaction?
      String[] returnArr = new String[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         returnArr[ii] = publish(sessionId, msgUnitArr[ii]); // authenticate.check() is in called method
      }
      return returnArr;
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
   public final MessageUnit[] get(String sessionId, XmlKey xmlKey, GetQoS getQoS) throws XmlBlasterException
   {
      ClientInfo clientInfo = authenticate.check(sessionId);
      return requestBroker.get(clientInfo, xmlKey, getQoS);
   }
   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MessageUnit[] get(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
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


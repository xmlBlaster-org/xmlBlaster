/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: XmlBlasterImpl.java,v 1.19 2002/06/25 17:49:00 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.plugins.PluginManager;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;

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
   private final String ME;
   private final RequestBroker requestBroker;
   private final Authenticate authenticate;
   private final Global glob;
   private final LogChannel log;

   private static final byte[] EMPTY_BYTES = "".getBytes();

   /**
    * One instance of this represents one xmlBlaster server.
    * @param authenticate The authentication service
    */
   public XmlBlasterImpl(Authenticate authenticate) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      this.glob = authenticate.getGlobal();
      this.ME = "XmlBlasterImpl" + this.glob.getLogPraefixDashed();
      this.log = this.glob.getLog("core");
      this.requestBroker = new RequestBroker(authenticate);
   }

   /**
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String subscribe(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering subscribe(" + sessionId + ", key, qos)");

         // authentication and authorization security checks
         MessageUnit msgUnit = new MessageUnit(xmlKey_literal, EMPTY_BYTES, qos_literal);
         SessionInfo sessionInfo = authenticate.check(sessionId);
         msgUnit = checkMessage(sessionInfo, msgUnit, Constants.SUBSCRIBE);

         XmlKey xmlKey = new XmlKey(glob, msgUnit.getXmlKey());
         SubscribeQoS subscribeQoS = new SubscribeQoS(glob, msgUnit.getQos());

         String ret = requestBroker.subscribe(sessionInfo, xmlKey, subscribeQoS);
         
         return sessionInfo.getSecuritySession().exportMessage(ret);
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         log.error(ME, "Internal problem: " + e.toString());
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Internal problem: " + e.toString());
      }
   }

   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void unSubscribe(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering unSubscribe(" + sessionId + ", key, qos)");

      try {
         // authentication and authorization security checks
         MessageUnit msgUnit = new MessageUnit(xmlKey_literal, EMPTY_BYTES, qos_literal);
         SessionInfo sessionInfo = authenticate.check(sessionId);
         msgUnit = checkMessage(sessionInfo, msgUnit, Constants.UNSUBSCRIBE);

         XmlKey xmlKey = new XmlKey(glob, msgUnit.getXmlKey());
         UnSubscribeQoS unSubscribeQoS = new UnSubscribeQoS(msgUnit.getQos());
         requestBroker.unSubscribe(sessionInfo, xmlKey, unSubscribeQoS);
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         log.error(ME, "Internal problem: " + e.toString());
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Internal problem: " + e.toString());
      }
   }

   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish(String sessionId, MessageUnit msgUnit) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering publish()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);
         msgUnit = checkMessage(sessionInfo, msgUnit, Constants.PUBLISH);

         XmlKey xmlKey = new XmlKey(glob, msgUnit.xmlKey, true);
         PublishQos publishQos = new PublishQos(glob, msgUnit.qos);

         String ret = requestBroker.publish(sessionInfo, xmlKey, msgUnit, publishQos);

         return sessionInfo.getSecuritySession().exportMessage(ret);
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         log.error(ME, "Internal problem: " + e.toString());
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Internal problem: " + e.toString());
      }
   }

   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(String sessionId, MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering publishArr()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);
         I_Session sec = sessionInfo.getSecuritySession();

         // How to guarantee complete transaction?
         String[] returnArr = new String[msgUnitArr.length];
         for (int ii=0; ii<msgUnitArr.length; ii++) {
            MessageUnit msgUnit = checkMessage(sessionInfo, msgUnitArr[ii], Constants.PUBLISH);
            XmlKey xmlKey = new XmlKey(glob, msgUnit.getXmlKey(), true);
            PublishQos publishQos = new PublishQos(glob, msgUnit.getQos());
            String ret = requestBroker.publish(sessionInfo, xmlKey, msgUnit, publishQos);
            returnArr[ii] = sec.exportMessage(ret);
         }

         return returnArr;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         log.error(ME, "Internal problem: " + e.toString());
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Internal problem: " + e.toString());
      }
   }

   /**
    * Publish messages. 
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void publishOneway(String sessionId, MessageUnit[] msgUnitArr)
   {
      try {
         publishArr(sessionId, msgUnitArr);
      }
      catch (Throwable e) {
         log.error(ME, "Caught exception on publish which can't be delivered to client because of 'oneway' mode: " + e.toString());
      }
   }

   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering erase()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);
         MessageUnit msgUnit = new MessageUnit(xmlKey_literal, EMPTY_BYTES, qos_literal);
         msgUnit = checkMessage(sessionInfo, msgUnit, Constants.ERASE);

         XmlKey xmlKey = new XmlKey(glob, msgUnit.xmlKey);
         EraseQoS eraseQoS = new EraseQoS(glob, msgUnit.qos);
         String [] retArr = requestBroker.erase(sessionInfo, xmlKey, eraseQoS);

         I_Session sec = sessionInfo.getSecuritySession();
         for (int ii=0; ii<retArr.length; ii++)
            retArr[ii] = sec.exportMessage(retArr[ii]);
         return retArr;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         log.error(ME, "Internal problem: " + e.toString());
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Internal problem: " + e.toString());
      }
   }

   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MessageUnit[] get(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering get()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);
         MessageUnit msgUnit = new MessageUnit(xmlKey_literal, EMPTY_BYTES, qos_literal);
         msgUnit = checkMessage(sessionInfo, msgUnit, Constants.GET);

         XmlKey xmlKey = new XmlKey(glob, msgUnit.xmlKey);
         GetQoS getQoS = new GetQoS(glob, msgUnit.qos);
         MessageUnit[] msgUnitArr = requestBroker.get(sessionInfo, xmlKey, getQoS);

         I_Session sec = sessionInfo.getSecuritySession();
         for (int ii=0; ii<msgUnitArr.length; ii++)
            msgUnitArr[ii] = sec.exportMessage(msgUnitArr[ii]);

         return msgUnitArr;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         log.error(ME, "Internal problem: " + e.toString());
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Internal problem: " + e.toString());
      }
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

   /**
    * Check message via security plugin. 
    * <p/>
    * <ul>
    *   <li>First we import the message using the interceptor</li>
    *   <li>Then we do the authorization</li>
    * </ul>
    * @param sessionInfo The sessionInfo (we are already authenticated)
    * @param MessageUnit The message.
    * @param String actionKey (eg. PUBLISH, GET, ...)
    * @exception XmlBlasterException Thrown if seal/signature checks fail, the identity in unknown
    *                                or the message format has errors.<br />
    *            Throws "NotAuthorized" if client may not do the action with this message
    */
   private MessageUnit checkMessage(SessionInfo sessionInfo, MessageUnit msgUnit, String action) throws XmlBlasterException 
   {
      I_Session sessionSecCtx = sessionInfo.getSecuritySession();
      if (sessionSecCtx==null) { // assert
         throw new XmlBlasterException(ME+".accessDenied", "unknown session - internal error.");
      }

      // check the message, if it was treated with confidentiality and integrity
      msgUnit = sessionSecCtx.importMessage(msgUnit);

      // check if ths user is permitted to do this action with this message
      I_Subject subjSecCtx = sessionSecCtx.getSubject();
      if (!subjSecCtx.isAuthorized(action, msgUnit.xmlKey)) {
         throw new XmlBlasterException("NotAuthorized",
                       "Subject '" + subjSecCtx.getName() + "' is not permitted perform action '" + action +
                       "' on key '" + msgUnit.xmlKey + "'");
      }
      
      return msgUnit;
   }

   /**
    * ping xmlBlaster if everything is OK. 
    * <p />
    * @param qos ""
    * @return ""
    */
   public final String ping(String qos)
   {
      return "";
   }
}


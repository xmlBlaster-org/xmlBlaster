/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: XmlBlasterImpl.java,v 1.20 2002/11/26 12:38:25 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.qos.GetQosServer;
import org.xmlBlaster.engine.qos.EraseQosServer;
import org.xmlBlaster.engine.qos.SubscribeQosServer;
import org.xmlBlaster.engine.qos.UnSubscribeQosServer;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.authentication.plugins.PluginManager;
import org.xmlBlaster.engine.helper.MessageUnit;
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
      this.ME = "XmlBlasterImpl" + this.glob.getLogPrefixDashed();
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
         MessageUnit msgUnit = new MessageUnit(glob, xmlKey_literal, EMPTY_BYTES, qos_literal);
         SessionInfo sessionInfo = authenticate.check(sessionId);
         msgUnit = checkMessage(sessionInfo, msgUnit, MethodName.SUBSCRIBE);

         XmlKey xmlKey = new XmlKey(glob, msgUnit.getKey());
         SubscribeQosServer subscribeQoS = new SubscribeQosServer(glob, msgUnit.getQos());

         String ret = requestBroker.subscribe(sessionInfo, xmlKey, subscribeQoS);
         
         return sessionInfo.getSecuritySession().exportMessage(ret);
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         log.error(ME, "Internal problem: " + e.toString());
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_SUBSCRIBE.toString(), e);
      }
   }

   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] unSubscribe(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering unSubscribe(" + sessionId + ", key, qos)");

      try {
         // authentication and authorization security checks
         MessageUnit msgUnit = new MessageUnit(glob, xmlKey_literal, EMPTY_BYTES, qos_literal);
         SessionInfo sessionInfo = authenticate.check(sessionId);
         msgUnit = checkMessage(sessionInfo, msgUnit, MethodName.UNSUBSCRIBE);

         XmlKey xmlKey = new XmlKey(glob, msgUnit.getKey());
         UnSubscribeQosServer unSubscribeQosServer = new UnSubscribeQosServer(glob, msgUnit.getQos());
         return requestBroker.unSubscribe(sessionInfo, xmlKey, unSubscribeQosServer);
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         log.error(ME, "Internal problem: " + e.toString());
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_UNSUBSCRIBE.toString(), e);
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
         msgUnit = checkMessage(sessionInfo, msgUnit, MethodName.PUBLISH);

         XmlKey xmlKey = new XmlKey(glob, msgUnit.getKey(), true);
         PublishQosServer publishQos = new PublishQosServer(glob, msgUnit.getQos());

         String ret = requestBroker.publish(sessionInfo, xmlKey, msgUnit, publishQos);

         return sessionInfo.getSecuritySession().exportMessage(ret);
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         log.error(ME, "Internal problem: " + e.toString());
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_PUBLISH.toString(), e);
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
            MessageUnit msgUnit = checkMessage(sessionInfo, msgUnitArr[ii], MethodName.PUBLISH);
            XmlKey xmlKey = new XmlKey(glob, msgUnit.getKey(), true);
            PublishQosServer publishQos = new PublishQosServer(glob, msgUnit.getQos());
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
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_PUBLISH.toString(), e);
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
         MessageUnit msgUnit = new MessageUnit(glob, xmlKey_literal, EMPTY_BYTES, qos_literal);
         msgUnit = checkMessage(sessionInfo, msgUnit, MethodName.ERASE);

         XmlKey xmlKey = new XmlKey(glob, msgUnit.getKey());
         EraseQosServer eraseQoS = new EraseQosServer(glob, msgUnit.getQos());
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
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_ERASE.toString(), e);
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
         MessageUnit msgUnit = new MessageUnit(glob, xmlKey_literal, EMPTY_BYTES, qos_literal);
         msgUnit = checkMessage(sessionInfo, msgUnit, MethodName.GET);

         XmlKey xmlKey = new XmlKey(glob, msgUnit.getKey());
         GetQosServer getQoS = new GetQosServer(glob, msgUnit.getQos());
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
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_GET.toString(), e);
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
   private MessageUnit checkMessage(SessionInfo sessionInfo, MessageUnit msgUnit, MethodName action) throws XmlBlasterException 
   {
      I_Session sessionSecCtx = sessionInfo.getSecuritySession();
      if (sessionSecCtx==null) { // assert
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".accessDenied", "unknown session - internal error.");
      }

      // check the message, if it was treated with confidentiality and integrity
      msgUnit = sessionSecCtx.importMessage(msgUnit);

      // check if ths user is permitted to do this action with this message
      I_Subject subjSecCtx = sessionSecCtx.getSubject();
      if (!subjSecCtx.isAuthorized(action, msgUnit.getKey())) {
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHORIZATION_NOTAUTHORIZED, ME,
                       "Subject '" + subjSecCtx.getName() + "' is not permitted to perform action '" + action +
                       "' on key '" + msgUnit.getKey() + "'");
      }
      
      return msgUnit;
   }

   /**
    * ping xmlBlaster if everything is OK. 
    * <p />
    * @param qos ""
    * @return "<qos/>"
    */
   public final String ping(String qos)
   {
      return "<qos/>";
   }
}


/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: XmlBlasterImpl.java,v 1.21 2002/12/18 11:22:10 ruff Exp $
Author:    xmlBlaster@marcelruff.info
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
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.authentication.plugins.PluginManager;
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

         // authentication security check
         SessionInfo sessionInfo = authenticate.check(sessionId);
         
         // import and authorize message
         MsgUnitRaw msgUnitRaw = importAndAuthorize(sessionInfo,
                                       new MsgUnitRaw(xmlKey_literal, null, qos_literal),
                                       MethodName.SUBSCRIBE);

         // Parse XML key and XML QoS
         QueryKeyData queryKey = glob.getQueryKeyFactory().readObject(msgUnitRaw.getKey());
         SubscribeQosServer subscribeQos = new SubscribeQosServer(glob, msgUnitRaw.getQos());

         // Invoke xmlBlaster
         String ret = requestBroker.subscribe(sessionInfo, queryKey, subscribeQos);
         
         // export (encrypt) return value
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
         SessionInfo sessionInfo = authenticate.check(sessionId);

         // import and authorize message
         MsgUnitRaw msgUnitRaw = importAndAuthorize(sessionInfo,
                                       new MsgUnitRaw(xmlKey_literal, null, qos_literal),
                                       MethodName.UNSUBSCRIBE);
         
         // Parse XML key and XML QoS
         QueryKeyData queryKey = glob.getQueryKeyFactory().readObject(msgUnitRaw.getKey());
         UnSubscribeQosServer unSubscribeQosServer = new UnSubscribeQosServer(glob, msgUnitRaw.getQos());

         // Invoke xmlBlaster
         String [] retArr = requestBroker.unSubscribe(sessionInfo, queryKey, unSubscribeQosServer);

         // export (encrypt) return value
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
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_UNSUBSCRIBE.toString(), e);
      }
   }

   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish(String sessionId, MsgUnitRaw msgUnitRaw) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering publish()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);
         msgUnitRaw = importAndAuthorize(sessionInfo, msgUnitRaw, MethodName.PUBLISH);

         String ret = requestBroker.publish(sessionInfo, toMsgUnit(msgUnitRaw));

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
    * Parse the raw MsgUnitRaw
    */
   private MsgUnit toMsgUnit(MsgUnitRaw msgUnitRaw) throws XmlBlasterException {
      MsgKeyData key = glob.getMsgKeyFactory().readObject(msgUnitRaw.getKey());
      PublishQosServer qos = new PublishQosServer(glob, msgUnitRaw.getQos());
      return new MsgUnit(glob, key, msgUnitRaw.getContent(), qos.getData());
   }

   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(String sessionId, MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering publishArr()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);
         I_Session sec = sessionInfo.getSecuritySession();

         // How to guarantee complete transaction?
         String[] returnArr = new String[msgUnitArr.length];
         for (int ii=0; ii<msgUnitArr.length; ii++) {
            MsgUnitRaw msgUnitRaw = importAndAuthorize(sessionInfo, msgUnitArr[ii], MethodName.PUBLISH);
            String ret = requestBroker.publish(sessionInfo, toMsgUnit(msgUnitRaw));
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
   public final void publishOneway(String sessionId, MsgUnitRaw[] msgUnitArr) {
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
   public final String[] erase(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering erase()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);

         // import (decrypt) and authorize message
         MsgUnitRaw msgUnitRaw = importAndAuthorize(sessionInfo,
                                       new MsgUnitRaw(xmlKey_literal, null, qos_literal),
                                       MethodName.ERASE);

         // Parse XML key and XML QoS
         QueryKeyData queryKey = glob.getQueryKeyFactory().readObject(msgUnitRaw.getKey());
         EraseQosServer eraseQosServer = new EraseQosServer(glob, msgUnitRaw.getQos());

         // Invoke xmlBlaster
         String [] retArr = requestBroker.erase(sessionInfo, queryKey, eraseQosServer);

         // export (encrypt) return value
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
   public final MsgUnitRaw[] get(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering get()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);

         // import (decrypt) and authorize message
         MsgUnitRaw msgUnitRaw = importAndAuthorize(sessionInfo,
                                       new MsgUnitRaw(xmlKey_literal, null, qos_literal),
                                       MethodName.GET);

         // Parse XML key and XML QoS
         QueryKeyData queryKey = glob.getQueryKeyFactory().readObject(msgUnitRaw.getKey());
         GetQosServer getQosServer = new GetQosServer(glob, msgUnitRaw.getQos());

         // Invoke xmlBlaster
         MsgUnit[] msgUnitArr = requestBroker.get(sessionInfo, queryKey, getQosServer);

         // export (encrypt) return value
         MsgUnitRaw[] msgUnitRawArr = new MsgUnitRaw[msgUnitArr.length];
         I_Session sec = sessionInfo.getSecuritySession();
         for (int ii=0; ii<msgUnitArr.length; ii++)
            msgUnitRawArr[ii] = sec.exportMessage(msgUnitArr[ii].getMsgUnitRaw());

         return msgUnitRawArr;
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
   public final String toXml() throws XmlBlasterException {
      return requestBroker.toXml();
   }

   /**
    * Dump state of RequestBroker into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException {
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
    * @param MsgUnit The message, probably encrypted
    * @param String actionKey (eg. PUBLISH, GET, ...)
    * @return The message decrypted (readable)
    * @exception XmlBlasterException Thrown if seal/signature checks fail, the identity in unknown
    *                                or the message format has errors.<br />
    *            Throws "NotAuthorized" if client may not do the action with this message
    */
   private MsgUnitRaw importAndAuthorize(SessionInfo sessionInfo, MsgUnitRaw msgUnit, MethodName action) throws XmlBlasterException {
      I_Session sessionSecCtx = sessionInfo.getSecuritySession();
      if (sessionSecCtx==null) { // assert
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".accessDenied", "unknown session - internal error.");
      }

      // check the message, if it was treated with confidentiality and integrity
      msgUnit = sessionSecCtx.importMessage(msgUnit);
      /*
      msgUnit = new MsgUnitRaw(
               (msgUnit.getKey().size() > 0) ? sessionSecCtx.importMessage(msgUnit.getKey()) : msgUnit.getKey(), 
               (msgUnit.getContent().length > 0) ? sessionSecCtx.importMessage(msgUnit.getContent()) : msgUnit.getContent(),
               (msgUnit.getQos().size() > 0) ? sessionSecCtx.importMessage(msgUnit.getQos()) : msgUnit.getQos());
      */

      // check if this user is permitted to do this action with this message
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
   public final String ping(String qos) {
      return "<qos/>";
   }
}


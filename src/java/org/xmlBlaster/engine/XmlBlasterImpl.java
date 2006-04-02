/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.engine.qos.GetQosServer;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.EraseQosServer;
import org.xmlBlaster.engine.qos.SubscribeQosServer;
import org.xmlBlaster.engine.qos.UnSubscribeQosServer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.QosData;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.dispatch.DispatchStatistic;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.plugins.CryptDataHolder;
import org.xmlBlaster.authentication.plugins.DataHolder;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.authentication.plugins.SessionHolder;

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
 * @author xmlBlaster@marcelruff.info
 */
public class XmlBlasterImpl implements org.xmlBlaster.protocol.I_XmlBlaster
{
   private final String ME;
   private final RequestBroker requestBroker;
   private final Authenticate authenticate;
   private final AvailabilityChecker availabilityChecker;
   private final ServerScope glob;
   private static Logger log = Logger.getLogger(XmlBlasterImpl.class.getName());
   private final byte[] EMPTY_BYTEARR = null;

   /**
    * One instance of this represents one xmlBlaster server.
    * @param authenticate The authentication service
    */
   public XmlBlasterImpl(Authenticate authenticate) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      this.glob = authenticate.getGlobal();
      this.ME = "XmlBlasterImpl" + this.glob.getLogPrefixDashed();

      this.requestBroker = new RequestBroker(authenticate);
      this.availabilityChecker = new AvailabilityChecker(this.glob);
   }

   /**
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String subscribe(AddressServer addressServer, String sessionId,
                                 String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering subscribe(" + sessionId + ", key, qos)");

      try {
         // authentication security check
         SessionInfo sessionInfo = authenticate.check(sessionId);
         
         // import and authorize message
         MsgUnit msgUnit = importAndAuthorize(sessionInfo, addressServer,
                                       new MsgUnitRaw(xmlKey_literal, EMPTY_BYTEARR, qos_literal),
                                       MethodName.SUBSCRIBE);

         SubscribeQosServer subscribeQos = new SubscribeQosServer(glob, (QueryQosData)msgUnit.getQosData());

         // Invoke xmlBlaster
         String ret = requestBroker.subscribe(sessionInfo, (QueryKeyData)msgUnit.getKeyData(), subscribeQos);


         sessionInfo.getDispatchStatistic().incrNumSubscribe(1);
         
         // export (encrypt) return value
         MsgUnitRaw in = new MsgUnitRaw(null, (byte[])null, ret);
         CryptDataHolder dataHolder = new CryptDataHolder(MethodName.SUBSCRIBE, in);
         dataHolder.setReturnValue(true);
         return sessionInfo.getSecuritySession().exportMessage(dataHolder).getQos();
      }
      catch (Throwable e) {
         throw this.availabilityChecker.checkException(MethodName.SUBSCRIBE, e);
      }
   }

   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] unSubscribe(AddressServer addressServer, String sessionId,
                                     String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering unSubscribe(" + sessionId + ", key, qos)");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);

         // import and authorize message
         MsgUnit msgUnit = importAndAuthorize(sessionInfo, addressServer,
                                       new MsgUnitRaw(xmlKey_literal, EMPTY_BYTEARR, qos_literal),
                                       MethodName.UNSUBSCRIBE);
         
         UnSubscribeQosServer unSubscribeQosServer = new UnSubscribeQosServer(glob, (QueryQosData)msgUnit.getQosData());

         // Invoke xmlBlaster
         String [] retArr = requestBroker.unSubscribe(sessionInfo, (QueryKeyData)msgUnit.getKeyData(), unSubscribeQosServer);

         sessionInfo.getDispatchStatistic().incrNumUnSubscribe(1);

         // export (encrypt) return value
         I_Session sec = sessionInfo.getSecuritySession();
         for (int ii=0; ii<retArr.length; ii++) {
            CryptDataHolder dataHolder = new CryptDataHolder(MethodName.UNSUBSCRIBE, new MsgUnitRaw(null, (byte[])null, retArr[ii]));
            dataHolder.setReturnValue(true);
            retArr[ii] = sec.exportMessage(dataHolder).getQos();
         }
         return retArr;

      }
      catch (Throwable e) {
         throw this.availabilityChecker.checkException(MethodName.UNSUBSCRIBE, e);
      }
   }

   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish(AddressServer addressServer, String sessionId, MsgUnitRaw msgUnitRaw) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publish()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);

         MsgUnit msgUnit = importAndAuthorize(sessionInfo, addressServer, msgUnitRaw, MethodName.PUBLISH);
         
         String ret = requestBroker.publish(sessionInfo, msgUnit);

         sessionInfo.getDispatchStatistic().incrNumPublish(1);

         CryptDataHolder dataHolder = new CryptDataHolder(MethodName.PUBLISH, new MsgUnitRaw(null, (byte[])null, ret));
         dataHolder.setReturnValue(true);
         return sessionInfo.getSecuritySession().exportMessage(dataHolder).getQos();
      }
      catch (Throwable e) {
         throw this.availabilityChecker.checkException(MethodName.PUBLISH, e);
      }
   }

   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(AddressServer addressServer, String sessionId,
                                    MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publishArr()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);
         I_Session sec = sessionInfo.getSecuritySession();

         // How to guarantee complete transaction?
         DispatchStatistic statistic = sessionInfo.getDispatchStatistic();
         String[] returnArr = new String[msgUnitArr.length];
         for (int ii=0; ii<msgUnitArr.length; ii++) {
            // TODO: Implement native PUBLISH_ARR
            MsgUnit msgUnit = importAndAuthorize(sessionInfo, addressServer, msgUnitArr[ii], MethodName.PUBLISH);
            String ret = requestBroker.publish(sessionInfo, msgUnit);
            statistic.incrNumPublish(1);
            CryptDataHolder dataHolder = new CryptDataHolder(MethodName.PUBLISH_ARR, new MsgUnitRaw(null, (byte[])null, ret));
            dataHolder.setReturnValue(true);
            returnArr[ii] = sec.exportMessage(dataHolder).getQos();
         }

         return returnArr;
      }
      catch (Throwable e) {
         throw this.availabilityChecker.checkException(MethodName.PUBLISH_ARR, e);
      }
   }

   /**
    * Publish messages. 
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void publishOneway(AddressServer addressServer, String sessionId, MsgUnitRaw[] msgUnitArr) {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publishOneway()");
      try {
         publishArr(addressServer, sessionId, msgUnitArr);
      }
      catch (Throwable e) {
         log.severe("Caught exception on publish which can't be delivered to client because of 'oneway' mode: " + e.getMessage());
      }
   }

   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase(AddressServer addressServer, String sessionId,
                               String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering erase()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);

         // import (decrypt) and authorize message
         MsgUnit msgUnit = importAndAuthorize(sessionInfo, addressServer,
                                       new MsgUnitRaw(xmlKey_literal, EMPTY_BYTEARR, qos_literal),
                                       MethodName.ERASE);

         EraseQosServer eraseQosServer = new EraseQosServer(glob, (QueryQosData)msgUnit.getQosData());

         // Invoke xmlBlaster
         String [] retArr = requestBroker.erase(sessionInfo, (QueryKeyData)msgUnit.getKeyData(), eraseQosServer);

         sessionInfo.getDispatchStatistic().incrNumErase(1);

         // export (encrypt) return value
         I_Session sec = sessionInfo.getSecuritySession();
         for (int ii=0; ii<retArr.length; ii++) {
            CryptDataHolder dataHolder = new CryptDataHolder(MethodName.ERASE, new MsgUnitRaw(null, (byte[])null, retArr[ii]));
            dataHolder.setReturnValue(true);
            retArr[ii] = sec.exportMessage(dataHolder).getQos();
         }
         return retArr;
      }
      catch (Throwable e) {
         throw this.availabilityChecker.checkException(MethodName.ERASE, e);
      }
   }

   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MsgUnitRaw[] get(AddressServer addressServer, String sessionId,
                                 String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering get()");

      try {
         // authentication and authorization security checks
         SessionInfo sessionInfo = authenticate.check(sessionId);

         // import (decrypt) and authorize message
         MsgUnit msgUnit = importAndAuthorize(sessionInfo, addressServer,
                                   new MsgUnitRaw(xmlKey_literal, EMPTY_BYTEARR, qos_literal),
                                   MethodName.GET);

         // Parse XML key and XML QoS
         GetQosServer getQosServer = new GetQosServer(glob, (QueryQosData)msgUnit.getQosData());

         // Invoke xmlBlaster
         MsgUnit[] msgUnitArr = requestBroker.get(sessionInfo, (QueryKeyData)msgUnit.getKeyData(), getQosServer);

         sessionInfo.getDispatchStatistic().incrNumGet(1);

         // export (encrypt) return value
         MsgUnitRaw[] msgUnitRawArr = new MsgUnitRaw[msgUnitArr.length];
         I_Session sec = sessionInfo.getSecuritySession();
         for (int ii=0; ii<msgUnitArr.length; ii++) {
            CryptDataHolder dataHolder = new CryptDataHolder(MethodName.GET, msgUnitArr[ii].getMsgUnitRaw());
            dataHolder.setReturnValue(true);
            msgUnitRawArr[ii] = sec.exportMessage(dataHolder);
         }

         return msgUnitRawArr;
      }
      catch (Throwable e) {
         throw this.availabilityChecker.checkException(MethodName.GET, e);
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
    * @param addressServer The server side protocol plugin information (like IP and port)
    * @param MsgUnit The message, probably encrypted
    * @param String actionKey (eg. PUBLISH, GET, ...)
    * @return The message decrypted (readable) of type MsgUnit (ready parsed)
    * @exception XmlBlasterException Thrown if seal/signature checks fail, the identity in unknown
    *                                or the message format has errors.<br />
    *            Throws "NotAuthorized" if client may not do the action with this message
    */
   private MsgUnit importAndAuthorize(SessionInfo sessionInfo, AddressServer addressServer, 
                       MsgUnitRaw msgUnitRaw, MethodName action) throws XmlBlasterException {
      I_Session sessionSecCtx = sessionInfo.getSecuritySession();
      if (sessionSecCtx==null) { // assert
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME+".accessDenied", "unknown session - internal error.");
      }

      // check the message, if it was treated with confidentiality and integrity
      CryptDataHolder ctyptDataHolder = new CryptDataHolder(action, msgUnitRaw, null);
      msgUnitRaw = sessionSecCtx.importMessage(ctyptDataHolder);

      // Parse XML key and XML QoS
      MsgUnit msgUnit = new MsgUnit(glob, msgUnitRaw.getKey(), msgUnitRaw.getContent(), msgUnitRaw.getQos(), action);
      QosData qosData = msgUnit.getQosData();

      // Currently we have misused used the clientProperty to transport this information
      if (qosData.getClientProperty(Constants.PERSISTENCE_ID) != null)
         qosData.isFromPersistenceRecovery(true);
      
      // Check if server is ready (throws XmlBlasterException otherwise)
      this.availabilityChecker.checkServerIsReady(sessionInfo.getSessionName(), addressServer, msgUnit, action);

      // Protect against faked sender name
      if (sessionInfo.getConnectQos().isClusterNode()) {
         if (qosData.getSender() == null) // In cluster routing don't overwrite the original sender
            qosData.setSender(sessionInfo.getSessionName());
      }
      else {
         if (qosData.getSender() == null) {
            qosData.setSender(sessionInfo.getSessionName());
         }
         else if (!sessionInfo.getSessionName().equalsAbsolute(qosData.getSender())) {
            //if (! publishQos.isFromPersistenceStore()) {
            if (!this.authenticate.acceptWrongSenderAddress(sessionInfo)) {
               log.warning(sessionInfo.getId() + " sends message '" + msgUnit.getKeyOid() + "' with invalid sender name '" + qosData.getSender() + "', we fix this");
               qosData.setSender(sessionInfo.getSessionName());
            }
            else {
               log.info(sessionInfo.getId() + " sends message '" + msgUnit.getKeyOid() + "' with invalid sender name '" + qosData.getSender() + "', we accept it");
            }
         }
      }

      /*
      msgUnitRaw = new MsgUnitRaw(
               (msgUnit.getKey().size() > 0) ? sessionSecCtx.importMessage(msgUnit.getKey()) : msgUnit.getKey(), 
               (msgUnit.getContent().length > 0) ? sessionSecCtx.importMessage(msgUnit.getContent()) : msgUnit.getContent(),
               (msgUnit.getQos().size() > 0) ? sessionSecCtx.importMessage(msgUnit.getQos()) : msgUnit.getQos());
      */

      // check if this user is permitted to do this action with this message
      I_Subject subjSecCtx = sessionSecCtx.getSubject();
      SessionHolder sessionHolder = new SessionHolder(sessionInfo, addressServer);
      DataHolder dataHolder = new DataHolder(action, msgUnit);
      if (!sessionSecCtx.isAuthorized(sessionHolder, dataHolder)) {
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHORIZATION_NOTAUTHORIZED, ME,
                       "Subject '" + subjSecCtx.getName() + "' is not permitted to perform action '" + action +
                       "' on key '" + msgUnit.getKey() + "'");
      }
      
      return msgUnit;
   }

   /**
    * ping xmlBlaster if everything is OK and if xmlBlaster is willing to accept requests. 
    * @return "<qos><state id='OK'/></qos>" if we are ready, otherwise the current run level string
    * @see org.xmlBlaster.engine.AvailabilityChecker#getStatus(String)
    */
   public final String ping(AddressServer addressServer, String qos) {
      String ret = "<qos><state id='" + this.availabilityChecker.getStatus(qos) + "'/></qos>";
      if (log.isLoggable(Level.FINER)) log.finer("Entering ping("+qos+"), returning " + ret + " ...");
      return ret;
   }

   /**
    * @todo Call me
    */
   public final void shutdown() {
      this.availabilityChecker.shutdown();
   }
}


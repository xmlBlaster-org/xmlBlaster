/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: XmlBlasterImpl.java,v 1.8 2002/02/12 21:51:51 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.plugins.PluginManager;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.authentication.Authenticate;
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
   private String ME = "XmlBlasterImpl";
   private RequestBroker requestBroker;
   private Authenticate authenticate;

   private PluginManager plgnLdr = null;

   // action key --- used to ckeck access rights
   public static final String         GET = "get";
   public static final String       ERASE = "erase";
   public static final String     PUBLISH = "publish";
   public static final String   SUBSCRIBE = "subscribe";
   public static final String UNSUBSCRIBE = "unSubscribe";
   public static final String      UPDATE = "update";
   public static final String        PING = "ping";
   public static final String     CONNECT = "connect";
   public static final String  DISCONNECT = "disconnect";
   //public static final String   EXCEPTION = "exception";

   /**
    * One instance of this represents one xmlBlaster server.
    * @param authenticate The authentication service
    */
   public XmlBlasterImpl(Authenticate authenticate) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      this.requestBroker = new RequestBroker(authenticate);
      plgnLdr = PluginManager.getInstance();
   }

   /**
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   //protected final String subscribe(String sessionId, XmlKey xmlKey, SubscribeQoS subscribeQoS) throws XmlBlasterException
   private final String subscribe(String sessionId, XmlKey xmlKey, SubscribeQoS subscribeQoS) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME+".#subscribe(String, XmlKey, SubscribeQoS)=String", "-------START--------\n");

      ClientInfo clientInfo = authenticate.check(sessionId);

      if (Log.CALL) Log.call(ME+".#subscribe(String, XmlKey, SubscribeQoS)=String", "-------END----------\n");
      return requestBroker.subscribe(clientInfo, xmlKey, subscribeQoS);
   }

   /**
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String subscribe(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME+".+subscribe(String, String, String)=String", "-------START--------\n");

      // --- security checks --------------------------------------------------
      MessageUnit msgUnit = new MessageUnit(xmlKey_literal, "".getBytes(), qos_literal);
      msgUnit = checkMessage(sessionId, msgUnit, SUBSCRIBE);

      // ----------------------------------------------------------------------
      ClientInfo clientInfo = authenticate.check(sessionId);
      XmlKey xmlKey = new XmlKey(msgUnit.getXmlKey());
      SubscribeQoS subscribeQoS = new SubscribeQoS(msgUnit.getQos());

      if (Log.CALL) Log.call(ME+".+subscribe(String, String, String)=String", "-------END----------\n");
      return exportMessage(sessionId, requestBroker.subscribe(clientInfo, xmlKey, subscribeQoS));
   }


   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   //protected final void unSubscribe(String sessionId, XmlKey xmlKey, UnSubscribeQoS unSubscribeQoS) throws XmlBlasterException
   private final void unSubscribe(String sessionId, XmlKey xmlKey, UnSubscribeQoS unSubscribeQoS) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME+".#unSubscribe(String, XmlKey, UnSubscribeQos)=void", "-------START--------\n");
      ClientInfo clientInfo = authenticate.check(sessionId);
      requestBroker.unSubscribe(clientInfo, xmlKey, unSubscribeQoS);
      if (Log.CALL) Log.call(ME+".#unSubscribe(String, XmlKey, UnSubscribeQos)=void", "-------END----------\n");
   }

   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void unSubscribe(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME+".+UnSubscribe(String, XmlKey, UnSubscribeQos)=void", "-------START--------\n");

      // --- security checks --------------------------------------------------
      MessageUnit msgUnit = new MessageUnit(xmlKey_literal, "".getBytes(), qos_literal);
      msgUnit = checkMessage(sessionId, msgUnit, UNSUBSCRIBE);

      // ----------------------------------------------------------------------
      ClientInfo clientInfo = authenticate.check(sessionId);
      XmlKey xmlKey = new XmlKey(msgUnit.getXmlKey());
      UnSubscribeQoS unSubscribeQoS = new UnSubscribeQoS(msgUnit.getQos());
      requestBroker.unSubscribe(clientInfo, xmlKey, unSubscribeQoS);

      if (Log.CALL) Log.call(ME+".+unSubscribe(String, String, String)=void", "-------END----------\n");
   }


   /**
    * Publish a message.
    * <p />
    * If you have in a native driver the XmlKey and PublishQoS objects already
    * use this method to avoid parsing again.
    * @see org.xmlBlaster.engine.RequestBroker
    */
   //protected final String publish(String sessionId, XmlKey xmlKey, MessageUnit msgUnit, PublishQoS publishQoS) throws XmlBlasterException
   private final String publish(String sessionId, XmlKey xmlKey, MessageUnit msgUnit, PublishQoS publishQoS) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME+".#publish(String, XmlKey, MessageUnit, PublishQos)=String", "-------CALLED-------\n");
      msgUnit.setKey(xmlKey.toString());

      return publish(sessionId, msgUnit);
   }

   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish(String sessionId, MessageUnit msgUnit) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME+".+publish(String, MessageUnit)=String", "-------CALLED-------\n");

      // --- security checks --------------------------------------------------
      msgUnit = checkMessage(sessionId, msgUnit, PUBLISH);

      // ----------------------------------------------------------------------
      ClientInfo clientInfo = authenticate.check(sessionId);
      XmlKey xmlKey = new XmlKey(msgUnit.xmlKey, true);
      PublishQoS publishQoS = new PublishQoS(msgUnit.qos);

      return exportMessage(sessionId, requestBroker.publish(clientInfo, xmlKey, msgUnit, publishQoS));
   }


   /**
    * Publish messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr(String sessionId, MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME+".+publishArr(String, MessageUniti[])=String", "-------CALLED-------\n");

      // --- security checks --------------------------------------------------
//      for (int i=0; i<msgUnitArr.length; i++) {
//         msgUnitArr[i] = checkMessage(sessionId, msgUnitArr[i], PUBLISH);
//      }

      // ----------------------------------------------------------------------
      if (msgUnitArr == null) {
         Log.error(ME + ".InvalidArguments", "The argument of method publishArr() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments", "The argument of method publishArr() are invalid");
      }
      // How to guarantee complete transaction?
      String[] returnArr = new String[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         returnArr[ii] = publish(sessionId, msgUnitArr[ii]); // authenticate.check() is in called method
      }
//      return exportMessage(sessionId, returnArr);
      return returnArr;
   }


   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   //protected final String[] erase(String sessionId, XmlKey xmlKey, EraseQoS eraseQoS) throws XmlBlasterException
   private final String[] erase(String sessionId, XmlKey xmlKey, EraseQoS eraseQoS) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME+".#erase(String, XmlKey, EraseQoS)=String[]", "-------CALLED-------\n");

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
      if (Log.CALL) Log.call(ME+".+erase(String, String, String)=String[]", "-------CALLED-------\n");

      // --- security checks --------------------------------------------------
      MessageUnit msgUnit = new MessageUnit(xmlKey_literal, "".getBytes(), qos_literal);
      msgUnit = checkMessage(sessionId, msgUnit, ERASE);

      // ----------------------------------------------------------------------
      ClientInfo clientInfo = authenticate.check(sessionId);
      XmlKey xmlKey = new XmlKey(msgUnit.xmlKey);
      EraseQoS eraseQoS = new EraseQoS(msgUnit.qos);
      return exportMessage(sessionId, requestBroker.erase(clientInfo, xmlKey, eraseQoS));
   }


   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   //protected final MessageUnit[] get(String sessionId, XmlKey xmlKey, GetQoS getQoS) throws XmlBlasterException
   private final MessageUnit[] get(String sessionId, XmlKey xmlKey, GetQoS getQoS) throws XmlBlasterException
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
      // --- security checks --------------------------------------------------
      MessageUnit msgUnit = new MessageUnit(xmlKey_literal, "".getBytes(), qos_literal);
      msgUnit = checkMessage(sessionId, msgUnit, GET);

      // ----------------------------------------------------------------------
      ClientInfo clientInfo = authenticate.check(sessionId);
      XmlKey xmlKey = new XmlKey(msgUnit.xmlKey);
      GetQoS getQoS = new GetQoS(msgUnit.qos);
      return exportMessage(sessionId, requestBroker.get(clientInfo, xmlKey, getQoS));
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
    * @param String Supposed sessionId.
    * @param MessageUnit The message.
    * @param String actionKey (eg. PUBLISH, GET, ...)
    * @exception XmlBlasterException Thrown if seal/signature checks fail, the identity in unknown
    *                                or the message format has errors.
    */
   private MessageUnit checkMessage(String sessionId, MessageUnit msgUnit, String action) throws XmlBlasterException{
      I_Manager secMgr = plgnLdr.getManager(sessionId);
      I_Session sessionSecCtx = secMgr.getSessionById(sessionId);
      if (sessionSecCtx==null) {
         throw new XmlBlasterException(ME+".accessDenied", "unknown session.");
      }

      I_Subject subjSecCtx = sessionSecCtx.getSubject();
      // check the message 'publish', if it was treated with confidentiality and integrity
      msgUnit = sessionSecCtx.importMessage(msgUnit);
      // check if ths user is permitted to publish such a message
      if (!subjSecCtx.isAuthorized(action, msgUnit.xmlKey)) {
         throw new XmlBlasterException(ME+".accessDenied",
                                       "Subject '" + subjSecCtx.getName() +
                                       "' is not permitted perform action '" + PUBLISH +
                                       "' on key '" + msgUnit.xmlKey + "'");
      }
      return msgUnit;
   }

   private MessageUnit exportMessage(String sessionId, MessageUnit msgUnit) throws XmlBlasterException
   {
      I_Manager secMgr = plgnLdr.getManager(sessionId);
      I_Session sessionSecCtx = secMgr.getSessionById(sessionId);
      if (sessionSecCtx==null) {
         throw new XmlBlasterException(ME+".accessDenied", "Unknown session!");
      }

      return sessionSecCtx.exportMessage(msgUnit);
   }

   private MessageUnit[] exportMessage(String sessionId, MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      I_Manager secMgr = plgnLdr.getManager(sessionId);
      I_Session sessionSecCtx = secMgr.getSessionById(sessionId);
      if (sessionSecCtx==null) {
         throw new XmlBlasterException(ME+".accessDenied", "Unknown session!");
      }

      for (int i=0; i<msgUnitArr.length; i++) {
         msgUnitArr[i]=sessionSecCtx.exportMessage(msgUnitArr[i]);
      }

      return msgUnitArr;
   }

   private String exportMessage(String sessionId, String msg) throws XmlBlasterException
   {
      I_Manager secMgr = plgnLdr.getManager(sessionId);
      I_Session sessionSecCtx = secMgr.getSessionById(sessionId);
      if (sessionSecCtx==null) {
         throw new XmlBlasterException(ME+".accessDenied", "Unknown session!");
      }

      return sessionSecCtx.exportMessage(msg);
   }

   private String[] exportMessage(String sessionId, String[] msgArr) throws XmlBlasterException
   {
      I_Manager secMgr = plgnLdr.getManager(sessionId);
      I_Session sessionSecCtx = secMgr.getSessionById(sessionId);
      if (sessionSecCtx==null) {
         throw new XmlBlasterException(ME+".accessDenied", "Unknown session!");
      }

      for (int i=0; i<msgArr.length; i++) {
         msgArr[i]=sessionSecCtx.exportMessage(msgArr[i]);
      }

      return msgArr;
   }

}


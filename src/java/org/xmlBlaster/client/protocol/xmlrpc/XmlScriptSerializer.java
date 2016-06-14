/*------------------------------------------------------------------------------
Name:      EmailConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles connection to xmlBlaster with plain emails
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;

import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.util.xbformat.XmlScriptParser;
import org.xmlBlaster.util.MsgUnitRaw;

/**
 * 
 */
public class XmlScriptSerializer {

   private final Global glob;
   private String secretSessionId = "unknown";
   
   private I_ProgressListener progressListener; // currently not used.
   
   private XmlScriptParser parser;
   
   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter. 
    */
   public XmlScriptSerializer(Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = glob;
      parser = new XmlScriptParser();
      parser.init(glob, null, pluginInfo);
   }

   public void setSecretSessionId(String secretSessionId) {
      this.secretSessionId = secretSessionId;
   }
   
   /**
    * Login to the server. 
    * <p />
    * @param connectQos The encrypted connect QoS 
    * @exception XmlBlasterException if login fails
    */
   public String getConnect(String connectQos) throws XmlBlasterException {
      return getLiteral(connectQos, MethodName.CONNECT, MsgInfo.INVOKE_BYTE);
   }

    /**
    * Does a logout and removes the callback server.
    * <p />
    * @param sessionId The client sessionId
    */       
   public String getDisconnect(String qos) throws XmlBlasterException {
      return getLiteral(qos, MethodName.DISCONNECT, MsgInfo.INVOKE_BYTE);
   }

   /**
    */
   public String getSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      return getLiteral(xmlKey_literal, qos_literal, MethodName.SUBSCRIBE);
   }

   /**
    * 
    */
   public final String getUnSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      return getLiteral(xmlKey_literal, qos_literal, MethodName.UNSUBSCRIBE);
   }

   /**
    * 
    */
   public String getPublish(MsgUnitRaw msgUnit) throws XmlBlasterException {
      return getLiteral(msgUnit, MethodName.PUBLISH);
   }

   /**
    * 
    */
   public String getPublishArr(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      return getLiteral(msgUnitArr, MethodName.PUBLISH_ARR, MsgInfo.INVOKE_BYTE);
   }

   /**
    * 
    */
   public String getPublishOneway(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      return getLiteral(msgUnitArr, MethodName.PUBLISH_ONEWAY, MsgInfo.INVOKE_BYTE);
   }

   /**
    * 
    */
   public String getErase(String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      return getLiteral(xmlKey_literal, qos_literal, MethodName.ERASE);
   }

   /**
    * Synchronous access a message.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html">The interface.get requirement</a>
    */
   public String getGet(String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      return getLiteral(xmlKey_literal, qos_literal, MethodName.GET);
   }

   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String getPing(String qos) throws XmlBlasterException {
	   String ret = getLiteral(qos, MethodName.PING, MsgInfo.INVOKE_BYTE);
	   return ret;
   }
   private static Logger log = Logger.getLogger(XmlScriptSerializer.class.getName());
   
   public String getPingResponse(String qos) throws XmlBlasterException {
      return getLiteral(qos, MethodName.PING, MsgInfo.RESPONSE_BYTE);
   }
   
   public String getUpdateResponse(String ok) throws XmlBlasterException {
      return getLiteral(ok, MethodName.UPDATE, MsgInfo.RESPONSE_BYTE);
   }
   
   public String getUpdateException(String ex) throws XmlBlasterException {
      return getLiteral(ex, MethodName.UPDATE, MsgInfo.EXCEPTION_BYTE);
   }
   
   private String getLiteral(String qos, MethodName methodName, byte typeByte) throws XmlBlasterException {
      MsgUnitRaw[] msgArr = { new MsgUnitRaw(null, (byte[])null, qos) };
      return getLiteral(msgArr, methodName, typeByte);
   }

   private String getLiteral(String key, String qos, MethodName methodName) throws XmlBlasterException {
      MsgUnitRaw[] msgArr = { new MsgUnitRaw(key, (byte[])null, qos) };
      return getLiteral(msgArr, methodName, MsgInfo.INVOKE_BYTE);
   }

   private String getLiteral(MsgUnitRaw[] msgArr, MethodName methodName, byte typeByte) throws XmlBlasterException {
      MsgInfo msgInfo = new MsgInfo(this.glob, typeByte, methodName, secretSessionId, progressListener);
      msgInfo.addMessage(msgArr);
      msgInfo.createRequestId(null);
      return parser.toLiteral(msgInfo);
   }

   private String getLiteral(MsgUnitRaw msgUnit, MethodName methodName) throws XmlBlasterException {
      return getLiteral(new MsgUnitRaw[] { msgUnit }, methodName, MsgInfo.INVOKE_BYTE);
   }
   
}

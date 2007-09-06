/*------------------------------------------------------------------------------
Name:      XmlScriptClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.script;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.ConnectQosSaxFactory;
import org.xmlBlaster.util.qos.DisconnectQosData;
import org.xmlBlaster.util.qos.DisconnectQosSaxFactory;
import org.xmlBlaster.util.xbformat.MsgInfo;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * XmlScriptClient
 * <p>
 * Example for usage:
 * </p>
 * <p>
 * <tt>
 * java javaclients.XmlScript -requestFile inFile.xml -responseFile outFile.xml -updateFile updFile.xml
 * </tt>
 * </p>
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.script.html">The client.script requirement</a>
 */
public class XmlScriptClient extends XmlScriptInterpreter implements I_Callback {
   
   private final String ME = "XmlScriptClient";
   private static Logger log = Logger.getLogger(XmlScriptClient.class.getName());
   private I_XmlBlasterAccess access;
   
   private boolean isConnected;

   private I_MsgUnitCb msgUnitCb;
   
   private final Global glob;
   private I_Callback callback;
   private ConnectQosSaxFactory connectQosFactory;
   private DisconnectQosSaxFactory disconnectQosFactory;

   /**
    * This constructor is the most generic one (more degrees of freedom)
    * @param glob the global to use
    * @param access the I_XmlBlasterAccess to use (can be different from the default 
    *        given by the global.
    * @param callback The I_Callback implementation to be used (you can provide your own desidered behaviour)
    * @param attachments the attachments where to search when a content is stored in the attachment (with the 'link' attribute)
    * @param out the OutputStream where to send the responses of the invocations done to xmlBlaster
    */
   public XmlScriptClient(Global glob, I_XmlBlasterAccess access, I_Callback callback, HashMap attachments, OutputStream out) {
      super(glob, attachments, out);
      this.glob = glob;

      this.access = access;
      this.callback = callback;
      this.connectQosFactory = new ConnectQosSaxFactory(this.glob);
      this.disconnectQosFactory = new DisconnectQosSaxFactory(this.glob);

      if (this.access != null) {
         this.isConnected = this.access.isConnected();
      }
   }

   /**
    * This is a convenience constructor which takes the default I_Callback implementation provided
    * (StreamCallback).
    *  
    * @param glob the global to use
    * @param access the I_XmlBlasterAccess to use (can be different from the default 
    *        given by the global.
    * @param cbStream the OutputStream where to send the information coming in
    *        asynchroneously via the update method (could be different from the
    *        synchroneous output stream).
    * @param responseStream the synchroneous OutputStream
    * @param attachments the attachments where to find attached contents
    * 
    * @see StreamCallback
    */
   public XmlScriptClient(Global glob, I_XmlBlasterAccess access, OutputStream cbStream, OutputStream responseStream, HashMap attachments) {
      this(glob, access, new StreamCallback(glob, cbStream, "  "), attachments, responseStream);
   }

   /**
    * Convenience constructor which takes a minimal amount of parameters. The 
    * accessor taken is the one provided by the given global. The I_Callback 
    * implementation used is the StreamCallback. The asynchroneous output is sent
    * to the same stream as the synchroneous one. 
    * @param glob the global to use. The I_XmlBlasterAccess will be taken from
    *        it.
    * @param out. The OutputStream used for all outputs (sync and async).
    */
   public XmlScriptClient(Global glob, OutputStream out) {
      this(glob, glob.getXmlBlasterAccess(), out, out, null);
   }
   
   /**
    * You can register a callback which can manipulate the MsgUnit just
    * before it is sent. 
    */
   public void registerMsgUnitCb(I_MsgUnitCb msgUnitCb) {
      this.msgUnitCb = msgUnitCb;
   }
   
   public void setProperty(String key, String value) throws XmlBlasterException {
      this.glob.getProperty().set(key, value);
   }
   
   public boolean fireMethod(MethodName methodName,
         String sessionId, String requestId, byte type)
         throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) XmlScriptClient.log.fine("fireMethod "
            + MsgInfo.getTypeStr(type)
            + ": " + methodName.toString()
            + ": " + this.key.toString()
            + " " + this.qos.toString());
      if (type != MsgInfo.INVOKE_BYTE)
         log.warning("Unexpected message of type '" + MsgInfo.getTypeStr(type) + "'");
      try {
         if (MethodName.CONNECT.equals(methodName) || !this.isConnected) {
            boolean implicitConnect = !MethodName.CONNECT.equals(methodName);
            // if (this.qos.length() < 1) this.qos.append("<qos />");
            String ret = null;
            I_Callback cb = null;
            if (this.callback != null) cb = this; // we intercept callbacks
            if (implicitConnect || this.qos.length() < 1) {
               log.warning("Doing implicit xmlBlaster.connect() as no valid <connect/> markup is in the script");
               ConnectQos connectQos = new ConnectQos(this.glob);
               ret = this.access.connect(connectQos, cb).toXml();
            }
            else {
               ConnectQosData data = this.connectQosFactory.readObject(this.qos.toString());
               // nectQosData data = new ConnectQosServer(this.glob, this.qos.toString()).getData();
               ConnectReturnQos tmp = this.access.connect(new ConnectQos(this.glob, data), cb);
               if (tmp != null) ret = tmp.toXml("  ");
               else ret = "";
            }
            writeResponse(methodName, ret);
            this.isConnected = true;
            if (!implicitConnect) {
               return true;
            }
         }
         if (MethodName.DISCONNECT.equals(methodName)) {
            if (this.qos.length() < 1) this.qos.append("<qos />");
            DisconnectQosData disconnectQosData = this.disconnectQosFactory.readObject(this.qos.toString());
            boolean ret = this.access.disconnect(new DisconnectQos(this.glob, disconnectQosData));
            writeResponse(methodName, "\n"+ret);
            return true;
         }

         if (this.qos.length() < 1) this.qos.append("<qos />");
         if (this.key.length() < 1) this.key.append("<key />");

         if (MethodName.PUBLISH.equals(methodName)) {
            MsgUnit msgUnit = buildMsgUnit();
            if (this.msgUnitCb != null) {
               this.msgUnitCb.intercept(msgUnit);
            }
            if (log.isLoggable(Level.FINE)) XmlScriptClient.log.fine("appendEndOfElement publish: " + msgUnit.toXml());
            PublishReturnQos ret = this.access.publish(msgUnit);
            writeResponse(methodName, (ret != null)?ret.toXml("  "):null);
            return true;
         }
         if (MethodName.PUBLISH_ARR.equals(methodName)) {
            int size = this.messageList.size();
            MsgUnit[] msgs = new MsgUnit[size];
            for (int i=0; i < size; i++) {
               if (log.isLoggable(Level.FINE)) XmlScriptClient.log.fine("appendEndOfElement publishArr: " + msgs[i].toXml());
               msgs[i] = (MsgUnit)this.messageList.get(i);
            }
            PublishReturnQos[] ret = this.access.publishArr(msgs);
            String[] retStr = new String[ret.length];
            for (int i=0; i < ret.length; i++) retStr[i] = ret[i].toXml("    ");
            writeResponse(methodName, retStr);
            return true;
         }
         if (MethodName.PUBLISH_ONEWAY.equals(methodName)) {
            int size = this.messageList.size();
            MsgUnit[] msgs = new MsgUnit[size];
            for (int i=0; i < size; i++) {
               if (log.isLoggable(Level.FINE)) XmlScriptClient.log.fine("appendEndOfElement publishArr: " + msgs[i].toXml());
               msgs[i] = (MsgUnit)this.messageList.get(i);
            }
            this.access.publishOneway(msgs);
            return true;
         }
         if (MethodName.SUBSCRIBE.equals(methodName)) {
            SubscribeReturnQos ret = this.access.subscribe(this.key.toString(), this.qos.toString());
            writeResponse(methodName, ret.toXml("    "));
            return true;
         }
         if (MethodName.UNSUBSCRIBE.equals(methodName)) {
            UnSubscribeReturnQos[] ret = this.access.unSubscribe(this.key.toString(), this.qos.toString());
            String[] retStr = new String[ret.length];
            for (int i=0; i < ret.length; i++) retStr[i] = ret[i].toXml("    ");
            writeResponse(methodName, retStr);
            return true;
         }
         if (MethodName.ERASE.equals(methodName)) {
            EraseReturnQos[] ret = this.access.erase(this.key.toString(), this.qos.toString());
            String[] retStr = new String[ret.length];
            for (int i=0; i < ret.length; i++) retStr[i] = ret[i].toXml("    ");
            writeResponse(methodName, retStr);
            return true;
         }
         if (MethodName.GET.equals(methodName)) {
            MsgUnit[] ret = this.access.get(this.key.toString(), this.qos.toString());
            String[] retStr = new String[ret.length];
            for (int i=0; i < ret.length; i++) retStr[i] = ret[i].toXml("    ");
            writeResponse(methodName, retStr);
            return true;
         }
      }
      catch (XmlBlasterException e) {
         log.warning(e.getMessage());
         
         // The exception has already a <exception> root tag
         writeResponse(null/*MethodName.EXCEPTION*/, e.toXml("    "));
         
         // For connect excpetions we stop parsing
         if (MethodName.CONNECT.equals(methodName))
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_STOP, ME, "Connection failed", e);
         
         return true;
      }
      catch (Throwable e) {
         log.severe(e.toString());
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME, "", e);
      }
      
      log.warning("fireMethod with methodName=" + methodName.toString() + " is not implemented: " + this.key.toString() + " " + this.qos.toString());
      return false;
   }

   /*
<xmlBlasterResponse>
  <publish>
     <qos>
      <key oid='1'/>
      <rcvTimestamp nanos='1131654994574000000'/>
     <isPublish/>
     </qos>
  </publish>
  <publishArr>
    <message>
       <qos>
          <key oid='test'/>
          <rcvTimestamp nanos='1075409585342000001'/>
          <isPublish/>
       </qos>
    </message>
    <message>
       <qos>
          <key oid='test'/>
          <rcvTimestamp nanos='1075409585348000001'/>
          <isPublish/>
       </qos>
    </message>
  </publishArr>
</xmlBlasterResponse>
    */
   /**
    * Write respone message to OutputStream. 
    * @param methodName Can be null
    * @param message A well formed XML message or null
    */
   private void writeResponse(MethodName methodName, String message) throws XmlBlasterException {
      String[] messages = new String[(message==null) ? 0 : 1];
      if (messages.length == 1) messages[0] = message; 
      writeResponse(methodName, messages);
   }
   
   private void writeResponse(MethodName methodName, String[] messages) throws XmlBlasterException {
      //super.response.append("\n<!-- __________________________________ ").append((methodName==null)?"":methodName.toString()).append(" response _____________________ -->");
      if (methodName != null) super.response.append("\n<").append(methodName.toString()).append(">");
      if (messages != null && messages.length > 0) {
         if (messages.length == 1)
            super.response.append(messages[0]);
         else {
            for (int i=0; i < messages.length; i++) {
               super.response.append("\n  <message>");
               super.response.append(messages[i]);
               super.response.append("\n  </message>\n");
            }
         }
      }
      if (methodName != null) super.response.append("\n</").append(methodName.toString()).append(">\n");
      flushResponse();
   }
   
   public static void main(String[] args) {
      /*
      String request = "<xmlBlaster>\n" +
                       "  <connect>" +
                       "    <securityPlugin type='aaa' version='bbb'>\n" +
                       "      <user>michele</user>\n" +
                       "      <passwd><![CDATA[secret    ]]></passwd>\n" +
                       "    </securityPlugin>\n" +
                       "  </connect>\n" +
                       "  <publish>\n" +
                       "    <key>xxxx</key>\n" +
                       "    <content xlink='sss'/>\n" +
                       "    <qos></qos>\n" +
                       "  </publish>\n" +
                       "  <subscribe/>\n" +
                       "  <disconnect/>\n" +
                       "</xmlBlaster>";
      */
      try {
         Global glob = new Global();
         String[] tmp = new String[args.length-2];
         for (int i=0; i < tmp.length; i++) tmp[i] = args[i+2];
         
         glob.init(tmp);
         FileOutputStream out = new FileOutputStream(args[1]);
         XmlScriptClient client = new XmlScriptClient(glob, out);
         FileReader in = new FileReader(args[0]);
         client.parse(in);
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }

	public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
		super.update(cbSessionId, updateKey, content, updateQos);
		if (this.callback != null) {
			return this.callback.update(cbSessionId, updateKey, content, updateQos);
		}
		return null;
	}
}

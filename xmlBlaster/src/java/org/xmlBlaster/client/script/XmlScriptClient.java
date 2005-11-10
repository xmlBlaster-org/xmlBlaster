/*------------------------------------------------------------------------------
Name:      XmlScriptClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.script;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.ConnectQosSaxFactory;
import org.xmlBlaster.util.qos.DisconnectQosData;
import org.xmlBlaster.util.qos.DisconnectQosSaxFactory;

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
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.script.html">The client.script requirement</a>
 */
public class XmlScriptClient extends XmlScriptInterpreter {
   
   private final String ME = "XmlScriptClient";
   private final LogChannel log;
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
      this.log = glob.getLog("script");
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
   
   public void fireMethod(MethodName methodName) throws XmlBlasterException {
      if (MethodName.CONNECT.equals(methodName) || !this.isConnected) {
         boolean implicitConnect = !MethodName.CONNECT.equals(methodName);
         if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement connect: " + this.qos.toString());
         // if (this.qos.length() < 1) this.qos.append("<qos />");
         String ret = null;
         if (implicitConnect || this.qos.length() < 1) {
            ConnectQos connectQos = new ConnectQos(this.glob);
            ret = this.access.connect(connectQos, this.callback).toXml();
         }
         else {
            ConnectQosData data = this.connectQosFactory.readObject(this.qos.toString());
            // nectQosData data = new ConnectQosServer(this.glob, this.qos.toString()).getData();
            ConnectReturnQos tmp = this.access.connect(new ConnectQos(this.glob, data), this.callback);
            if (tmp != null) ret = tmp.toXml("  ");
            else ret = "";
         }
         this.response.append("\n<!-- __________________________________  connect ________________________________ -->");
         this.response.append("\n<connect>");
         this.response.append(ret);
         this.response.append("\n</connect>\n");
         flushResponse();
         this.isConnected = true;
         if (!implicitConnect) {
            return;
         }
      }
      if (MethodName.DISCONNECT.equals(methodName)) {
         if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement disconnect: " + this.qos.toString());
         if (this.qos.length() < 1) this.qos.append("<qos />");
         DisconnectQosData disconnectQosData = this.disconnectQosFactory.readObject(this.qos.toString());
         boolean ret = this.access.disconnect(new DisconnectQos(this.glob, disconnectQosData));
         this.response.append("\n<!-- __________________________________  disconnect _____________________________ -->");
         this.response.append("\n<disconnect>").append(ret).append("</disconnect>\n");
         flushResponse();
         return;
      }
      if (MethodName.PUBLISH.equals(methodName)) {
         if (this.qos.length() < 1) this.qos.append("<qos />");
         if (this.key.length() < 1) this.key.append("<key />");
         MsgUnit msgUnit = buildMsgUnit();
         if (this.msgUnitCb != null) {
            this.msgUnitCb.intercept(msgUnit);
         }
         if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement publish: " + msgUnit.toXml());
         PublishReturnQos ret = this.access.publish(msgUnit);
         this.response.append("\n<!-- __________________________________  publish ________________________________ -->");
         this.response.append("\n<publish>");
         // this.response.append("  <messageId>");
         if (ret != null) this.response.append(ret.toXml("  "));
         // this.response.append("  </messageId>\n");
         this.response.append("\n</publish>\n");
         flushResponse();
         return;
      }
      if (MethodName.PUBLISH_ARR.equals(methodName)) {
         if (this.qos.length() < 1) this.qos.append("<qos />");
         if (this.key.length() < 1) this.key.append("<key />");
         int size = this.messageList.size();
         MsgUnit[] msgs = new MsgUnit[size];
         for (int i=0; i < size; i++) {
            if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement publishArr: " + msgs[i].toXml());
            msgs[i] = (MsgUnit)this.messageList.get(i);
         }
         PublishReturnQos[] ret = this.access.publishArr(msgs);
         this.response.append("\n<!-- __________________________________  publishArr _____________________________ -->");
         this.response.append("\n<publishArr>");
         if (ret != null) {
            for (int i=0; i < ret.length; i++) {
               this.response.append("\n  <message>");
               this.response.append(ret[i].toXml("    "));
               this.response.append("\n  </message>\n");
            }
         }
         this.response.append("\n</publishArr>\n");
         flushResponse();
         return;
      }
      if (MethodName.SUBSCRIBE.equals(methodName)) {
         if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement subscribe: " + this.key.toString() + " " + this.qos.toString());
         if (this.qos.length() < 1) this.qos.append("<qos />");
         if (this.key.length() < 1) this.key.append("<key />");
         SubscribeReturnQos ret = this.access.subscribe(this.key.toString(), this.qos.toString());
         this.response.append("\n<!-- __________________________________  subscribe ______________________________ -->");
         this.response.append("\n<subscribe>");
         // this.response.append("  <subscribeId>");
         if (ret != null) this.response.append(ret.toXml("    "));
         // this.response.append("  </subscribeId>\n");
         this.response.append("\n</subscribe>\n");
         flushResponse();
         return;
      }
      if (MethodName.UNSUBSCRIBE.equals(methodName)) {
         if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement unSubscribe: " + this.key.toString() + " " + this.qos.toString());
         if (this.qos.length() < 1) this.qos.append("<qos />");
         if (this.key.length() < 1) this.key.append("<key />");
         
         UnSubscribeReturnQos[] ret = this.access.unSubscribe(this.key.toString(), this.qos.toString());
         this.response.append("\n<!-- __________________________________  unSubscribe ____________________________ -->");
         this.response.append("\n<unSubscribe>");
         if (ret != null) for (int i=0; i < ret.length; i++) this.response.append(ret[i].toXml("  "));
         this.response.append("\n</unSubscribe>\n");

         flushResponse();
         return;
      }
      if (MethodName.ERASE.equals(methodName)) {
         if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement erase: " + this.key.toString() + " " + this.qos.toString());
         if (this.qos.length() < 1) this.qos.append("<qos />");
         if (this.key.length() < 1) this.key.append("<key />");
         EraseReturnQos[] ret = this.access.erase(this.key.toString(), this.qos.toString());
         this.response.append("\n<!-- __________________________________  erase __________________________________ -->");
         this.response.append("\n<erase>");
         if (ret != null) {
            for (int i=0; i < ret.length; i++) {
               // this.response.append("  <messageId>");
               this.response.append(ret[i].toXml("  "));
               // this.response.append("  </messageId>\n");
            }
         }
         this.response.append("\n</erase>\n");
         flushResponse();
         return;
      }
      if (MethodName.GET.equals(methodName)) {
         if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement get: " + this.key.toString() + " " + this.qos.toString());
         if (this.qos.length() < 1) this.qos.append("<qos />");
         if (this.key.length() < 1) this.key.append("<key />");
         MsgUnit[] ret = this.access.get(this.key.toString(), this.qos.toString());
         this.response.append("\n<!-- __________________________________  get ____________________________________ -->");
         this.response.append("\n<get>");
         if (ret != null) {
            for (int i=0; i < ret.length; i++) {
               this.response.append("\n  <message>");
               this.response.append(ret[i].toXml("    "));
               this.response.append("\n  </message>");
            }
         }
         this.response.append("\n</get>\n");
         flushResponse();
         return;
      }
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
         XmlScriptClient interpreter = new XmlScriptClient(glob, out);
         FileReader in = new FileReader(args[0]);
         interpreter.parse(in);
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
}

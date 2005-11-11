/*------------------------------------------------------------------------------
Name:      XmlScriptParser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.xbformat;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.script.I_MsgUnitCb;
import org.xmlBlaster.client.script.XmlScriptInterpreter;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.protocol.email.Pop3Driver;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.DisconnectQosData;
import org.xmlBlaster.util.MsgUnitRaw;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * XmlScriptParser class for XML formated messages. 
 * <br />
 * This class creates and parses xml script messages which can be used
 * to transfer over a email or socket connection.
 * <br />
 * XmlScriptParser instances may be reused, but are NOT reentrant (there are many 'global' variables)
 * <br />
 * Please read the requirement specification
 * <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.script.html">The client.script requirement</a>
 * @author xmlBlaster@marcelruff.info
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.script.html">The client.script requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 */
public class XmlScriptParser extends XmlScriptInterpreter implements I_MsgInfoParser
{
   private static Logger log = Logger.getLogger(XmlScriptParser.class.getName());
   private static final String ME = "xbformat.XmlScriptParser";
   private Global glob;

   public static final String XMLSCRIPT_EXTENSION = ".xml";
   public static final String XMLSCRIPT_ZLIB_EXTENSION = ".xmlz";

   /** If not null somebody wants to be notified about the current bytes send over socket */
   private I_ProgressListener progressListener;
   
   private MsgInfo msgInfoParsed;

   public XmlScriptParser() {
   }

   public void init(Global glob, I_ProgressListener progressListener) {
      this.glob = glob;
      this.progressListener = progressListener;
      super.initialize(glob, null, null);
   }
   
   /**
    * @param isCompressed true/false
    * @return XMLSCRIPT_EXTENSION = ".xml";
    *         XMLSCRIPT_ZLIB_EXTENSION = ".xmlz";
    */
   public final String getExtension(boolean isCompressed) {
      return (isCompressed) ? XMLSCRIPT_ZLIB_EXTENSION : XMLSCRIPT_EXTENSION; 
   }

   /**
    * This parses the raw message from an InputStream (typically from a email or a socket).
    * Use the get...() methods to access the data.
    * <p />
    * This method blocks until a message arrives
    */
   public final MsgInfo parse(InputStream in) throws  IOException, IllegalArgumentException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering parse()");
      this.msgInfoParsed = new MsgInfo(this.glob);
      this.msgInfoParsed.setMsgInfoParser(this);

      try {
         Reader reader = new InputStreamReader(in);
         //XmlScriptInterpreter interpreter = new XmlScriptInterpreter(this.glob, this.glob.getXmlBlasterAccess(), this.outStream, this.updStream, null);
         //interpreter.parse(reader);
      }
      catch (Exception e) {
         log.severe("Client failed: " + e.toString());
         // e.printStackTrace();
      }
      
      if (log.isLoggable(Level.FINE)) log.fine("Leaving parse(), message successfully parsed");
      return this.msgInfoParsed;
   }

   /**
    * Called during XML parsing. 
    */
   public boolean fireMethod(MethodName methodName) throws XmlBlasterException {
      MsgUnitRaw msgUnitRaw = new MsgUnitRaw(
                            super.key.toString(),
                            super.content.toString().getBytes(),
                            super.qos.toString());
      this.msgInfoParsed.setMethodName(methodName);
      this.msgInfoParsed.addMessage(msgUnitRaw);
      return true;
      /*
      if (MethodName.CONNECT.equals(methodName)) {
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
      */
   }

   /**
    * Returns a XML data string.
    */
   public final byte[] createRawMsg(MsgInfo msgInfo) throws XmlBlasterException {

      try {
         long len = msgInfo.getUserDataLen() + 500;
         ByteArray out = new ByteArray((int)len);
         out.write("XXXDummy".getBytes());
         return out.toByteArray();
      }
      catch(IOException e) {
         String text = "Creation of message failed.";
         log.warning(text + " " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, text, e);
      }
   }

   /**
    * Get the raw messages as a string, for tests and for dumping only
    * @return The stringified message, null bytes are replaced by '*'
    */
   public final String toLiteral(MsgInfo msgInfo) throws XmlBlasterException {
      return createLiteral(createRawMsg(msgInfo));
   }

   public final String toLiteral(byte[] arr) {
      return createLiteral(arr);
   }
   
   /**
    * Get the raw messages as a string, for tests and for dumping only
    * @return The stringified message, null bytes are replaced by '*'
    */
   public static final String createLiteral(byte[] arr) {
      StringBuffer buffer = new StringBuffer(arr.length+10);
      byte[] dummy = new byte[1];
      for (int ii=0; ii<arr.length; ii++) {
         if (arr[ii] == 0)
            buffer.append("*");
         else {
            dummy[0] = arr[ii];
            buffer.append(new String(dummy));
         }
      }
      return buffer.toString();
   }


   public static void main( String[] args ) {
   }
}

/*------------------------------------------------------------------------------
Name:      XmlScriptInterpreter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.script;

import org.xmlBlaster.util.EncodableData;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;

import org.jutils.log.LogChannel;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.xmlBlaster.util.Global;

/**
 * Parse email body. 
 *
 *  <subscribe>
 *     <key>....</key>
 *     <qos>...</qos>
 *  </subscribe>
 *
 *  <unSubscribe>
 *     <key>
 *     </key>
 *  </unSubscribe>
 *
 *  <publish>
 *     <key></key>
 *     <content link='ff'></content>
 *     <qos>...</qos>  
 *  </publish>
 *
 *  <publishArr>
 *     <key></key>
 *     <content link='ff'></content>
 *     <qos>...</qos>
 *     <key></key>
 *     <content link='ff'></content>
 *     <qos>...</qos>
 *       .......
 *     <key></key>
 *     <content link='ff'></content>
 *     <qos>...</qos>
 *  </publishArr>
 *
 *  <erase>
 *     <key></key>
 *     <qos>...</qos>
 *  </erase>
 *
 *  <erase>
 *     <key></key>
 *     <qos>...</qos>
 *  </erase>
 */


/**
 * XmlScriptInterpreter
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class XmlScriptInterpreter extends SaxHandlerBase {
   
   private final String ME = "XmlScriptInterpreter";
   private final LogChannel log;
   private I_XmlBlasterAccess access;
   
   // private boolean isInXmlBlaster = false;
   private HashSet commandsToFire = new HashSet();
   private StringBuffer qos = new StringBuffer();
   private StringBuffer key = new StringBuffer();
   private StringBuffer content = new StringBuffer();
   private EncodableData contentData;
   private boolean inQos, inKey, inContent;
   private String link;
   private HashMap attachments;
   private ArrayList messageList;
   private StringBuffer response;
   private final Global glob;
   private I_Callback callback;
   private OutputStream out;
   private String currentCommand;
   
   public XmlScriptInterpreter(Global glob, I_XmlBlasterAccess access, I_Callback callback, HashMap attachments, OutputStream out) {
      super();
      this.glob = glob;
      this.log = glob.getLog("script");
      this.commandsToFire.add("connect");
      this.commandsToFire.add("subscribe");
      this.commandsToFire.add("unsubscribe");
      this.commandsToFire.add("publish");
      this.commandsToFire.add("publishArr");
      this.commandsToFire.add("erase");
      this.commandsToFire.add("disconnect");

      this.response = new StringBuffer("<xmlBlasterResponse>\n");
      this.access = access;
      this.callback = callback;
      this.attachments = attachments;
      this.out = out;
   }

   public XmlScriptInterpreter(Global glob, I_XmlBlasterAccess access, OutputStream cbStream, OutputStream responseStream, HashMap attachments) {
      this(glob, access, new StreamCallback(cbStream, "  "), attachments, responseStream);
   }

   public XmlScriptInterpreter(Global glob, OutputStream out) {
      this(glob, glob.getXmlBlasterAccess(), out, out, null);
   }

   /**
    * converts the tag start to a string
    * @param qName
    * @param attr
    * @return
    */
   protected String writeElementStart(String qName, Attributes attr) {
      this.link = null;
      StringBuffer buf = new StringBuffer("<");
      buf.append(qName);
      int nmax = attr.getLength();
      for (int i=0; i < nmax; i++) {
         String name = attr.getQName(i);
         String value = attr.getValue(i);
         if ("content".equals(qName))
            if ("link".equalsIgnoreCase(name)) this.link = value;
         buf.append(' ');
         buf.append(name);
         buf.append("='");
         buf.append(value);
         buf.append('\'');
      }
      buf.append('>');
      return buf.toString();
   }

   public synchronized void parse(InputStream in) throws XmlBlasterException {
      this.init(new InputSource(in));
   }

   public void characters(char[] ch, int start, int length) {
      if (this.inQos) this.qos.append(ch);
      else if (this.inKey) this.key.append(ch);
      else if (this.inContent) {
         String tmp = new String(ch, start, length);
         this.content.append(tmp);
      } 
      else super.characters(ch, start, length);
   }

   public void startElement(String namespaceURI, String localName, String qName, Attributes atts) {
      if (this.commandsToFire.contains(qName) && !this.inQos && !this.inKey && !this.inContent) {
         this.character = new StringBuffer();
         this.character.append(this.writeElementStart(qName, atts));
         this.currentCommand = qName; 
         return;
      }

      if ("key".equals(qName)) {
         this.inKey = true;
         this.key = new StringBuffer();
         this.key.append(this.writeElementStart(qName, atts));
         return;
      }

      if ("qos".equals(qName)) {
         this.inQos = true;
         this.qos = new StringBuffer();
         this.qos.append(this.writeElementStart(qName, atts));
         return;
      }

      if ("content".equals(qName)) {
         this.inContent = true;
         this.content = new StringBuffer();
         String type = atts.getValue("type");
         String encoding = atts.getValue("encoding");
         this.contentData = new EncodableData(this.glob, "content", null, type, encoding);
         // this.content.append(this.writeElementStart(qName, atts));
         return;
      }
   }

   private void appendEndOfElement(StringBuffer buf, String qName) {
      buf.append("</");
      buf.append(qName);
      buf.append('>');
      try {
         if ("connect".equals(qName)) {
            if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement connect: " + this.qos.toString());
            // if (this.qos.length() < 1) this.qos.append("<qos />");
            String ret = null;
            if (this.qos.length() < 1) {
               ConnectQos connectQos = new ConnectQos(this.glob);
               ret = this.access.connect(connectQos, this.callback).toXml();
            }
            else {
               ConnectQosServer connectQos = new ConnectQosServer(this.glob, this.qos.toString());
               ret = this.access.connect(new ConnectQos(this.glob, connectQos.getData()), this.callback).toXml();
            }
            this.response.append("<connect>\n");
            this.response.append(ret);
            this.response.append("</connect>\n");
            flushResponse();
            return;
         }
         if ("disconnect".equals(qName)) {
            if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement disconnect: " + this.qos.toString());
            if (this.qos.length() < 1) this.qos.append("<qos />");
            DisconnectQosServer disconnectQos = new DisconnectQosServer(this.glob, this.qos.toString());
            this.access.disconnect(new DisconnectQos(this.glob, disconnectQos.getData()));
            flushResponse();
            return;
         }
         if ("publish".equals(qName)) {
            if (this.qos.length() < 1) this.qos.append("<qos />");
            if (this.key.length() < 1) this.key.append("<key />");
            MsgUnit msgUnit = buildMsgUnit();
            if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement publish: " + msgUnit.toXml());
            String ret = this.access.publish(msgUnit).toXml();
            this.response.append("<publish>\n");
            // this.response.append("  <messageId>");
            this.response.append(ret);
            // this.response.append("  </messageId>\n");
            this.response.append("</publish>\n");
            flushResponse();
            return;
         }
         if ("publishArr".equals(qName)) {
            if (this.qos.length() < 1) this.qos.append("<qos />");
            if (this.key.length() < 1) this.key.append("<key />");
            int size = this.messageList.size();
            MsgUnit[] msgs = new MsgUnit[size];
            for (int i=0; i < size; i++) {
               if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement publishArr: " + msgs[i].toXml());
               msgs[i] = (MsgUnit)this.messageList.get(i);
            }
            PublishReturnQos[] ret = this.access.publishArr(msgs);
            this.response.append("<publishArr>\n");
            for (int i=0; i < ret.length; i++) {
               // this.response.append("  <messageId>");
               this.response.append(ret[i].toXml());
               // this.response.append("  </messageId>\n");
            }
            this.response.append("</publishArr>\n");
            flushResponse();
            return;
         }
         if ("subscribe".equals(qName)) {
            if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement subscribe: " + this.key.toString() + " " + this.qos.toString());
            if (this.qos.length() < 1) this.qos.append("<qos />");
            if (this.key.length() < 1) this.key.append("<key />");
            String ret = this.access.subscribe(this.key.toString(), this.qos.toString()).toXml();
            this.response.append("<subscribe>\n");
            // this.response.append("  <subscribeId>");
            this.response.append(ret);
            // this.response.append("  </subscribeId>\n");
            this.response.append("</subscribe>\n");
            flushResponse();
            return;
         }
         if ("unSubscribe".equals(qName)) {
            if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement unSubscribe: " + this.key.toString() + " " + this.qos.toString());
            if (this.qos.length() < 1) this.qos.append("<qos />");
            if (this.key.length() < 1) this.key.append("<key />");
            this.access.unSubscribe(this.key.toString(), this.qos.toString());
            flushResponse();
            return;
         }
         if ("erase".equals(qName)) {
            if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement erase: " + this.key.toString() + " " + this.qos.toString());
            if (this.qos.length() < 1) this.qos.append("<qos />");
            if (this.key.length() < 1) this.key.append("<key />");
            EraseReturnQos[] ret = this.access.erase(this.key.toString(), this.qos.toString());
            this.response.append("<erase>\n");
            for (int i=0; i < ret.length; i++) {
               // this.response.append("  <messageId>");
               this.response.append(ret[i].toXml());
               // this.response.append("  </messageId>\n");
            }
            this.response.append("</erase>\n");
            flushResponse();
            return;
         }
         if ("get".equals(qName)) {
            if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement get: " + this.key.toString() + " " + this.qos.toString());
            if (this.qos.length() < 1) this.qos.append("<qos />");
            if (this.key.length() < 1) this.key.append("<key />");
            MsgUnit[] ret = this.access.get(this.key.toString(), this.qos.toString());
            this.response.append("<get>\n");
            this.response.append("  <message>\n");
            for (int i=0; i < ret.length; i++) {
               this.response.append(ret[i].toXml());
            }
            this.response.append("  </message>\n");
            this.response.append("</get>\n");
            flushResponse();
            return;
         }
         if ("qos".equals(qName)) {
            this.inQos = false;
         }
         if ("key".equals(qName)) {
            this.inKey = false;
         }
         if ("content".equals(qName)) {
            this.inContent = false;
         }
      }
      catch (Exception ex) {  // handle here the exception
         ex.printStackTrace();
      }
   }

   private void flushResponse() throws IOException {
      try {
         if (this.out != null) {
            synchronized(this.out) {
               this.out.write(this.response.toString().getBytes());
            }
         }   
      }
      finally {
         this.response = new StringBuffer();
      }
   }

   private MsgUnit buildMsgUnit() throws XmlBlasterException {
      byte[] currentContent = null;
      if (this.link == null)
         currentContent = this.contentData.getBlobValue(); 
      else {
         if (this.attachments != null && this.attachments.containsKey(this.link)) {
            currentContent = (byte[])this.attachments.get(this.link);
         }
         else {
            // throw exception
         }
      }
      MsgUnit msgUnit = new MsgUnit(this.key.toString(),
         currentContent, this.qos.toString());
      return msgUnit;
   }

   public void endElement(String namespaceURI, String localName, String qName) {
      if (this.inQos) appendEndOfElement(this.qos, qName);
      if (this.inKey) appendEndOfElement(this.key, qName);

      if ("content".equals(qName)) {
         this.inContent = false;
         this.contentData.setValueRaw(this.content.toString());
         return;
      }
      if ("key".equals(qName)) {
         this.inKey = false;
         return;
      }
      if ("qos".equals(qName)) {
         this.inQos = false;
         return;
      }
      // comes here since the end tag is not part of the content
      if (this.inContent) appendEndOfElement(this.content, qName);

      if (commandsToFire.contains(qName)) {
         appendEndOfElement(this.character, qName);
         return;
      }
   }


   public static void main(String[] args)
   {
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

      try {
         Global glob = new Global();
         String[] tmp = new String[args.length-2];
         for (int i=0; i < tmp.length; i++) tmp[i] = args[i+2];
         
         glob.init(tmp);
         FileOutputStream out = new FileOutputStream(args[1]);
         XmlScriptInterpreter interpreter = new XmlScriptInterpreter(glob, out);
         InputStream in = new FileInputStream(args[0]);
         interpreter.parse(in);
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }

}

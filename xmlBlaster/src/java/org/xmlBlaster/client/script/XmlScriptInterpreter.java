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

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.DisconnectQosServer;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.qos.ConnectQosData;

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
   
   /** a set of names of allowed commands */   
   private HashSet commandsToFire = new HashSet();
   
   private StringBuffer qos = new StringBuffer();
   private StringBuffer key = new StringBuffer();
   private StringBuffer content = new StringBuffer();

   /** Encapsulates the content of the current message (useful for encoding) */
   private EncodableData contentData;
   // private boolean inQos, inKey, inContent;
   private int inQos, inKey, inContent;
   private String link;
   
   /** the attachments (some contents can be in the attachments) */
   private HashMap attachments;
   
   /** used to accumulate all messages to be sent with publishArr */
   private ArrayList messageList;
   
   /** buffer used as a place holder for the responses of the xmlBlaster invocations */
   private StringBuffer response;
   private final Global glob;
   private I_Callback callback;
   private OutputStream out;
   private String currentCommand;
   
   /**
    * This constructor is the most generic one (more degrees of freedom)
    * @param glob the global to use
    * @param access the I_XmlBlasterAccess to use (can be different from the default 
    *        given by the global.
    * @param callback The I_Callback implementation to be used (you can provide your own desidered behaviour)
    * @param attachments the attachments where to search when a content is stored in the attachment (with the 'link' attribute)
    * @param out the OutputStream where to send the responses of the invocations done to xmlBlaster
    */
   public XmlScriptInterpreter(Global glob, I_XmlBlasterAccess access, I_Callback callback, HashMap attachments, OutputStream out) {
      super();
      this.glob = glob;
      this.log = glob.getLog("script");
      this.commandsToFire.add("get");
      this.commandsToFire.add("connect");
      this.commandsToFire.add("subscribe");
      this.commandsToFire.add("unSubscribe");
      this.commandsToFire.add("publish");
      this.commandsToFire.add("publishArr");
      this.commandsToFire.add("erase");
      this.commandsToFire.add("disconnect");

      this.access = access;
      this.callback = callback;
      this.attachments = attachments;
      this.out = out;
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
   public XmlScriptInterpreter(Global glob, I_XmlBlasterAccess access, OutputStream cbStream, OutputStream responseStream, HashMap attachments) {
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
   public XmlScriptInterpreter(Global glob, OutputStream out) {
      this(glob, glob.getXmlBlasterAccess(), out, out, null);
   }

   /**
    * converts the tag sctart to a string
    * @param qName
    * @param attr
    * @return
    */
   protected String writeElementStart(String qName, Attributes attr) {
      StringBuffer buf = new StringBuffer("<");
      buf.append(qName);
      int nmax = attr.getLength();
      for (int i=0; i < nmax; i++) {
         String name = attr.getQName(i);
         String value = attr.getValue(i);
         buf.append(' ');
         buf.append(name);
         buf.append("='");
         buf.append(value);
         buf.append('\'');
      }
      buf.append('>');
      return buf.toString();
   }

   /**
    * Parses the given input stream and executes the specified commands.
    * @param in the input stream from which to read the xml input.
    * @throws XmlBlasterException
    */
   public synchronized void parse(Reader in) throws XmlBlasterException {
      this.inQos = 0;
      this.inKey = 0;
      this.inContent = 0;
      try {
         this.out.flush();
      }
      catch (IOException ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".parse: could not flush the output stream: original message: '" + ex.getMessage() + "'");
      }
      this.init(new InputSource(in));
   }

   public void characters(char[] ch, int start, int length) {
      // append on the corresponding buffer
      if (this.inQos > 0) this.qos.append(ch, start, length);
      else if (this.inKey > 0) this.key.append(ch, start, length);
      else if (this.inContent > 0) this.content.append(ch, start, length);
      else super.characters(ch, start, length);
   }

   /**
    * Increments the corresponding counter only if it is already in one such element
    * @param qName
    */
   private void incrementInElementCounters(String qName) {
      if ("qos".equals(qName) && this.inQos > 0) this.inQos++;
      else if ("key".equals(qName) && this.inKey > 0) this.inKey++;
      else if ("content".equals(qName) && this.inContent > 0) this.inContent++;
   }

   public void startElement(String namespaceURI, String localName, String qName, Attributes atts) {
      checkNestedTags();
      if (this.inQos > 0) {
         this.qos.append(writeElementStart(qName, atts));
         incrementInElementCounters(qName);
         return;
      }
      if (this.inKey > 0) {
         this.key.append(writeElementStart(qName, atts));
         incrementInElementCounters(qName);
         return;
      }
      if (this.inContent > 0) {
         this.content.append(writeElementStart(qName, atts));
         incrementInElementCounters(qName);
         return;
      }

      if (this.commandsToFire.contains(qName)) {
         this.character = new StringBuffer();
         this.character.append(this.writeElementStart(qName, atts));
         this.currentCommand = qName;
         if ("publishArr".equals(qName)) this.messageList = new ArrayList();
         return;
      }

      if ("key".equals(qName)) {
         this.inKey++;
         this.key = new StringBuffer();
         this.key.append(this.writeElementStart(qName, atts));
         return;
      }

      if ("qos".equals(qName)) {
         this.inQos++;
         this.qos = new StringBuffer();
         this.qos.append(this.writeElementStart(qName, atts));
         return;
      }

      if ("content".equals(qName)) {
         this.inContent++;
         this.link = null;
         this.content = new StringBuffer();
         String type = atts.getValue("type");
         String encoding = atts.getValue("encoding");
         this.contentData = new EncodableData(this.glob, "content", null, type, encoding);
         String tmp = atts.getValue("link");
         if (tmp != null && tmp.length() > 0) this.link = tmp;
         // this.content.append(this.writeElementStart(qName, atts));
         return;
      }
      
      if ("wait".equals(qName)) {
         String tmp = atts.getValue("delay");
         if (tmp != null) {
            try {
               long delay = Long.parseLong(tmp);
               Thread.sleep(delay);
            }
            catch (Throwable e) {
            }
         }
         return;
      }

      if ("xmlBlaster".equals(qName)) {
         String id = atts.getValue("id");
         this.response = new StringBuffer("<xmlBlasterResponse");
         if (id != null) this.response.append(" id='").append(id).append("'");         
         this.response.append(">\n");
         return;
      }
   }

   /**
    * Appends the end stream to the current StringBuffer
    * @param buf the StringBuffer to be used 
    * @param qName the name of the command
    */
   private void appendEndOfElement(StringBuffer buf, String qName) {
      buf.append("</");
      buf.append(qName);
      buf.append('>');
   }
   
   /**
    * Fires the given xmlBlaster command and sends the response to the output stream
    * @param qName
    */
   private void fireCommand(String qName) {      
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
               // ConnectQosData data = new ConnectQosData(this.glob, new ConnectQosSaxFactory(this.glob), this.qos.toString(), this.glob.getNodeId());
               ConnectQosData data = new ConnectQosServer(this.glob, this.qos.toString()).getData();
               ConnectReturnQos tmp = this.access.connect(new ConnectQos(this.glob, data), this.callback);
               if (tmp != null) ret = tmp.toXml("  ");
               else ret = "";
            }
            this.response.append("\n<!-- __________________________________  connect ________________________________ -->");
            this.response.append("\n<connect>");
            this.response.append(ret);
            this.response.append("\n</connect>\n");
            flushResponse();
            return;
         }
         if ("disconnect".equals(qName)) {
            if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement disconnect: " + this.qos.toString());
            if (this.qos.length() < 1) this.qos.append("<qos />");
            DisconnectQosServer disconnectQos = new DisconnectQosServer(this.glob, this.qos.toString());
            boolean ret = this.access.disconnect(new DisconnectQos(this.glob, disconnectQos.getData()));
            this.response.append("\n<!-- __________________________________  disconnect _____________________________ -->");
            this.response.append("\n<disconnect>").append(ret).append("</disconnect>\n");
            flushResponse();
            return;
         }
         if ("publish".equals(qName)) {
            if (this.qos.length() < 1) this.qos.append("<qos />");
            if (this.key.length() < 1) this.key.append("<key />");
            MsgUnit msgUnit = buildMsgUnit();
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
         if ("subscribe".equals(qName)) {
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
         if ("unSubscribe".equals(qName)) {
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
         if ("erase".equals(qName)) {
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
         if ("get".equals(qName)) {
            if (this.log.TRACE) this.log.trace(ME, "appendEndOfElement get: " + this.key.toString() + " " + this.qos.toString());
            if (this.qos.length() < 1) this.qos.append("<qos />");
            if (this.key.length() < 1) this.key.append("<key />");
            MsgUnit[] ret = this.access.get(this.key.toString(), this.qos.toString());
            this.response.append("\n<!-- __________________________________  get ____________________________________ -->");
            this.response.append("\n<get>");
            this.response.append("\n  <message>");
            if (ret != null) {
               for (int i=0; i < ret.length; i++) {
                  this.response.append(ret[i].toXml("    "));
               }
            }
            this.response.append("\n  </message>");
            this.response.append("\n</get>\n");
            flushResponse();
            return;
         }

         if ("qos".equals(qName)) {
            this.inQos--;
         }
         if ("key".equals(qName)) {
            this.inKey--;
         }
         if ("content".equals(qName)) {
            this.inContent--;
         }
      }
      catch (Exception ex) {  // handle here the exception
         ex.printStackTrace();
      }
   }

   private void flushResponse() {
      try {
         if (this.out != null) {
            synchronized(this.out) {
               this.out.write(this.response.toString().getBytes());
            }
         }   
      }
      catch (IOException ex) {
         this.log.error(ME, "flushResponse exception occured " + ex.getMessage());
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
            Object obj = this.attachments.get(this.link);
            if (obj instanceof String) {
               this.contentData.setValueRaw((String)obj);
               currentContent = this.contentData.getBlobValue();
            }
            else {
               currentContent = (byte[])obj;
            }
         }
         else {
            throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "buildMsgUnit: the attachment '" + this.link + "' was not found");
            // throw exception
         }
      }
      MsgUnit msgUnit = new MsgUnit(this.key.toString(),
         currentContent, this.qos.toString());
      return msgUnit;
   }


   private void checkNestedTags() {
      int sum = 0;
      if (this.inContent > 0 ) sum++;
      if (this.inKey > 0) sum++;
      if (this.inQos > 0) sum++;
      if (sum > 1) {
         Thread.dumpStack();
         this.log.error(ME, "check: there is an internal error!! Mismatch with the nested tags ...");
      }    
   }

   public void endElement(String namespaceURI, String localName, String qName) {
      checkNestedTags();
      if (this.inQos > 0) {
         appendEndOfElement(this.qos, qName);
         if ("qos".equals(qName) && this.inQos > 0) this.inQos--;
         return;
      }
      if (this.inKey > 0) {
         appendEndOfElement(this.key, qName);
         if ("key".equals(qName) && this.inKey > 0) this.inKey--;
         return;
      }
      if ("content".equals(qName)) {
         if (this.inContent > 0) this.inContent--;
         if (this.inContent > 0) appendEndOfElement(this.content, qName); // because nested content tags should be there (only the outher not)
         this.contentData.setValueRaw(this.content.toString());
         return;
      }
      if ("xmlBlaster".equals(qName)) {
         this.response = new StringBuffer("\n</xmlBlasterResponse>\n");
         flushResponse();
      }  
      if ("message".equals(qName)) {
         try {
            this.messageList.add(buildMsgUnit());
         }
         catch(XmlBlasterException e) {
            this.log.error(ME, "endElement '" + qName + "' exception occurred when trying to build message unit: " + e.getMessage());         }
         return;
      }
      // comes here since the end tag is not part of the content
      if (this.inContent > 0) appendEndOfElement(this.content, qName);

      if (commandsToFire.contains(qName)) {
         appendEndOfElement(this.character, qName);
         fireCommand(qName);
         return;
      }
   }

   public static void main(String[] args) {
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
         FileReader in = new FileReader(args[0]);
         interpreter.parse(in);
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }

}

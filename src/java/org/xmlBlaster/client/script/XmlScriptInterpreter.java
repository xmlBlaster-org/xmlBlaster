/*------------------------------------------------------------------------------
Name:      XmlScriptInterpreter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.script;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.apache.log.util.LoggerListener;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.util.EncodableData;
import org.xmlBlaster.util.I_ReplaceVariable;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.StopParseException;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Properties;
import java.io.File;


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
 * Abstract class to parse and construct a XML represantation of xmlBlaster invocations for scripting. 
 * <p>
 * Example for command line scripting usage:
 * </p>
 * <p>
 * <tt>
 * java javaclients.XmlScript -requestFile inFile.xml -responseFile outFile.xml -updateFile updFile.xml
 * </tt>
 * </p>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.script.html">The client.script requirement</a>
 */
public abstract class XmlScriptInterpreter extends SaxHandlerBase {
   
   private final String ME = "XmlScriptInterpreter";
   private static Logger log = Logger.getLogger(XmlScriptInterpreter.class.getName());
   protected Global glob;
   
   /** a set of names of allowed commands */   
   private HashSet commandsToFire = new HashSet();
   
   protected StringBuffer qos = new StringBuffer();
   protected StringBuffer key = new StringBuffer();
   private StringBuffer content = new StringBuffer(); // To access use contentData
   protected StringBuffer cdata = new StringBuffer();

   /** Replace e.g. ${ICAO} with command line setting '-ICAO EDDI' */
   private boolean replaceQosTokens;
   private boolean replaceKeyTokens;
   private boolean replaceContentTokens;
   /** Replace tokens in wait or echo markup */
   private boolean replaceTokens;

   /** Encapsulates the content of the current message (useful for encoding) */
   protected EncodableData contentData;
   // private boolean inQos, inKey, inContent;
   private int inQos, inKey, inContent, inCDATA;
   private String link;
   private String sessionId;
   private String requestId;
   private byte type; // I=invoke R=response E=exception
   private String  propertyName;
   private boolean inProperty;

   /** the attachments (some contents can be in the attachments) */
   private HashMap attachments;
   
   /** used to accumulate all messages to be sent with publishArr */
   protected ArrayList messageList;
   
   /** buffer used as a place holder for the responses of the xmlBlaster invocations */
   protected StringBuffer response;
   protected boolean needsRootEndTag;
   protected OutputStream out;
   
   protected Object waitMutex = new Object();
   protected long updateCounter;
   protected int waitNumUpdates;
   
   /**
    * Set true to send a simple exception format like
      <pre>
      &lt;update type='E'>
       &lt;qos>&lt;state id='ERROR' info='user'/>&lt;/qos>
      &lt;/update>
      </pre>
      <p>Note: The errorCode is stripped to the main category</p>
    */
   protected boolean sendSimpleExceptionFormat;
   
   public final static String ROOT_TAG = "xmlBlaster";
   public final static String ROOTRESPONSE_TAG = "xmlBlasterResponse";
   public final String KEY_TAG = MsgUnitRaw.KEY_TAG;
   public final String CONTENT_TAG = MsgUnitRaw.CONTENT_TAG;
   public final String QOS_TAG = MsgUnitRaw.QOS_TAG;

   public final String ECHO_TAG = "echo";
   public final String INPUT_TAG = "input";
   public final String WAIT_TAG = "wait";
   
   /**
    * You need to call initialize() if using this default constructor. 
    */
   public XmlScriptInterpreter() {
   }
   
   /**
    * This constructor is the most generic one (more degrees of freedom)
    * @param glob the global to use
    * @param attachments the attachments where to search
    *           when a content is stored in the attachment
    *           (with the 'link' attribute); can be null
    * @param out the OutputStream where to send the responses of the invocations done to xmlBlaster
    */
   public XmlScriptInterpreter(Global glob, HashMap attachments, OutputStream out) {
      super(glob);
      initialize(glob, attachments, out);
   }

   /**
    * @param glob the global to use
    * @param attachments the attachments where to search when a content is stored in the attachment (with the 'link' attribute)
    * @param out the OutputStream where to send the responses of the invocations done to xmlBlaster
    */
   public void initialize(Global glob, HashMap attachments, OutputStream out) {
      this.glob = glob;

      setUseLexicalHandler(true);
      this.commandsToFire.add(MethodName.GET.toString());
      this.commandsToFire.add(MethodName.CONNECT.toString());
      this.commandsToFire.add(MethodName.PING.toString());
      this.commandsToFire.add(MethodName.SUBSCRIBE.toString());
      this.commandsToFire.add(MethodName.UNSUBSCRIBE.toString());
      this.commandsToFire.add(MethodName.PUBLISH.toString());
      this.commandsToFire.add(MethodName.PUBLISH_ARR.toString());
      this.commandsToFire.add(MethodName.PUBLISH_ONEWAY.toString());
      this.commandsToFire.add(MethodName.UPDATE.toString());
      this.commandsToFire.add(MethodName.UPDATE_ONEWAY.toString());
      this.commandsToFire.add(MethodName.ERASE.toString());
      this.commandsToFire.add(MethodName.DISCONNECT.toString());
      this.commandsToFire.add(MethodName.EXCEPTION.toString());

      this.attachments = attachments;
      this.out = out;
      this.needsRootEndTag = false; // will be true when start tag is written
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
    * Parses the given reader and executes the specified commands.
    * @param in the reader from which to read the xml input.
    * @throws XmlBlasterException
    */
   public synchronized void parse(Reader in) throws XmlBlasterException {
      this.inQos = 0;
      this.inKey = 0;
      this.inContent = 0;
      this.inCDATA = 0;
      if (this.out != null) {
         try {
            this.out.flush();
         }
         catch (IOException ex) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".parse: could not flush the output stream: original message: '" + ex.toString() + "'");
         }
      }
      super.init(new InputSource(in)); // parse with SAX
   }

   public void characters(char[] ch, int start, int length) {
      // append on the corresponding buffer
      if (this.inCDATA > 0) {
         this.cdata.append(ch, start, length);
      }
      else if (this.inQos > 0) {
         this.qos.append(ch, start, length);
      }
      else if (this.inKey > 0) {
         this.key.append(ch, start, length);
      }
      else if (this.inContent > 0) {
         this.content.append(ch, start, length);
      }
      else super.characters(ch, start, length);
   }

   /**
    * Increments the corresponding counter only if it is already in one such element
    * @param qName
    */
   private void incrementInElementCounters(String qName) {
      if (QOS_TAG.equals(qName) && this.inQos > 0) this.inQos++;
      else if (KEY_TAG.equals(qName) && this.inKey > 0) this.inKey++;
      else if (CONTENT_TAG.equals(qName) && this.inContent > 0) this.inContent++;
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
         if (MethodName.PUBLISH_ARR.equals(qName) ||
             MethodName.PUBLISH_ONEWAY.equals(qName))
            this.messageList = new ArrayList();
         this.sessionId = atts.getValue("sessionId");
         this.requestId = atts.getValue("requestId");
         String typeStr = atts.getValue("type");
         if (typeStr!=null && typeStr.length() > 0)
            this.type = typeStr.getBytes()[0];
         else
            this.type = MsgInfo.INVOKE_BYTE; // 'I'
         return;
      }

      if ("replaceTokens".equals(qName)) {
          this.replaceTokens = true;
          return;
       }
       
      if ("replaceKeyTokens".equals(qName)) {
         this.replaceKeyTokens = true;
         return;
      }
      
      if (KEY_TAG.equals(qName)) {
         this.inKey++;
         this.key.setLength(0);
         this.key.append(this.writeElementStart(qName, atts));
         return;
      }

      if ("replaceQosTokens".equals(qName)) {
         this.replaceQosTokens = true;
         return;
      }

      if (QOS_TAG.equals(qName)) {
         this.inQos++;
         this.qos.setLength(0);
         this.qos.append(this.writeElementStart(qName, atts));
         return;
      }

      if ("replaceContentTokens".equals(qName)) {
         this.replaceContentTokens = true;
         return;
      }

      if (CONTENT_TAG.equals(qName)) {
         this.inContent++;
         this.link = null;
         this.content.setLength(0);
         String sizeStr = atts.getValue("size"); // long
         String type = atts.getValue("type"); // byte[]
         String encoding = atts.getValue("encoding"); // base64
         this.contentData = new EncodableData(CONTENT_TAG, null, type, encoding);
         try {
            if (sizeStr != null) {
               long size = Long.valueOf(sizeStr).longValue();
               if (size > 0)
                  this.contentData.setSize(size);
            }
         } catch (NumberFormatException e) {}
         String tmp = atts.getValue("link");
         if (tmp != null && tmp.length() > 0) this.link = tmp;
         // this.content.append(this.writeElementStart(qName, atts));
         return;
      }
      
      if (WAIT_TAG.equals(qName)) {
         String message = atts.getValue("message");
         if (message != null) {
        	message = replaceVariables(message);
            System.out.println(message);
         }
         this.waitNumUpdates = 0;
         String tmp = atts.getValue("updates");
         tmp = replaceVariables(tmp);
         if (tmp != null) {
            try {
               this.waitNumUpdates = Integer.parseInt(tmp.trim());
            }
            catch (Throwable e) {
            }
         }
         long delay = Integer.MAX_VALUE;
         tmp = atts.getValue("delay");
         tmp = replaceVariables(tmp);
         if (tmp != null) {
            try {
               delay = Long.parseLong(tmp.trim());
               if (delay == 0) delay = Integer.MAX_VALUE;
            }
            catch (Throwable e) {
            }
         }
         if (this.waitNumUpdates > 0 || delay > 0) {
           synchronized (this.waitMutex) {
              if (this.waitNumUpdates > 0 && this.updateCounter >= this.waitNumUpdates) {
                 // updates have arrived already
              }
              else {
                 try {
               	  waitMutex.wait(delay);
                 }
                 catch (InterruptedException e) {
                 }
                 if (this.waitNumUpdates == 0 || this.updateCounter < this.waitNumUpdates) {
                    log.info("wait timeout occurred after " + delay + " milli.");
                 }
                 this.updateCounter = 0;
              }
           }
         }
         return;
      }

      if (ECHO_TAG.equals(qName)) { // console output <echo message="Hello"/>
         String message = atts.getValue("message");
         if (message == null) {
            message = "";
         }
      	 message = replaceVariables(message);
         System.out.println(message);
         return;
      }

      if (INPUT_TAG.equals(qName)) { // User input from console <input message="Hit a key: " delay="100"/>
         String inputMessage = atts.getValue("message");
         if (inputMessage == null) {
            inputMessage = "Hit a key to continue> ";
         }
         inputMessage = replaceVariables(inputMessage);
         // this.validargs = atts.getValue("validargs"); "y/n"
         {  // Wait a bit to have updates processed
            String tmp = atts.getValue("delay");
            if (tmp == null) {
               tmp = "500";
            }
            tmp = replaceVariables(tmp);
           try {
              long delay = Long.parseLong(tmp.trim());
              Thread.sleep(delay);
           }
           catch (Throwable e) {
           }
         }
         /*int ret = */Global.waitOnKeyboardHit(inputMessage);
         return;
      }

      if (ROOT_TAG.equals(qName)) { // "xmlBlaster"
         String id = atts.getValue("id");
         this.response = new StringBuffer("<").append(ROOTRESPONSE_TAG);
         if (id != null) this.response.append(" id='").append(id).append("'");         
         this.response.append(">\n");
         this.needsRootEndTag = true;
         return;
      }

      if ("property".equals(qName)) {
         this.inProperty = true;
         // <property name='transactionId'>Something&lt;/property>
         this.propertyName = atts.getValue("name");
         return;
      }
   }
   
   private String replaceVariables(String template) {
      if (!replaceTokens) return template;
      
      ReplaceVariable r = new ReplaceVariable();
      String result = r.replace(template,
         new I_ReplaceVariable() {
            public String get(String key) {
               return glob.getProperty().get(key, key);
            }
         });
      return result;
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
    * Replace e.g. ${XY} with the variable from global properties. 
    * @param text The complete string which may contain zero to many ${...}
    *             variables, if null we return null
    * @return The new value where all found ${} are replaced.
    */
   public String replaceVariable(String text) {
      if (text == null) return null;
      int lastFrom = -1;
      for (int i=0; i<20; i++) { // max recursion/replacement depth
         int from = text.indexOf("${");
         if (from == -1) return text;
         if (lastFrom != -1 && lastFrom == from) return text; // recursion
         int to = text.indexOf("}", from);
         if (to == -1) return text;
         String key = text.substring(from+2, to);
         String value = glob.getProperty().get(key, "${"+key+"}");
         text = text.substring(0,from) + value + text.substring(to+1);
         lastFrom = from;
      }
      return text;
   }

   /**
    * On each remote method invocation this function is called. 
    * @param methodName
    * @param type 'I'=invoke 'R'=response 'E'=exception
    * @return true: The methodName was known and is successfully processed
    *         false: The methodName is not known and nothing is processed
    * @throws XmlBlasterException Will lead to stop parsing further
    */
   abstract public boolean fireMethod(MethodName methodName,
         String sessionId, String requestId, byte type) throws XmlBlasterException;

   /**
    * Set a property into Global scope. 
    */
   abstract public void setProperty(String key, String value) throws XmlBlasterException;

   /**
    * Fires the given xmlBlaster command and sends the response to the output stream
    * @param qName
    */
   private void fireCommand(String qName) throws XmlBlasterException {
      if (replaceQosTokens) {
         String tmp = this.qos.toString();
         this.qos.setLength(0);
         this.qos.append(replaceVariable(tmp));
      }
      if (replaceKeyTokens) {
         String tmp = this.key.toString();
         this.key.setLength(0);
         this.key.append(replaceVariable(tmp));
      }
      if (replaceContentTokens) {
         String tmp = this.content.toString();
         this.content.setLength(0);
         this.content.append(replaceVariable(tmp));
      }
      
      if (QOS_TAG.equals(qName)) {
         this.inQos--;
         return;
      }
      if (KEY_TAG.equals(qName)) {
         this.inKey--;
         return;
      }
      if (CONTENT_TAG.equals(qName)) {
         this.inContent--;
         return;
      }

      boolean processed = fireMethod(MethodName.toMethodName(qName), this.sessionId, this.requestId, this.type);
      if (processed) {
         if (this.content != null) this.content.setLength(0);
         if (this.qos != null) this.qos.setLength(0);
         if (this.key != null) this.key.setLength(0);
         return;
      }
   }

   protected void flushResponse() throws XmlBlasterException {
      try {
         if (this.out != null) {
            if (log.isLoggable(Level.FINE)) log.fine("Sending response: " + this.response.toString());
            synchronized(this.out) {
               this.out.write(this.response.toString().getBytes("UTF-8"));
            }
         }   
      }
      catch (IOException ex) {
         log.severe("flushResponse exception occured " + ex.getMessage());
         throw XmlBlasterException.convert(this.glob, ME, ErrorCode.INTERNAL_UNKNOWN.toString(), ex);
      }
      finally {
         this.response = new StringBuffer();
      }
   }

   protected MsgUnit buildMsgUnit() throws XmlBlasterException {
      byte[] currentContent = null;
      if (this.link == null)
         currentContent = (this.contentData == null) ? new byte[0] : this.contentData.getBlobValue(); 
      else {
         if (this.attachments != null && this.attachments.containsKey(this.link)) {
            Object obj = this.attachments.get(this.link);
            if (obj instanceof String) {
               if (this.contentData == null)
                  this.contentData = new EncodableData(CONTENT_TAG, null, Constants.TYPE_STRING, Constants.ENCODING_NONE);
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
      /*
      if (sum > 1) {
         Thread.dumpStack();
         log.error(ME, "check: there is an internal error!! Mismatch with the nested tags ...");
      } 
      */
   }

   public void endElement(String namespaceURI, String localName, String qName) {
      try {
         checkNestedTags();
         if (this.inQos > 0) {
            appendEndOfElement(this.qos, qName);
            if (QOS_TAG.equals(qName) && this.inQos > 0) this.inQos--;
            return;
         }
         if (this.inKey > 0) {
            appendEndOfElement(this.key, qName);
            if (KEY_TAG.equals(qName) && this.inKey > 0) this.inKey--;
            return;
         }
         if (CONTENT_TAG.equals(qName)) {
            if (this.inContent > 0) this.inContent--;
            if (this.inContent > 0) appendEndOfElement(this.content, qName); // because nested content tags should be there (only the outher not)
            this.contentData.setValueRaw(this.content.toString());
            return;
         }
         if (ROOT_TAG.equals(qName)) { // "xmlBlaster"
            this.response = new StringBuffer("\n</");
            this.response.append(ROOTRESPONSE_TAG).append(">\n");
            flushResponse();
            this.needsRootEndTag = false;
         }  
         if ("message".equals(qName)) {
            try {
               if (this.messageList == null) {
                  throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "To send multiple messages please use " + MethodName.PUBLISH_ARR + " or " + MethodName.PUBLISH_ONEWAY);
               }
               this.messageList.add(buildMsgUnit());
            }
            catch(XmlBlasterException e) {
               log.severe("endElement '" + qName + "' exception occurred when trying to build message unit: " + e.getMessage());         }
            return;
         }
         // comes here since the end tag is not part of the content
         if (this.inContent > 0) appendEndOfElement(this.content, qName);

         if ("property".equals(qName) && this.inProperty) {
            this.inProperty = false;
            if (this.propertyName != null) {
               setProperty(this.propertyName, character.toString().trim());
            }
            character.setLength(0);
            return;
         }

         if (this.commandsToFire.contains(qName)) {
            appendEndOfElement(this.character, qName);
            fireCommand(qName);
            return;
         }
      }
      catch (XmlBlasterException e) {
         if (log.isLoggable(Level.FINE)) XmlScriptInterpreter.log.fine("endElement exception occured " + e.getMessage());
         if (this.needsRootEndTag) { // is </xmlBlasterResponse> missing?
            this.response = new StringBuffer("\n</");
            this.response.append(ROOTRESPONSE_TAG).append(">\n");
            try {
               flushResponse();
            } catch (XmlBlasterException e1) {
               e1.printStackTrace();
            }
         }
         throw new StopParseException(e);
      }
      catch (StopParseException e) {
         if (this.needsRootEndTag) { // is </xmlBlasterResponse> missing?
            this.response = new StringBuffer("\n</");
            this.response.append(ROOTRESPONSE_TAG).append(">\n");
            try {
               flushResponse();
            } catch (XmlBlasterException e1) {
               e1.printStackTrace();
            }
         }
         throw e;
      }
   }

   public void startCDATA() {
      if (log.isLoggable(Level.FINER)) XmlScriptInterpreter.log.finer("startCDATA");
      this.inCDATA++;
      if (this.inContent == 0)
         this.cdata.append("<![CDATA[");
   }
   
   public void endCDATA() {
      if (log.isLoggable(Level.FINER)) {
         String txt = "";
         if (this.qos != null) this.qos.toString();
         log.finer("endCDATA: " + txt);
      }
      this.inCDATA--;
      if (this.inContent == 0) 
         this.cdata.append("]]>");
      if (this.inCDATA == 0) {
         // append on the corresponding buffer
         if (this.inQos > 0) this.qos.append(this.cdata);
         else if (this.inKey > 0) this.key.append(this.cdata);
         else if (this.inContent > 0) this.content.append(this.cdata);
         else super.character.append(this.cdata);
         this.cdata = new StringBuffer();
      }
   }

   public String wrapForScripting(MsgUnit msgUnit, String comment) {
      return wrapForScripting(ROOT_TAG, msgUnit, comment);
   }

   /**
    * Dump the MsgUnit to XML, the dump includes the xml header (UTF-8). 
    * <br />
    * Example: 
    * <pre>
    * String xml = XmlScriptInterpreter.wrapForScripting(
    *      XmlScriptInterpreter.ROOT_TAG,
    *       msgUnit,
    *      "XmlScripting dump");
    *</pre>
    * @param rootTag Usually XmlScriptInterpreter.ROOT_TAG="xmlBlaster"
    * @param msgUnit null is OK
    * @param comment Some comment you want to add or null
    * @return
    */
   public static String wrapForScripting(String rootTag, MsgUnit msgUnit, String comment) {
      MsgUnit[] msgUnitArr = (msgUnit == null) ? new MsgUnit[0] : new MsgUnit[1];
      if (msgUnitArr.length == 1) msgUnitArr[0] = msgUnit;
      return wrapForScripting(rootTag, msgUnitArr, comment);
   }

   public static String wrapForScripting(String rootTag, MsgUnit[] msgUnitArr, String comment) {
      if (msgUnitArr == null) msgUnitArr = new MsgUnit[0];
      StringBuffer sb = new StringBuffer(1024+(msgUnitArr.length*256));
      sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      sb.append("\n<").append(rootTag).append(">");
      if (comment != null && comment.length() > 0) sb.append("\n<!-- " + comment + " -->");
      for (int i=0; i<msgUnitArr.length; i++) {
         MsgUnit msgUnit = msgUnitArr[i];
         String xml = msgUnit.toXml((String)null, (Properties)null);
         sb.append("\n <").append(msgUnit.getMethodName().toString()).append(">");
         sb.append(xml);
         sb.append("\n </").append(msgUnit.getMethodName().toString()).append(">");
      }
      sb.append("\n</").append(rootTag).append(">");
      return sb.toString();
   }
   
   /**
    * Dump the given MsgUnitRaw to XML.
    * Example for a PublishReturnQos:
    * <pre>
    * &lt;-- A comment -->
    *   &lt;publish>
     &lt;qos>
      &lt;key oid='1'/>
      &lt;rcvTimestamp nanos='1131654994574000000'/>
     &lt;isPublish/>
     &lt;/qos>
  &lt;/publish>
</pre>
    * @param methodName The method to invoke, like "publishArr"
    * @param sessionId An optional sessionId or null
    * @param requestId An optional requestId or null
    * @param msgUnits The msgUnits to serialize
    * @param header For example 
<?xml version='1.0' encoding='UTF-8'?>
    * @param comment An optional comment to add, can be null
    * @param schemaDecl Used for root tag, for example
xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' 
xsi:noNamespaceSchemaLocation='xmlBlasterPublish.xsd'
    * @param out The output sink for the result
    * @param type 'I' is for invoke, 'R' for reply and 'E' for exception
    * @throws XmlBlasterException
    */
   protected void serialize(MethodName methodName, String sessionId,
         String requestId, MsgUnitRaw[] msgUnits,
         String header, String comment, String schemaDecl,
         OutputStream out, byte type) throws IOException {
      String offset = " ";
      StringBuffer sb = new StringBuffer(1024+(1024));
      
      if (header == null) header = "<?xml version='1.0' encoding='UTF-8'?>\n";
      sb.append(header);
      
      if (comment != null && comment.length() > 0)
         sb.append("\n<!-- ").append(comment).append( " -->");
      if (methodName != null) {
         sb.append("\n<").append(methodName.toString());
         if (schemaDecl != null && schemaDecl.length() > 0)
            sb.append(" ").append(schemaDecl);
         if (sessionId != null && sessionId.length() > 0)
            sb.append(" sessionId='").append(sessionId).append("'");
         if (requestId != null && requestId.length() > 0)
            sb.append(" requestId='").append(requestId).append("'");
         if (type != 0) {
            sb.append(" type='").append(MsgInfo.getTypeChar(type)).append("'");
         }
         sb.append(">");
      }
      out.write(sb.toString().getBytes());
      
      if (msgUnits != null && msgUnits.length > 0) {
         if (msgUnits.length == 1) {
            if (type == MsgInfo.EXCEPTION_BYTE && this.sendSimpleExceptionFormat) {
               // This must be parsable on the other side similar to XmlBlasterException.parseByteArr()
               // See code in MsgInfo.java
               //ErrorCode errorCode = ErrorCode.getCategory(msgUnits[0].getQos()); -> toplevel only, like 'user' 
               ErrorCode errorCode = ErrorCode.toErrorCode(msgUnits[0].getQos()); 
               StringBuffer buf = new StringBuffer(1024);
               buf.append("<qos><state id='ERROR' info='").append(errorCode.getErrorCode());
               buf.append("'/></qos>");
               out.write(buf.toString().getBytes(Constants.UTF8_ENCODING));
            }
            else {
               msgUnits[0].toXml(offset, out, (Properties)null);
            }
         }
         else {
            for (int i=0; i < msgUnits.length; i++) {
               out.write("\n  <message>".getBytes());
               msgUnits[i].toXml(offset, out, (Properties)null);
               out.write("\n  </message>\n".getBytes());
            }
         }
      }
      
      if (methodName != null) {
         out.write("\n</".getBytes());
         out.write(methodName.toString().getBytes());
         out.write(">\n".getBytes());
      }
      out.flush();
   }
   
   
   public static String dumpToFile(String path, String fn, String xml) throws XmlBlasterException {
      try {
         if (path != null) {
            File dir = new File(path);
            dir.mkdirs();
         }
         File to_file = new File(path, fn);
         FileOutputStream to = new FileOutputStream(to_file);
         to.write(xml.getBytes());
         to.close();
         return to_file.toString();
      }
      catch (java.io.IOException e) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_ILLEGALARGUMENT, "dumpToFile", "Please check your '"+path+"' or file name '" + fn + "'", e);
      }
   }
 
   /**
    * If a callback handler was registered, we will be notified here
    * about updates as well
    * @param cbSessionId
    * @param updateKey
    * @param content
    * @param updateQos
    * @return
    * @throws XmlBlasterException
    */
	public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      synchronized (this.waitMutex) {
         if (updateQos.isOk())
            this.updateCounter++;
         if (this.waitNumUpdates > 0 && this.updateCounter >= this.waitNumUpdates) {
      		if (this.updateCounter == this.waitNumUpdates) log.info("Fire notify, " + this.updateCounter + " updates arrived");
          	waitMutex.notify();
         }
      }
      if (log.isLoggable(Level.FINE)) log.fine("Received #" + this.updateCounter);
		return null;
	}
   
   public static void main(String[] args) {
      try {
         Global glob = new Global();
         String[] tmp = new String[args.length-2];
         for (int i=0; i < tmp.length; i++) tmp[i] = args[i+2];
         
         glob.init(tmp);
         FileOutputStream out = new FileOutputStream(args[1]);
         XmlScriptInterpreter interpreter = new XmlScriptClient(glob, out);
         FileReader in = new FileReader(args[0]);
         interpreter.parse(in);
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
}

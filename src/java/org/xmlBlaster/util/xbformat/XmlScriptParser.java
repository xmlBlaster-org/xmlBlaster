/*------------------------------------------------------------------------------
 Name:      XmlScriptParser.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.xbformat;

import org.xmlBlaster.client.script.XmlScriptInterpreter;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.MsgUnitRaw;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * XmlScriptParser class for XML formated messages. <br />
 * This class creates and parses xml script messages which can be used to
 * transfer over a email or socket connection. <br />
 * XmlScriptParser instances may be reused, but are NOT reentrant (there are
 * many 'global' variables) <br />
 * Please read the requirement specification <a
 * href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.script.html">The
 * client.script requirement</a>
 * 
 * @author xmlBlaster@marcelruff.info
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.script.html">The
 *      client.script requirement</a>
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html#script">The
 *      protocol.socket requirement used with scripting protocol.</a>
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/examples/xmlScriptMsgExamples.txt">Some examples
 *      of xmlScript messages.</a>
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/client/script/XmlBlasterScript.xsd">XSD For XmlScript Messages.</a>
 *      
 */
public class XmlScriptParser extends XmlScriptInterpreter implements
      I_MsgInfoParser {
   private static Logger log = Logger
         .getLogger(XmlScriptParser.class.getName());

   private static final String ME = "xbformat.XmlScriptParser";

   private Global glob;

   public static final String XMLSCRIPT_EXTENSION = ".xml";

   public static final String XMLSCRIPT_ZLIB_EXTENSION = ".xmlz";

   public static final String XMLSCRIPT_MIMETYPE = "text/xmlBlasterScript";
   
   public static final String XMLSCRIPT_ZLIB_MIMETYPE = "application/xmlBlasterScriptz";
   
   /**
    *  If used by email, the InputStream finishes when the attaachment is read,
    *  if used over socket, we need to terminate the script with a null byte
    */
   private boolean isNullTerminated;
   
   /** <?xml version='1.0' encoding='UTF-8'?> */
   private String xmlDecl;
   
   private boolean sendResponseSessionId;
   private boolean sendResponseRequestId;
   private boolean sendRequestSessionId;
   private boolean sendRequestRequestId;

   /**
    *  xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' 
    *   xsi:noNamespaceSchemaLocation='xmlBlasterPublish.xsd'
   */
   private String schemaDecl;
   
   
   public static String getEncodingFromProcessingInstruction(String pi) {
      //<?xml version='1.0' encoding='UTF-8'?>
      if (pi == null || pi.length() < 1)
         return Constants.UTF8_ENCODING;
      int i = pi.indexOf("encoding=");
      if (i == -1 || i+5+"encoding=".length() >= pi.length())
         return Constants.UTF8_ENCODING;
      String endStr = pi.substring(i + "encoding=".length()+1);
      int i2 = endStr.indexOf("'");
      if (i2 == -1)
         i2 = endStr.indexOf('"');
      String charset = endStr.substring(0, i2);
      return charset;
   }
   
   static {
      MsgInfoParserFactory.instance().register(XMLSCRIPT_EXTENSION, XmlScriptParser.class.getName());
      MsgInfoParserFactory.instance().register(XMLSCRIPT_ZLIB_EXTENSION, XmlScriptParser.class.getName());
      MsgInfoParserFactory.instance().register(XMLSCRIPT_MIMETYPE, XmlScriptParser.class.getName());
      MsgInfoParserFactory.instance().register(XMLSCRIPT_ZLIB_MIMETYPE, XmlScriptParser.class.getName());
   }

   /**
    * If not null somebody wants to be notified about the current bytes send
    * over socket
    */
   private I_ProgressListener progressListener;

   private ArrayList msgInfoParsed;

   public XmlScriptParser() {
   }

   public void init(Global glob, I_ProgressListener progressListener,
         I_PluginConfig pluginConfig) throws XmlBlasterException {
      this.glob = glob;
      this.progressListener = progressListener;
      this.xmlDecl = glob.get("xmlDeclaration", (String)null, null, pluginConfig);
      this.schemaDecl = glob.get("schemaDeclaration", (String)null, null, pluginConfig);
      this.sendResponseSessionId = glob.get("sendResponseSessionId", true, null, pluginConfig);
      this.sendResponseRequestId = glob.get("sendResponseRequestId", true, null, pluginConfig);
      this.sendRequestSessionId = glob.get("sendRequestSessionId", true, null, pluginConfig);
      this.sendRequestRequestId = glob.get("sendRequestRequestId", true, null, pluginConfig);
      this.isNullTerminated = glob.get("isNullTerminated", false, null, pluginConfig);
      super.sendSimpleExceptionFormat = glob.get("sendSimpleExceptionFormat", false, null, pluginConfig);
      super.simpleExceptionFormatList = glob.get("simpleExceptionFormatList", (String)null, null, pluginConfig);
      if (super.simpleExceptionFormatList != null) {
         log.finest("simpleExceptionFormatList=" + super.simpleExceptionFormatList);
      }
      super.forceReadable = glob.get("forceReadable", false, null, pluginConfig);
      super.inhibitContentCDATAWrapping = glob.get(Constants.INHIBIT_CONTENT_CDATA_WRAPPING, false, null, pluginConfig);
      super.initialize(glob, null, null);
   }

   /**
    * Get a specific extension for this format. 
    * @return For example 
    *  XMLSCRIPT_MIMETYPE = "text/xmlBlasterScript" or
    *  XMLSCRIPT_MIMETYPE_ZLIB = "text/xmlBlasterScriptz"
    */
   public String getMimetype(boolean isCompressed) {
      return (isCompressed) ? XMLSCRIPT_ZLIB_MIMETYPE : XMLSCRIPT_MIMETYPE;
   }

   /**
    * @param isCompressed
    *           true/false
    * @return XMLSCRIPT_EXTENSION = ".xml"; XMLSCRIPT_ZLIB_EXTENSION = ".xmlz";
    */
   public final String getExtension(boolean isCompressed) {
      return (isCompressed) ? XMLSCRIPT_ZLIB_EXTENSION : XMLSCRIPT_EXTENSION;
   }

   /**
    * This parses the raw message from an InputStream (typically from a email or
    * a socket). Use the get...() methods to access the data.
    * <p />
    * This method blocks until a message arrives
    */
   public final MsgInfo[] parse(InputStream in) throws IOException, XmlBlasterException {
      if (log.isLoggable(Level.FINER))
         log.finer("Entering parse()");
      
      MsgInfo[] msgInfos = new MsgInfo[0];

      if (this.isNullTerminated) {
         // If bytes are coming over a socket, we need to distinguish
         // one script from the next one: Here we do it by a ZERO byte after
         // each script.
         // If "in" is coming from an email attachment, it terminates automatically
         // after the script, so we don't enter this "if" statement
         try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            while (true) {
               int bt = in.read();
               if (bt == -1) { // EOF
                  throw new IOException(ME + ": Got EOF");
               }
               if (bt == 0) break;
               out.write(bt);
            }
            in = new ByteArrayInputStream(out.toByteArray()); // Now "in" contains exactly one script
            if (log.isLoggable(Level.FINEST)) log.finest("Got script [" + new String(out.toByteArray()) + "]");
         } catch (IOException e) {
            throw e;
         } catch (Exception e) {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Socket connection failure", e);
         }
      }
      
      this.msgInfoParsed = new ArrayList();

      Reader reader = new InputStreamReader(in);
      super.parse(reader);
      /*
      byte[] buf = new byte[100];
      in.read(buf);
      //reader.read(buf);
      System.out.println("XmlScriptParser:" + new String(buf));
      //super.parse(reader);
       
       */
      msgInfos = (MsgInfo[])this.msgInfoParsed.toArray(new MsgInfo[this.msgInfoParsed.size()]);
      
      if (this.progressListener != null) {
         long size = 0;
         String text = (msgInfos.length > 0) ? msgInfos[0].getMethodNameStr() : "XmlScript";
         for (int i=0; i<msgInfos.length; i++)
            size += msgInfos[i].getUserDataLen();
         this.progressListener.progressRead(text, size, size);
      }

      if (log.isLoggable(Level.FINE))
         log.fine("Leaving parse(), message successfully parsed");
      this.msgInfoParsed = null;
      return msgInfos;
   }

   /**
    * Called during XML parsing.
    */
   public void setProperty(String key, String value) throws XmlBlasterException {
      this.glob.getProperty().set(key, value);
   }
   
   /**
    * Called during XML parsing.
    */
   public boolean fireMethod(MethodName methodName,
         String sessionId, String requestId, byte type)
         throws XmlBlasterException {
      MsgInfo msgInfo = new MsgInfo(this.glob);
      msgInfo.setMsgInfoParser(this);
      if (msgInfo.getNumMessages() > 0) {
         log.severe("Multiple method invocation in one message is not supported, we ignore "
                     + methodName.toString());
         return false;
      }
      if (super.messageList != null && super.messageList.size() > 0) {
         // MethodName.PUBLISH_ARR and PUBLISH_ONEWAY
         log.severe("Sending message arrays with '" + methodName.toString() + "' is not implemented");
         return false;
      }
      
      
      MsgUnitRaw msgUnitRaw = new MsgUnitRaw(Constants.toUtf8Bytes(super.key.toString()),
            super.contentData, Constants.toUtf8Bytes(super.qos.toString()));
      msgInfo.setMethodName(methodName);
      msgInfo.setSecretSessionId(sessionId);
      msgInfo.setRequestId(requestId);
      msgInfo.setType(type);
      msgInfo.addMessage(msgUnitRaw);
      this.msgInfoParsed.add(msgInfo);
      return true;
   }

   /**
    * Returns a XML data string.
    */
   public final byte[] createRawMsg(MsgInfo msgInfo) throws XmlBlasterException {
      try {
         long len = msgInfo.getUserDataLen() + 500;
         ByteArray out = new ByteArray((int) len);
         
         boolean isResponseOrException = 
              msgInfo.getType() == MsgInfo.RESPONSE_BYTE ||
              msgInfo.getType() == MsgInfo.EXCEPTION_BYTE;
         
         String sessionId = msgInfo.getSecretSessionId();
         if ((isResponseOrException && !this.sendResponseSessionId) || !sendRequestSessionId)
            sessionId = null;

         String requestId = msgInfo.getRequestId();
         if ((isResponseOrException && !this.sendResponseRequestId) || !sendRequestRequestId)
            requestId = null;
         
         super.serialize(msgInfo.getMethodName(),
                         sessionId,
                         requestId,
                         msgInfo.getMessageArr(),
                         this.xmlDecl, null, this.schemaDecl,
                         out, msgInfo.getType());
         if (this.progressListener != null) {
            this.progressListener.progressWrite(msgInfo.getMethodNameStr(),
                  len, len);
         }
         return out.toByteArray();
      } catch (IOException e) {
         String text = "Creation of message failed.";
         log.warning(text + " " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME,
               text, e);
      }
   }

   /**
    * Get the raw messages as a string, for tests and for dumping only
    * 
    * @return The stringified message, null bytes are replaced by '*'
    */
   public final String toLiteral(MsgInfo msgInfo) throws XmlBlasterException {
      return toLiteral(createRawMsg(msgInfo));
   }

   public final String toLiteral(byte[] arr) {
      //return new String(arr);
      byte[] tmp = new byte[arr.length];
      for (int ii=0; ii<arr.length; ii++) {
         if (arr[ii] == 0)
            tmp [ii] = (byte)'*';
         else
            tmp[ii] = arr[ii];
      }
      return new String(tmp);
   }

   /**
    * java org.xmlBlaster.util.xbformat.XmlScriptParser
    */
   public static void main(String[] args) {
      try {
         Global glob = Global.instance();
         XmlScriptParser parser = new XmlScriptParser();
         {
            System.out.println("TEST0 PING");
            parser.init(glob, null, null);
            MsgInfo msgInfo = new MsgInfo(glob, MsgInfo.RESPONSE_BYTE, "12",
                  MethodName.PING, "secret", null);
            byte[] content = null;
            String qos = null;
            MsgUnitRaw msg = new MsgUnitRaw(null, content, qos);
            msgInfo.addMessage(msg);
            byte[] raw = parser.createRawMsg(msgInfo);
            System.out.println(parser.toLiteral(raw));

            PipedInputStream in = new PipedInputStream();
            PipedOutputStream out = new PipedOutputStream(in);
            out.write(raw);
            out.flush();
            out.close();
            MsgInfo msgInfoNew = parser.parse(in)[0];
            byte[] rawNew = parser.createRawMsg(msgInfoNew);
            System.out.println("Parsed and dumped again:" + parser.toLiteral(rawNew));
         }
         {
            System.out.println("TEST1 SHOULD FORCE BASE64");
            parser.init(glob, null, null);
            MsgInfo msgInfo = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, "12",
                  MethodName.PUBLISH, "secret", null);
            byte[] content = "hello&bye]]>".getBytes();
            //content[3] = 0;
            MsgUnitRaw msg = new MsgUnitRaw("<key oid='hello'/>", content, "<qos></qos>");
            msgInfo.addMessage(msg);
            byte[] raw = parser.createRawMsg(msgInfo);
            System.out.println("Initial creation:" + parser.toLiteral(raw));

            PipedInputStream in = new PipedInputStream();
            PipedOutputStream out = new PipedOutputStream(in);
            out.write(raw);
            out.flush();
            out.close();
            MsgInfo msgInfoNew = parser.parse(in)[0];
            byte[] rawNew = parser.createRawMsg(msgInfoNew);
            System.out.println("Parsed and dumped again:" + parser.toLiteral(rawNew));
         }
         {
            System.out.println("TEST2 SHOULD KEEP LITERAL STRING");
            parser.init(glob, null, null);
            MsgInfo msgInfo = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, "12",
                  MethodName.PUBLISH, "secret", null);
            byte[] content = "Hello World!".getBytes();
            MsgUnitRaw msg = new MsgUnitRaw("<key oid='hello'/>", content, "<qos></qos>");
            msgInfo.addMessage(msg);
            byte[] raw = parser.createRawMsg(msgInfo);
            System.out.println("Initial creation:" + parser.toLiteral(raw));

            PipedInputStream in = new PipedInputStream();
            PipedOutputStream out = new PipedOutputStream(in);
            out.write(raw);
            out.flush();
            out.close();
            MsgInfo msgInfoNew = parser.parse(in)[0];
            byte[] rawNew = parser.createRawMsg(msgInfoNew);
            System.out.println("Parsed and dumped again:" + parser.toLiteral(rawNew));
         }
         {
            System.out.println("TEST3");
            parser.init(glob, null, null);
            MsgInfo msgInfo = new MsgInfo(glob, MsgInfo.RESPONSE_BYTE, "12",
                  MethodName.PUBLISH, "secret", null);
            byte[] content = null;
            String qos = "<qos>" +
                         "\n  <key oid='1'/>" +
                         "\n  <rcvTimestamp nanos='1131654994574000000'/>" +
                         "\n  <isPublish/>" +
                         "\n</qos>";
            MsgUnitRaw msg = new MsgUnitRaw(null, content, qos);
            msgInfo.addMessage(msg);
            byte[] raw = parser.createRawMsg(msgInfo);
            System.out.println(parser.toLiteral(raw));
         }
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

   /**
    *  If used by email, the InputStream finishes when the attaachment is read,
    *  if used over socket, we need to terminate each script with a null byte
    */
   public boolean isNullTerminated() {
      return isNullTerminated;
   }
}

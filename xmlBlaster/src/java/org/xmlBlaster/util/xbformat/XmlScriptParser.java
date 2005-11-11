/*------------------------------------------------------------------------------
 Name:      XmlScriptParser.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.xbformat;

import org.xmlBlaster.client.script.XmlScriptInterpreter;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnitRaw;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
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
 */
public class XmlScriptParser extends XmlScriptInterpreter implements
      I_MsgInfoParser {
   private static Logger log = Logger
         .getLogger(XmlScriptParser.class.getName());

   private static final String ME = "xbformat.XmlScriptParser";

   private Global glob;

   public static final String XMLSCRIPT_EXTENSION = ".xml";

   public static final String XMLSCRIPT_ZLIB_EXTENSION = ".xmlz";

   /**
    * If not null somebody wants to be notified about the current bytes send
    * over socket
    */
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
   public final MsgInfo parse(InputStream in) {
      if (log.isLoggable(Level.FINER))
         log.finer("Entering parse()");
      this.msgInfoParsed = new MsgInfo(this.glob);
      this.msgInfoParsed.setMsgInfoParser(this);

      try {
         Reader reader = new InputStreamReader(in);
         super.parse(reader);
         if (this.progressListener != null) {
            long size = this.msgInfoParsed.getUserDataLen();
            this.progressListener.progressRead(this.msgInfoParsed
                  .getMethodNameStr(), size, size);
         }
      } catch (Exception e) {
         e.printStackTrace();
         log.severe("Client failed: " + e.toString());
      }

      if (log.isLoggable(Level.FINE))
         log.fine("Leaving parse(), message successfully parsed");
      return this.msgInfoParsed;
   }

   /**
    * Called during XML parsing.
    */
   public boolean fireMethod(MethodName methodName,
         String sessionId, String requestId)
         throws XmlBlasterException {
      if (this.msgInfoParsed.getNumMessages() > 0) {
         log.severe("Multiple method invocation in one message is not yet supported, we ignore "
                     + methodName.toString());
         return false;
      }
      if (super.messageList != null && super.messageList.size() > 0) {
         // MethodName.PUBLISH_ARR and PUBLISH_ONEWAY
         log.severe("Sending message arrays with '" + methodName.toString() + "' is not implemented");
         return false;
      }
      
      
      MsgUnitRaw msgUnitRaw = new MsgUnitRaw(super.key.toString(),
            super.contentData, super.qos.toString());
      this.msgInfoParsed.setMethodName(methodName);
      this.msgInfoParsed.setSecretSessionId(sessionId);
      this.msgInfoParsed.setRequestId(requestId);
      this.msgInfoParsed.addMessage(msgUnitRaw);
      return true;
   }

   /**
    * Returns a XML data string.
    */
   public final byte[] createRawMsg(MsgInfo msgInfo) throws XmlBlasterException {
      try {
         long len = msgInfo.getUserDataLen() + 500;
         ByteArray out = new ByteArray((int) len);
         super.serialize(msgInfo.getMethodName(),
                         msgInfo.getSecretSessionId(),
                         msgInfo.getRequestId(),
                         msgInfo.getMessageArr(),
                         null, out);
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
            parser.init(glob, null);
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
            MsgInfo msgInfoNew = parser.parse(in);
            byte[] rawNew = parser.createRawMsg(msgInfoNew);
            System.out.println("Parsed and dumped again:" + parser.toLiteral(rawNew));
         }
         {
            System.out.println("TEST1 SHOULD FORCE BASE64");
            parser.init(glob, null);
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
            MsgInfo msgInfoNew = parser.parse(in);
            byte[] rawNew = parser.createRawMsg(msgInfoNew);
            System.out.println("Parsed and dumped again:" + parser.toLiteral(rawNew));
         }
         {
            System.out.println("TEST2 SHOULD KEEP LITERAL STRING");
            parser.init(glob, null);
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
            MsgInfo msgInfoNew = parser.parse(in);
            byte[] rawNew = parser.createRawMsg(msgInfoNew);
            System.out.println("Parsed and dumped again:" + parser.toLiteral(rawNew));
         }
         {
            System.out.println("TEST3");
            parser.init(glob, null);
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
}

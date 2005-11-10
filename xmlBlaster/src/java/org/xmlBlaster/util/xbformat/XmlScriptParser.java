/*------------------------------------------------------------------------------
Name:      XmlScriptParser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.xbformat;

import org.jutils.log.LogChannel;

import org.xmlBlaster.client.script.I_MsgUnitCb;
import org.xmlBlaster.client.script.XmlScriptInterpreter;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnitRaw;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

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
public class XmlScriptParser implements I_MsgInfoParser
{
   private static final String ME = "xbformat.XmlScriptParser";
   private Global glob;
   private LogChannel log;

   public static final String XMLSCRIPT_EXTENSION = ".xml";
   public static final String XMLSCRIPT_ZLIB_EXTENSION = ".xmlz";

   /** If not null somebody wants to be notified about the current bytes send over socket */
   private I_ProgressListener progressListener;

   public XmlScriptParser() {
   }

   public void init(Global glob, I_ProgressListener progressListener) {
      this.glob = glob;
      this.log = glob.getLog("script");
      this.progressListener = progressListener;
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
      if (log.CALL) log.call(ME, "Entering parse()");
      MsgInfo msgInfo = new MsgInfo(this.glob);
      msgInfo.setMsgInfoParser(this);

      try {
         Reader reader = new InputStreamReader(in);
         //XmlScriptInterpreter interpreter = new XmlScriptInterpreter(this.glob, this.glob.getXmlBlasterAccess(), this.outStream, this.updStream, null);
         //interpreter.parse(reader);
      }
      catch (Exception e) {
         log.error(ME, "Client failed: " + e.toString());
         // e.printStackTrace();
      }
      
      if (log.TRACE) log.trace(ME, "Leaving parse(), message successfully parsed");
      return msgInfo;
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
         log.warn(ME, text + " " + e.toString());
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

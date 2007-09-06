/*------------------------------------------------------------------------------
Name:      StreamCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.script;

import java.io.IOException;
import java.io.OutputStream;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * StreamCallback is a sample implementation of the I_Callback interface which
 * provides basic functionality as a callback to the XmlScriptInterpreter. It writes
 * the information it gets from the update method to the output stream in an xml formatted
 * way.
 * &lt;p/>
 * If you want another behavior (for example by outputting the content in base64) you
 * can overwrite the writeContent method.
 *   
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class StreamCallback implements I_Callback {

   private final String ME = "StreamCallback";
   private Global global;
   private static Logger log = Logger.getLogger(StreamCallback.class.getName());
   private OutputStream out;
   private String offset = "";

   /**
    * The constructor 
    * @param global The global
    * @param out the output stream to which you want to send the information coming to the
    *        update method.
    * @param offset the offset to use.
    */
   public StreamCallback(Global global, OutputStream out, String offset) {
      this.global = global;

      this.out = out;
      if (offset != null) this.offset = offset;
   }


   /**
    * Invoked in the update method to write out the content in an xml 
    * formatted way.
    *
    * @param content the content to write
    * @param buf the StringBuffer object to fill with the content
    */
   protected void writeContent(byte[] content, StringBuffer buf) {
      buf.append(this.offset).append("   ").append("<content>");
      buf.append("<![CDATA[").append(new String(content)).append("]]>"); // here you can change according to your needs
      buf.append("</content>");
   }

   /**
    * Enforced by I_Callback
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      if (this.out == null) return "OK";
      StringBuffer buf = new StringBuffer();
      buf.append("\n<!-- ___________________________________ update ________________________________ -->");
      buf.append("\n").append(this.offset).append("<update>\n");
      buf.append(this.offset).append("<sessionId>").append(cbSessionId).append("</sessionId>");
      buf.append(updateKey.toXml(this.offset + "  ")).append("\n");
      writeContent(content, buf);
      buf.append(updateQos.toXml(this.offset + "  ")).append("\n");
      buf.append(this.offset).append("</update>\n");
      synchronized (this.out) {
         try {
            this.out.write(buf.toString().getBytes());
         }
         catch (IOException ex) {
            throw new XmlBlasterException(updateKey.getGlobal(), ErrorCode.USER_CLIENTCODE, ME + ".update");
         }
      }
      return "OK";
   }

}

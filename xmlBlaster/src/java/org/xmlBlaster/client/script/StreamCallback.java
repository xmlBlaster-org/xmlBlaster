/*------------------------------------------------------------------------------
Name:      StreamCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.script;

import java.io.IOException;
import java.io.OutputStream;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;

/**
 * StreamCallback
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class StreamCallback implements I_Callback {

   private final String ME = "StreamCallack";
   private OutputStream out;
   private String offset = "  ";

   public StreamCallback(OutputStream out, String offset) {
      this.out = out;
      if (offset != null) this.offset = offset;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.client.I_Callback#update(java.lang.String, org.xmlBlaster.client.key.UpdateKey, byte[], org.xmlBlaster.client.qos.UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      if (this.out == null) return "OK";
      StringBuffer buf = new StringBuffer();
      buf.append(this.offset).append("<update>\n");
      buf.append(this.offset).append("  <sessionId>").append(cbSessionId).append("</sessionId>\n");
      buf.append(updateKey.toXml()).append("\n");
      buf.append(this.offset).append("  <content>\n");
      buf.append(this.offset).append("    ").append(new String(content)).append("\n");
      buf.append(this.offset).append("  </content>\n");
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

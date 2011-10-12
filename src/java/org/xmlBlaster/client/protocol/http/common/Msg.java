/*------------------------------------------------------------------------------
Name:      Msg.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.http.common;

import java.io.UnsupportedEncodingException;
import java.util.Hashtable;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * Encapsulates the xmlKey, content and qos. 
 */
public final class Msg
{
   private transient static final Hashtable EMPTY_MAP = new Hashtable();
   private transient static final byte[] EMPTY_BYTEARR = new byte[0];
   private final Hashtable qos;
   private final Hashtable key;
   private final byte[] content;

   public Msg(Hashtable key, byte[] content, Hashtable qos) {
      this.qos = (qos == null) ? EMPTY_MAP : qos;
      this.key = (key == null) ? EMPTY_MAP : key;
      this.content = (content == null) ? EMPTY_BYTEARR : content;
   }

   /**
    * The message key, never null
    */
   public Hashtable getKey() {
      return this.key;
   }

   /**
    * Get the raw content, never null
    */
   public byte[] getContent() {
      return this.content;
   }

   /**
    * Get the raw content, never null
    */
   public String getContentStr() throws XmlBlasterException {
      String encoding = (String)qos.get(Constants.CLIENTPROPERTY_CONTENT_CHARSET);
      if (encoding == null)
         encoding = Constants.UTF8_ENCODING;
      try {
         return new String(this.content, encoding);
      } catch (UnsupportedEncodingException e) {
         e.printStackTrace();
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_ILLEGALARGUMENT, "ClientProperty", "Could not encode according to '" + encoding + "': " + e.getMessage());
      }
   }

   /**
    * The message QoS, never null
    */
   public Hashtable getQos() {
      return this.qos;
   }
}

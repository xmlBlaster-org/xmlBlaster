/*------------------------------------------------------------------------------
Name:      XmlScriptAccess.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.activex;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Base64;

/**
 * Event object used to transport a callback message back to ActiveX (C#, VisualBasic). 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class UpdateEvent extends java.util.EventObject {
   String cbSessionId;
   UpdateKey key;
   byte[] content;
   UpdateQos qos;
   public UpdateEvent(Object source, String cbSessionId, UpdateKey key, byte[] content, UpdateQos qos) {
      super(source);
      this.cbSessionId = cbSessionId;
      this.key = key;
      this.content = content;
      this.qos = qos;
   }
   
   public String getCbSessionId() {
      return this.cbSessionId;
   }
   
   public UpdateKey getKey() {
      return this.key;
   }

   /**
    * NOTE: Passing byte[] seems to be broken with Java bridge! 
    *
    * If you have binary data use #getContentBase64() as a work around
    * you need to decode it yourself in Basic/C#.<br />
    * If you have a string content (like xml markup) you can call
    * #getContentStr() directly.
    *
    * @see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4887461
    */ 
   public byte[] getContent() {
      return this.content;
   }

   public String getContentStr() {
      return new String(this.content);
   }

   public int getContentLength() {
      return this.content.length;
   }

   /**
    * Access binary content encoded with Base64. 
    */
   public String getContentBase64() {
      String encoded = Base64.encode(this.content);
      return encoded;
   }

   public UpdateQos getQos() {
      return this.qos;
   }
}


/*------------------------------------------------------------------------------
Name:      XmlScriptAccess.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.activex;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;

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
   public byte[] getContent() {
      return this.content;
   }
   public String getContentStr() {
      return new String(this.content);
   }
   public UpdateQos getQos() {
      return this.qos;
   }
}


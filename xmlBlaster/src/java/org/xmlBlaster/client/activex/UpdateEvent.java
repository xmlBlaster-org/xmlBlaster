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
   String key;
   byte[] content;
   String qos;
   public UpdateEvent(Object source, String cbSessionId, String key, byte[] content, String qos) {
      super(source);
      this.cbSessionId = cbSessionId;
      this.key = key;
      this.content = content;
      this.qos = qos;
   }
   public String getCbSessionId() {
      return this.cbSessionId;
   }
   public String getKey() {
      return this.key;
   }
   public String getContentStr() {
      return new String(this.content);
   }
   public String getQos() {
      return this.qos;
   }
}


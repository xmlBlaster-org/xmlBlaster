/*------------------------------------------------------------------------------
Name:      MessageWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    Thomas Bodemer
------------------------------------------------------------------------------*/
package javaclients.simplereader;

import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;


public class MessageWrapper {
   private String secretCallbackSessionId;
   private UpdateKey updateKey;
   private byte[] content;
   private UpdateQos updateQoS;

   public MessageWrapper(String secretCallbackSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQoS) {
      this.secretCallbackSessionId = secretCallbackSessionId;
      this.updateKey = updateKey;
      this.content = content;
      this.updateQoS = updateQoS;
   }
   public String getSecretCallbackSessionId() {
      return (this.secretCallbackSessionId);
   }
   public UpdateKey getUpdateKey() {
      return (this.updateKey);
   }
   public byte[] getContent() {
      return (this.content);
   }
   public UpdateQos getUpdateQos() {
      return (this.updateQoS);
   }
} // -- class

// -- file

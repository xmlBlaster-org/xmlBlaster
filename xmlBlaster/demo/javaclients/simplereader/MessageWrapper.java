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
   private String loginName;
   private UpdateKey updateKey;
   private byte[] content;
   private UpdateQos updateQoS;

   public MessageWrapper(String loginName, UpdateKey updateKey, byte[] content, UpdateQos updateQoS) {
      this.loginName = loginName;
      this.updateKey = updateKey;
      this.content = content;
      this.updateQoS = updateQoS;
   }
   public String getLoginName() {
      return (this.loginName);
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

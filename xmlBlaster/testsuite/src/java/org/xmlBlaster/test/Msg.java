/*------------------------------------------------------------------------------
Name:      Msgs.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test;

import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;

/**
 * Helper for testsuite to store a received message with update()
 */
public class Msg
{
   private String cbSessionId;
   private UpdateKey updateKey;
   private byte[] content;
   private UpdateQos updateQos;

   public Msg(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      this.cbSessionId = cbSessionId;
      this.updateKey = updateKey;
      this.content = content;
      this.updateQos = updateQos;
   }

   public String getCbSessionId() {
      return this.cbSessionId;
   }

   public byte[] getContent() {
      return this.content;
   }

   public String getContentStr() {
      return new String(this.content);
   }

   /**
    * @exception IllegalArgumentException
    */
   public int getContentInt() {
      try {
         return Integer.parseInt(getContentStr());
      } catch(NumberFormatException e) {
         throw new IllegalArgumentException("Invalid number " + getContentStr() + ": " + e.toString() + ": " + updateKey.toXml());
      }
   }

   public UpdateKey getUpdateKey() {
      return this.updateKey;
   }

   public String getOid() {
      return (this.updateKey == null) ? null : this.updateKey.getOid();
   }

   public UpdateQos getUpdateQos() {
      return this.updateQos;
   }

   public String getState() {
      return (this.updateQos == null) ? null : this.updateQos.getState();
   }
}

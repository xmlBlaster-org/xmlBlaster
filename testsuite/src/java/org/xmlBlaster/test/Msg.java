/*------------------------------------------------------------------------------
Name:      Msg.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test;

import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.MsgQosData;

import junit.framework.Assert;

/**
 * Helper for testsuite to store a received message with update()
 */
public class Msg extends Assert
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

   /**
    * Check if the given message (usually published) is the one we hold (usually updated).
    * throws a junit assert on error
    * @param msgUnit the expected message
    */
   public void compareMsg(MsgUnit msgUnit) {
      MsgQosData qos = (MsgQosData)msgUnit.getQosData();
      assertEquals("The keyOid is wrong", msgUnit.getKeyOid(), updateKey.getOid());
      assertEquals("The persistence flag is lost", qos.isPersistent(), updateQos.isPersistent());
      assertEquals("The message content length is corrupted", msgUnit.getContent().length, content.length);
      try {
         assertTrue("The message content is corrupted, expected='"+
                    msgUnit.getContentStr()+"' but was '"+new String(content)+"'", msgUnit.sameContent(content));
      } catch (XmlBlasterException e) {
         e.printStackTrace();
         fail("Exception: " + e.getMessage());
      }
   }   

   /**
    * Check if the given PublishReturnQos (from a publisher) is the one we hold (usually updated).
    * throws a junit assert on error
    * @param retQos the expected data
    */
   public void compareMsg(PublishReturnQos retQos) {
      assertEquals("The receive timestamp is corrupted", retQos.getRcvTimestamp(), updateQos.getRcvTimestamp());
   }   
}

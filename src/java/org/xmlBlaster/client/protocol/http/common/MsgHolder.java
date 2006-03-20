/*------------------------------------------------------------------------------
Name:      MsgHolder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.protocol.http.common;

/**
 * MsgHolder is a placeholder for the messages to be sent to xmlBlaster, i.e.
 * the requests to xmlBlaster.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class MsgHolder {

   private final String oid;
   private final String key;
   private final String qos;
   private final byte[] content;

   /**
    * 
    * @param oid the oid to be passed or null
    * @param key the key of the message or null
    * @param qos the qos or null
    * @param content the content or null
    */
   public MsgHolder(String oid, String key, String qos, byte[] content) {
      this.oid = oid;
      this.key = key;
      this.qos = qos;
      this.content = content;
   }
   
   public String getOid() {
      return this.oid;
   }
   
   public String getKey() {
      return this.key;
   }
   
   public String getQos() {
      return this.qos;
   }
   
   public byte[] getContent() {
      return this.content;
   }
   
}

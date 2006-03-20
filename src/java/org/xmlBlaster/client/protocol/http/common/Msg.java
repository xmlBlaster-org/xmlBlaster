/*------------------------------------------------------------------------------
Name:      Msg.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.http.common;

import java.util.Hashtable;

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
   public String getContentStr() {
      return new String(this.content);
   }

   /**
    * The message QoS, never null
    */
   public Hashtable getQos() {
      return this.qos;
   }
}

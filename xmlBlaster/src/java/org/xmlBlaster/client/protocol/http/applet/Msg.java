/*------------------------------------------------------------------------------
Name:      Msg.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.http.applet;

import java.util.Map;
import java.util.TreeMap;

/**
 * Encapsulates the xmlKey, content and qos. 
 */
public final class Msg implements java.io.Serializable
{
   private transient static final Map EMPTY_MAP = new TreeMap();
   private transient static final byte[] EMPTY_BYTEARR = new byte[0];
   private final Map qos;
   private final Map key;
   private final byte[] content;

   public Msg(Map key, byte[] content, Map qos) {
      this.qos = (qos == null) ? EMPTY_MAP : qos;
      this.key = (key == null) ? EMPTY_MAP : key;
      this.content = (content == null) ? EMPTY_BYTEARR : content;
   }

   /**
    * The message key, never null
    */
   public Map getKey() {
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
   public Map getQos() {
      return this.qos;
   }
}

/*------------------------------------------------------------------------------
Name:      MsgHolder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.protocol.http.common;

/**
 * MsgHolder
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class MsgHolder {

   public MsgHolder(String oid, String key, String qos, byte[] content) {
      this.oid = oid;
      this.key = key;
      this.qos = qos;
      this.content = content;
   }

   public String oid;
   public String key;
   public String qos;
   public byte[] content;

}

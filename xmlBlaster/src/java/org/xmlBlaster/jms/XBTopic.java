/*------------------------------------------------------------------------------
Name:      XBTopic.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;

import javax.jms.JMSException;
import javax.jms.Topic;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

/**
 * XBTopic
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTopic implements Topic, Referenceable, Externalizable {

   private final static String ME = "XBTopic";
   private final static long SER_VERSION = 1;
   private String topicName;
   
   public XBTopic() {
   }

   public XBTopic(String topicName) {
      this.topicName = topicName;
   }

   public String getTopicName() throws JMSException {
      return this.topicName;
   }

   public String toString() {
      return this.topicName;
   }

   public Reference getReference() {
      Reference ret = new Reference(this.getClass().getName(), XBObjectFactory.class.getName(), null);
      ret.add(new StringRefAddr("topicName", this.topicName));         
      return ret;
   }

   public void readExternal(ObjectInput oi) throws IOException, ClassNotFoundException {
      long version = oi.readLong();
      if (version <= SER_VERSION) {
         this.topicName = (String)oi.readObject();
      }
      else throw new IOException(ME + ".writeExternal: current version '" + SER_VERSION + "' is older than serialized version '" + version + "'");
   }
   
   public void writeExternal(java.io.ObjectOutput oo) throws IOException {
      oo.writeLong(SER_VERSION);
      oo.writeObject(this.topicName);
   }

}

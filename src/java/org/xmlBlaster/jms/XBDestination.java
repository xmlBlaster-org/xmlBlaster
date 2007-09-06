/*------------------------------------------------------------------------------
Name:      XBDestination.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

/**
 * XBTopic
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public class XBDestination implements Topic, Queue, Destination, Referenceable, Externalizable {

   /**
    * 
    */
   private static final long serialVersionUID = 1L;
   private final static String ME = "XBDestination";
   private String topicName;
   private String queueName;
   private boolean forceQueuing;
   
   public XBDestination() {
   }

   /**
    * We can pass multiple queues by specifying as the queue name a comma separated list of queues.
    * This is an extention to jms.
    * @param topicName
    * @param queueName
    */
   public XBDestination(String topicName, String queueName) {
      this(topicName, queueName, false);
   }

   public XBDestination(String topicName, String queueName, boolean forceQueuing) {
      this.topicName = topicName;
      this.queueName = queueName;
      this.forceQueuing = forceQueuing;
   }

   public String getTopicName() throws JMSException {
      return this.topicName;
   }

   public boolean getForceQueuing() throws JMSException {
      return this.forceQueuing;
   }

   /**
    * @see javax.jms.Queue#getQueueName()
    */
   public String getQueueName() throws JMSException {
      return this.queueName;
   }

   public String toString() {
      return this.topicName;
   }

   public Reference getReference() {
      Reference ret = new Reference(this.getClass().getName(), XBObjectFactory.class.getName(), null);
      ret.add(new StringRefAddr("topicName", this.topicName));
      ret.add(new StringRefAddr("queueName", this.queueName));
      ret.add(new StringRefAddr("forceQueuing", "" + this.forceQueuing));
      return ret;
   }

   public void readExternal(ObjectInput oi) throws IOException, ClassNotFoundException {
      long version = oi.readLong();
      if (version <= serialVersionUID) {
         this.topicName = (String)oi.readObject();
         this.queueName = (String)oi.readObject();
         this.forceQueuing = ((Boolean)oi.readObject()).booleanValue();
      }
      else throw new IOException(ME + ".writeExternal: current version '" + serialVersionUID + "' is older than serialized version '" + version + "'");
   }
   
   public void writeExternal(java.io.ObjectOutput oo) throws IOException {
      oo.writeLong(serialVersionUID);
      oo.writeObject(this.topicName);
      oo.writeObject(this.queueName);
      oo.writeObject(new Boolean(this.queueName));
   }

}

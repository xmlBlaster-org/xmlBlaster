/*------------------------------------------------------------------------------
Name:      UpdateQosServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.def.PriorityEnum;


/**
 * Handling of update() quality of services in the server core.
 * <p />
 * This decorator hides the real qos data object and gives us a server specific view on it. 
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.util.qos.MsgQosData
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 */
public final class UpdateQosServer
{
   private final MsgQosData msgQosData;

   /**
    * A constructor for Pub/Sub message (with a subscriptionId). 
    * @param e.g. Constants.STATE_OK
    */
   public UpdateQosServer(Global glob, MsgQosData msgQosData, String state, String subscriptionId) {
      this.msgQosData = msgQosData;
      setData(this.msgQosData, state, subscriptionId);
   }

   /**
    * A constructor for PtP messages. 
    * @param e.g. Constants.STATE_OK
    */
   public UpdateQosServer(Global glob, MsgQosData msgQosData, String state) {
      this(glob, msgQosData, state, null);
   }

   /**
    * A constructor for PtP messages. 
    */
   public UpdateQosServer(Global glob, MsgQosData msgQosData) {
      this(glob, msgQosData, Constants.STATE_OK);
   }

   /**
    * Static manipulator if you want to avoid constructing one instance of UpdateQosServer
    */
   public static void setData(MsgQosData msgQosData, String state, String subscriptionId) {
      msgQosData.setState(state);
      msgQosData.setSubscriptionId(subscriptionId);
   }

   public MsgQosData getMsgQosData() {
      return this.msgQosData;
   }

   public String getSubscriptionId() {
      return this.msgQosData.getSubscriptionId();
   }

   public String getState() {
      return this.msgQosData.getState();
   }

   public PriorityEnum getPriority() {
      return this.msgQosData.getPriority();
   }

   public void incrRedeliver() {
      incrRedeliver(this.msgQosData);
   }

   public static void incrRedeliver(MsgQosData msgQosData) {
      msgQosData.incrRedeliver();
   }

   /**
    * Creates the returned callback QoS of the update() method. 
    * <p />
    * The XML syntax is described in the class description.
    * @param index Index of entry in queue
    * @param max Number of entries in queue
    */
   public String toXml(long index, long size) {
      return toXml(this.msgQosData, index, size);
   }

   /**
    * Static manipulator if you want to avoid constructing one instance of UpdateQosServer
    */
   public static String toXml(MsgQosData msgQosData, long index, long size) {
      msgQosData.setQueueIndex(index);
      msgQosData.setQueueSize(size);
      return msgQosData.toXml();
   }
}

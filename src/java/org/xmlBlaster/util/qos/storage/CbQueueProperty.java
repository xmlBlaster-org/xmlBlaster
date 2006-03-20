/*------------------------------------------------------------------------------
Name:      CbQueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.CallbackAddress;

/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */
public class CbQueueProperty extends QueuePropertyBase
{
   private static final String ME = "CbQueueProperty";
   private static Logger log = Logger.getLogger(CbQueueProperty.class.getName());

   /**
    * @param relating  To what is this queue related: Constants.RELATING_CALLBACK | Constants.RELATING_SUBJECT
    * @see QueuePropertyBase#QueuePropertyBase(Global, String)
    */
   public CbQueueProperty(Global glob, String relating, String nodeId) {
      super(glob, nodeId);

      String rel = (relating == null) ? Constants.RELATING_CALLBACK : relating;
      setRelating(rel);
      super.initialize(rel);
      if (log.isLoggable(Level.FINE)) log.fine("Created CbQueueProperty " + rel + " " + super.nodeId);
   }

   /**
    * Show some important settings for logging
    */
   public final String getSettings() {
      StringBuffer buf = new StringBuffer(256);
      buf.append("type=").append(getType()).append(" onOverflow=").append(getOnOverflow()).append(" onFailure=").append(getOnFailure()).append(" maxEntries=").append(getMaxEntries());
      if (getCurrentCallbackAddress() != null)
         buf.append(" ").append(getCurrentCallbackAddress().getSettings());
      return buf.toString();
   }

   /**
    * @param relating    To what is this queue related: Constants.RELATING_CALLBACK | Constants.RELATING_SUBJECT
    */
   public final void setRelating(String relating) {
      if (relating == null) {
         this.relating = Constants.RELATING_CALLBACK;
         return;
      }
      relating = relating.toLowerCase();
      if (Constants.RELATING_CALLBACK.equals(relating))
         this.relating = Constants.RELATING_CALLBACK;
      else if (Constants.RELATING_SUBJECT.equals(relating))
         this.relating = Constants.RELATING_SUBJECT;
      else {
         log.warning("setRelating: The queue relating attribute is invalid '" + relating + "', setting to session scope");
         this.relating = Constants.RELATING_CALLBACK;
      }
   }

   public final boolean isSubjectRelated() {
      return Constants.RELATING_SUBJECT.equals(getRelating());
   }
   public final boolean isSessionRelated() {
      return Constants.RELATING_CALLBACK.equals(getRelating());
   }

   public final boolean onOverflowDeadMessage() {
      if (Constants.ONOVERFLOW_DEADMESSAGE.equalsIgnoreCase(getOnOverflow()))
         return true;
      return false;
   }


   /**
    * Currently only one address is allowed, failover addresses will be implemented in a future version
    */
   public void setCallbackAddress(CallbackAddress address) {
      this.addressArr = new CallbackAddress[1];
      this.addressArr[0] = address;
   }

   /**
    */
   public void setCallbackAddresses(CallbackAddress[] addresses) {
      if (addresses == null) {
         this.addressArr = EMPTY_ADDRESS_ARR;
      }
      else {
         this.addressArr = addresses;
      }
   }

   /**
    * @return array with size 0 if none available
    */
   public CallbackAddress[] getCallbackAddresses()
   {
      CallbackAddress[] cba = new CallbackAddress[this.addressArr.length];
      for (int ii=0; ii<this.addressArr.length; ii++)
         cba[ii] = (CallbackAddress)this.addressArr[ii];
      return cba;
   }

   /**
    * @return Never null, a default is created if none is available. 
    */
   public CallbackAddress getCurrentCallbackAddress() {
      if (this.addressArr.length > 1)
         log.severe("We have " + this.addressArr.length + " callback addresses, using the first only");
      if (this.addressArr.length > 0)
         return (CallbackAddress)this.addressArr[0];
      CallbackAddress addr = new CallbackAddress(glob);
      setCallbackAddress(addr);
      return addr;
   }

   /** For testing: java org.xmlBlaster.engine.helper.CbQueueProperty */
   public static void main(String[] args) {
      CbQueueProperty prop = new CbQueueProperty(new Global(args), null, null);
      System.out.println(prop.toXml());
      CallbackAddress adr = new CallbackAddress(new Global(args), "EMAIL");
      adr.setRawAddress("et@mars.sun");
      prop.setCallbackAddress(adr);
      System.out.println(prop.toXml());
   }
}



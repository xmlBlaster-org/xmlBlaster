/*------------------------------------------------------------------------------
Name:      CbQueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: CbQueueProperty.java,v 1.6 2002/12/18 10:17:28 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xml.sax.Attributes;

/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.util.ConnectQos
 */
public class CbQueueProperty extends QueuePropertyBase
{
   private static final String ME = "CbQueueProperty";
   private final LogChannel log;

   /**
    * @param relating  To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   public CbQueueProperty(Global glob, String relating, String nodeId) {
      super(glob, nodeId);
      this.log = glob.getLog("dispatch");
      initialize();
      setRelating(relating);
   }

   /**
    * Show some important settings for logging
    */
   public final String getSettings() {
      StringBuffer buf = new StringBuffer(256);
      buf.append("type=").append(getType()).append(" onOverflow=").append(getOnOverflow()).append(" onFailure=").append(getOnFailure()).append(" maxMsg=").append(getMaxMsg());
      if (getCurrentCallbackAddress() != null)
         buf.append(" ").append(getCurrentCallbackAddress().getSettings());
      return buf.toString();
   }

   /**
    * Configure property settings
    */
   protected void initialize() {
      super.initialize("cb");
   }

   /**
    * @param relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT
    */
   public final void setRelating(String relating) {
      if (relating == null) {
         this.relating = Constants.RELATING_SESSION;
         return;
      }
      relating = relating.toLowerCase();
      if (Constants.RELATING_SESSION.equals(relating))
         this.relating = Constants.RELATING_SESSION;
      else if (Constants.RELATING_SUBJECT.equals(relating))
         this.relating = Constants.RELATING_SUBJECT;
      else {
         log.warn(ME, "The queue relating attribute is invalid '" + relating + "', setting to session scope");
         this.relating = Constants.RELATING_SESSION;
      }
   }

   public final boolean isSubjectRelated() {
      return Constants.RELATING_SUBJECT.equals(getRelating());
   }
   public final boolean isSessionRelated() {
      return Constants.RELATING_SESSION.equals(getRelating());
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
      this.addressArr = addresses;
   }

   /**
    * @return array with size 0 if none available
    */
   public CallbackAddress[] getCallbackAddresses()
   {
      CallbackAddress[] cba = new CallbackAddress[addressArr.length];
      for (int ii=0; ii<addressArr.length; ii++)
         cba[ii] = (CallbackAddress)addressArr[ii];
      return cba;
   }

   /**
    * @return null if none available
    */
   public CallbackAddress getCurrentCallbackAddress() {
      if (this.addressArr.length > 1)
         log.error(ME, "We have " + this.addressArr.length + " callback addresses, using the first only");
      if (this.addressArr.length > 0)
         return (CallbackAddress)this.addressArr[0];
      return null;
   }

   /** For testing: java org.xmlBlaster.engine.helper.CbQueueProperty */
   public static void main(String[] args) {
      CbQueueProperty prop = new CbQueueProperty(new Global(args), null, null);
      System.out.println(prop.toXml());
      CallbackAddress adr = new CallbackAddress(new Global(args), "EMAIL");
      adr.setAddress("et@mars.sun");
      prop.setCallbackAddress(adr);
      System.out.println(prop.toXml());
   }
}



/*------------------------------------------------------------------------------
Name:      QueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.engine.helper.QueuePropertyBase;
import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.util.enum.Constants;

/**
 * Helper class to configure the client side queue. 
 */
public class QueueProperty extends QueuePropertyBase
{
   private static final String ME = "QueueProperty";

   public QueueProperty(Global glob) {
      this(glob, null);
   }

   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   public QueueProperty(Global glob, String nodeId) {
      super(glob, nodeId);
      setRelating(Constants.RELATING_CLIENT);
      initialize();
   }

   /**
    * Show some important settings for logging
    */
   public final String getSettings() {
      StringBuffer buf = new StringBuffer(256);
      buf.append("onOverflow=").append(getOnOverflow()).append(" onFailure=").append(getOnFailure()).append(" maxMsg=").append(getMaxMsg());
      if (getCurrentAddress() != null)
         buf.append(" ").append(getCurrentAddress().getSettings());
      return buf.toString();
   }

   /**
    * Configure property settings
    */
   protected void initialize() {
      super.initialize(""); // change to "client"
   }

   /**
    * Currently only one address is allowed, failover addresses will be implemented in a future version
    */
   public void setAddress(Address address) {
      this.addressArr = new Address[1];
      this.addressArr[0] = address;
   }

   /**
    */
   public void setAddresses(Address[] addresses) {
      this.addressArr = addresses;
   }

   /**
    * @return null if none available
   public Address[] getAddresses() {
      return (Address[])this.addressArr;
   }
    */

   /**
    * @return null if none available
    */
   public Address getCurrentAddress() {
      if (this.addressArr.length > 0)
         return (Address)this.addressArr[0];
      return null;
   }

   /**
    * Get a usage string for the connection parameters
    */
   public final String usage() {
      return super.usage("Control client side fail save queue properties (message recorder):");
   }

   /** For testing: java org.xmlBlaster.engine.helper.QueueProperty */
   public static void main(String[] args) {
      QueueProperty prop = new QueueProperty(new Global(args), null);
      System.out.println(prop.toXml());
      Address adr = new Address(new Global(args), "EMAIL");
      adr.setAddress("et@mars.sun");
      prop.setAddress(adr);
      System.out.println(prop.toXml());
   }
}



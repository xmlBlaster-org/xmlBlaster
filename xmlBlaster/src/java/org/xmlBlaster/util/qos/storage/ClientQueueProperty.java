/*------------------------------------------------------------------------------
Name:      ClientQueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.address.Address;
import org.xml.sax.Attributes;

/**
 * Helper class holding the client side queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */
public class ClientQueueProperty extends QueuePropertyBase
{
   private static final String ME = "ClientQueueProperty";

   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue/maxEntries and -queue/clientSide/maxEntries[heron] will be searched
    */
   public ClientQueueProperty(Global glob, String nodeId) {
      super(glob, nodeId);
      setRelating(Constants.RELATING_CLIENT);
      super.initialize(Constants.RELATING_CLIENT);
   }

   /**
    * Show some important settings for logging
    */
   public final String getSettings() {
      StringBuffer buf = new StringBuffer(256);
      buf.append("type=").append(getType()).append(" onOverflow=").append(getOnOverflow()).append(" onFailure=").append(getOnFailure()).append(" maxEntries=").append(getMaxEntries());
      if (getCurrentAddress() != null)
         buf.append(" ").append(getCurrentAddress().getSettings());
      return buf.toString();
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

   /** For testing: java org.xmlBlaster.util.qos.storage.ClientQueueProperty */
   public static void main(String[] args) {
      ClientQueueProperty prop = new ClientQueueProperty(new Global(args), null);
      System.out.println(prop.toXml());
      Address adr = new Address(new Global(args), "EMAIL");
      adr.setRawAddress("et@mars.sun");
      prop.setAddress(adr);
      System.out.println(prop.toXml());
   }
}



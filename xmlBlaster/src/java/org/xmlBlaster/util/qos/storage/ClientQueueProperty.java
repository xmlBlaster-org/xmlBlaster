/*------------------------------------------------------------------------------
Name:      ClientQueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.AddressBase;

/**
 * Helper class holding the client side queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */
public class ClientQueueProperty extends QueuePropertyBase
{
   private static final String ME = "ClientQueueProperty";
   private String defaultType;

   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue/maxEntries and -queue/connection/maxEntries[heron] will be searched
    */
   public ClientQueueProperty(Global glob, String nodeId) {
      super(glob, nodeId);
      setRelating(Constants.RELATING_CLIENT);
      super.initialize(Constants.RELATING_CLIENT);

      // On client side we store the complete messages in the queue, therefor increase max settings here:
      //super.maxEntriesCache.setDefaultValue(2000);
      super.maxEntries.setDefaultValue(Integer.MAX_VALUE);
      //super.maxBytesCache.setDefaultValue(Integer.MAX_VALUE);
      super.maxBytes.setDefaultValue(Integer.MAX_VALUE);
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
   public synchronized void setAddress(Address address) {
      if (address == null) return;
      this.addressArr = new Address[1];
      this.addressArr[0] = address;
   }

   public synchronized void addAddress(Address address) {
      AddressBase[] arr = this.addressArr;
      this.addressArr = new AddressBase[arr.length+1];
      for (int i=0; i<arr.length; i++) {
         this.addressArr[i] = arr[i];
      }
      this.addressArr[arr.length] = address;
   }

   /**
    */
   public synchronized void setAddresses(Address[] addresses) {
      if (addresses == null) {
         this.addressArr = EMPTY_ADDRESS_ARR;
      }
      else {
         this.addressArr = addresses;
      }
   }

   /**
    * @return Never null but the length may be 0
    */
   public AddressBase[] getAddresses() {
      return this.addressArr;
   }

   /**
    * @return null if none available
    */
   public synchronized Address getCurrentAddress() {
      if (this.addressArr.length == 0) {
         return null;
      }
      else if (this.addressArr.length == 1) {
         return (Address)this.addressArr[0];
      }
      else {
         for (int i=0; i<this.addressArr.length; i++) {
            if (getDefaultType().equals(this.addressArr[i].getType())) {
               return (Address)this.addressArr[i];
            }
         }
         return (Address)this.addressArr[0];
      }
   }

   /**
    * Try to find the default protocol address as configured for clients
    * @return Never null
    */
   private String getDefaultType() {
      if (this.defaultType == null) {
         synchronized (this) {
            if (this.defaultType == null) {
               Address def = new Address(glob);
               this.defaultType = def.getType();
               if (this.defaultType == null) {
                  this.defaultType = "SOCKET";
               }
            }
         }
      }
      return this.defaultType;
   }

   /**
    * Does the given address belong to this setup?
    */
   public synchronized boolean contains(Address other) {
      if (other == null) return false;
      for (int i=0; i<this.addressArr.length; i++) {
         if (this.addressArr[i].isSameAddress(other))
            return true;
      }
      return false;
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



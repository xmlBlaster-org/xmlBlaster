/*------------------------------------------------------------------------------
Name:      SubscriptionInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handles exactly one subscritpion (client reference and QoS of this subscrition
           $Revision: 1.2 $  $Date: 1999/11/10 20:26:49 $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;


/**
 * SubscriptionInfo
 * This is just a container to hold references on all interesting data
 * concerning a subscription of exactly one MessageContainer of exactly one Client
 */
public class SubscriptionInfo implements Comparable
{
   private String ME = "SubscriptionInfo";

   private ClientInfo clientInfo;   // reference on ClientInfo
   private XmlKey xmlKey;           // reference to xmlKey
   private XmlQoS xmlQoS;           // reference to 'Quality of Service' of subscription

   private long creationTime = System.currentTimeMillis();

   public SubscriptionInfo(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS subscribeQoS)
   {
      this.clientInfo = clientInfo;
      this.xmlKey = xmlKey;
      this.xmlQoS = subscribeQoS;

      if (Log.CALLS) Log.trace(ME, "Created new SubscriptionInfo " + xmlKey.getUniqueKey());
   }


   /**
    * @return the creation time of this subscription (in millis)
    */
   public long getCreationTime()
   {
      return creationTime;
   }


   /**
    * Compare method needed for Interface Comparable. 
    *
    * This determines the sorting order, by which the
    * client receive their updates.
    * For now, the client which subscribed first, is served first
    */
   public int compareTo(Object o)
   {
      SubscriptionInfo sub = (SubscriptionInfo)o;
      long diff = sub.getCreationTime() - getCreationTime();
      if (diff < 0L)
         return -1;
      else if (diff > 0L)
         return +1;
      else
         return 0;
   }


   public ClientInfo getClientInfo()
   {
      return clientInfo;
   }

   public XmlKey getXmlKey()
   {
      return xmlKey;
   }

   public XmlQoS getXmlQoS()
   {
      return xmlQoS;
   }

   /**
    * @return A unique key for this particular subscription
    */
   public String getUniqueKey()
   {
      return "Subscription-" + clientInfo.hashCode() + "-" + xmlKey.hashCode() + "-" + xmlQoS.hashCode(); // !!!hack?
   }

   public final BlasterCallback getCB() throws XmlBlasterException
   {
      return clientInfo.getCB();
   }


}

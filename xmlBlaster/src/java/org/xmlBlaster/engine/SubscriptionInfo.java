/*------------------------------------------------------------------------------
Name:      SubscriptionInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles exactly one subscritpion (client reference and QoS of this subscrition
Version:   $Id: SubscriptionInfo.java,v 1.8 1999/11/18 22:12:14 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.clientIdl.BlasterCallback;


/**
 * SubscriptionInfo
 * This is just a container to hold references on all interesting data
 * concerning a subscription of exactly one MessageUnit of exactly one Client
 */
public class SubscriptionInfo /* implements Comparable see SORT_PROBLEM */
{
   private String ME = "SubscriptionInfo";

   private ClientInfo clientInfo;   // reference on ClientInfo
   private XmlKey xmlKey;           // reference to xmlKey
   private XmlQoS xmlQoS;           // reference to 'Quality of Service' of subscription
   private String uniqueKey=null;

   private MessageUnitHandler myHandler;  // reference to my managing container

   private long creationTime = System.currentTimeMillis();

   public SubscriptionInfo(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS subscribeQoS) throws XmlBlasterException
   {
      this.clientInfo = clientInfo;
      this.xmlKey = xmlKey;
      this.xmlQoS = subscribeQoS;

      if (Log.CALLS) Log.trace(ME, "Created new SubscriptionInfo " + xmlKey.getUniqueKey());
   }


   /**
    * This must be called as soon as my MessageUnitHandler handles me!
    */
   public void addMessageUnitHandler(MessageUnitHandler myHandler)
   {
      this.myHandler = myHandler;
   }


   /**
    * @return the creation time of this subscription (in millis)
    */
   public long getCreationTime()
   {
      return creationTime;
   }


   /**
    * Telling my container that i'm not enteristing any more
    */
   public void removeSubscribe() throws XmlBlasterException
   {
      if (myHandler == null) {
         Log.warning(ME, "The oid=" + uniqueKey + " has no MessageUnitHandler which takes care of it");
         return;
      }
      myHandler.removeSubscriber(this);
   }


   /**
    * Compare method needed for Interface Comparable. 
    *
    * This determines the sorting order, by which the
    * client receive their updates.
    * For now, the client which subscribed first, is served first
    */
   /*SORT_PROBLEM: Works fine with TreeSet, but with TreeMap i get the key here :-(
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
   */

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
   public String getUniqueKey() throws XmlBlasterException
   {
      if (uniqueKey == null) {
         StringBuffer buf = new StringBuffer(80);

         buf.append("Subscription-").append(clientInfo.getUniqueKey());

         buf.append("-").append(xmlKey.getUniqueKey());

         buf.append("-").append(xmlQoS.toString()); // !!!hack?

         uniqueKey = buf.toString();
      }
      return uniqueKey;
   }

}

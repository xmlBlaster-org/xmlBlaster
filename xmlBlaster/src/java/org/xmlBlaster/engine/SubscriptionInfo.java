/*------------------------------------------------------------------------------
Name:      SubscriptionInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles exactly one subscritpion (client reference and QoS of this subscrition
Version:   $Id: SubscriptionInfo.java,v 1.12 1999/11/23 15:35:48 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.util.TimeHelper;
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
    * Clean up everything, since i will be deleted now
    */
   private void erase()
   {
      if (Log.TRACE) Log.trace(ME, "Entering erase()");
      // clientInfo = null; not my business
      xmlKey = null;
      xmlQoS = null;
      uniqueKey = null;
   }


   /**
    * This must be called as soon as my MessageUnitHandler handles me!
    */
   public void addMessageUnitHandler(MessageUnitHandler myHandler)
   {
      if (myHandler == null)
         Log.error(ME, "addMessageUnitHandler with myHandler==null seems to be strange");

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
    * Telling my container that i am not subscribing any more
    */
   public void removeSubscribe() throws XmlBlasterException
   {
      if (myHandler == null) {
         Log.warning(ME, "The id=" + uniqueKey + " has no MessageUnitHandler which takes care of it");
         return;
      }
      myHandler.removeSubscriber(uniqueKey);
      erase();
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
         uniqueKey = SubscriptionInfo.generateUniqueKey(clientInfo, xmlKey, xmlQoS).toString();
      }
      return uniqueKey;
   }


   /**
    * This static method may be used from external objects to get the unique key
    * of a subscription, which is a function of f(clientInfo,xmlKey,xmlQoS)
    *
    * @return A unique key for this particular subscription, for example:<br>
    *         <code>Subscription-00 11 4D 4D 4D 4D 4C 0B 33 04 03 3F -null-null-943279576139-2-</code>
    */
   public static String generateUniqueKey(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS xmlQoS) throws XmlBlasterException
   {
      StringBuffer buf = new StringBuffer(80);

      buf.append("Subscription-").append(clientInfo.getUniqueKeyHex());

      buf.append("-").append(xmlKey.getUniqueKey());

      // !!!!!  still missing !!!!  buf.append("-").append(xmlQoS.toString()); // !!!hack?

      return buf.toString();
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of SubscriptionInfo
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of SubscriptionInfo
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<SubscriptionInfo id='" + getUniqueKey() + "'>");
      sb.append(offset + "   <clientInfo id='" + (clientInfo==null ? "null" : clientInfo.toString()) + "'/>");
      sb.append(offset + "   <xmlKey oid='" + (xmlKey==null ? "null" : xmlKey.getUniqueKey()) + "'/>");
      sb.append(offset + "   <xmlQoS id='" + (xmlQoS==null ? "null" : xmlQoS.toString()) + "'/>");
      sb.append(offset + "   <messageUnitHandler id='" + (myHandler==null ? "null" : myHandler.getUniqueKey()) + "'/>");
      sb.append(offset + "   <creationTime>" + TimeHelper.getDateTimeDump(creationTime) + "</creationTime>");
      sb.append(offset + "</SubscriptionInfo>\n");
      return sb;
   }

}

/*------------------------------------------------------------------------------
Name:      SubscriptionInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles exactly one subscritpion (client reference and QoS of this subscrition
Version:   $Id: SubscriptionInfo.java,v 1.24 2000/06/05 15:32:30 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.SubscribeQoS;
import org.xmlBlaster.engine.xml2java.UnSubscribeQoS;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.util.XmlQoSBase;
import org.jutils.TimeHelper;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;


/**
 * This is just a container to hold references on all interesting data
 * concerning a subscription of exactly one MessageUnit of exactly one Client.
 * <p />
 */
public class SubscriptionInfo /* implements Comparable see SORT_PROBLEM */
{
   private String ME = "SubscriptionInfo";

   /** reference on ClientInfo */
   private ClientInfo clientInfo;
   /** reference to xmlKey */
   private XmlKey xmlKey;
   /** reference to 'Quality of Service' base class */
   private XmlQoSBase xmlQoSBase = null;
   /** reference to 'Quality of Service' of subscription */
   private SubscribeQoS subscribeQoS = null;
   /** reference to 'Quality of Service' of unsubscription */
   private UnSubscribeQoS unSubscribeQoS = null;
   /** The unique key of a subscription, which is a function of f(clientInfo,xmlKey,xmlQoS). <br />
       This is the returned id of a subscribe() invocation */
   private String uniqueKey=null;
   /** reference to my managing container */
   private MessageUnitHandler myHandler;
   /** A reference to the query subscription (XPATH), which created this subscription
       If the subscription was EXACT, querySub is null */
   private SubscriptionInfo querySub = null;

   private long creationTime = System.currentTimeMillis();


   /**
    * Use this constructor for an exact subscription.
    * @param clientInfo The client we deal with
    * @param xmlKey     The message meta info
    * @param qos        This may be a SubscribeQoS or a UnSubscribeQoS instance (very bad hack!)
    */
   public SubscriptionInfo(ClientInfo clientInfo, XmlKey xmlKey, XmlQoSBase qos) throws XmlBlasterException
   {
      init(clientInfo, xmlKey, qos);
   }


   /**
    * Use this constructor it the subscription is a result of a XPath subscription
    * @param clientInfo The client we deal with
    * @param xmlKey     The message meta info
    * @param qos        This may be a SubscribeQoS or a UnSubscribeQoS instance (very bad hack!)
    */
   public SubscriptionInfo(SubscriptionInfo querySub, XmlKey xmlKey) throws XmlBlasterException
   {
      this.querySub = querySub;
      init(querySub.getClientInfo(), xmlKey, querySub.getSubscribeQoS());
   }


   private void init(ClientInfo clientInfo, XmlKey xmlKey, XmlQoSBase qos) throws XmlBlasterException
   {
      this.clientInfo = clientInfo;
      this.xmlKey = xmlKey;

      // very bad hack, needs redesign (SubscribeQoS or UnSubscribeQoS are handled here)
      if (qos instanceof SubscribeQoS)
         this.subscribeQoS = (SubscribeQoS)qos;
      else
         this.unSubscribeQoS = (UnSubscribeQoS)qos;

      if (Log.CALLS) Log.trace(ME, "Created new SubscriptionInfo " + xmlKey.getUniqueKey());
   }


   /**
    * Clean up everything, since i will be deleted now.
    */
   private void erase()
   {
      if (Log.TRACE) Log.trace(ME, "Entering erase()");
      // clientInfo = null; not my business
      xmlKey = null;
      xmlQoSBase = null;
      subscribeQoS = null;
      unSubscribeQoS = null;
      uniqueKey = null;
   }


   /**
    * This must be called as soon as my MessageUnitHandler handles me.
    * @param myHandler I'm handled (lifetime) by this handler
    */
   public void addMessageUnitHandler(MessageUnitHandler myHandler)
   {
      if (myHandler == null)
         Log.error(ME, "addMessageUnitHandler with myHandler==null seems to be strange");

      this.myHandler = myHandler;
   }


   /**
    * Time when this Subscription is invoked.
    * @return the creation time of this subscription (in millis)
    */
   public long getCreationTime()
   {
      return creationTime;
   }


   /**
    * Telling my container that i am not subscribing any more.
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
    * @return The message wrapper object
    * @exception If no MessageUnitWrapper available
    */
   final MessageUnitWrapper getMessageUnitWrapper() throws XmlBlasterException
   {
      if (myHandler == null) {
         Log.warning(ME, "Key oid=" + uniqueKey + " has no MessageUnitHandler which takes care of it");
         throw new XmlBlasterException(ME + ".NoMessageUnitWrapper", "Key oid=" + uniqueKey + " has no MessageUnitHandler which takes care of it");
      }
      return myHandler.getMessageUnitWrapper();
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

   /**
    * Access on ClientInfo object
    * @return ClientInfo object
    */
   public ClientInfo getClientInfo()
   {
      return clientInfo;
   }


   /**
    * Access on XmlKey object
    * @return XmlKey object
    */
   public XmlKey getXmlKey()
   {
      return xmlKey;
   }


   /**
    * Access on SubscribeQoS object
    * @return SubscribeQoS object
    */
   public SubscribeQoS getSubscribeQoS()
   {
      return subscribeQoS;
   }


   /**
    * Accessing a unique id generated for this SubscriptionInfo.
    * @return A unique key for this particular subscription
    */
   public String getUniqueKey() throws XmlBlasterException
   {
      if (uniqueKey == null) {
         uniqueKey = SubscriptionInfo.generateUniqueKey(clientInfo, xmlKey, xmlQoSBase).toString();
      }
      return uniqueKey;
   }


   /**
    * Accessing the unique subscription id from method subscribe(), which was the reason for this SubscriptionInfo
    * @return The subscription id which is used in updateQoS - $lt;subscritpionId>
    */
   public String getSubSourceUniqueKey() throws XmlBlasterException
   {
      if (querySub != null) {
         return querySub.getUniqueKey();
      }
      return xmlKey.getUniqueKey();  // Exact subscriptions deliver the key oid
   }


   /**
    * This static method may be used from external objects to get the unique key
    * of a subscription, which is a function of f(clientInfo,xmlKey,xmlQoS).
    * <p />
    * @return A unique key for this particular subscription, for example:<br>
    *         <code>Subscription-00 11 4D 4D 4D 4D 4C 0B 33 04 03 3F -null-null-943279576139-2-</code>
    */
   public static String generateUniqueKey(ClientInfo clientInfo, XmlKey xmlKey, XmlQoSBase xmlQoS) throws XmlBlasterException
   {
      StringBuffer buf = new StringBuffer(80);

      buf.append("Subscription-").append(clientInfo.getUniqueKey());

      buf.append("-").append(xmlKey.getUniqueKey());

      // !!!  still missing:  buf.append("-").append(xmlQoS.toString()); // !!!hack?

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
      if (subscribeQoS != null)
         sb.append(subscribeQoS.printOn(extraOffset + "   ").toString());
      else
         sb.append(offset + "   <SubscribeQoS></SubscribeQoS>");
      if (unSubscribeQoS != null)
         sb.append(unSubscribeQoS.printOn(extraOffset + "   ").toString());
      else
         sb.append(offset + "   <UnSubscribeQoS></UnSubscribeQoS>");
      sb.append(offset + "   <msgUnitHandler id='" + (myHandler==null ? "null" : myHandler.getUniqueKey()) + "'/>");
      sb.append(offset + "   <creationTime>" + TimeHelper.getDateTimeDump(creationTime) + "</creationTime>");
      sb.append(offset + "</SubscriptionInfo>\n");
      return sb;
   }

}

/*------------------------------------------------------------------------------
Name:      SubscriptionInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handles exactly one subscritpion (client reference and QoS of this subscrition
           $Revision: 1.1 $  $Date: 1999/11/08 22:39:55 $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;


/**
 * SubscriptionInfo
 * This is just a container to hold references on all interesting data
 * concerning a subscription of exactly one MessageContainer of exactly one Client
 */
public class SubscriptionInfo
{
   private String ME = "SubscriptionInfo";

   private ClientInfo clientInfo;   // reference on ClientInfo
   private XmlKey xmlKey;           // reference to xmlKey
   private XmlQoS xmlQoS;           // reference to 'Quality of Service' of subscription

   public SubscriptionInfo(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS subscribeQoS)
   {
      this.clientInfo = clientInfo;
      this.xmlKey = xmlKey;
      this.xmlQoS = subscribeQoS;

      if (Log.CALLS) Log.trace(ME, "Created new SubscriptionInfo " + xmlKey.getUniqueKey());
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

   public String getUniqueKey()
   {
      return xmlKey.getUniqueKey(); // !!!hack, should somehow including similar QoS
   }

   public final BlasterCallback getCB() throws XmlBlasterException
   {
      return clientInfo.getCB();
   }


}

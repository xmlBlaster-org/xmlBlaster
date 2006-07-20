/*------------------------------------------------------------------------------
Name:      SubscriptionEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The subscribe/unsubscribe event object
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * An event which indicates that a new subscription or unsubscribe occurred.
 * <p />
 * It carries the SubscriptionInfo reference inside.
 *
 * @author Marcel Ruff
 */
public class SubscriptionEvent extends java.util.EventObject {
   private static final long serialVersionUID = 1L;

   /**
    * Constructs a SubscriptionEvent object.
    *
    * @param source the SubscriptionInfo object
    */
   public SubscriptionEvent(SubscriptionInfo subscriptionInfo) {
       super(subscriptionInfo);
   }

   /**
    * Returns the originator of the event.
    *
    * @return the SubscriptionInfo object
    */
   public SubscriptionInfo getSubscriptionInfo() {
       return (SubscriptionInfo)source;
   }
   
   public String toString() {
      SubscriptionInfo i = getSubscriptionInfo();
      if (i == null) return "";
      return i.getSubscriptionId() + " / " + i.getSessionName() + " / " + i.getKeyOid();
   }
}

/*------------------------------------------------------------------------------
Name:      SubscriptionEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The subscribe/unsubscribe event object
Version:   $Id: SubscriptionEvent.java,v 1.1 1999/11/18 16:59:55 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * An event which indicates that a new subscription or unsubscribe ocurred
 * It carries the SubscriptionInfo reference inside
 *
 * @version $Id: SubscriptionEvent.java,v 1.1 1999/11/18 16:59:55 ruff Exp $
 * @author Marcel Ruff
 */
public class SubscriptionEvent extends java.util.EventObject
{
   /**
    * Constructs a SubscriptionEvent object.
    *
    * @param source the SubscriptionInfo object
    */
   public SubscriptionEvent(SubscriptionInfo subscriptionInfo)
   {
       super(subscriptionInfo);
   }

   /**
    * Returns the originator of the event.
    *
    * @return the Authentication object that originated the event
    */
   public SubscriptionInfo getSubscriptionInfo() {
       return (SubscriptionInfo)source;
   }
}

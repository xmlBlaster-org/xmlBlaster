/*------------------------------------------------------------------------------
Name:      I_SubscriptionListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

/**
 * I_SubscriptionListener
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public interface I_SubscriptionListener {

   /**
    * Invoked when a subscriber is added to the TopicHandler
    * @param subscriptionInfo
    */
   void onAddSubscriber(SubscriptionInfo subscriptionInfo);
   
   /**
    * Invoked when a subscriber is removed from the TopicHandler
    * @param subscriptionInfo
    */
   void onRemoveSubscriber(SubscriptionInfo subscriptionInfo);

}

/*------------------------------------------------------------------------------
Name:      I_AdminSubscription.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

/**
 * Declares available methods of a SubscriptionInfo object for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by SubscriptionInfo.java, delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 0.844
 */
public interface I_AdminSubscription {
   /**
    * Access the unique identifier of this subscription. 
    * @return For example "__subId:heron-12334550000"
    */
   public String getId();
   /**
    * Get the session name of the subscriber. 
    * @return The session name
    */
   public String getSessionName();
   /**
    * Get the subscribed topic. 
    * @return The topic identifier
    */
   public String getTopicId();
   /**
    * Get the parent subscription ID. 
    * For example if this subscription is a result of a XPath subscription
    * @return Can be null
    */
   public String getParentSubscription();
   /**
    * Get my depending subscriptions (childrens), usually caused by an XPATH subscription. 
    * @return Array of subscriptionIds
    */
   public String[] getDependingSubscriptions();
   /**
    * Get the human readable timestamp when this subscription was established. 
    * @return The date and time of creation
    */
   public String getCreationTimestamp();
   /**
    * Get the configuration for this subscription. 
    * @return The XML dump of the QoS
    */
   public String getSubscribeQosStr();
   /**
    * Access the filter QoS of this subscription. 
    */
   public String[] getAccessFilters();
   /**
    * Gets the uniqueId for the persistence of this session.
    * @return the uniqueId used to identify this session as an  entry
    * in the queue where it is stored  (for persistent subscriptions).
    * If the session is not persistent it returns -1L.
    */
   public long getPersistenceId();
}


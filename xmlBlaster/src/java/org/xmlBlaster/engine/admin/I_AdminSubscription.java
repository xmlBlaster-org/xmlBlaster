/*------------------------------------------------------------------------------
Name:      I_AdminSubscription.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Declares available methods of a SubscriptionInfo object for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by SubscriptionInfo.java, delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 0.844
 */
public interface I_AdminSubscription {
   public String getId();
   public String getSessionName();
   public String getTopicId();
   public String getParentSubscription();
   public String getCreationTimestamp();
}


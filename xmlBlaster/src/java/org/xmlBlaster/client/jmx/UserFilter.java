/*------------------------------------------------------------------------------
Name:      UserFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import javax.management.NotificationFilter;
import javax.management.Notification;

/**
 * Used for JMX-Notification Mechanism
 * Can be used for Filterung Notifications
 */
public class UserFilter implements NotificationFilter {
  public boolean isNotificationEnabled(Notification n) {
    return true;
  }

}
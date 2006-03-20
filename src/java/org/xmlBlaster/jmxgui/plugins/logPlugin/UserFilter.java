package org.xmlBlaster.jmxgui.plugins.logPlugin;

import javax.management.*;

public class UserFilter implements NotificationFilter {
  public boolean isNotificationEnabled(Notification n) {
    return true;
  }

}
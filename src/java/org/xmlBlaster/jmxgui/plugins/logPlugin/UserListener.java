package org.xmlBlaster.jmxgui.plugins.logPlugin;

import javax.management.*;
import java.io.Serializable;

public class UserListener  implements NotificationListener, Serializable {
  public void handleNotification(Notification notif, Object handback) {
    System.out.println("Something changed!");
  }

}
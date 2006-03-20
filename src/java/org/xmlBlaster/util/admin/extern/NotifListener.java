/*------------------------------------------------------------------------------
Name:      JmxWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin.extern;

import javax.management.NotificationListener;
import javax.management.Notification;
import java.io.Serializable;
import org.xmlBlaster.util.XmlBlasterException;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.admin.extern.SerializeHelper;

/**
 * Internal Listener for Changes within MBeans<br>
 * Listens local, on the serverside, for changes on MBeans.
 * When invoked, sends a Notification-event to the clientsystem
 */
public class NotifListener  implements NotificationListener, Serializable {

  XmlBlasterConnector connector = null;
  I_XmlBlasterAccess returnCon = null;
  Global glob = null;
  SerializeHelper serHelp = null;

  public NotifListener(XmlBlasterConnector connector, I_XmlBlasterAccess returnCon, Global glob) {
    this.connector = connector;
    this.returnCon = returnCon;
    this.glob = glob;
    serHelp = new SerializeHelper(glob);
  }

  public void handleNotification(Notification notif, Object handback) {

    try {
      ConnectQos qos = new ConnectQos(glob, "InternalConnector", "connector");
      PublishReturnQos rqos = returnCon.publish(new MsgUnit("<key oid='xmlBlasterMBeans_Notification'/>", serHelp.serializeObject(notif), "<qos/>"));
    }
    catch (XmlBlasterException ex) {
      ex.printStackTrace();
    }
    catch (Exception e) {
      e.printStackTrace();
    }

  }

}

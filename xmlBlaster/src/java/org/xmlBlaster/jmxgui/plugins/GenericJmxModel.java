/*------------------------------------------------------------------------------
Name:      GenericJmxModel.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui.plugins;

import org.xmlBlaster.jmxgui.*;

import javax.management.ObjectName;
import javax.management.MBeanAttributeInfo;
import javax.management.MalformedObjectNameException;
import java.util.Vector;
import java.util.HashMap;
import org.xmlBlaster.client.jmx.*;
import java.rmi.*;

/**
 * Model that wraps key-value pairs from MBeans that are exposed for management
 */
public class GenericJmxModel extends javax.swing.table.AbstractTableModel{
  protected Vector vecMBeans;
  protected ConnectorClient connectorClient = null;
  protected AsyncMBeanServer aServer = null;
  protected ObjectName RequestBroker = null;
  private HashMap hmtable = new HashMap();
  private String objectName ="";
  private String className="";

  public GenericJmxModel(ConnectorClient connectorClient, String objectName, String className) {
    this.objectName = objectName;
    this.connectorClient = connectorClient;
    this.className = className;
    loadData();
  }

  public void loadData() {
    try {
      ObjectName name = new ObjectName(objectName);
      boolean isLoaded = ((Boolean) (connectorClient.getServer().isRegistered(name).get())).booleanValue();
      if (!isLoaded) {
        loadMBean(objectName, className);
      }
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
    exploreMBeans();
    fillHashMap();
  }

  public void fillHashMap() {
    for (int i = 0; i<vecMBeans.size(); i++) {
      try {
        hmtable.put(new Integer(i), aServer.getAttribute(RequestBroker, (String) vecMBeans.get(i)).get());
      }
      catch (RemoteException ex) {
      }
    }
  }

  public void exploreMBeans() {
    vecMBeans = new Vector();
    try {
      aServer= connectorClient.getServer();
    }
    catch (ConnectorException ex) {
      ex.printStackTrace();
    }

    try {
      RequestBroker = new ObjectName(objectName);
    }
    catch (MalformedObjectNameException ex) {
    }
    MBeanAttributeInfo[] attrInfo = connectorClient.exploreMBeansByObjectName(objectName);
    for (int i =0; i<attrInfo.length; i++) {
      vecMBeans.addElement(attrInfo[i].getName());
    }
  }

  public void loadMBean(String myObjectName, String className) {
    try {
      AsyncMBeanServer server = connectorClient.getServer();
      server.createMBean(className, new ObjectName(myObjectName));
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }



//Swing implementation
  public boolean isCellEditable (int i, int j) {
    return false;
  }

  public int getRowCount() {
    return vecMBeans.size();
  }

  public int getColumnCount() {
    return 2;
  }
  public Object getValueAt(int row, int column) {
    Object obj = new Object();

    if (column == 0) {
      obj = vecMBeans.get(row);
    }
    if (column == 1) {
      try {
        obj = hmtable.get(new Integer(row));
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    return obj;
  }

  public String getColumnName(int columnIndex) {
    String name = "";
    if (columnIndex==0) {
     name= "key";
    }
    if (columnIndex==1) {
     name = "value";
    }
    return name;
  }

}

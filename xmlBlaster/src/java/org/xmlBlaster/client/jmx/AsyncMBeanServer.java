/*------------------------------------------------------------------------------
Name:      AsyncMBeanServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import javax.management.*;

public interface AsyncMBeanServer {

  /* returns ObjectInstance */
  public Callback createMBean(String className,
                              ObjectName name);

  /* returns ObjectInstance */
  public Callback createMBean(String className,
                              ObjectName name,
                              ObjectName loaderName);

  /* return ObjectInstance */
  public Callback createMBean(String className,
                              ObjectName name,
                              Object[] params,
                              String[] signature);

  /* returns ObjectInstance */
  public Callback createMBean(String className,
                              ObjectName name,
                              ObjectName loaderName,
                              Object[] params,
                              String[] signature);

  public void unregisterMBean(ObjectName name);

  /* returns ObjectInstance */
  public Callback getObjectInstance(ObjectName name);

  /* returns boolean */
  public Callback isRegistered(ObjectName name);

  /* returns Integer */
  public Callback getMBeanCount();

  /* returns Object */
  public Callback getAttribute(ObjectName name,
                               String attribute);

  /* returns AttributeList */
  public Callback getAttributes(ObjectName name,
                                String[] attributes);

  public void setAttribute(ObjectName name,
                           Attribute attribute);

  /* returns AttributeList */
  public Callback setAttributes(ObjectName name,
                                AttributeList attributes);

  /* returns Object */
  public Callback invoke(ObjectName name,
                         String operationName,
                         Object[] params,
                         String[] signature);

  /* returns String */
  public Callback getDefaultDomain();

  public void addNotificationListener(
                         ObjectName name,
                         NotificationListener listener,
                         NotificationFilter filter,
                         Object handback);

  public void addNotificationListener(
                           ObjectName name,
                           String className,
                           NotificationFilter filter);


  public void addNotificationListener(
                         ObjectName name,
                         ObjectName listener,
                         NotificationFilter filter,
                         Object handback);

  public void removeNotificationListener(
                         ObjectName name,
                         NotificationListener listener);

  public void removeNotificationListener(
                         ObjectName name);

  /* returns MBeanInfo */
  public Callback getMBeanInfo(ObjectName name);

  /* returns boolean */
  public Callback isInstanceOf(ObjectName name,
                               String className);

  /* returns Set */
  public Callback queryMBeans(ObjectName name, QueryExp query);

  /* returns Set */
  public Callback queryNames(ObjectName name, QueryExp query);

  public void close();

}


/*------------------------------------------------------------------------------
Name:      RemoteMBeanServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import javax.management.*;
import java.rmi.*;
import java.util.*;

public interface RemoteMBeanServer {

  public ObjectInstance createMBean(String className,
                                    ObjectName name) throws
                                  ReflectionException,
                                  InstanceAlreadyExistsException,
                                  MBeanRegistrationException,
                                  MBeanException,
                                  NotCompliantMBeanException;

  public ObjectInstance createMBean(String className,
                                    ObjectName name,
                                    ObjectName loaderName) throws ReflectionException,
                                  InstanceAlreadyExistsException,
                                  MBeanRegistrationException,
                                  MBeanException,
                                  NotCompliantMBeanException,
                                  InstanceNotFoundException;

  public ObjectInstance createMBean(String className,
                                    ObjectName name,
                                    Object[] params,
                                    String[] signature) throws ReflectionException,
                                  InstanceAlreadyExistsException,
                                  MBeanRegistrationException,
                                  MBeanException,
                                  NotCompliantMBeanException;

  public ObjectInstance createMBean(String className,
                                    ObjectName name,
                                    ObjectName loaderName,
                                    Object[] params,
                                    String[] signature) throws ReflectionException,
                                  InstanceAlreadyExistsException,
                                  MBeanRegistrationException,
                                  MBeanException,
                                  NotCompliantMBeanException,
                                  InstanceNotFoundException;

  public void unregisterMBean(ObjectName name) throws InstanceNotFoundException,
                            MBeanRegistrationException;

  public ObjectInstance getObjectInstance(ObjectName name) throws InstanceNotFoundException;

  public boolean isRegistered(ObjectName name);

  public Integer getMBeanCount();

  public Object getAttribute(ObjectName name,
                               String attribute) throws MBeanException,
                                     AttributeNotFoundException,
                                     InstanceNotFoundException,
                                     ReflectionException;

  public AttributeList getAttributes(ObjectName name,
                                String[] attributes) throws InstanceNotFoundException,
                                   ReflectionException;

  public void setAttribute(ObjectName name,
                           Attribute attribute) throws InstanceNotFoundException,
                         AttributeNotFoundException,
                         InvalidAttributeValueException,
                         MBeanException,
                         ReflectionException;

  public AttributeList setAttributes(ObjectName name,
                                AttributeList attributes) throws InstanceNotFoundException,
                                   ReflectionException;

  public Object invoke(ObjectName name,
                         String operationName,
                         Object[] params,
                         String[] signature) throws InstanceNotFoundException,
                               MBeanException,
                               ReflectionException;

  public String getDefaultDomain();

  public void addNotificationListener(
                         ObjectName name,
                         NotificationListener listener,
                         NotificationFilter filter,
                         Object handback) throws InstanceNotFoundException;

  public void addNotificationListener(
                         ObjectName name,
                         ObjectName listener,
                         NotificationFilter filter,
                         Object handback) throws InstanceNotFoundException;

  public void removeNotificationListener(
                         ObjectName name,
                         NotificationListener listener) throws InstanceNotFoundException,
                                       ListenerNotFoundException;

  public void removeNotificationListener(
                         ObjectName name,
                         ObjectName listener) throws InstanceNotFoundException,
                                       ListenerNotFoundException;

  public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException,
                              IntrospectionException,
                              ReflectionException;

  public boolean isInstanceOf(ObjectName name,
                               String className) throws InstanceNotFoundException;

  public Set queryMBeans(ObjectName name, QueryExp query);

  public Set queryNames(ObjectName name, QueryExp query);

  public void close();

}



package org.xmlBlaster.util.admin.extern;


public interface XmlBlasterConnectorMBean {
  public void start(String agentId);
  public boolean isConnectorAlive();
  public void stop(String agentId);
  public void startInternal(javax.management.MBeanServer server);
}
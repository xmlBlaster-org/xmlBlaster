package org.xmlBlaster.engine.cluster;

public interface ClusterNodeMBean {
   String getConnectionStateStr();
   boolean isAllowed();
   void setAllowed(boolean allowed);
   boolean isAvailable();
   boolean isLocalNode();
}

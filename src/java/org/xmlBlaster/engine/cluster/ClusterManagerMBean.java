/*------------------------------------------------------------------------------
Name:      ClusterManagerMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.admin.I_AdminPlugin;

/**
 * JMX control for ClusterManager. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public interface ClusterManagerMBean extends I_AdminPlugin {
   int getNumNodes();
   
   /**
    * Access a list of known cluster nodes e.g. "heron,avalon,bilbo,frodo"
    * @return If cluster is switched off just our node
    */
   String getNodeList();

   /**
    * Access a list of known cluster nodes e.g. "heron","avalon","bilbo","frodo"
    * @return If cluster is switched off just our node
    */
   String[] getNodes();
   
   String addClusterNode(String xml);
   
   String toXml();
}

/*------------------------------------------------------------------------------
Name:      ClusterManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main manager class for clustering
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.Global;

/**
 * The manager instance for a cluster node. 
 * Each xmlBlaster server instance has one instance
 * of this class to manage its behaviour in the cluster. 
 * @author ruff@swand.lake.de
 * @since 0.79e
 */
public class ClusterManager {

   public ClusterManager(Global glob) {
   }

   public XmlBlasterConnection getAddConnection(){
      return addConnection;
   }

   public void setAddConnection(XmlBlasterConnection addConnection){
      this.addConnection = addConnection;
   }

   public XmlBlasterConnection getConnection(){
      return connection;
   }

   public void setConnection(XmlBlasterConnection connection){
      this.connection = connection;
   }

   private XmlBlasterConnection addConnection;
   private XmlBlasterConnection connection;
}

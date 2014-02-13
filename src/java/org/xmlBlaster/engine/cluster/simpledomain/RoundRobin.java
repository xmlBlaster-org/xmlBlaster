/*------------------------------------------------------------------------------
Name:      RoundRobin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Simple demo implementation for clustering
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster.simpledomain;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.cluster.I_LoadBalancer;
import org.xmlBlaster.engine.cluster.NodeMasterInfo;
import org.xmlBlaster.engine.cluster.ClusterManager;

import java.util.Set;
import java.util.Iterator;

/**
 * Implements dummy load balancing for xmlBlaster using round robin approach. 
 * @author xmlBlaster@marcelruff.info 
 * @since 0.79e
 */
final public class RoundRobin implements I_LoadBalancer, I_Plugin {

   private String ME = "RoundRobin";
   private ServerScope glob = null;
   private static Logger log = Logger.getLogger(RoundRobin.class.getName());
   private int counter = 0;

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(ServerScope glob, ClusterManager clusterManager) {
      this.glob = glob;

      this.ME = this.ME + "-" + glob.getId();
      log.info("Round robin load balancer is initialized");
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Return plugin type for Plugin loader
    * @return "RoundRobin"
    */
   public String getType() {
      return "RoundRobin";
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return "1.0";
   }

   /**
    * Get a human readable name of this filter implementation
    * @return "RoundRobin"
    */
   public String getName() {
      return "RoundRobin";
   }

   /**
    * We determine which xmlBlaster node to choose with a simple counter. 
    * <p />
    * @param nodeMasterInfoSet A set containing NodeMasterInfo objects, the possible xmlBlaster nodes.
    *                       Is never null, but may have a size of 0.
    * @return The chosen nodeMasterInfo to handle the message or null to handle it locally
    * @see org.xmlBlaster.engine.cluster.I_LoadBalancer#getClusterNode(java.util.Set, String)
    */
   public synchronized NodeMasterInfo getClusterNode(Set nodeMasterInfoSet, String keyOidForLogging) throws XmlBlasterException {

      // TODO: Change return to NodeMasterInfo[] if multiple fail over nodes exist !!!
      
      if (nodeMasterInfoSet.size() == 0) {
         log.warning("Empty nodeMasterInfoSet, using local node");
         return null; // clusterManager.getMyClusterNode(); // handle locally
      }

      if (counter >= nodeMasterInfoSet.size()) // counter is our RoundRobin approach
         counter = 0;

      /*
       The Set is sorted after
       "<available:stratum:nodeId>"
       available := 0 connected, 1 polling, 2 unavailable
       stratum   := 0 master, 1 stratum, 2 stratum ...

       So we may choose a slave routing to a master,
       the chosen stratum must be smaller (closer to the master) than our current stratum
       
       There must be the possibility to use a slave instead of the master
       directly (choosing the stratum which is exactly one smaller?)
      */

      // Step 1: Find out my stratum for this message (if i have one)
      // Check all rules to find my lowest stratum
      int myStratum = Integer.MAX_VALUE;
      Iterator it = nodeMasterInfoSet.iterator();
      while (it.hasNext()) {
         NodeMasterInfo nodeMasterInfo = (NodeMasterInfo)it.next();
         if (nodeMasterInfo.getClusterNode().isLocalNode()) {
            if (nodeMasterInfo.getStratum() < myStratum) {
               myStratum = nodeMasterInfo.getStratum();
               break;
            }
         }
      }

      // Step 2: Take the node with the lowest stratum or myself
      // Aaheem, this is no round robin ... :-)
      // We know that the Set is sorted after available:stratum:nodeId
      it = nodeMasterInfoSet.iterator();
      while (it.hasNext()) {
         NodeMasterInfo nodeMasterInfo = (NodeMasterInfo)it.next();
         if (myStratum <= nodeMasterInfo.getStratum()) {
            // handle locally, no need to send to a worse or equal stratum
            if (nodeMasterInfo.getStratum() > 0) {
               log.warning("Selected myself as master node from a choice of " + nodeMasterInfoSet.size()
                    + " nodes, but we are only stratum=" + nodeMasterInfo.getStratum() + ". The message is not routed further! " + keyOidForLogging + " -> " + nodeMasterInfo.getKeyMappingFirstLog());
            }
            else {
               if (log.isLoggable(Level.FINE)) log.fine("Selected myself as master node from a choice of " + nodeMasterInfoSet.size() + " nodes: " + keyOidForLogging + " -> " + nodeMasterInfo.getKeyMappingFirstLog());
            }
            return null; // handle locally: clusterManager.getMyClusterNode();
         }

         if (log.isLoggable(Level.FINE))
            log.fine("Selected master node id='" + 
                     nodeMasterInfo.getClusterNode().getId() + "' from a choice of " + 
                     nodeMasterInfoSet.size() + " nodes.  alive = " + 
                     nodeMasterInfo.getClusterNode().isAlive() + ", polling = " +
                     nodeMasterInfo.getClusterNode().isPolling());

         return nodeMasterInfo;
      }

      /*
      // Step 2: Filter the possible nodes (round robin)
      it = nodeMasterInfoSet.iterator();
      int ii=0;
      while (it.hasNext()) {
         Object obj = it.next();
         if (ii == counter) {
            NodeMasterInfo nodeMasterInfo = (NodeMasterInfo)obj;
            ClusterNode clusterNode = nodeMasterInfo.getClusterNode();
            if (log.isLoggable(Level.FINE)) log.trace(ME, "Selected master node id='" + clusterNode.getId() + "' from a choice of " + nodeMasterInfoSet.size() + " nodes");
            counter++;
            return clusterNode;
         }
         ii++;
      }
      */

      log.warning("Can't find master, using local node");
      return null; // handle locally: clusterManager.getMyClusterNode();
   }

   public void shutdown() throws XmlBlasterException {
   }
}

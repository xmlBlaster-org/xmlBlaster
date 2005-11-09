/*------------------------------------------------------------------------------
Name:      RoundRobin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Simple demo implementation for clustering
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster.simpledomain;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.cluster.I_LoadBalancer;
import org.xmlBlaster.engine.cluster.NodeDomainInfo;
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
   private Global glob = null;
   private LogChannel log = null;
   private int counter = 0;

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob, ClusterManager clusterManager) {
      this.glob = glob;
      this.log = this.glob.getLog("cluster");
      this.ME = this.ME + "-" + glob.getId();
      log.info(ME, "Round robin load balancer is initialized");
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
    * @param nodeDomainInfoSet A set containing NodeDomainInfo objects, the possible xmlBlaster nodes.
    *                       Is never null, but may have a size of 0.
    * @return The chosen nodeDomainInfo to handle the message or null to handle it locally
    * @see org.xmlBlaster.engine.cluster.I_LoadBalancer#getClusterNode(java.util.Set)
    */
   public synchronized NodeDomainInfo getClusterNode(Set nodeDomainInfoSet) throws XmlBlasterException {

      // TODO: Change return to NodeDomainInfo[] if multiple fail over nodes exist !!!
      
      if (nodeDomainInfoSet.size() == 0) {
         log.warn(ME, "Empty nodeDomainInfoSet, using local node");
         return null; // clusterManager.getMyClusterNode(); // handle locally
      }

      if (counter >= nodeDomainInfoSet.size()) // counter is our RoundRobin approach
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
      Iterator it = nodeDomainInfoSet.iterator();
      while (it.hasNext()) {
         NodeDomainInfo nodeDomainInfo = (NodeDomainInfo)it.next();
         if (nodeDomainInfo.getClusterNode().isLocalNode()) {
            if (nodeDomainInfo.getStratum() < myStratum) {
               myStratum = nodeDomainInfo.getStratum();
               break;
            }
         }
      }

      // Step 2: Take the node with the lowest stratum or myself
      // Aaheem, this is no round robin ... :-)
      // We know that the Set is sorted after available:stratum:nodeId
      it = nodeDomainInfoSet.iterator();
      while (it.hasNext()) {
         NodeDomainInfo nodeDomainInfo = (NodeDomainInfo)it.next();
         if (myStratum <= nodeDomainInfo.getStratum()) {
            // handle locally, no need to send to a worse or equal stratum
            if (nodeDomainInfo.getStratum() > 0) {
               log.warn(ME, "Selected myself as master node from a choice of " + nodeDomainInfoSet.size()
                    + " nodes, but we are only stratum=" + nodeDomainInfo.getStratum() + ". The message is not routed further!");
            }
            else {
               if (log.TRACE) log.trace(ME, "Selected myself as master node from a choice of " + nodeDomainInfoSet.size() + " nodes");
            }
            return null; // handle locally: clusterManager.getMyClusterNode();
         }

         if (log.TRACE)
            log.trace(ME, "Selected master node id='" + 
                     nodeDomainInfo.getClusterNode().getId() + "' from a choice of " + 
                     nodeDomainInfoSet.size() + " nodes.  alive = " + 
                     nodeDomainInfo.getClusterNode().isAlive() + ", polling = " +
                     nodeDomainInfo.getClusterNode().isPolling());

         return nodeDomainInfo;
      }

      /*
      // Step 2: Filter the possible nodes (round robin)
      it = nodeDomainInfoSet.iterator();
      int ii=0;
      while (it.hasNext()) {
         Object obj = it.next();
         if (ii == counter) {
            NodeDomainInfo nodeDomainInfo = (NodeDomainInfo)obj;
            ClusterNode clusterNode = nodeDomainInfo.getClusterNode();
            if (log.TRACE) log.trace(ME, "Selected master node id='" + clusterNode.getId() + "' from a choice of " + nodeDomainInfoSet.size() + " nodes");
            counter++;
            return clusterNode;
         }
         ii++;
      }
      */

      log.warn(ME, "Can't find master, using local node");
      return null; // handle locally: clusterManager.getMyClusterNode();
   }

   public void shutdown() throws XmlBlasterException {
   }
}

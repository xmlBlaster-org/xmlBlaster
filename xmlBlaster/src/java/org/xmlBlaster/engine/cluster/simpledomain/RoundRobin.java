/*------------------------------------------------------------------------------
Name:      RoundRobin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Simple demo implementation for clustering
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster.simpledomain;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.cluster.I_LoadBalancer;
import org.xmlBlaster.engine.cluster.NodeInfo;
import org.xmlBlaster.engine.cluster.ClusterNode;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;

import java.util.Set;
import java.util.Iterator;

/**
 * Implements dummy load balancing for xmlBlaster using round robin approach. 
 * @author ruff@swand.lake.de 
 * @since 0.79e
 */
final public class RoundRobin implements I_LoadBalancer, I_Plugin {

   private final String ME = "RoundRobin";
   private Global glob;
   private Log log;
   private int counter = 0;

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob) {
      this.glob = glob;
      this.log = glob.getLog();
      log.info(ME, "Round robin load balancer is initialized");
   }

   /**
    * This method is called by the PluginManager.
    * <p />
    * This xmlBlaster.properties entry example
    * <pre>
    *   LoadBalancerPlugin[RoundRobin][1.0]=org.xmlBlaster.engine.cluster.simpledomain.RoundRobin,DEFAULT_MAX_LEN=200
    * </pre>
    * passes 
    * <pre>
    *   options[0]="DEFAULT_MAX_LEN"
    *   options[1]="200"
    * </pre>
    * <p/>
    * @param Global   An xmlBlaster instance global object holding logging and property informations
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(org.xmlBlaster.util.Global glob, String[] options) throws XmlBlasterException {
      if (options != null) {
         for (int ii=0; ii<options.length-1; ii++) {
            if (options[ii].equalsIgnoreCase("DUMMY")) {
               //DUMMY = (new Long(options[++ii])).longValue();  // ...
            }
         }
      }
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
    * @param clusterNodeSet A set containing ClusterNode objects, the possible xmlBlaster nodes.
    *                       Is never null, but may have a size of 0.
    * @return The chosen clusterNode to handle the message or null
    */
   public synchronized ClusterNode getClusterNode(Set clusterNodeSet) throws XmlBlasterException {
      if (clusterNodeSet.size() == 0) {
         log.warn(ME, "Empty clusterNodeSet, using local node");
         return glob.getClusterManager().getMyClusterNode(); // handle locally
      }

      if (counter >= clusterNodeSet.size()) // counter is our RoundRobin approach
         counter = 0;

      /* !!!
       TODO: We should sort the set/map after
       "<available:stratum:nodeId>"
       available := 0 OK, 1 polling, 2 unavailable
       stratum   := 0 master, 1 stratum, 2 stratum ...

       So we may choose a slave routing to a master,
       the chosen stratum must smaller (closer to the master) than our current stratum
       
       There must be the possibility to use a slave instead of the master
       directly (choosing the stratum which is exactly one smaller?)
      */

      Iterator it = clusterNodeSet.iterator();
      int ii=0;
      while (it.hasNext()) {
         Object obj = it.next();
         if (ii == counter) {
            ClusterNode clusterNode = (ClusterNode)obj;
            log.info(ME, "Selected master node id='" + clusterNode.getId() + "' from a choice of " + clusterNodeSet.size() + " nodes");
            counter++;
            return clusterNode;
         }
         ii++;
      }

      log.warn(ME, "Can't find master, using local node");
      return glob.getClusterManager().getMyClusterNode(); // handle locally
   }
}

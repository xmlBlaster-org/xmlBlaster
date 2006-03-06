/*------------------------------------------------------------------------------
Name:      LoadBalancerPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for load balancing
Version:   $Id$
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.ServerScope;

/**
 * Loads the plugin to support cluster load balancing. 
 * Please register your plugins in xmlBlaster.properties, for example:
 * <pre>
 * LoadBalancerPlugin[MyLoadBalancer][1.0]=com.mycompany.MyLoadBalancer
 * </pre>
 * Only one entry is allowed.
 */
public class LoadBalancerPluginManager extends PluginManagerBase {
   /**
    * The default plugin "org.xmlBlaster.engine.cluster.simpledomain.RoundRobin"
    * is loaded if not otherwise specified
    */
   private static final String defaultPluginName = "org.xmlBlaster.engine.cluster.simpledomain.RoundRobin";
   public static final String pluginPropertyName = "LoadBalancerPlugin";

   private final ServerScope glob;
   private final ClusterManager clusterManager;

   public LoadBalancerPluginManager(ServerScope glob, ClusterManager clusterManager) {
      super(glob);
      this.glob = glob;
      this.clusterManager = clusterManager;
   }

   /**
    * Return a specific plugin. 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return The load balancer for this type and version or null if none is specified
    */
   public I_LoadBalancer getPlugin(String type, String version) throws XmlBlasterException {
      return (I_LoadBalancer)getPluginObject(type, version);
   }

   public void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) {
      ((I_LoadBalancer)plugin).initialize(glob, clusterManager);
   }

   /**
    * @return The name of the property in xmlBlaster.property "LoadBalancerPlugin"
    * for "LoadBalancerPlugin[RoundRobin][1.0]"
    */
   protected String getPluginPropertyName() {
      return pluginPropertyName;
   }

   /**
    * @return please return your default plugin classname or null if not specified
    */
   public String getDefaultPluginName(String type, String version) {
      return defaultPluginName;
   }
}

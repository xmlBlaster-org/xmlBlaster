/*------------------------------------------------------------------------------
Name:      LoadBalancerPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for load balancing
Version:   $Id: LoadBalancerPluginManager.java,v 1.11 2002/08/23 21:29:45 ruff Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.PluginManagerBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;

/**
 * Loads the plugin to support cluster load balancing. 
 * Please register your plugins in xmlBlaster.properties, for example:
 * <pre>
 * LoadBalancerPlugin[MyLoadBalancer][1.0]=com.mycompany.MyLoadBalancer
 * </pre>
 * Only one entry is allowed.
 */
public class LoadBalancerPluginManager extends PluginManagerBase {

   private static final String ME = "LoadBalancerPluginManager";
   /**
    * The default plugin "org.xmlBlaster.engine.cluster.simpledomain.RoundRobin"
    * is loaded if not otherwise specified
    */
   private static final String defaultPluginName = "org.xmlBlaster.engine.cluster.simpledomain.RoundRobin";
   public static final String pluginPropertyName = "LoadBalancerPlugin";

   private final Global glob;
   private final LogChannel log;
   private final ClusterManager clusterManager;

   public LoadBalancerPluginManager(Global glob, ClusterManager clusterManager) {
      super(glob);
      this.glob = glob;
      this.log = this.glob.getLog("cluster");
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
      if (log.CALL) log.call(ME+".getPlugin()", "Loading " + getPluginPropertyName(type, version));
      I_LoadBalancer filterPlugin = null;
      String[] pluginNameAndParam = null;

      pluginNameAndParam = choosePlugin(type, version);

      if(pluginNameAndParam!=null && pluginNameAndParam[0]!=null && pluginNameAndParam[0].length()>1) {
         filterPlugin = (I_LoadBalancer)managers.get(pluginNameAndParam[0]);
         if (filterPlugin!=null) return filterPlugin;
         filterPlugin = loadPlugin(pluginNameAndParam);
      }
      else {
         //throw new XmlBlasterException(ME+".notSupported","The requested security manager isn't supported!");
      }

      return filterPlugin;
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


   /**
    * Loads the plugin. 
    * <p/>
    * @param String[] The first element of this array contains the class name
    *                 e.g. org.xmlBlaster.engine.cluster.simpledomain.RoundRobin<br />
    *                 Following elements are arguments for the plugin. (Like in c/c++ the command-line arguments.)
    * @return I_LoadBalancer
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_LoadBalancer loadPlugin(String[] pluginNameAndParam) throws XmlBlasterException
   {
      I_LoadBalancer i = (I_LoadBalancer)super.instantiatePlugin(pluginNameAndParam);
      i.initialize(glob, clusterManager);
      return i;
   }
}

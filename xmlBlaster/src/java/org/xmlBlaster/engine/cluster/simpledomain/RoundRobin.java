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
import org.xmlBlaster.client.protocol.XmlBlasterConnection;


/**
 * Implements dummy load balancing for xmlBlaster using round robin approach. 
 * @author ruff@swand.lake.de 
 * @since 0.79e
 */
final public class RoundRobin implements I_LoadBalancer, I_Plugin {

   private final String ME = "RoundRobin";
   private Global glob;
   private Log log;

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

   public XmlBlasterConnection getConnection(NodeInfo[] nodeInfoArr) throws XmlBlasterException {
      Log.error(ME, "getConnection not implemented");
      return null;
   }
}

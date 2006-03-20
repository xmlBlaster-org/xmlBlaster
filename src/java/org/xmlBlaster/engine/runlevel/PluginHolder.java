/*------------------------------------------------------------------------------
Name:      PluginHolder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.TreeSet;

/**
 * This class contains the information on how to configure a certain pluginand when a certain plugin is invoked by the run level manager
 * <p>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.runlevel.html">engine.runlevel requirement</a>
 * <pre>
 *
 *  &lt;plugin id='storage:CACHE' className='org.xmlBlaster.engine.msgstore.cache.PersistenceCachePlugin'>
 *     &lt;attribute id='transientQueue'>storage:RAM&lt;/attribute>
 *     &lt;attribute id='persistentQueue'>storage:JDBC&lt;/attribute>
 *  &lt;/plugin>
 *
 * </pre>
 */
public class PluginHolder {
   private String ME = "PluginHolder";
   private final Global glob;
   private static Logger log = Logger.getLogger(PluginHolder.class.getName());

   private Hashtable pluginConfigsDefault;

   /** 
    * This is a double Hashtable: an Hashtable containing one hashtable for every node found. Every one
    * of these node specific Hashtables contains all PluginConfig objects defined in the corresponding node
    * section.
    */
   private Hashtable pluginConfigsNodes;

   /**
    * This constructor takes all parameters needed
    */
   public PluginHolder(Global glob, Hashtable pluginConfigsDefault, Hashtable pluginConfigsNodes) {
      this.glob = glob;

      if (log.isLoggable(Level.FINER)) this.log.finer("constructor");
      if (pluginConfigsDefault != null )
         this.pluginConfigsDefault = pluginConfigsDefault;
      else this.pluginConfigsDefault = new Hashtable();

      if (pluginConfigsNodes != null) this.pluginConfigsNodes = pluginConfigsNodes;
      else this.pluginConfigsNodes = new Hashtable();

   }

   /**
    * Minimal constructor
    */
   public PluginHolder(Global glob) {
      this(glob, (Hashtable)null, (Hashtable)null);
   }

   public void addDefaultPluginConfig(PluginConfig pluginConfig) {
      this.pluginConfigsDefault.put(pluginConfig.getId(), pluginConfig);
   }

   /**
    *  Adds a pluginConfig object to the specified node.
    * @param node the node to which to add the pluginConfig object
    * @param pluginConfig the object to add to the holder.
    */
   public void addPluginConfig(String node, PluginConfig pluginConfig) {
      // check first if the node already exists ...
      if (log.isLoggable(Level.FINER)) this.log.finer("addPluginConfig for node '" + node + "'");
      Hashtable tmp = (Hashtable)this.pluginConfigsNodes.get(node);
      if (tmp == null) { // then it does not exist (add one empty table)
         tmp = new Hashtable();
         this.pluginConfigsNodes.put(node, tmp);
      }
      tmp.put(pluginConfig.getId(), pluginConfig);

   }

   /**
    * returns the plugin specified with the given id and the given node. If a
    * plugin configuration is not found in the specified node, then it is
    * searched in the defaults. If none is found there either, then a null is
    * returned.
    * @param node the nodeId scope on which to do the request
    * @param id the unique string identifying the plugin
    */
   public PluginConfig getPluginConfig(String node, String id) {
      if (log.isLoggable(Level.FINER)) this.log.finer("getPluginConfig for node '" + node + "'");
      Hashtable nodeTable = (Hashtable)this.pluginConfigsNodes.get(node);
      if (nodeTable != null) {
         Object tmp = nodeTable.get(id);
         if (tmp != null) return (PluginConfig)tmp;
      }
      return (PluginConfig)this.pluginConfigsDefault.get(id);
   }


   /**
    * returns all PluginConfig found for the specified node (and the default)
    * @param node the node for which to search.
    */
   public PluginConfig[] getAllPluginConfig(String node) {
      if (log.isLoggable(Level.FINER)) this.log.finer("getAllPluginConfig for node '" + node + "'");
      Hashtable tmp = (Hashtable)this.pluginConfigsDefault.clone();
      if (this.pluginConfigsNodes!=null && node!=null) {
         Hashtable nodeTable = (Hashtable)this.pluginConfigsNodes.get(node);
         if (nodeTable != null) {
            Enumeration enumer = nodeTable.keys();
            while (enumer.hasMoreElements()) {
               String key = (String)enumer.nextElement();
               tmp.put(key, nodeTable.get(key));
            }
         }
      }
      else {
         log.warning("No cluster node id given, checking only default plugins");
      }
      // prepare the return array ...
      int size = tmp.size();
      PluginConfig[] ret = new PluginConfig[size];
      int i = 0;
      Enumeration enumer = tmp.keys();
      while (enumer.hasMoreElements()) {
         String key = (String)enumer.nextElement();
         ret[i] = (PluginConfig)tmp.get(key);
         i++;
      }
      return ret;
   }


   /**
    * returns an xml litteral string representing all entries found in the configuration file.
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(512);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<xmlBlaster>");
      // append all defaults ...
      Enumeration enumer = this.pluginConfigsDefault.keys();
      while (enumer.hasMoreElements()) {
         String key = (String)enumer.nextElement();
         PluginConfig pluginConfig = (PluginConfig)this.pluginConfigsDefault.get(key);
         sb.append(pluginConfig.toXml(extraOffset + "   "));
      }

      enumer = this.pluginConfigsNodes.keys();
      while (enumer.hasMoreElements()) {
         String nodeId = (String)enumer.nextElement();
         Hashtable nodeTable = (Hashtable)this.pluginConfigsNodes.get(nodeId);
         sb.append(offset).append("   ").append("<node id='").append(nodeId).append("'>");
         Enumeration enumNodes = nodeTable.keys();
         while (enumNodes.hasMoreElements()) {
            String key = (String)enumNodes.nextElement();
           PluginConfig pluginConfig = (PluginConfig)nodeTable.get(key);
            sb.append(pluginConfig.toXml(extraOffset + "      "));
         }
         sb.append(offset).append("   ").append("</node>");
      }

      sb.append(offset).append("</xmlBlaster>");
      return sb.toString();
   }

   public String toXml() {
      return toXml("");
   }


   /** 
    * Returns a hashset containing all plugins which have a startup level defined.
    * The returns are already in the right sequence.
    * @param nodeId the id of the node to retrieve
    * @param lowRunlevel the runlevel from which to start retreive (inclusive)
    * @param highRunlevel the runlevel to which to retrieve (inclusive)
    */
   public TreeSet getStartupSequence(String nodeId, int lowRunlevel, int highRunlevel) {
      if (log.isLoggable(Level.FINER)) this.log.finer("getStartupSequence for node '" + nodeId + 
                         "' and runlevel '" + lowRunlevel + "' to '" + highRunlevel + "'");
      if (lowRunlevel > highRunlevel) {
         log.severe(".getStartupSequence: the low run level '" + lowRunlevel + "' is higher than the high run level '" + highRunlevel + "'");
      }
      TreeSet startupSet = new TreeSet(new PluginConfigComparator(this.glob, true));
      PluginConfig[] plugins = getAllPluginConfig(nodeId);
      for (int i=0; i < plugins.length; i++) {
         RunLevelAction action = plugins[i].getUpAction();
         if (action != null) {
            int runlevel = action.getOnStartupRunlevel();
            if (runlevel >= lowRunlevel && runlevel <= highRunlevel)
               startupSet.add(plugins[i]);
         }
      }
      return startupSet;
   }
   
   /** 
    * Returns a hashset containing all plugins which have a shutdown level defined.
    * The returns are already in the right sequence.
    * @param nodeId the id of the node to retrieve
    * @param lowRunlevel the runlevel from which to start retreive (inclusive)
    * @param highRunlevel the runlevel to which to retrieve (inclusive)
    */
   public TreeSet getShutdownSequence(String nodeId, int lowRunlevel, int highRunlevel) {
      if (log.isLoggable(Level.FINER)) this.log.finer("getShutdownSequence for node '" + nodeId + 
                        "' and runlevel '" + lowRunlevel + "' to '" + highRunlevel + "'");
      if (lowRunlevel > highRunlevel) {
         log.severe(".getShutdownSequence: the low run level '" + lowRunlevel + "' is higher than the high run level '" + highRunlevel + "'");
      }
      TreeSet shutdownSet = new TreeSet(new PluginConfigComparator(this.glob, false));
      PluginConfig[] plugins = getAllPluginConfig(nodeId);
      for (int i=0; i < plugins.length; i++) {
         RunLevelAction action = plugins[i].getDownAction();
         if (action != null) {
            int runlevel = action.getOnShutdownRunlevel();
            if (runlevel >= lowRunlevel && runlevel <= highRunlevel)
               shutdownSet.add(plugins[i]);
         }
      }
      return shutdownSet;
   }

}

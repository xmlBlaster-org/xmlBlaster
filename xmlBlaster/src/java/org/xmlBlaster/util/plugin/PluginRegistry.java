/*------------------------------------------------------------------------------
Name:      PluginRegistry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Baseclass to load plugins.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.plugin;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;

import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Enumeration;

/**
 * One instance of this class is used to keep track of all cached plugins.
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class PluginRegistry {
   private static String ME = "PluginRegistry";
   private final Global glob;
   private final LogChannel log;
   /** key=pluginId String, value=I_Plugin */
   private Hashtable plugins;


   public PluginRegistry(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("core");
      this.plugins = new Hashtable();
   }


   /**
    * Gets the plugin which has previously been registered with the given id.
    * @param id the key under which the plugin to get has been registered.
    * @return I_Plugin the plugin associated to the given key. If there is no such
    *         plugin, then null is returned.
    */
   public I_Plugin getPlugin(String id)  {
      if (id == null) return null;
      synchronized(this) {
         return (I_Plugin)this.plugins.get(id);
      }
   }

   /**
    * Registers the plugin into this registry.
    * @param id the key to use to register this plugin
    * @param plugin the plugin to register
    * @return boolean 'true' if the registration was successful. 'false' if the 
    *         plugin was already registered.
    */
   public boolean register(String id, I_Plugin plugin) {
      if (id == null) return false;
      synchronized(this) {
         if (this.plugins.containsKey(id)) return false;
         this.plugins.put(id, plugin);
         return false;
      }
   }

   /**
    * unregisters the specified plugin. 
    * @param id the id under which the plugin has been registered.
    * @return I_Plugin the plugin which has been unregistered or null if none was
    *         found.
    */
   public I_Plugin unRegister(String id) {
      if (id == null) return null;
      synchronized(this) {
         return (I_Plugin)this.plugins.remove(id);
      } 
   }

   /*
    * Returns the plugins which are registered in the cache with a name prefix
    * as the one specified in 'type'.
    * Example:
    * plugin is registered as:
    * protocol:IOR
    * type is 'protocol', then the plugin is found.
   public Vector getPluginsOfGroup(String type) {
      if (this.log.CALL) this.log.call(ME, "getPluginsOfGroup '" + type + "'");
      Vector ret = new Vector();
      synchronized(this) {
         Enumeration enum = this.plugins.keys();
         while (enum.hasMoreElements()) {
            String key = (String)enum.nextElement();
            StringTokenizer tokenizer = new StringTokenizer(key, ":");
            if (tokenizer.hasMoreTokens()) {
               String token = tokenizer.nextToken();
               if (this.log.TRACE) this.log.trace(ME, "getPluginsOfGroup: token '" + token + "'");
               if (type.equalsIgnoreCase(token))
                  ret.add(this.plugins.get(key));
            }
         }
      }
      return ret;
   }
   */

   /**
    * Returns the plugins which are implementing the interface I_Driver. 
    * @return Vector with matching I_Driver entries
    */
   public Vector getPluginsOfInterfaceI_Driver() {
      if (this.log.CALL) this.log.call(ME, "getPluginsOfInterfaceI_Driver()");
      Vector ret = new Vector();
      synchronized(this) {
         Enumeration enum = this.plugins.elements();
         while (enum.hasMoreElements()) {
            Object next = enum.nextElement();
            //log.error(ME, "Compare I_Driver: " + next.getClass().getName());
            if (next instanceof org.xmlBlaster.protocol.I_Driver) {
               if (log.TRACE) log.trace(ME, "Added I_Driver implementation " + next.getClass().getName());
               ret.add(next);
            }
         }
      }
      return ret;
   }

   /**
    * Returns the plugins which are implementing the interface I_Queue. 
    * @return Vector with matching I_Queue entries
    */
   public Vector getPluginsOfInterfaceI_Queue() {
      if (this.log.CALL) this.log.call(ME, "getPluginsOfInterfaceI_Queue()");
      Vector ret = new Vector();
      synchronized(this) {
         Enumeration enum = this.plugins.elements();
         while (enum.hasMoreElements()) {
            Object next = enum.nextElement();
            //log.error(ME, "Compare I_Queue: " + next.getClass().getName());
            if (next instanceof org.xmlBlaster.util.queue.I_Queue) {
               if (log.TRACE) log.trace(ME, "Added I_Queue implementation " + next.getClass().getName());
               ret.add(next);
            }
         }
      }
      return ret;
   }
}

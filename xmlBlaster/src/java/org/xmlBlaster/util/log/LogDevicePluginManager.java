/*------------------------------------------------------------------------------
Name:      LogDeviceManagerBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   PluginManager to load logging plugins.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.log;

import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.I_PluginManager;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;

import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import java.util.StringTokenizer;
import java.net.URL;
/**
 * A plugin manager for pluggable log device factories.
 *
 * <p>It's possible to add pluggable log devices to xmlBlaster.properties. 
 * A pluggable log device is identified by the plugin name <b>LoggableDevicePlugin</b>. By implementing the {@link I_LogDeviceFactory} a logger may be dynamically added to the available loggers in XmlBlaster. For example to ad a Console logger plugin the following would typically be specifyed as a property:</p>
 <pre>
  LoggableDevicePlugin[console][1.0]=org.xmlBlaster.util.log.ConsoleLogDeviceFactory
 </pre>
 *
 * <p>The loading logic of the logging plugins are defined in {@link org.xmlBlaster.util.Global}, but is described here. To actually use a logger plugin one has to specify the logDevices to actually use. These are specifyed by giving a comma separated list of plugin names in the propery <b>logDevice</b>. A logDevice may specifyed globaly, or for a particular logging domain, given by LogChannel.getChannelKey().So for example, given the following plugins:</p>
<pre>
LoggableDevicePlugin[console][1.0]=org.xmlBlaster.util.log.ConsoleLogDeviceFactory
LoggableDevicePlugin[file][1.0]=org.xmlBlaster.util.log.FileLogDeviceFactory,logFile=mylogfile
</pre>
<p>One could specify that for all logger console should be used:</p>
<pre>
logDevice=console
</pre>
<p>But also that for a particular LogChannel another configuration should be used. In this case all logging done against the cb LogChannel would go both to the console and the file loggers. If console had not been specifyed, only file would be used for cb.</p>
<pre>
logDevice[cb]=console,file
</pre>
 *
 *
 * @author Peter Antman
 * @version $Revision: 1.3 $ $Date: 2002/12/18 15:19:51 $
 */

public class LogDevicePluginManager implements I_PluginManager {
   private static final String ME = "LogDevicePluginManager";
   public static final String PLUGIN_NAME = "LoggableDevicePlugin";
   
   protected Global glob = null;
   protected Hashtable managers = new Hashtable(); // currently loaded plugins
   
   public LogDevicePluginManager(Global glob) {
      this.glob = glob;
   }

   /**
    * @return a Console logger factory.
    */
   public String getDefaultPluginName(String type, String version) {
      return "LoggableDevicePlugin[console][1.0]=org.xmlBlaster.util.log.ConsoleLogDeviceFactory";//Not specifyed yet
   }
   public String getName() {
      return  PLUGIN_NAME;
   }
   
   /**
    * The central method witch returns a logger plugin of type type.
    */
   public I_LogDeviceFactory getFactory(String type, String version) throws XmlBlasterException{
      return (I_LogDeviceFactory) getPluginObject(type, version);
   }

   // Have to reproduce the code from PluginManagerBase to get around
   // circularity errors that happens when its tries to create a logger!
   public I_Plugin getPluginObject(String type, String version) throws XmlBlasterException {
      I_LogDeviceFactory plug = null;

      PluginInfo pluginInfo = new PluginInfo(glob,this,type, version);

      // check in hash if plugin is instanciated already
      plug = (I_LogDeviceFactory)managers.get(pluginInfo.getClassName());
      if (plug!=null) return plug;

      // not in hash, instanciat plugin
      plug = instantiatePlugin(pluginInfo);

      return plug;
   }
   public final String createPluginPropertyKey(String type, String version) {
      StringBuffer buf = new StringBuffer(80);
      buf.append(getName());
      if (type != null)
         buf.append("[").append(type).append("]");
      if (version != null)
         buf.append("[").append(version).append("]");
      return buf.toString();
   }
   protected I_LogDeviceFactory instantiatePlugin(PluginInfo pluginInfo) throws XmlBlasterException
   {
      I_LogDeviceFactory plugin = null;
      String pluginName = pluginInfo.getClassName();
      try {
      // We do it the only right way ;-)
         Class clazz = Thread.currentThread().getContextClassLoader().loadClass(pluginName);
         plugin = (I_LogDeviceFactory)clazz.newInstance();
      }catch(ClassNotFoundException e) {
         throw new XmlBlasterException(ME,"Could not find class: "+pluginName+" " + e);
      }
      catch(InstantiationException e) {
         throw new XmlBlasterException(ME,"Could not instantiate class: "+pluginName+" " + e);
      }catch(IllegalAccessException e) {
         throw new XmlBlasterException(ME,"Could not access class: "+pluginName+" " + e);
      }
      
      
      // Initialize the plugin
      if (plugin != null) {
         try {
            plugin.init(glob, pluginInfo);
         } catch (XmlBlasterException e) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "Initializing of plugin " + plugin.getType() + " failed", e);
         }
      }
      managers.put(pluginName, plugin);
      
      return plugin;
   }     

 
}

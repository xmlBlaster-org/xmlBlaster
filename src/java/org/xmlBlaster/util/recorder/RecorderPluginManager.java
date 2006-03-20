/*------------------------------------------------------------------------------
Name:      RecorderPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for queuing of tail back messages
Version:   $Id$
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.recorder.I_InvocationRecorder;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.client.protocol.I_XmlBlaster;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Please register your plugins in xmlBlaster.properties. 
 * Example
 * <pre>
 *   RecorderPlugin[FileRecorder][1.0]=org.xmlBlaster.util.recorder.file.FileRecorder
 * </pre>
 * See <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/util.recorder.html">util.recorder</a> and
 * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.failsafe.html">client.failsafe</a> requirement
 */
public class RecorderPluginManager extends PluginManagerBase {

   private static final String ME = "RecorderPluginManager";
   public static final String pluginPropertyName = "RecorderPlugin";

   private final Global glob;
   private static Logger log = Logger.getLogger(RecorderPluginManager.class.getName());

   public RecorderPluginManager(Global glob) {
      super(glob);
      this.glob = glob;

   }

   /**
    * Return a specific filter plugin from cache (on first request it is created). 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @param fn The file name for persistence or null (will be generated or ignored if RAM based)
    * @return The RecorderFilter for this type and version or null if none is specified
    */
   public I_InvocationRecorder getPlugin(String type, String version, String fn, long maxEntries,
             I_XmlBlaster serverCallback, I_CallbackRaw clientCallback) throws XmlBlasterException {

      if (log.isLoggable(Level.FINER)) log.finer("Loading " + createPluginPropertyKey(type, version));
      I_InvocationRecorder plugin = null;

      PluginInfo pluginInfo = new PluginInfo(glob, this, type, version);

      try {
         plugin = (I_InvocationRecorder)getFromPluginCache(pluginInfo.getId());
         if (plugin!=null) return plugin;

         plugin = loadPlugin(pluginInfo);
         plugin.initialize(glob, fn, maxEntries, serverCallback); //, clientCallback);
      }
      catch(Throwable e) {
         log.severe("Can't load plugin: " + pluginInfo.toString());
         e.printStackTrace();
      }

      return plugin;
   }

   public void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) {
   }

   /**
   * @return The name of the property in xmlBlaster.property "MimeRecorderPlugin"
   * for "MimeRecorderPlugin[demo][1.0]"
   */
   protected String getPluginPropertyName() {
      return pluginPropertyName;
   }

   /**
    * @return please return your default plugin classname or null if not specified
    */
   public String getDefaultPluginName(String type, String version) {
      /*
      if (type != null) {
         if (type.equals("FileRecorder"))
            return "org.xmlBlaster.util.recorder.file.FileRecorder";
         else
            return null;
      }
      */
      return "org.xmlBlaster.util.recorder.file.FileRecorder";
      //return "org.xmlBlaster.util.recorder.ram.RamRecorder";
   }

   /**
    * Loads a new created invocation recorder plugin. 
    * <p/>
    * @param pluginInfo The struct containing the plugin specific data
    * @return I_InvocationRecorder
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_InvocationRecorder loadPlugin(PluginInfo pluginInfo) throws XmlBlasterException {
      return (I_InvocationRecorder)super.instantiatePlugin(pluginInfo, false);
   }
}

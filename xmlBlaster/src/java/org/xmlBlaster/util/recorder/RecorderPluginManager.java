/*------------------------------------------------------------------------------
Name:      RecorderPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for queuing of tail back messages
Version:   $Id: RecorderPluginManager.java,v 1.1 2002/05/27 20:52:24 ruff Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.PluginManagerBase;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.recorder.I_InvocationRecorder;
import org.xmlBlaster.client.I_CallbackRaw;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.engine.helper.Constants;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Please register your plugins in xmlBlaster.properties, for example:
 * <pre>
 *   RecorderPlugin[FileRecorder][1.0]=org.xmlBlaster.util.recorder.file.FileRecorder
 * </pre>
 * See <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/util.recorder.html">util.recorder</a> and
 * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.failsave.html">client.failsave</a> requirement
 */
public class RecorderPluginManager extends PluginManagerBase {

   private static final String ME = "RecorderPluginManager";
   public static final String pluginPropertyName = "RecorderPlugin";

   private final Global glob;
   private final LogChannel log;

   public RecorderPluginManager(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = this.glob.getLog("recorder");
   }

   /**
    * Return a specific MIME based message filter plugin. 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return The RecorderFilter for this type and version or null if none is specified
    */
   public I_InvocationRecorder getPlugin(String type, String version, int maxEntries,
             I_XmlBlaster serverCallback, I_CallbackRaw clientCallback) throws XmlBlasterException {

      if (log.CALL) log.call(ME+".getPlugin()", "Loading " + getPluginPropertyName(type, version));
      I_InvocationRecorder plugin = null;
      String[] pluginNameAndParam = null;

      pluginNameAndParam = choosePlugin(type, version);

      if(pluginNameAndParam!=null && pluginNameAndParam[0]!=null && pluginNameAndParam[0].length()>1) {
         plugin = (I_InvocationRecorder)managers.get(pluginNameAndParam[0]);
         if (plugin!=null) return plugin;
         plugin = loadPlugin(pluginNameAndParam);
         plugin.initialize(glob, maxEntries, serverCallback, clientCallback);
      }
      else {
         //throw new XmlBlasterException(ME+".notSupported","The requested invocation recorder isn't supported!");
      }

      return plugin;
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
      //return "org.xmlBlaster.util.recorder.file.FileRecorder";
      return "org.xmlBlaster.util.recorder.ram.RamRecorder";
   }

   /**
    * Loads a invocation recorder plugin. 
    * <p/>
    * @param String[] The first element of this array contains the class name
    *                 e.g. org.xmlBlaster.util.recorder.RamRecorder<br />
    *                 Following elements are arguments for the plugin. (Like in c/c++ the command-line arguments.)
    * @return I_InvocationRecorder
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_InvocationRecorder loadPlugin(String[] pluginNameAndParam) throws XmlBlasterException {
      return (I_InvocationRecorder)super.instantiatePlugin(pluginNameAndParam);
   }
}

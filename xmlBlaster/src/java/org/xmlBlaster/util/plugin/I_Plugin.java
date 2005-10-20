package org.xmlBlaster.util.plugin;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * This interface must be implemented by xmlBlaster plugins. 
 *
 * @author W. Kleinertz (wkl) H. Goetzger
 * @author <a href="mailto:Konrad.Krafft@doubleslash.de">Konrad Krafft</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public interface I_Plugin
{
   /**
    * This method is called by the PluginManager.
    * <p/>
    * Example how options are evaluated:
    * <pre>
    *   // An entry in xmlBlaster.properties (in one line):
    *   MimeSubscribePlugin[ContentLenFilter][1.0]=\
    *                 org.xmlBlaster.engine.mime.demo.ContentLenFilter,\
    *                 DEFAULT_MAX_LEN=200,DEFAULT_MIN_LEN=20
    *
    *  // Access it like this:
    *  java.util.Properties props = pluginInfo.getParameters();
    *  String maxLen = (String)props.get("DEFAULT_MAX_LEN");
    *  String throwLen = (String)props.get("THROW_EXCEPTION_FOR_LEN");
    * </pre>
    * @param Global   An xmlBlaster instance global object holding logging and property informations
    * @param PluginInfo A container holding informations about the plugin, e.g. its parameters
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException;

   public String getType();
   public String getVersion();

   /**
    * Cleans up the resource.
    * @throws XmlBlasterException if an exception occurs. The exception is
    * handled by the RunLevelManager depending on how the plugin has been
    * configured with the action:
    * <p/>
    * &lt;action do='STOP' onShutdownRunlevel='2' sequence='5'
    *     onFail='resource.configuration.pluginFailed'>
    * 
    * If onFail is defined to something, the RunLevelManager will stop.
    */
   public void shutdown() throws XmlBlasterException;

}

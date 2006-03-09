/*------------------------------------------------------------------------------
 Name:      SocketGetterPlugin.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.socketgetter;

import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;
import java.util.logging.Logger;

/**
 * SocketGetterPlugin is a plugin wrapper if you want to run SocketGetter inside
 * xmlBlaster. <br/> The configuration of this plugin in the
 * <b>xmlBlasterPlugins.xml</b> may look like:
 * 
 * <pre>
 *  &lt;plugin id='SocketGetter' className='org.xmlBlaster.contrib.socketgetter.SocketGetterPlugin'&gt;
 *     &lt;action do='LOAD' onStartupRunlevel='9' sequence='9' onFail='resource.configuration.pluginFailed'/&gt;
 *     &lt;action do='STOP' onShutdownRunlevel='6' sequence='1'/&gt;
 * 
 *     &lt;attribute id='port'&gt;9876&lt;/attribute&gt;
 *  &lt;/plugin&gt;
 * </pre>
 * 
 * @see org.xmlBlaster.contrib.socketgetter.SocketGetter
 * 
 * @author <a href="mailto:goetzger@xmlblaster.org">Heinrich G&ouml;tzger</a>
 */
public class SocketGetterPlugin extends GlobalInfo {

   /** Holds the logger for this class. */
   private static Logger log = Logger.getLogger(SocketGetterPlugin.class
         .getName());

   /** Holds the socket getter instance for this plugin. */
   private Thread socketGetter;

   /**
    * Default constructor, you need to call {@link doInit()} thereafter.
    * 
    * @see #doInit(Global, PluginInfo)
    */
   public SocketGetterPlugin() {
      super((String[]) null);
   }
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,
    *      org.xmlBlaster.util.plugin.PluginInfo)
    */
   protected void doInit(Global global_, PluginInfo pluginInfo)
         throws XmlBlasterException {
      try {
         socketGetter = new SocketGetter(this.global, getInt("port", 0));
         socketGetter.start();
      } catch (Throwable e) {
         throw new XmlBlasterException(this.global,
               ErrorCode.RESOURCE_CONFIGURATION, "SocketGetterPlugin",
               "init failed", e);
      }
      log.info("Loaded SocketGetter plugin '" + getType() + "'");
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      super.shutdown();
      try {
         ((SocketGetter)socketGetter).shutdown();
      } catch (Throwable e) {
         log.throwing(this.getClass().getName(), "shutdown", e);
      }
      log.info("Stopped SocketGetter plugin '" + getType() + "'");
   }
}

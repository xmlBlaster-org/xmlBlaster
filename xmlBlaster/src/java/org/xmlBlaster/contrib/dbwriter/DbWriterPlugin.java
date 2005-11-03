/*------------------------------------------------------------------------------
Name:      DbWriterPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwriter;

import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;
import java.util.logging.Logger;

/**
 * DbWriterPlugin is a plugin wrapper if you want to run DbWriter inside xmlBlaster. 
 * <p>
 * This plugin uses <tt>java.util.logging</tt> and redirects the logging to xmlBlasters default
 * logging framework. You can switch this off by setting the attribute <tt>xmlBlaster/jdk14loggingCapture</tt> to false.
 * </p>
 * 
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 */
public class DbWriterPlugin extends GlobalInfo {
   private static Logger log = Logger.getLogger(DbWriterPlugin.class.getName());
   private DbWriter dbWriter;

   /**
    * Default constructor, you need to call <tt>init()<tt> thereafter.
    */
   public DbWriterPlugin() {
      super(new String[] {"dbPool.class", "mom.class", "parser.class", "dbWriter.writer.class"});
   }
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   protected void doInit(Global global_, PluginInfo pluginInfo) throws XmlBlasterException {
      try {
         this.dbWriter = new DbWriter(this);
      }
      catch (Throwable e) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_CONFIGURATION, "DbWriterPlugin", "init failed", e); 
      }
      log.info("Loaded DbWatcher plugin '" + getType() + "'");
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      super.shutdown();
      try {
         this.dbWriter.shutdown();
      }
      catch (Throwable e) {
         log.warning("Ignoring shutdown problem: " + e.toString());
      }
      log.info("Stopped DbWatcher plugin '" + getType() + "'");
   }
}

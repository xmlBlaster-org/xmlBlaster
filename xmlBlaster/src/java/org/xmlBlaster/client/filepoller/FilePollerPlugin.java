/*------------------------------------------------------------------------------
Name:      FilePollerPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.filepoller;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;


/**
 * FilePollerPlugin polls on a directory in the file system for new files. If one new file
 * is found which meets the required specifications, its content is read and published.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class FilePollerPlugin implements I_Plugin {
   private final static String ME = "FilePollerPlugin";
   private PluginInfo info;
   private Publisher publisherClient;
   
   public FilePollerPlugin() {
   }
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(Global global, PluginInfo pluginInfo) throws XmlBlasterException {
      LogChannel log = global.getLog("filepoller");
      if (log.CALL)
         log.call(ME, "init");
      this.info = pluginInfo;
      this.publisherClient = new Publisher(global, this.getType(), this.info);
      this.publisherClient.init();
      if (log.DUMP) {
         log.dump(ME, "init: plugin paramenters: '" + this.info.dumpPluginParameters() + "'");
         log.dump(ME, "init: plugin user data  : '" + this.info.getUserData() + "'");
      }
   }

   /**
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      return this.info.getType();
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      return this.info.getVersion();
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      this.publisherClient.shutdown();
   }
}

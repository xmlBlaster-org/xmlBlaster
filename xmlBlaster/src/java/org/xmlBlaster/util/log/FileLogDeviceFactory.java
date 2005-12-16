/*------------------------------------------------------------------------------
Name:      I_LogDeviceFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   A Pluggable LogableDevice factory that returns LogDeviceFile loggers.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.log;
import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;

import org.jutils.log.LogableDevice;
import org.jutils.log.LogChannel;
import org.jutils.log.LogDeviceFile;
/**
 * A factory for creating file log devices of type org.jutils.log.LogDeviceFile.
 * <p>The plugin is of type <b>file</b>.</p>
 *
 * <p>The factory will only return a device of it can find a logFile property that fits. The search order for a LogChannel with key key is:</p>
 * <ul>
 *  <li>-logFile[key]</li>
 *  <li>-logFile</li>
 *  <li>,logFile in the plugin property</li>
 *  </ul>
 * <p>Here is some possible combinations:</p>
<pre>
LoggableDevicePlugin[console][1.0]=org.xmlBlaster.util.log.ConsoleLogDeviceFactory
LoggableDevicePlugin[file][1.0]=org.xmlBlaster.util.log.FileLogDeviceFactory,logFile=mylogfile
logDevice=console
logDevice[cb]=console,file

logDevice[corba]=console,file
logFile[corba]=mylogfile.corba
</pre>
<p>This would mean:</p>
<ul>
 <li>for all channels except cb and corba use console.</li>
 <li>For cb use console and file with the logfile mylogfile.</li>
 <li>for corba use console and file with logfile mylogfile.corba.</li>
</ul>

 *
 * @author Peter Antman
 * @version $Revision: 1.2 $ $Date$
 */

public class FileLogDeviceFactory implements I_LogDeviceFactory {
   public static final String LOG_FILE = "logFile";
   protected String logFile;
   protected Global glob;
   /**
    * Get default logFile, glob is searched first,then the plugin info.
    */
   public void init(Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = glob;
      Properties prop = pluginInfo.getParameters();
      logFile = glob.getProperty().get("logFile", (String)null);
      if (logFile == null)logFile = prop.getProperty(LOG_FILE, null);
      
   }
   
   public String getType() {return "file";}
   public String getVersion() {return "1.0";}

   /**
    * Get a loggable device, only return a device if a logfile is possible to 
    * find in properties. If a logFile[type] is in glob, this will be used if
    * logChannel.getChannelKey is of type.
    */
   public  LogableDevice getLogDevice(LogChannel channel) {
      String key = channel.getChannelKey();
      String strFilename = null;
      if (key != null) strFilename = glob.getProperty().get("logFile[" + key + "]", logFile);
      if (strFilename != null) {
         LogDeviceFile ldf = new LogDeviceFile(channel, strFilename);
         int max = glob.getProperty().get("maxLogFileLines", -1);
         if (max > 0) {
            ldf.setMaxLogFileLines(max);
         }
         return ldf;
      } else {
         // How do we log in loghandling
         return null;
      }
   }

   public void shutdown() throws XmlBlasterException {
   }

}

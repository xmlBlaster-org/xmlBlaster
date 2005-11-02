/*------------------------------------------------------------------------------
Name:      I_LogDeviceFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   A Pluggable LogableDevice factory that returns LogDeviceConsole loggers.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.log;

import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;

import org.jutils.log.LogableDevice;
import org.jutils.log.LogChannel;
import org.jutils.log.LogDeviceConsole;
/**
 * A factory for creating file log devices of type org.jutils.log.LogDeviceConsole
 * <p>This plugin is of type <b>console</b></p>
 * @author Peter Antman
 * @version $Revision: 1.2 $ $Date$
 */

public class ConsoleLogDeviceFactory implements I_LogDeviceFactory {
   
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {

   }
   /**
    * @return the string console.
    */
   public String getType() {return "console";}
   public String getVersion() { return "1.0";}
   /**
    * @return a new LogDeviceConsole each time beeing called.
    */
   public  LogableDevice getLogDevice(LogChannel channel) {
      return new LogDeviceConsole(channel);
   }

   public void shutdown() throws XmlBlasterException {
   }
}

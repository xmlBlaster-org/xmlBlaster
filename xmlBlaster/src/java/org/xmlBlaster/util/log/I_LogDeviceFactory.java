/*------------------------------------------------------------------------------
Name:      I_LogDeviceFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for  pluggable LogableDevice factories.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.log;

import org.jutils.log.LogableDevice;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.I_Plugin;
/**
 * Interface for a factory that returns LogableDevices. 
 *
 * <p>A I_LogDeviceFactory is a plugin that will get loaded by the 
 * LogDevicePluginManager. A typical plugin property might look like this:</p>
 * <pre>
 *   LogDevicePlugin[file][1.0]=org.xmlBlaster.util.log.FileLogDeviceFactory,logFile=myLogFile
 *  </pre>
 *
 *<p>Access to any properties given in Global or PluginInfo is accessed in the {@link #init} method.</p>
 *
 *
 * @author Peter Antman
 * @version $Revision: 1.2 $ $Date: 2003/07/17 09:34:12 $
 */

public interface I_LogDeviceFactory extends I_Plugin {
   /**
    * Return a LogableDevice, it is up to the factory to decide if each returned device is unique or if it reuses instances.
    * @return a LogableDevice or null if it was not possible to create one.
    */
   public  LogableDevice getLogDevice(LogChannel channel); 
}

/*------------------------------------------------------------------------------
Name:      ClassLoaderFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.classloader;
import java.net.URLClassLoader;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;
/**
 * Interface for a classloader factory.
 * Set param classloaderFactory= to implementing class.
 *
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>
 * @version $Revision: 1.11 $
 */

public interface ClassLoaderFactory {
   public void init(Global glob);
   public URLClassLoader getPluginClassLoader(PluginInfo pluginInfo) throws XmlBlasterException;
   public URLClassLoader getXmlBlasterClassLoader() throws XmlBlasterException;
}// ClassLoaderFactory

/*------------------------------------------------------------------------------
Name:      ContextClassLoaderFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.classloader;
import java.net.URLClassLoader;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;
/**
 * ClassLoaderFactory that returns the context classloader, to be used
 * in embedded/appserver environments.
 * <p>Set classloaderFactory=org.xmlBlaster.util.classloader.ContextClassLoaderFactory.</p>
 *
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>
 * @version $Revision: 1.1 $
 */

public class ContextClassLoaderFactory implements ClassLoaderFactory {
   public ContextClassLoaderFactory (){
      
   }
   public void init(Global glob) { 
      // Not used
   }
   public URLClassLoader getPluginClassLoader(PluginInfo pluginInfo) throws XmlBlasterException {
      return (URLClassLoader)Thread.currentThread().getContextClassLoader();
   }
   public URLClassLoader getXmlBlasterClassLoader() throws XmlBlasterException {
      return (URLClassLoader)Thread.currentThread().getContextClassLoader();
   }
}// ContextClassLoaderFactory

/*------------------------------------------------------------------------------
Name:      DbWriterPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwriter;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * DbWriterPlugin is a plugin wrapper if you want to run DbWriter inside xmlBlaster. 
 * <p>
 * This plugin uses <tt>java.util.logging</tt> and redirects the logging to xmlBlasters default
 * logging framework. You can switch this off by setting the attribute <tt>xmlBlaster/jdk14loggingCapture</tt> to false.
 * </p>
 * 
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 */
public interface DbWriterPluginMBean {
   String addDbWriter(String loginName, String password);
   public String getClients();
   public String removeDbWriter(String loginName);
   public void shutdown() throws XmlBlasterException;
}

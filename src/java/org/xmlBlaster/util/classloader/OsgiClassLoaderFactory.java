/*------------------------------------------------------------------------------
Name:      XmlBlasterClassLoader.java
Project:   xmlBlaster.org
Copyright: LGPL or GPL
Author:    yavin AT gmx.com
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.classloader;

import java.net.URL;
import java.net.URLClassLoader;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.classloader.StandaloneClassLoaderFactory;
import org.xmlBlaster.util.plugin.PluginInfo;

/*
As there were several questions about how I solved the classloader issue
 attached you will find the specialized classloaderfactory. If you use
this workaround don't forget to set the property "urlClassLoaderFactory".

See also at the topic "[xmlblaster] small fix regarding classloader" in
the news group archive.
*/

/**
 * Custom <code>ClassLoaderFactory</code> for xmlBlaster. This implementation
 * takes care of ClassCastExceptions in the default
 * <code>StandaloneClassLoaderFactory</code>. It assumes that the current
 * classloader is an instance of <code>URLClassLoader</code> but it can be an
 * <code>EclipseClassLoader</code> or something else. If the default implemenation fails a new
 * delegating <code>URLCLassLoader</code> will be returned to the callers. To use this implementation set
 * xmlBlaster property <i>urlClassLoaderFactory</i>.
 * 
 * @see org.xmlBlaster.util.Global#getClassLoaderFactory()
 * @see org.xmlBlaster.util.classloader.StandaloneClassLoaderFactory
 * @author Kai Klesatschke <kai.klesatschke@netallied.de>
 */
public class OsgiClassLoaderFactory extends StandaloneClassLoaderFactory
{
    //@Override
    /*
     * (non-Javadoc)
     * 
     * @see org.xmlBlaster.util.classloader.StandaloneClassLoaderFactory#getPluginClassLoader(org.xmlBlaster.util.plugin.PluginInfo)
     */
    public URLClassLoader getPluginClassLoader(PluginInfo pluginInfo) throws XmlBlasterException
    {
        try
        {
            return super.getPluginClassLoader(pluginInfo);
        } catch (ClassCastException e)
        {
            return new URLClassLoader(new URL[0], this.getClass().getClassLoader());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.xmlBlaster.util.classloader.ClassLoaderFactory#getXmlBlasterClassLoader()
     */
    public URLClassLoader getXmlBlasterClassLoader() throws XmlBlasterException
    {
        try
        {
            return super.getXmlBlasterClassLoader();
        } catch (ClassCastException e)
        {
            return new URLClassLoader(new URL[0], this.getClass().getClassLoader());
        }
    }

}


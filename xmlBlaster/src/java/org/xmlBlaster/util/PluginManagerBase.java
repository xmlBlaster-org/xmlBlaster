/*------------------------------------------------------------------------------
Name:      PluginManagerBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Baseclass to load plugins.
Version:   $Id: PluginManagerBase.java,v 1.14 2002/07/13 19:00:14 ruff Exp $
Author:    W. Kleinertz (wkl), Heinrich Goetzger goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.log.LogChannel;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.Authenticate;

import java.util.Hashtable;
import java.util.Vector;
import java.util.StringTokenizer;
import java.net.URL;

/**
 * Baseclass to load plugins.
 * <p />
 * A typical syntax in the xmlBlaster.properties file is:
 * <pre>
 *   MimeSubscribePlugin[ContentLenFilter][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter,DEFAULT_MAX_LEN=200,DEFAULT_MIN_LEN=20
 * </pre>
 * @author W. Kleinertz (wkl) H. Goetzger
 * @version 1.0
 */
abstract public class PluginManagerBase {

   private static String ME = "PluginManagerBase";
   protected Hashtable managers = new Hashtable(); // currently loaded plugins
   private final Global glob;
   private final LogChannel log;

   protected PluginManagerBase(org.xmlBlaster.util.Global glob) {
      this.glob = glob;
      this.log = glob.getLog("classloader");
   }

   /**
    * Return a specific plugin.
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return I_Plugin The plugin which is suitable to handle the request.
    * @exception XmlBlasterException Thrown if no suitable plugin has been found.
    */
   public I_Plugin getPluginObject(String type, String version) throws XmlBlasterException {
      if (log.CALL) log.call(ME+".getPluginObject()", "Loading plugin type=" + type + " version=" + version);
      I_Plugin plug = null;
      String[] pluginNameAndParam = null;

      pluginNameAndParam = choosePlugin(type, version);

      if((pluginNameAndParam!=null) &&
         (pluginNameAndParam[0]!=null) &&
         (!pluginNameAndParam.equals("")))
      {
         // check in hash if plugin is instanciated already
         plug = (I_Plugin)managers.get(pluginNameAndParam[0]);
         if (plug!=null) return plug;

         // not in hash, instanciat plugin
         plug = instantiatePlugin(pluginNameAndParam);
      }
      else {
         throw new XmlBlasterException(ME+".notSupported","The requested plugin [" + type + "][" + version + "] isn't supported!");
      }

      return plug;
   }

   /**
    * @param type can be null
    * @param version can be null
    * @return please return your default plugin classname or null if not specified
    */
   abstract public String getDefaultPluginName(String type, String version);


   /**
    * Tries to return an instance of the default plugin.
    *
    */
   public I_Plugin getDummyPlugin() {

      String name = getDefaultPluginName(null, null);
      if (name == null)
         return null;

      I_Plugin plug = (I_Plugin)managers.get(name);
      if (plug!=null) return plug;

      try {
         String[] defPlgn={name};
         plug = instantiatePlugin(defPlgn);
      } catch(XmlBlasterException e) {} // ???

      return plug;
   }

   /**
   * @return The name of the property in xmlBlaster.property, e.g. "Security.Server.Plugin"
   * for "Security.Server.Plugin[simple][1.0]"
   */
   abstract protected String getPluginPropertyName();

   /**
    * @return e.g. "Security.Server.Plugin[simple][1.0]"
    */
   public final String getPluginPropertyName(String type, String version) {
      StringBuffer buf = new StringBuffer(80);
      buf.append(getPluginPropertyName()).append("[").append(type).append("][").append(version).append("]");
      return buf.toString();
   }

   /**
    * Resolve type and version to the plugins name
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return String[] The name of the requested plugin and its parameters (as from xmlBlaster.properties)
    */
   protected String[] choosePlugin(String type, String version) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering choosePlugin(" + type + ", " + version + ")");
      String[] pluginData=null;
      String rawString;

      rawString = glob.getProperty().get(getPluginPropertyName(type, version), (String)null);
      if (rawString==null) {
         if (type != null)
            log.warn(ME, "Plugin type=" + type + " version=" + version + " not found, choosing default plugin");
         rawString = getDefaultPluginName(type, version);
      }
      if(rawString!=null) {
         Vector tmp = new Vector();
         StringTokenizer st = new StringTokenizer(rawString, ",=");
         while(st.hasMoreTokens()) {
            tmp.addElement(st.nextToken());
         }
         //pluginData = (String[])tmp.toArray();
         pluginData=new String[tmp.size()];
         for(int i=0;i<tmp.size();i++) {
            pluginData[i]=(String)tmp.elementAt(i);
         }
      }
      //else
      //   log.warn(ME, "Accessing " + getPluginPropertyName(type, version) + " failed, no such entry found in xmlBlaster.properties");
      if (pluginData != null && pluginData[0].equalsIgnoreCase("")) pluginData = null;

      return pluginData;
   }

   /**
    * Loads a plugin.
    * <p/>
    * @param String[] The first element of this array contains the class name
    *                 e.g. org.xmlBlaster.authentication.plugins.Manager<br />
    *                 Following elements are arguments for the plugin. (Like in c/c++ the command-line arguments.)
    * @return I_Plugin
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_Plugin instantiatePlugin(String[] pluginNameAndParam) throws XmlBlasterException
   {
      // separate parameter and plugin name
      String[] param= new String[pluginNameAndParam.length-1];
      String pluginName = pluginNameAndParam[0];
      System.arraycopy(pluginNameAndParam,1,param,0,pluginNameAndParam.length-1);

      I_Plugin manager = null;
      try {
         ClassLoaderFactory factory = glob.getClassLoaderFactory();
         if (factory != null) {
            if (log.TRACE) log.trace(ME, "useXmlBlasterClassloader=true: Trying Class.forName('" + pluginName + "') ...");
            XmlBlasterClassLoader ucl = factory.getXmlBlasterClassLoader(this, pluginName);
            manager = (I_Plugin)ucl.loadClass(pluginName).newInstance();

            if (log.TRACE) log.trace(ME, "Found I_Plugin '" + pluginName + "'");
            if (log.TRACE) {
               URL[] callerURL = ucl.getURLs();
               log.trace(ME, "ClassLoaderClassPath: BEGIN");
                      for (int ii = 0; ii < callerURL.length; ii++)
                      log.trace(ME, "from Loader " + ii +": " + callerURL[ii].toString() );
               log.trace(ME, "ClassLoaderClassPath: END");
            }

            // who loaded plugin?
            if (log.TRACE) log.trace(ME, "pluginName '" + pluginName + "' loaded by " + factory.which(manager, pluginName) );
            if (log.TRACE) {
               log.trace(ME, "listClassPath: BEGIN after instanciation of manager");
               glob.getClassLoaderFactory().listClassPath(manager);
               log.trace(ME, "listClassPath: END");
            }

            if (log.TRACE) {
               URL[] callerURL = ucl.getURLs();
               log.trace(ME, "ClassLoaderClassPath: BEGIN after manager");
                      for (int ii = 0; ii < callerURL.length; ii++)
                      log.trace(ME, "from Loader " + ii +": " + callerURL[ii].toString() );
               log.trace(ME, "ClassLoaderClassPath: END");
            }
         }
         else { // Use JVM default class loader:
           Class cl = java.lang.Class.forName(pluginName);
           manager = (I_Plugin)cl.newInstance();
         }
      }
      catch (IllegalAccessException e) {
         log.error(ME, "The plugin class '" + pluginName + "' is not accessible\n -> check the plugin name and/or the CLASSPATH to the plugin");
         throw new XmlBlasterException(ME+".NoClass", "The Plugin class '" + pluginName + "' is not accessible\n -> check the plugin name and/or the CLASSPATH to the plugin");
      }
      catch (SecurityException e) {
         log.error(ME, "No right to access the plugin class or initializer '" + pluginName + "'");
         throw new XmlBlasterException(ME+".NoAccess", "No right to access the plugin class or initializer '" + pluginName + "'");
      }
      catch (Throwable e) {
         log.error(ME, "The plugin class or initializer '" + pluginName + "' is invalid\n -> check the plugin name and/or the CLASSPATH to the driver file: \n'" + e.toString() + "'");
         e.printStackTrace();
         throw new XmlBlasterException(ME+".Invalid", "The plugin class or initializer '" + pluginName + "' is invalid\n -> check the plugin name and/or the CLASSPATH to the driver file: " + e.toString());
      }

      // Initialize the plugin
      if (manager != null) {
         try {
            manager.init(glob, param);
            log.info(ME, "Plugin '" + pluginName + "' successfully initialized.");
         } catch (XmlBlasterException e) {
            //log.error(ME, "Initializing of plugin " + manager.getType() + " failed:" + e.reason);
            throw new XmlBlasterException(ME+".NoInit", "Initializing of plugin " + manager.getType() + " failed:" + e.reason);
         }
      }
      managers.put(pluginName, manager);

      return manager;
   }
}

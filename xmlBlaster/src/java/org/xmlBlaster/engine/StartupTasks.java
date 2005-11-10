/*------------------------------------------------------------------------------
Name:      StartupTasks.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.MalformedURLException;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.script.XmlScriptClient;

/**
 * This plugin is loaded on xmlBlaster startup and executes the script file <tt>xmlBlasterStartup.xml</tt>. 
 * <p>
 * The file <tt>xmlBlasterStartup.xml</tt> follows the syntax rules of {@link org.xmlBlaster.client.script.XmlScriptInterpreter}.
 * </p>
 *
 * <p>
 * This <tt>StartupTasks</tt> plugin is started with the run level manager as configured in <tt>xmlBlasterPlugins.xml</tt>,
 * for example:</p>
 * <pre>
 *  &lt;plugin id='StartupTasks' className='org.xmlBlaster.engine.StartupTasks'>
 *     &lt;action do='LOAD' onStartupRunlevel='7' sequence='1'
 *                          onFail='resource.configuration.pluginFailed'/>
 *     &lt;action do='STOP' onShutdownRunlevel='6' sequence='1'/>
 *     &lt;attribute id='loginName'>_StartupTasks&lt;/attribute>
 *     &lt;attribute id='password'>secret&lt;/attribute>
 *     &lt;attribute id='scriptFileName'>xmlBlasterStartup.xml&lt;/attribute>
 *     &lt;attribute id='directoryName'>${user.home}&lt;/attribute>
 *     &lt;attribute id='outFileName'>&lt;/attribute>
 *  &lt;/plugin>
 * </pre>
 * <p>The script file name defaults to <tt>$HOME/xmlBlasterStartup.xml</tt>.</p>
 * <p>
 * We use the <tt>LOCAL</tt> protocol driver to talk to xmlBlaster, therefor this
 * plugin works only if the client and server is in the same virtual machine (JVM).
 * </p>
 *
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.startupTasks.html">The admin.startupTasks requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.runlevel.howto.html">The engine.runlevel.howto requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.script.html">The client.script requirement</a>
 * @since 1.0.1
 */
public class StartupTasks implements I_Plugin {

   private final static String ME = StartupTasks.class.getName();
   
   private PluginInfo pluginInfo;
   private Global global;
   private LogChannel log;

   private I_XmlBlasterAccess connection;
   private String directoryName = null; // (String)System.getProperty("user.home"); // + (String)System.getProperty("file.separator") + "tmp";
   private String scriptFileName = "xmlBlasterStartup.xml";
   private URL scriptFileUrl;
   private String outFileName = "";

   private String loginName = "_StartupTasks";
   private String password = "secret";
   
   // forceBase64==false: ASCII dump for content if possible (XML embedable)
   //private boolean forceBase64 = false;

   /**
    * Initializes the plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.pluginInfo = pluginInfo;
      this.global = glob.getClone(glob.getNativeConnectArgs());
      try {
         this.global.addObjectEntry("ServerNodeScope", glob.getObjectEntry("ServerNodeScope"));
         this.log = this.global.getLog("core");

         if (this.log.CALL) this.log.call(ME, "init");

         this.directoryName = this.global.get("directoryName", this.directoryName, null, this.pluginInfo);
         this.scriptFileName = this.global.get("scriptFileName", this.scriptFileName, null, this.pluginInfo);
         this.outFileName = this.global.get("outFileName", this.outFileName, null, this.pluginInfo);

         if (this.directoryName == null || this.directoryName.length() < 1) {
            // Use xmlBlaster search path (including CLASSPATH)
            FileLocator fileLocator = new FileLocator(this.global);
            this.scriptFileUrl = fileLocator.findFileInXmlBlasterSearchPath((String)null, this.scriptFileName);
         }
         else {
            // Use given path
            File f = new File(this.directoryName, this.scriptFileName);
            if (f.exists() && f.isFile() && f.canRead()) {
               try {
                  this.scriptFileUrl = f.toURL();
               } catch (MalformedURLException e) {
                  log.warn(ME, e.toString());
               }
            }
         }
         
         if (this.scriptFileUrl != null) {
            log.info(ME, "Using startup script file '" + this.scriptFileUrl.toString() + "'");
         }
         else {
            log.warn(ME, "No startup script file '" + this.scriptFileName + "' found, we continue without.");
            return;
         }

         this.loginName = this.global.get("loginName", this.loginName, null, this.pluginInfo);
         this.password = this.global.get("password", this.password, null, this.pluginInfo);

         excuteStartupTasks();
      }
      finally {
         this.global = null;
      }
   }

   /**
    * @return the plugin type, defaults to "StartupTasks"
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      if (this.pluginInfo != null) return this.pluginInfo.getType();
      return ME;
   }

   /**
    * @return the plugin version, defaults to "1.0"
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      if (this.pluginInfo != null) return this.pluginInfo.getVersion();
      return "1.0";
   }

   /**
    * Shutdown the plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
   }

   /**
    * On startup execute given script. 
    */
   private void excuteStartupTasks() throws XmlBlasterException {
      if (this.scriptFileUrl == null) {
         return;
      }
      try {
         this.connection = new XmlBlasterAccess(this.global);

         ConnectQos connectQos = new ConnectQos(this.global, loginName, password);
         this.connection.connect(connectQos, new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.warn(ME, "Ignoring received message '" + updateKey.getOid() + "'");
               return Constants.RET_OK;
            }
         });

         OutputStream outStream = System.out;
         boolean needsClosing = false;
         if (this.outFileName != null && this.outFileName.length() > 0) {
            outStream = new FileOutputStream(this.outFileName);
            needsClosing = true;
         }

         XmlScriptClient interpreter =
            new XmlScriptClient(this.global, this.connection, outStream, outStream, null);

         InputStream in = this.scriptFileUrl.openStream();
         try {
            interpreter.parse(new InputStreamReader(in));
         }
         finally {
            try { in.close(); } catch(IOException e) { log.warn(ME, "Ignoring problem: " + e.toString()); }
            if (needsClosing) {
               try { outStream.close(); } catch(IOException e) { log.warn(ME, "Ignoring problem: " + e.toString()); }
            }
         }

         log.info(ME, "Successfully executed '" + this.scriptFileUrl.toString() + "'.");
      }
      catch (java.io.FileNotFoundException e) {
         log.warn(ME, "Can't execute  '" + this.scriptFileUrl.toString() + "': " + e.toString());
      }
      catch (java.io.IOException e) {
         log.warn(ME, "Can't open stream of '" + this.scriptFileUrl.toString() + "': " + e.toString());
      }
      catch (XmlBlasterException e) {
         log.warn(ME, "Can't execute  '" + this.scriptFileUrl.toString() + "': " + e.getMessage());
      }
      finally {
         try {
            if (this.connection != null) {
               if (this.connection.isConnected()) {
                  this.connection.disconnect(null);
               }
               this.connection = null;
            }
         }
         catch (Throwable e) {
            log.warn(ME, "Ignoring problem during disconnect: " + e.toString());
         }
      }
   }
}


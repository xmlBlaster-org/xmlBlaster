/*------------------------------------------------------------------------------
Name:      StartupTasks.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.io.File;
import java.io.FileOutputStream;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.EncodableData;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;

/**
 * This plugin is loaded on xmlBlaster startup and executes the script file <tt>xmlBlasterStartup.xml</tt>. 
 * <h2>TODO: Finish implementing</h2>
 * <p>
 * The file <tt>xmlBlasterStartup.xml</tt> follows the syntax rules of #org.xmlBlaster.client.script.XmlScriptInterpreter
 * </p>
 *
 * <p>
 * This <tt>StartupTasks</tt> plugin is started with the run level manager as configured in xmlBlasterPlugins.xml,
 * for example:</p>
 * <pre>
 *  &lt;plugin id='StartupTasks' className='org.xmlBlaster.engine.StartupTasks'>
 *     &lt;action do='LOAD' onStartupRunlevel='7' sequence='1'
 *                          onFail='resource.configuration.pluginFailed'/>
 *     &lt;action do='STOP' onShutdownRunlevel='6' sequence='1'/>
 *     &lt;attribute id='loginName'>_StartupTasks&lt;/attribute>
 *     &lt;attribute id='password'>secret&lt;/attribute>
 *     &lt;attribute id='scriptFileName'>xmlBlasterStartup.xml&lt;/attribute>
 *     &lt;attribute id='directoryName'>/tmp&lt;/attribute>
 *  &lt;/plugin>
 * </pre>
 * <p>The <tt>directorName</tt> defaults to <tt>$HOME/tmp</tt> and <tt>foceBase64=false</tt> tries to dump
 * the message content in human readable form (if the message dump xml syntax allows it).</p>
 * <p>
 * We use the <tt>LOCAL</tt> protocol driver to talk to xmlBlaster, therefor this
 * plugin works only if the client and server is in the same virtual machine (JVM).
 * </p>
 *
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.runlevel.howto.html">The engine.runlevel.howto requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.script.html">The client.script requirement</a>
 */
public class StartupTasks implements I_Plugin {

   private final static String ME = StartupTasks.class.getName();
   
   private PluginInfo pluginInfo;
   private Global global;
   private LogChannel log;

   private I_XmlBlasterAccess connection;
   private String directoryName;
   private String scriptFileName = "xmlBlasterStartup.xml";
   private SubscribeReturnQos subscribeReturnQos;

   private String loginName;
   private String password = "secret";
   /** forceBase64==false: ASCII dump for content if possible (XML embedable) */
   private boolean forceBase64 = false;

   private static final String[] nativeConnectArgs = {
              "-protocol", "LOCAL",
              "-dispatch/connection/pingInterval", "0",
              "-dispatch/connection/burstMode/collectTime", "0",
              "-queue/defaultPlugin", "RAM,1.0"
           };
   
   /**
    * Initializes the plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.pluginInfo = pluginInfo;
      this.global = glob.getClone(nativeConnectArgs);
      this.global.addObjectEntry("ServerNodeScope", glob.getObjectEntry("ServerNodeScope"));
      this.log = this.global.getLog("core");

      if (this.log.CALL) this.log.call(ME, "init");

      try {
         String defaultPath = (String)System.getProperty("user.home"); // + (String)System.getProperty("file.separator") + "tmp";
         this.directoryName = 
            this.global.getProperty().replaceVariableWithException("directoryName", 
            this.pluginInfo.getParameters().getProperty("directoryName",
            this.global.getProperty().get("plugin/"+getType()+"/directoryName", defaultPath)));

         String defaultScriptFileName = "xmlBlasterStartup.xml";
         this.scriptFileName =
            this.global.getProperty().replaceVariableWithException("scriptFileName", 
            this.pluginInfo.getParameters().getProperty("scriptFileName",
            this.global.getProperty().get("plugin/"+getType()+"/scriptFileName", defaultScriptFileName)));
      }
      catch (org.jutils.JUtilsException e) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "Can't initialize plugin '" + getType() + "'", e);
      }
      
      File f = new File(this.directoryName, this.scriptFileName);
      if (f.exists() && f.isFile() && f.canRead()) {
         log.info(ME, "Using startup script file '" + f.toString() + "'");
      }
      else {
         log.warn(ME, "No startup script file '" + f.toString() + "' found, we continue without.");
      }

      this.loginName = this.pluginInfo.getParameters().getProperty("loginName", ME);
      this.password = this.pluginInfo.getParameters().getProperty("password", this.password);

      excuteStartupTasks();
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
      if (this.log.CALL) this.log.call(ME, "shutdown");
      if (connection != null) connection.disconnect(null);
   }

   /**
    * On startup execute given script. 
    */
   private void excuteStartupTasks() throws XmlBlasterException {
      /*
      try {
         final String secretCbSessionId = new Timestamp().toString();

         this.connection = new XmlBlasterAccess(this.global);

         ConnectQos connectQos = new ConnectQos(this.global, loginName, password);
         connectQos.getSessionQos().setSessionTimeout(0L);
         connectQos.setSecretCbSessionId(secretCbSessionId);
         // Constants.ONOVERFLOW_DISCARDOLDEST

         this.connection.connect(connectQos, new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               
               if (!secretCbSessionId.equals(cbSessionId)) {
                  log.warn(ME, "Ignoring received message '" + updateKey.getOid() + "' because of wrong credentials '" + cbSessionId + "'");
                  return Constants.RET_OK;
               }
               log.info(ME, "Receiving asynchronous message '" + updateKey.getOid() + "'" );

               if (Constants.OID_DEAD_LETTER.equals(updateKey.getOid())) {
                  dumpMessage(updateKey, content, updateQos);
               }
               return Constants.RET_OK;
            }
         });  // Login to xmlBlaster, default handler for updates

         SubscribeKey sk = new SubscribeKey(this.global, Constants.OID_DEAD_LETTER);
         SubscribeQos sq = new SubscribeQos(this.global);
         sq.setWantInitialUpdate(false);
         this.subscribeReturnQos = this.connection.subscribe(sk, sq);

         log.info(ME, "Subscribed to topic '" + Constants.OID_DEAD_LETTER + "'");
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Can't dump '" + Constants.OID_DEAD_LETTER + "': " + e.getMessage());
      }
      */
   }
}


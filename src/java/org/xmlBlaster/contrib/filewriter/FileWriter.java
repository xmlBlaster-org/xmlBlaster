/*------------------------------------------------------------------------------
Name:      Receiver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE filep
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.filewriter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.contrib.ContribConstants;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.MomEventEngine;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.address.CallbackAddress;

/**
 * XmlBlaster plugin wrapper code.
 *
 * A plugin registration example (xmlBlasterPlugins.xml):
 * <pre>
    &lt;plugin create='true' id='FileWriter'
           className='org.xmlBlaster.contrib.filewriter.Receiver'>
      &lt;attribute id='mom.topicName'>fileWriterTopic&lt;/attribute>
      &lt;attribute id='connectQos'>&lt;![CDATA[
	   &lt;qos>
	      &lt;securityService type='htpasswd' version='1.0'>
	        &lt;user>FileWriter&lt;/user>
	        &lt;passwd>secret&lt;/passwd>
	      &lt;/securityService>
	      &lt;session name='client/FileWriter/session/1' timeout='0' maxSessions='1'
	                  clearSessions='true' reconnectSameClientOnly='false'/>
	   &lt;/qos>
	   ]]>
      &lt;/attribute>
      &lt;attribute id='directoryName'>${user.home}${file.separator}FileDumps&lt;/attribute>
      &lt;action do='LOAD' onStartupRunlevel='6' sequence='6'
                 onFail='resource.configuration.pluginFailed'/>
      &lt;action do='STOP' onShutdownRunlevel='5' sequence='6'/>
   &lt;/plugin>
 * </pre>
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class FileWriter extends GlobalInfo implements I_Callback {

   private String ME = "Receiver";
   private Global global;
   private static Logger log = Logger.getLogger(FileWriter.class.getName());
   private I_XmlBlasterAccess access;
   private ConnectQos connectQos;
   private String subscribeKey;
   private String subscribeQos;
   private FileWriterCallback callback;

   /** used to identify if it has shut down (to get a new global) */
   private boolean isShutdown;

   /** only used as a default login name and logging */
   private String name;
   private I_PluginConfig pluginConfig;
   private boolean momAdministered;
   
   public FileWriter() {
      super(new String[0]);
   }

   public FileWriter(Global globOrig, String name, I_PluginConfig config) throws XmlBlasterException {
      super(new String[0]);
      ME += "-" + name;
      this.name = name;
      prepareInit(globOrig, config);
      initConnection();
   }

   private void prepareInit(Global globOrig, I_PluginConfig config) throws XmlBlasterException {
      this.isShutdown = false;
      boolean useNativeCfg = globOrig.get("__useNativeCfg", true, null, config);
      String [] args = useNativeCfg ? globOrig.getNativeConnectArgs() : null;
      this.global = globOrig.getClone(args); // sets session.timeout to 0 etc.
      this.pluginConfig = config;
      if (log.isLoggable(Level.FINER))
         log.finer("constructor");
      // retrieve all necessary properties:

      String tmp  = this.global.get("mom.connectQos", (String)null, null, this.pluginConfig);
      if (tmp != null) {
         ConnectQosData data = this.global.getConnectQosFactory().readObject(tmp);
         this.connectQos = new ConnectQos(this.global, data);
         Object tmpObj = globOrig.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
         if (tmpObj != null)
            this.global.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, tmpObj);
      }
      else {
         String userId = this.global.get("mom.loginName", "_" + this.name, null, this.pluginConfig);
         String password = this.global.get("mom.password", (String)null, null, this.pluginConfig);
         this.connectQos = new ConnectQos(this.global, userId, password);
         this.global.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, globOrig.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope));
      }

      
      momAdministered = this.global.get("mom.administered", false, null, pluginConfig);
      
      tmp = this.global.get("mom.subscribeKey", (String)null, null, pluginConfig);
      String topicName =  this.global.get("mom.topicName", (String)null, null, pluginConfig);
      if (tmp != null) {
         subscribeKey = tmp;
         if (topicName != null)
            log.warning("constructor: since 'mom.subscribeKey' is defined, 'mom.topicName' will be ignored");
      }
      else if (!momAdministered) {
         if (topicName == null)
            throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "at least one of the properties 'mom.topicName' or 'mom.subscribeKey' must be defined");
         subscribeKey = (new SubscribeKey(this.global, topicName)).toXml();
      }
      this.subscribeQos = this.global.get("mom.subscribeQos", (String)null, null, this.pluginConfig);
      
      if (momAdministered) {
         if (subscribeKey != null)
            log.warning("'mom.administered' set to 'true' and 'mom.subscribeKey' set: will ignore subscription since this is triggered by the administator");
         if (subscribeQos != null)
            log.warning("'mom.administered' set to 'true' and 'mom.subscribeKey' set: will ignore subscription since this is triggered by the administator");
         
         String replDispatchPlugin = "ReplManager,1.0";
         CallbackAddress cbAddr = connectQos.getData().getCurrentCallbackAddress();
         if (cbAddr != null) {
            String dispatchPlugin = cbAddr.getDispatchPlugin();
            if (!replDispatchPlugin.equals(dispatchPlugin)) {
               cbAddr.setDispatchPlugin(replDispatchPlugin); 
               cbAddr.setRetries(-1);
            }
         }
         else {
            cbAddr = new CallbackAddress(this.global);
            cbAddr.setRetries(-1);
            cbAddr.setDispatchPlugin(replDispatchPlugin);
            connectQos.addCallbackAddress(cbAddr);
         }
      }
      if (subscribeQos == null)
         subscribeQos = "<qos/>";
      
      String directoryName = this.global.get("filewriter.directoryName", (String)null, null, this.pluginConfig);
      if (directoryName == null)
         throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "constructor: 'filewriter.directoryName' is mandatory");
      String tmpDirectoryName = this.global.get("filewriter.tmpDirectoryName", directoryName + File.separator + "tmp", null, this.pluginConfig);
      File d = new File(directoryName);
      if (!d.exists())
    	  if (!d.mkdirs())
   	         throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "Can't create directoryName="+directoryName);

      boolean overwrite = this.global.get("filewriter.overwrite", true, null, this.pluginConfig);
      String lockExtention =  this.global.get("filewriter.lockExtention", (String)null, null, this.pluginConfig);
      try {
         boolean isAbsolute = tmpDirectoryName.startsWith(File.separator) || tmpDirectoryName.startsWith("/");
         if (!isAbsolute) { // then make it absolute since callback needs absolute names
            if (directoryName.endsWith(File.separator) || directoryName.endsWith("/"))
               tmpDirectoryName = directoryName + tmpDirectoryName;
            else
               tmpDirectoryName = directoryName + File.separator + tmpDirectoryName;
         }
         boolean keepDumpFiles = false;
         this.callback = new FileWriterCallback(directoryName, tmpDirectoryName, lockExtention, overwrite, keepDumpFiles);
      }
      catch (Exception ex) {
         throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, "Receiver constructor", "exception occured", ex);
      }
   }

   /**
    * Connects to the xmlBlaster.
    *
    * @throws XmlBlasterException
    */
   private synchronized void initConnection() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER))
         log.finer("init");
      if (this.isShutdown) { // on a second init
         this.global = this.global.getClone(null);
      }
      this.isShutdown = false;
      this.access = this.global.getXmlBlasterAccess();
      this.access.connect(this.connectQos, this);
      if (!momAdministered)
         this.access.subscribe(this.subscribeKey, this.subscribeQos);

   }

   /**
    * If an exception occurs it means it could not publish the entry
    * @throws XmlBlasterException
    */
   public void shutdown() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER))
         log.finer("shutdown");
      synchronized (this) {
         this.isShutdown = false;
         if (this.access != null)
        	 this.access.disconnect(new DisconnectQos(this.global));
         if (this.global != null)
        	 this.global.shutdown();
      }
   }

   /**
    *
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      if (pluginConfig == null)
         return null;
      return pluginConfig.getType();
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      if (pluginConfig == null)
         return null;
      return pluginConfig.getVersion();
   }


   public void doInit(Global glob, PluginInfo info) throws XmlBlasterException {
      prepareInit(glob, info);
      initConnection();
   }

   public synchronized String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      try {
         InputStream is = MomEventEngine.decompress(new ByteArrayInputStream(content), updateQos.getClientProperties());
         String timestamp = "" + updateQos.getRcvTimestamp().getTimestamp();
         updateQos.getData().addClientProperty(ContribConstants.TIMESTAMP_ATTR, timestamp);
         this.callback.update(updateKey.getOid(), is, updateQos.getClientProperties());
         return "OK";
      }
      catch (Exception ex) {
         ex.printStackTrace();
         throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_HOLDBACK, "Receiver.update", "user exception", ex);
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_HOLDBACK, "Receiver.update", "user throwable", ex);
      }
   }
   
   
}




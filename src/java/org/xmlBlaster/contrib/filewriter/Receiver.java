/*------------------------------------------------------------------------------
Name:      Receiver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE filep
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.filewriter;

import java.io.File;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.contrib.MomEventEngine;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.ConnectQosData;

/**
 * Receiver
 * TODO Write a test for this class. NOTE THIS HAS NOT BEEN TESTED YET. PLEASE REMOVE THIS WHEN TESTED.
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class Receiver implements I_Plugin, I_Callback {

   private String ME = "Receiver";
   private Global global;
   private static Logger log = Logger.getLogger(Receiver.class.getName());
   private I_XmlBlasterAccess access;
   private ConnectQos connectQos;
   private String subscribeKey;
   private String subscribeQos;
   private FileWriterCallback callback;
   
   /** used to identify if it has shut down (to get a new global) */
   private boolean isShutdown;
   
   /** only used as a default login name and logging */
   private String name;
   private PluginInfo info;

   public Receiver(Global globOrig, String name, PluginInfo info) throws XmlBlasterException {
      ME += "-" + name;
      this.name = name;
      prepareInit(globOrig, info);
   }
   
   private void prepareInit(Global globOrig, PluginInfo info) throws XmlBlasterException {
      this.isShutdown = false;
      this.global = globOrig.getClone(globOrig.getNativeConnectArgs()); // sets session.timeout to 0 etc.
      this.info = info;

      if (log.isLoggable(Level.FINER)) 
         log.finer("constructor");
      // retrieve all necessary properties:
      String tmp = null;
      tmp = this.global.get("subscribeKey", (String)null, null, info);
      String topicName =  this.global.get("topicName", (String)null, null, info);
      if (tmp != null) {
         this.subscribeKey = tmp;
         if (topicName != null)
            log.warning("constructor: since 'subscribeKey' is defined, 'topicName' will be ignored");
      }
      else {
         if (topicName == null)
            throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "at least one of the properties 'topicName' or 'subsribeKey' must be defined");
         this.subscribeKey = (new SubscribeKey(this.global, topicName)).toXml(); 
      }
      
      this.subscribeQos = this.global.get("subscribeQos", "<qos/>", null, this.info);

      tmp  = this.global.get("connectQos", (String)null, null, this.info);
      if (tmp != null) {
         ConnectQosData data = this.global.getConnectQosFactory().readObject(tmp);
         this.connectQos = new ConnectQos(this.global, data);
      }
      else {
         String userId = this.global.get("loginName", "_" + this.name, null, this.info);
         String password = this.global.get("password", (String)null, null, this.info);
         this.connectQos = new ConnectQos(this.global, userId, password);
         this.global.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, globOrig.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope));
      }

      String directoryName = this.global.get("directoryName", (String)null, null, this.info);
      if (directoryName == null)
         throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "constructor: 'directoryName' is mandatory");
      String tmpDirectoryName = this.global.get("tmpDirectoryName", directoryName + File.separator + "tmp", null, this.info);

      boolean overwrite = this.global.get("overwrite", true, null, this.info);
      String lockExtention =  this.global.get("lockExtention", (String)null, null, this.info);
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
         this.access.disconnect(new DisconnectQos(this.global));
         this.global.shutdown();
      }
   }

   /**
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      return this.info.getType();
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      return this.info.getVersion();
   }
   

   public void init(Global glob, PluginInfo info) throws XmlBlasterException {
      prepareInit(glob, info);
      initConnection();
   }

   public synchronized String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      try {
         content = MomEventEngine.decompress(content, updateQos.getClientProperties());
         String timestamp = "" + updateQos.getRcvTimestamp().getTimestamp();
         updateQos.getData().addClientProperty("_timestamp", timestamp);
         this.callback.update(updateKey.getOid(), content, updateQos.getClientProperties());
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




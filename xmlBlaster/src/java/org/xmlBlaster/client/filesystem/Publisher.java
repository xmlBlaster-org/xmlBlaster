/*------------------------------------------------------------------------------
Name:      Publisher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE filep
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.filesystem;

import java.util.Properties;
import java.util.Set;

import org.jutils.init.Property;
import org.jutils.log.LogChannel;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.address.Address;


/**
 * Publisher
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class Publisher implements I_Timeout {

   private String ME = "Publisher";
   private Global global;
   private LogChannel log;
   private DirectoryManager directoryManager;
   private I_XmlBlasterAccess access;
   private String publishKey;
   private String publishQos;
   private ConnectQos connectQos;
   private long pollInterval;
   private long maximumFileSize;
   private Timestamp timeoutHandle;
   private static Timeout timeout = new Timeout("FileSystem-Poller");
   
   /** used to identify if it has shut down (to get a new global) */
   private boolean isShutdown;
   /** used to break the loop in doPublish when shutting down */
   private boolean forceShutdown;
   
   /** only used as a default login name and logging */
   private String name;
   
   public Publisher(Global global, String name, Properties properties) throws XmlBlasterException {
      ME += "-" + name;
      this.name = name;
      this.isShutdown = false;
      this.global = global.getClone(Property.propsToArgs(properties));
      this.log = this.global.getLog("filesystem");
      if (this.log.CALL) 
         this.log.call(ME, "constructor");
      // retreive all necessary properties:
      String tmp = null;
      tmp = this.global.getProperty().get("publishKey", (String)null);
      String topicName =  this.global.getProperty().get("topicName", (String)null);
      if (tmp != null) {
         // this.publishKey = new PublishKey(this.global, new MsgKeyData(this.global, tmp));
         this.publishKey = tmp;
         if (topicName != null)
            this.log.warn(ME, "constructor: since 'publishKey' is defined, 'topicName' will be ignored");
      }
      else {
         if (topicName == null)
            throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "at least one of the properties 'topicName' or 'publishKey' must be defined");
         this.publishKey = (new PublishKey(this.global, topicName)).toXml(); 
      }
      
      this.publishQos = this.global.getProperty().get("publishQos", "<qos/>");

      tmp  = this.global.getProperty().get("connectQos", (String)null);
      if (tmp != null) {
         ConnectQosData data = this.global.getConnectQosFactory().readObject(tmp);
         this.connectQos = new ConnectQos(this.global, data);
      }
      else {
         String userId = this.global.getProperty().get("loginName", "_" + this.name);
         String password = this.global.getProperty().get("password", (String)null);
         this.connectQos = new ConnectQos(this.global, userId, password);
         this.connectQos.setMaxSessions(100);
         Address address = this.connectQos.getAddress();
         this.global.addObjectEntry("ServerNodeScope", global.getObjectEntry("ServerNodeScope"));
         address.setType("LOCAL");
         address.setPingInterval(0L);
         address.setCollectTime(0L);
         this.connectQos.getClientQueueProperty().setType("RAM");
         this.connectQos.getClientQueueProperty().setVersion("1.0");
      }

      String fileFilter =  this.global.getProperty().get("fileFilter", (String)null);
      String directoryName = this.global.getProperty().get("directoryName", (String)null);
      if (directoryName == null)
         throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, ME, "constructor: 'directoryName' is mandatory");
      
      this.maximumFileSize = this.global.getProperty().get("maximumFileSize", -1L);
      long delaySinceLastFileChange = this.global.getProperty().get("delaySinceLastFileChange", 10000L);
      long warnOnEmptyFileDelay = this.global.getProperty().get("warnOnEmptyFileDelay", 60000L);
      this.pollInterval = this.global.getProperty().get("pollInterval", 2000L);

      String sent =  this.global.getProperty().get("sent", (String)null);
      String discarded =  this.global.getProperty().get("discarded", (String)null);
      String lockExtention =  this.global.getProperty().get("lockExtention", (String)null);
      
      this.directoryManager = new DirectoryManager(this.global, name, directoryName, maximumFileSize, delaySinceLastFileChange, warnOnEmptyFileDelay, fileFilter, sent, discarded, lockExtention);
      
   }
   
   /**
    * Connects to the xmlBlaster.
    * 
    * @throws XmlBlasterException
    */
   public synchronized void init() throws XmlBlasterException {
      if (this.log.CALL) 
         this.log.call(ME, "init");
      if (this.isShutdown) { // on a second init
         this.global = this.global.getClone(null);
      }
      this.isShutdown = false;
      this.forceShutdown = false;
      this.access = this.global.getXmlBlasterAccess();
      // no callback listener (we are not subscribing and don't want ptp)
      this.access.connect(this.connectQos, null);
      this.timeoutHandle = timeout.addTimeoutListener(this, this.pollInterval, null);
   }
   
   /**
    * If an exception occurs it means it could not publish the entry
    * @throws XmlBlasterException
    */
   public synchronized void shutdown() throws XmlBlasterException {
      if (this.log.CALL) 
         this.log.call(ME, "shutdown");
      timeout.removeTimeoutListener(this.timeoutHandle);
      this.forceShutdown = true; // in case doPublish is looping due to an exception
      synchronized (this) {
         this.isShutdown = false;
         this.access.disconnect(new DisconnectQos(this.global));
         this.global.shutdown();
      }
   }
   
   public synchronized void publish() {
      while (true) {
         try {
            doPublish();
            break;
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "publish: exception " + ex.getMessage());
            try {
               Thread.sleep(100L);
            }
            catch  (Exception e) {}      
         }
         if (this.forceShutdown)
            break;
      }
   }
   
   private void doPublish() throws XmlBlasterException {
      if (this.log.CALL) 
         this.log.call(ME, "doPublish");
      Set entries = this.directoryManager.getEntries();
      if (entries == null || entries.size() < 1)
         return;
      FileInfo[] infos = (FileInfo[])entries.toArray(new FileInfo[entries.size()]);
      for (int i=0; i < infos.length; i++) {
         if (this.maximumFileSize <= 0L || infos[i].getSize() <= this.maximumFileSize) {
            if (infos[i].getSize() > Integer.MAX_VALUE) {
               this.log.error(ME , "doPublish: sizes bigger than '" + Integer.MAX_VALUE + "' are currently not implemented");
            }
            else {
               byte[] content = this.directoryManager.getContent(infos[i]);
               if (content != null) {
                  MsgUnit msgUnit = new MsgUnit(this.publishKey, content, this.publishQos);
                  msgUnit.getQosData().addClientProperty("_fileName", infos[i].getRelativeName());
                  msgUnit.getQosData().addClientProperty("_fileDate", infos[i].getTimestamp());
                  this.access.publish(msgUnit);
               }
            }

            while (true) { // must repeat until it works or until shut down
               try {
                  boolean success = true;
                  this.directoryManager.deleteOrMoveEntry(infos[i].getName(), success);
                  break;
               }
               catch (XmlBlasterException ex) {
                  this.log.error(ME, ex.getMessage());
                  try {
                     Thread.sleep(this.pollInterval);
                  }
                  catch (Exception e){}
               }
               if (this.forceShutdown)
                  break;
            }
         }
         else { // delete or move to 'discarded'
            this.log.warn(ME, "doPublish: the file '" + infos[i].getName() + "' is too long (" + infos[i].getSize() + "'): I will remove it without publishing");
            boolean success = false;
            try {
               this.directoryManager.deleteOrMoveEntry(infos[i].getName(), success);
            }
            catch (XmlBlasterException ex) {
               this.log.warn(ME, "doPublish: could not handle file '" + infos[i].getName() + "' which was too big: check file and directories permissions and fix it manually: I will continue working anyway. " + ex.getMessage());
            }
         }
      }
   }
   
   /**
    * @see org.xmlBlaster.util.I_Timeout#timeout(java.lang.Object)
    */
   public void timeout(Object userData) {
      try {
         if (this.log.CALL) 
            this.log.call(ME, "timeout");
         publish();
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         this.log.error(ME, "timeout: " + ex.getMessage());
      }
      finally {
         this.timeoutHandle = timeout.addTimeoutListener(this, this.pollInterval, null);
      }
   }
      
}

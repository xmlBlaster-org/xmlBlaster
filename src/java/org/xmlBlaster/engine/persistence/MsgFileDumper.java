/*------------------------------------------------------------------------------
Name:      MsgFileDumper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.Timestamp;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.MsgUnitWrapper;

import java.io.File;
import java.util.Enumeration;


/**
 * A very simple, file based dump of a message. 
 * <br />
 * We store messages on the hard disk, one message to one file (plus a key and a qos file).
 * <br />
 * All methods are marked final, in hope to have some performance gain (could be changed to allow a customized driver)
 * <br />
 * Invoke (for testing only):<br />
 * <code>
 *    jaco org.xmlBlaster.engine.persistence.MsgFileDumper
 * </code>
 * @author xmlBlaster@marcelruff.info
 */
public final class MsgFileDumper
{
   private static final String ME = "MsgFileDumper";
   private Global glob = null;
   private static Logger log = Logger.getLogger(MsgFileDumper.class.getName());
   private String path = null;
   private final String XMLKEY_TOKEN = "-XmlKey.xml";
   private final String XMLQOS_TOKEN = "-XmlQos.xml";

   /**
    * Initializes an instance, creates and checks harddisk path
    * <p />
    * @param ServerScope Global object holding logging and property informations
    */
   public void init(org.xmlBlaster.util.Global glob) throws XmlBlasterException {
      init(glob, null);
   }

   /**
    * Initializes an instance, creates and checks harddisk path
    * <p />
    * @param glob Global object holding logging and property informations
    * @param path_ The path were to dump or null/empty
    */
   public void init(org.xmlBlaster.util.Global glob, String path_) throws XmlBlasterException {
      this.glob = glob;

      if (log.isLoggable(Level.FINER)) log.finer("Entering init()");

      if (path_ != null && path_.length() > 0) {
         this.path = path_;
      }
      else {
         String defaultPath = System.getProperty("user.home") + System.getProperty("file.separator") + "tmp";
         this.path = glob.getProperty().get("Persistence.Path", defaultPath);
         if (this.path == null) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "xmlBlaster will run memory based only, no persistence path is avalailable, please specify 'Persistence.Path' in xmlBlaster.properties");
         }
         this.path = this.path + System.getProperty("file.separator") + glob.getStrippedId();
      }

      File pp = new File(this.path);
      if (!pp.exists()) {
         log.info("Creating new directory " + this.path + " for persistence of messages");
         pp.mkdirs();
      }
      if (!pp.isDirectory()) {
         log.severe(this.path + " is no directory, please specify another 'Persistence.Path' in xmlBlaster.properties");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, this.path + " is no directory, please specify another 'Persistence.Path' in xmlBlaster.properties");
      }
      if (!pp.canWrite()) {
         log.severe("Sorry, no access permissions to " + this.path + ", please specify another 'Persistence.Path' in xmlBlaster.properties");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Sorry, no access permissions to " + this.path + ", please specify another 'Persistence.Path' in xmlBlaster.properties");
      }
   }

   /**
    * Closes the instance of the filedriver plugin
    * <p />
    */
   public void shutdown() throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("Not neccessary!");
   }

   /**
    * @return .e.g "HelloWorld-2002-02-10 10:52:40.879456789"
    */
   public String createFileName(String oid, long timestamp) {
      //return oid + "-" + timestamp;
      Timestamp ts = new Timestamp(timestamp);
      return Global.getStrippedString(oid + "-" + ts.toString());
   }

   public String getPersistencePath() {
      return this.path;
   }

   public String getPersistenceFileName(String fileName) {
      return this.path + System.getProperty("file.separator") + fileName;
   }

   /**
    * Allows a message to be stored.
    * <p />
    * It only stores the xmlKey, content and qos.
    * The other store() method is called for following messages, to store only message-content.
    * @param msgUnitWrapper The container with all necessary message info.
    * @return The file name of the message
    */
   public String store(MsgUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      return store(msgUnitWrapper, false);
   }

   private String store(MsgUnitWrapper msgUnitWrapper, boolean updateOnly) throws XmlBlasterException {

      String fileName = createFileName(msgUnitWrapper.getKeyOid(), msgUnitWrapper.getUniqueId());

      if (!updateOnly) {
         FileLocator.writeFile(this.path, fileName + this.XMLKEY_TOKEN, msgUnitWrapper.getMsgKeyData().toXml().getBytes());
      }
      FileLocator.writeFile(this.path, fileName, msgUnitWrapper.getMsgUnit().getContent());
      FileLocator.writeFile(this.path, fileName + this.XMLQOS_TOKEN, msgUnitWrapper.getMsgQosData().toXml().getBytes());
      if (log.isLoggable(Level.FINE)) log.fine("Successfully stored " + fileName);
      return getPersistenceFileName(fileName);
   }

   /**
    * Allows a stored message content to be updated.
    * <p />
    * It only stores the content, so the other store() method needs to be called first if this message is new.
    * @param xmlKey  To identify the message
    * @param content The data to store
    * @param qos The quality of service, may contain another publisher name
    * @return The file name of the message
    */
   public String update(MsgUnitWrapper msgUnitWrapper)throws XmlBlasterException {
      return store(msgUnitWrapper, true);
   }

   /**
    * Allows to fetch one message by oid and timestamp from the persistence.
    * <p />
    * @param   oid   The message oid (key oid="...")
    * @return the MsgUnit, which is persistent.
    */
   public MsgUnit fetch(String oid, long timestamp) throws XmlBlasterException {
      MsgUnit msgUnit = null;
      String fileName = createFileName(oid, timestamp);

      String xmlKey_literal = FileLocator.readAsciiFile(this.path, fileName + this.XMLKEY_TOKEN);
      byte[] content = FileLocator.readFile(this.path, fileName);
      String xmlQos_literal = FileLocator.readAsciiFile(this.path, fileName + this.XMLQOS_TOKEN);

      msgUnit = new MsgUnit(glob, xmlKey_literal, content, xmlQos_literal);

      if (log.isLoggable(Level.FINE)) log.fine("Successfully fetched message " + fileName);
      if (log.isLoggable(Level.FINEST)) log.finest("Successfully fetched message\n" + msgUnit.toXml());

      return msgUnit;
   }

   /**
    * Fetches all oid's of the messages from the persistence.
    * <p />
    * It is a helper method to invoke 'fetch(String oid)'.
    * @return a Enumeration of oids of all persistent MsgUnits. The oid is a String-Type.
    */
   public Enumeration fetchAllOids() throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Sorry fetchAllOids() is not implemented");
      /*
      Vector oidContainer = new Vector();

      File pp = new File(this.path);
      String[] fileArr = pp.list(new XmlKeyFilter());
      for (int ii=0; ii<fileArr.length; ii++) {
         // Strip the this.XMLKEY_TOKEN ...
         String oid = fileArr[ii].substring(0, fileArr[ii].length() - this.XMLKEY_TOKEN.length());
         // and load the messages in a vector ...
         oidContainer.addElement(oid);
      }
      log.info(ME, "Successfully got " + oidContainer.size() + " stored message-oids from " + this.path);

      return oidContainer.elements();
      */
   }

   /**
    * Allows a stored message to be deleted.
    * <p />
    * @param xmlKey  To identify the message
    */
   public void erase(XmlKey xmlKey) throws XmlBlasterException {
      throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "Sorry erase() is not implemented");
      /*
      String oid = xmlKey.getKeyOid(); // The file name
      FileUtil.deleteFile(this.path, oid + this.XMLKEY_TOKEN);
      FileUtil.deleteFile(this.path, oid);
      FileUtil.deleteFile(this.path, oid + this.XMLQOS_TOKEN);
      */
   }

   /** Invoke:  java org.xmlBlaster.engine.persistence.MsgFileDumper */
   public static void main(String args[]) {
      /*
      try {
         org.xmlBlaster.engine.Global glob = new org.xmlBlaster.engine.Global();
         org.xmlBlaster.client.key.PublishKey key = 
                 new org.xmlBlaster.client.key.PublishKey(glob, "someKey");
         org.xmlBlaster.engine.qos.PublishQosServer publishQosServer = 
                 new org.xmlBlaster.engine.qos.PublishQosServer(glob, "<qos><persistent/></qos>");
         String content = "Some content";
         org.xmlBlaster.util.MsgUnit msgUnit  = 
                 new org.xmlBlaster.util.MsgUnit(key.toXml(), content, publishQosServer.toXml());
         org.xmlBlaster.util.queue.StorageId storageId =
                 new org.xmlBlaster.util.queue.StorageId("SomeStorage", "anEntry");
         MsgUnitWrapper msgWrapper = new MsgUnitWrapper(glob, msgUnit, storageId);
         MsgFileDumper driver = new MsgFileDumper();
         driver.init(glob);
         String fileName = driver.store(msgWrapper);
         System.out.println("Dumped message to " + fileName);
      } catch (Exception e) {
         System.out.println(e.toString());
         e.printStackTrace();
      }
      */
   }
}


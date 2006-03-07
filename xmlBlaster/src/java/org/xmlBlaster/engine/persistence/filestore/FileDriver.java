/*------------------------------------------------------------------------------
Name:      FileDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a very simple, file based, persistence manager
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.filestore;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.util.XmlBlasterException;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.MsgUnitWrapper;

import org.xmlBlaster.engine.persistence.I_PersistenceDriver;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Enumeration;
import java.util.Vector;


/**
 * A very simple, file based, persistence manager.
 * <br />
 * This driver stores messages on the hard disk, one message to one file (plus a key and a qos file).
 * <br />
 * All methods are marked final, in hope to have some performance gain (could be changed to allow a customized driver)
 * <br />
 * CAUTION: This driver is not suitable for production purposes.
 * If you want to use this driver for more than some hundred different messages
 * we recommend to use it with the ReiserFS.<br />
 * Reiserfs is a file system using a plug-in based object oriented variant on classical balanced tree algorithms.<br />
 * See ftp://ftp.suse.com:/pub/suse/i386/update/6.3/reiserfs/<br />
 * and http://devlinux.com/projects/reiserfs/content_table.html
 * for further informations.
 * <br />
 * TODO: Extend interface to support caching!<br />
 * TODO: Is the File stuff thread save or do we need to add some synchronize?
 * <br />
 * Invoke (for testing only):<br />
 * <code>
 *    jaco org.xmlBlaster.engine.persistence.FileDriver
 * </code>
 */
public class FileDriver implements I_PersistenceDriver
{
   private static final String ME = "FileDriver";
   private Global glob = null;
   private static Logger log = Logger.getLogger(FileDriver.class.getName());
   private String path = null;
   private final String XMLKEY_TOKEN = "-XmlKey.xml";
   private final String XMLQOS_TOKEN = "-XmlQos.xml";


   /**
    * Constructs the FileDriver object (reflection constructor).
    */
   public FileDriver() throws XmlBlasterException
   {
   }


     /**
    * initialises an instance of the filedriver plugin
    * <p />
    * @param Global Global object holding logging and property informations
    * @param param  aditional parameter for the filedriver plugin
    */
   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public final void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = glob;

      if (log.isLoggable(Level.FINER)) log.finer("Entering init()");
      
      String defaultPath = System.getProperty("user.home") + System.getProperty("file.separator") + "tmp";
      path = glob.getProperty().get("Persistence.Path", defaultPath);
      if (path == null) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "xmlBlaster will run memory based only, no persistence path is avalailable, please specify 'Persistence.Path' in xmlBlaster.properties");
      }
      File pp = new File(path);
      if (!pp.exists()) {
         log.info("Creating new directory " + path + " for persitence of messages");
         pp.mkdirs();
      }
      if (!pp.isDirectory()) {
         log.severe(path + " is no directory, please specify another 'Persistence.Path' in xmlBlaster.properties");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, path + " is no directory, please specify another 'Persistence.Path' in xmlBlaster.properties");
      }
      if (!pp.canWrite()) {
         log.severe("Sorry, no access permissions to " + path + ", please specify another 'Persistence.Path' in xmlBlaster.properties");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Sorry, no access permissions to " + path + ", please specify another 'Persistence.Path' in xmlBlaster.properties");
      }
   }


     /**
    * Closes the instance of the filedriver plugin
    * <p />
    */
   public final void shutdown() throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("Not neccessary!");
   }


   /**
    * Allows a message to be stored.
    * <p />
    * It only stores the xmlKey, content and qos.
    * The other store() method is called for following messages, to store only message-content.
    * @param messageWrapper The container with all necessary message info.
    */
   public final void store(MsgUnitWrapper messageWrapper) throws XmlBlasterException
   {
      MsgKeyData xmlKey = messageWrapper.getMsgKeyData();
      MsgQosData qos = messageWrapper.getMsgQosData();
      //String mime = messageWrapper.getContentMime();
      byte[] content = messageWrapper.getMsgUnit().getContent();

      String oid = xmlKey.getOid(); // The file name

      FileLocator.writeFile(path, oid + XMLKEY_TOKEN, xmlKey.toXml().getBytes());
      FileLocator.writeFile(path, oid, content);
      FileLocator.writeFile(path, oid + XMLQOS_TOKEN, qos.toXml().getBytes());

      if (log.isLoggable(Level.FINE)) log.fine("Successfully stored " + oid);
   }


   /**
    * Allows a stored message content to be updated.
    * <p />
    * It only stores the content, so the other store() method needs to be called first if this message is new.
    * @param xmlKey  To identify the message
    * @param content The data to store
    * @param qos The quality of service, may contain another publisher name
    */
   public final void update(MsgUnitWrapper messageWrapper)throws XmlBlasterException
   {
      String oid = messageWrapper.getKeyOid();
      FileLocator.writeFile(path, oid, messageWrapper.getMsgUnit().getContent());
      // Store the sender as well:
      FileLocator.writeFile(path, oid + XMLQOS_TOKEN, messageWrapper.getMsgQosData().toXml().getBytes());
      if (log.isLoggable(Level.FINE)) log.fine("Successfully updated store " + messageWrapper.getKeyOid());
   }


   /**
    * Allows to fetch one message by oid from the persistence.
    * <p />
    * @param   oid   The message oid (key oid="...")
    * @return the MsgUnit, which is persistent.
    */
   public MsgUnit fetch(String oid) throws XmlBlasterException
   {
      MsgUnit msgUnit = null;
      String xmlKey_literal = FileLocator.readAsciiFile(path, oid + XMLKEY_TOKEN);

      byte[] content = FileLocator.readFile(path, oid);

      String xmlQos_literal = FileLocator.readAsciiFile(path, oid + XMLQOS_TOKEN);

      msgUnit = new MsgUnit(glob, xmlKey_literal, content, xmlQos_literal);

      if (log.isLoggable(Level.FINE)) log.fine("Successfully fetched message " + oid);
      if (log.isLoggable(Level.FINEST)) log.finest("Successfully fetched message\n" + msgUnit.toXml());
      return msgUnit;
   }


   /**
    * Fetches all oid's of the messages from the persistence.
    * <p />
    * It is a helper method to invoke 'fetch(String oid)'.
    * @return a Enumeration of oids of all persistent MsgUnits. The oid is a String-Type.
    */
    public Enumeration fetchAllOids() throws XmlBlasterException
    {
       Vector oidContainer = new Vector();

       File pp = new File(path);
       String[] fileArr = pp.list(new XmlKeyFilter());
       for (int ii=0; ii<fileArr.length; ii++) {
          // Strip the XMLKEY_TOKEN ...
          String oid = fileArr[ii].substring(0, fileArr[ii].length() - XMLKEY_TOKEN.length());
          // and load the messages in a vector ...
          oidContainer.addElement(oid);
       }
       log.info("Successfully got " + oidContainer.size() + " stored message-oids from " + path);

       return oidContainer.elements();
    }



   /**
    * Allows a stored message to be deleted.
    * <p />
    * @param xmlKey  To identify the message
    */
   public void erase(XmlKey xmlKey) throws XmlBlasterException
   {
      String oid = xmlKey.getOid(); // The file name
      FileLocator.deleteFile(path, oid + XMLKEY_TOKEN);
      FileLocator.deleteFile(path, oid);
      FileLocator.deleteFile(path, oid + XMLQOS_TOKEN);
   }


     /**
    * gives the type of the driver
    * <p />
    * @return the type of the driver
    */
   public String getType() {
      return null; // TODO !!!
   }


     /**
    * gives the version of the driver
    * <p />
    * @return the version of the driver
    */
   public String getVersion() {
      return null; // TODO !!!
   }


     /**
    * gives the name of the driver
    * <p />
    * @return the name of the driver
    */
   public String getName() {
      return ME;
   }


   /**
    * Filter only the xy-XmlKey.xml files.
    */
   private class XmlKeyFilter implements FilenameFilter
   {
      public boolean accept(File dir, String name)
      {
         if (name.endsWith(XMLKEY_TOKEN))
            return true;
         return false;
      }

   }

   /** Invoke:  jaco org.xmlBlaster.engine.persistence.FileDriver */
   public static void main(String args[])
   {
      try {
         /*FileDriver driver = */new FileDriver();
      } catch (Exception e) {
         System.out.println(e.toString());
         e.printStackTrace();
      }
   }
}


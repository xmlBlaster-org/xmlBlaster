/*------------------------------------------------------------------------------
Name:      FileDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a very simple, file based, persistence manager
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.filestore;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.jutils.io.FileUtil;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQos;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.RequestBroker;

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
   public final void init(org.xmlBlaster.util.Global glob, String[] param) throws XmlBlasterException {
      if (Log.CALL) Log.call(ME, "Entering init()");

      this.glob = glob;
      
      String defaultPath = (String)System.getProperty("user.home") + (String)System.getProperty("file.separator") + "tmp";
      path = glob.getProperty().get("Persistence.Path", defaultPath);
      if (path == null) {
         throw new XmlBlasterException(ME, "xmlBlaster will run memory based only, no persistence path is avalailable, please specify 'Persistence.Path' in xmlBlaster.properties");
      }
      File pp = new File(path);
      if (!pp.exists()) {
         Log.info(ME, "Creating new directory " + path + " for persitence of messages");
         pp.mkdirs();
      }
      if (!pp.isDirectory()) {
         Log.error(ME, path + " is no directory, please specify another 'Persistence.Path' in xmlBlaster.properties");
         throw new XmlBlasterException(ME, path + " is no directory, please specify another 'Persistence.Path' in xmlBlaster.properties");
      }
      if (!pp.canWrite()) {
         Log.error(ME, "Sorry, no access permissions to " + path + ", please specify another 'Persistence.Path' in xmlBlaster.properties");
         throw new XmlBlasterException(ME, "Sorry, no access permissions to " + path + ", please specify another 'Persistence.Path' in xmlBlaster.properties");
      }
   }


     /**
    * Closes the instance of the filedriver plugin
    * <p />
    */
   public final void shutdown() throws XmlBlasterException {
      if (Log.TRACE) Log.trace(ME, "Not neccessary!");
   }


   /**
    * Allows a message to be stored.
    * <p />
    * It only stores the xmlKey, content and qos.
    * The other store() method is called for following messages, to store only message-content.
    * @param messageWrapper The container with all necessary message info.
    */
   public final void store(MessageUnitWrapper messageWrapper) throws XmlBlasterException
   {
      XmlKey xmlKey = messageWrapper.getXmlKey();
      PublishQos qos = messageWrapper.getPublishQos();
      String mime = messageWrapper.getContentMime();
      byte[] content = messageWrapper.getMessageUnit().content;

      String oid = xmlKey.getKeyOid(); // The file name

      try {
         FileUtil.writeFile(path, oid + XMLKEY_TOKEN, xmlKey.literal());
         FileUtil.writeFile(path, oid, content);
         FileUtil.writeFile(path, oid + XMLQOS_TOKEN, qos.toXml());
      } catch (JUtilsException e) {
         throw new XmlBlasterException(e);
      }

      if (Log.TRACE) Log.trace(ME, "Successfully stored " + oid);
   }


   /**
    * Allows a stored message content to be updated.
    * <p />
    * It only stores the content, so the other store() method needs to be called first if this message is new.
    * @param xmlKey  To identify the message
    * @param content The data to store
    * @param qos The quality of service, may contain another publisher name
    */
   public final void update(MessageUnitWrapper messageWrapper)throws XmlBlasterException
   {

      try {
         String oid = messageWrapper.getUniqueKey();
         FileUtil.writeFile(path, oid, messageWrapper.getMessageUnit().getContent());
         // Store the sender as well:
         FileUtil.writeFile(path, oid + XMLQOS_TOKEN, messageWrapper.getPublishQos().toXml());
      } catch (JUtilsException e) {
         throw new XmlBlasterException(e);
      }

      if (Log.TRACE) Log.trace(ME, "Successfully updated store " + messageWrapper.getUniqueKey());
   }


   /**
    * Allows to fetch one message by oid from the persistence.
    * <p />
    * @param   oid   The message oid (key oid="...")
    * @return the MessageUnit, which is persistent.
    */
   public MessageUnit fetch(String oid) throws XmlBlasterException
   {
      MessageUnit msgUnit = null;

      try {
         String xmlKey_literal = FileUtil.readAsciiFile(path, oid + XMLKEY_TOKEN);

         byte[] content = FileUtil.readFile(path, oid);

         String xmlQos_literal = FileUtil.readAsciiFile(path, oid + XMLQOS_TOKEN);

         msgUnit = new MessageUnit(xmlKey_literal, content, xmlQos_literal);

         if (Log.TRACE) Log.trace(ME, "Successfully fetched message " + oid);
         if (Log.DUMP) Log.dump(ME, "Successfully fetched message\n" + msgUnit.toXml());
      } catch (JUtilsException e) {
         throw new XmlBlasterException(e);
      }

      return msgUnit;
   }


   /**
    * Fetches all oid's of the messages from the persistence.
    * <p />
    * It is a helper method to invoke 'fetch(String oid)'.
    * @return a Enumeration of oids of all persistent MessageUnits. The oid is a String-Type.
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
       Log.info(ME, "Successfully got " + oidContainer.size() + " stored message-oids from " + path);

       return oidContainer.elements();
    }



   /**
    * Allows a stored message to be deleted.
    * <p />
    * @param xmlKey  To identify the message
    */
   public void erase(XmlKey xmlKey) throws XmlBlasterException
   {
      String oid = xmlKey.getKeyOid(); // The file name
      FileUtil.deleteFile(path, oid + XMLKEY_TOKEN);
      FileUtil.deleteFile(path, oid);
      FileUtil.deleteFile(path, oid + XMLQOS_TOKEN);
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
         FileDriver driver = new FileDriver();
      } catch (Exception e) {
         Log.error(ME, e.toString());
         e.printStackTrace();
      }
      Log.exit(FileDriver.ME, "No test implemented");
   }
}


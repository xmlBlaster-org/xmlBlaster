/*------------------------------------------------------------------------------
Name:      FileDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a very simple, file based, persistence manager
Version:   $Id: FileDriver.java,v 1.2 2000/01/21 08:19:04 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.engine.*;
import org.xmlBlaster.util.*;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.MessageUnit;

import java.io.File;
import java.io.FilenameFilter;


/**
 * A very simple, file based, persistence manager.
 * <br />
 * This driver stores messages on the hard disk, one message to one file (plus a key and a qos file).
 * <br />
 * All methods are marked final, in hope to have some performance gain (could be changed to allow a customized driver)
 * <br />
 * CAUTION: This driver is not suitable for production purposes<br />
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
   private String path = null;
   private final String XMLKEY_TOKEN = "-XmlKey.xml";
   private final String XMLQOS_TOKEN = "-XmlQos.xml";


   /**
    * Constructs the FileDriver object (reflection constructor).
    */
   public FileDriver() throws XmlBlasterException
   {
      path = Property.getProperty("Persistence.Path", (String)null);
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
    * Allows a message to be stored.
    * <p />
    * It only stores the xmlKey, content and qos.
    * The other store() method is called for following messages, to store only message-content.
    * @param messageWrapper The container with all necessary message info.
    */
   public final void store(MessageUnitWrapper messageWrapper) throws XmlBlasterException
   {
      XmlKey xmlKey = messageWrapper.getXmlKey();
      PublishQoS qos = messageWrapper.getPublishQoS();
      String mime = messageWrapper.getContentMime();
      byte[] content = messageWrapper.getMessageUnit().content;

      String oid = xmlKey.getKeyOid(); // The file name

      FileUtil.writeFile(path, oid + XMLKEY_TOKEN, xmlKey.toXml());
      FileUtil.writeFile(path, oid, content);
      FileUtil.writeFile(path, oid + XMLQOS_TOKEN, qos.toXml());

      if (Log.TRACE) Log.trace(ME, "Successfully stored " + oid);
   }


   /**
    * Allows a stored message content to be updated.
    * <p />
    * It only stores the content, so the other store() method needs to be called first if this message is new.
    * @param xmlKey  To identify the message
    * @param content The data to store
    */
   public final void store(XmlKey xmlKey, byte[] content) throws XmlBlasterException
   {
      String oid = xmlKey.getKeyOid(); // The file name

      FileUtil.writeFile(path, oid, content);

      if (Log.TRACE) Log.trace(ME, "Successfully updated store " + oid);
   }


   /**
    * Gets all messages from the store. 
    * <p />
    * @param clientInfo    Needed to publish
    * @param requestBroker Needed to publish
    */
   public final void recover(ClientInfo clientInfo, RequestBroker requestBroker) throws XmlBlasterException
   {
      File pp = new File(path);
      String[] fileArr = pp.list(new XmlKeyFilter());
      for (int ii=0; ii<fileArr.length; ii++) {
         // Strip the XMLKEY_TOKEN ...
         String oid = fileArr[ii].substring(0, fileArr[ii].length() - XMLKEY_TOKEN.length());
         // and load the messages ...
         recover(oid, clientInfo, requestBroker);
      }
      Log.info(ME, "Successfully got all stored messages from " + path);
   }


   /**
    * Allows a message to be fetched from the store.
    * <p />
    * @param oid           The message name (key oid="...")
    * @param clientInfo    Needed to publish
    * @param requestBroker Needed to publish
    */
   public final void recover(String oid, ClientInfo clientInfo, RequestBroker requestBroker) throws XmlBlasterException
   {
      String xmlKey_literal = FileUtil.readAsciiFile(path, oid + XMLKEY_TOKEN);
      
      byte[] content = FileUtil.readFile(path, oid);

      MessageUnit messageUnit = new MessageUnit(xmlKey_literal, content);

      String xmlQos_literal = FileUtil.readAsciiFile(path, oid + XMLQOS_TOKEN);
      PublishQoS publishQos = new PublishQoS(xmlQos_literal, true); // you need true here!

      requestBroker.publish(clientInfo, messageUnit, publishQos);

      if (Log.TRACE) Log.trace(ME, "Successfully recovered message " + oid);
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


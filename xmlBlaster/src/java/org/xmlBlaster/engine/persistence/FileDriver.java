/*------------------------------------------------------------------------------
Name:      FileDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a very simple, file based, persistence manager
Version:   $Id: FileDriver.java,v 1.1 2000/01/20 19:34:33 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.engine.*;
import org.xmlBlaster.util.*;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.MessageUnit;
import java.io.File;


/**
 * A very simple, file based, persistence manager. 
 * <br />
 * This driver stores messages on the hard disk, one message to one file (plus a key and a qos file). 
 * <p />
 * CAUTION: This driver is not suitable for production purposes
 * <p />
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
   }


   /**
    * This method allows a message to be stored.
    * <p />
    * The protocol for storing is implemented in the derived class
    */
   public void store(MessageUnitWrapper messageWrapper) throws XmlBlasterException
   {
      XmlKey xmlKey = messageWrapper.getXmlKey();
      PublishQoS qos = messageWrapper.getPublishQoS();
      String mime = messageWrapper.getContentMime();
      byte[] content = messageWrapper.getMessageUnit().content;

      String contentName = xmlKey.getKeyOid(); // The file name

      if (FileUtil.writeFile(path, contentName + XMLKEY_TOKEN, xmlKey.toXml()))
         throw new XmlBlasterException(ME, "Can't write " + contentName + XMLKEY_TOKEN + " file in directory " + path);

      if (FileUtil.writeFile(path, contentName, content))
         throw new XmlBlasterException(ME, "Can't write " + contentName + " file in directory " + path);

      if (FileUtil.writeFile(path, contentName + XMLQOS_TOKEN, qos.toXml()))
         throw new XmlBlasterException(ME, "Can't write " + contentName + XMLQOS_TOKEN + " file in directory " + path);

      if (Log.TRACE) Log.trace(ME, "Successfully stored " + contentName);
      Log.info(ME, "Successfully stored " + contentName); // !!! remove again
   }


   /**
    * This method allows a stored message content to be updated.
    * <p />
    * The protocol for storing is implemented in the derived class
    */
   public void store(XmlKey xmlKey, byte[] content) throws XmlBlasterException
   {
      String contentName = xmlKey.getKeyOid(); // The file name

      if (Log.TRACE) Log.trace(ME, "Successfully updated store " + contentName);
      Log.error(ME, "Successfully updated store " + contentName); // !!! remove again
   }


   /**
    * This method allows a message to be stored.
    * <p />
    * The protocol for storing is implemented in the derived class
    */
   public void recover(RequestBroker requestBroker) throws XmlBlasterException
   {
      Log.error(ME, "Successfully got all stored messages from " + path);
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


/*------------------------------------------------------------------------------
Name:      XMLDBPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a XMLDB Plugin
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.xmldb;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;
import org.xmlBlaster.engine.persistence.xmldb.xindice.XindiceProxy;

//import org.xmldb.api.base.XMLDBException;
//import org.apache.xindice.util.XindiceException;

import java.util.Enumeration;
import java.util.Vector;

/**
 * A simple, XML:DB based, persistence plugin.
 * <br />
 * This plugin stores messages in a <a href="http://www.xmldb.org">XMLDB</a> database,
 * one message to one entry (plus a key and a qos entry).
 * <br />
 * All methods are marked final, in hope to have some performance gain
 * (could be changed to allow a customized driver)
 * <br />
 * The Testsuite has to be extended and there haven't been performance meassurements yet.
 * <br />
 * TODO: Extend interface to support caching!<br />
 * TODO: Is the Xindice stuff thread save or do we need to add some synchronize?
 * <br />
 * Invoke (for testing only):<br />
 * <code>
 *    java -cp $CLASSPATH:. org.xmlBlaster.engine.persistence.xmldb.XMLDBPlugin
 * </code>
 */
public class XMLDBPlugin implements I_PersistenceDriver
{
   private static final String ME = "XMLDBPlugin";
   private Global glob = null;
   private LogChannel log = null;
   private String driverPath = null;  // path where persistent messages will be stored
   private String colName = null;     // name of storage instance
   private XindiceProxy db = null;    // instance of db proxy Xindice, dbXML, eXist, ...

   private final String XMLKEY_TOKEN = "-XmlKey.xml";
   private final String XMLQOS_TOKEN = "-XmlQos.xml";
   private final String XMLCON_TOKEN = "-XmlCon.xml";
   private final String BEGCON_TOKEN = "<content><![CDATA[";
   private final String ENDCON_TOKEN = "]]></content>";
   private final String XML_PI_TOKEN = "<?xml version=\"1.0\"?>";

   /**
    * Constructs and opens the XMLDBPlugin object (reflection constructor).
    */
   public XMLDBPlugin() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "XMLDBPlugin (reflection constructor)");
   }

   /**
    * initialises an instance of the XMLDB persistence plugin
    * <p />
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = glob;
      this.log = glob.getLog("persistence");
      if (log.CALL) log.call(ME, "Entering init()");

      driverPath = glob.getProperty().get("Persistence.Path", "xmldb:xindice:///db");
      colName = glob.getProperty().get("Persistence.Collection", "xmlBlaster");

      log.info(ME, "using collectionPath '" + driverPath + "/" + colName + "'");

      // fixed to xindice right now, needs to be opend to eXist or Tamino or ... as well. TODO!!!
      db = new XindiceProxy(glob, driverPath + "/" + colName);

      // Create Collection manually by using shell-commands
      // xindiceadmin ac -c /db -n xmlBlaster
      // check with: xindiceadmin lc -c /db

      // Open&Set Collection

      // db.openCollection(driverPath + "/" + colName); // will be done with constructor now

   }


   /**
    * Closes the Xindice-Collection
    */
   public final void shutdown() throws XmlBlasterException {

      if (log.CALL) log.call(ME, "shutdown");
      db.closeCollection();
      log.info(ME, "succesfully closed collection '" + driverPath + "/" + colName);
   }


   /**
    * Allows a message to be stored.
    * <p />
    * It only stores the xml-form of the message, using KeyOid as key.
    * @param messageWrapper The container with all necessary message info.
    */
   public final void store(MessageUnitWrapper messageWrapper) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "store");

      /*
      XmlKey xmlKey = messageWrapper.getXmlKey();
      PublishQosServer qos = messageWrapper.getPublishQos();
      String mime = messageWrapper.getContentMime();
      byte[] content = messageWrapper.getMessageUnit().getContent();
      */

      // TODO is getMessageUnit().key more performant, but is it complete ???
      // qos as well

      String oid = messageWrapper.getXmlKey().getKeyOid();
      String key = messageWrapper.getXmlKey().toXml();

      db.addDocument(key, oid + XMLKEY_TOKEN);
      db.addDocument(BEGCON_TOKEN + new String(messageWrapper.getMessageUnit().getContent()) + ENDCON_TOKEN, oid + XMLCON_TOKEN);
      db.addDocument(messageWrapper.getPublishQos().toXml(), oid + XMLQOS_TOKEN);

      // log.trace(ME, "<content><![CDATA["+ new String(messageWrapper.getMessageUnit().getContent())+"]]></content>");
      //        log.trace(ME, new String(messageWrapper.getMessageUnit().getContent()));

      if (log.TRACE) log.trace(ME, "Successfully stored; oid= " + oid + "key= " + key );
   }


   /**
    * Allows a already stored message content to be updated.
    * <p />
    * It only stores the content, so the store() method needs to be called
    * first if this message is new.
    * @param messageWrapper The container with all necessary message info.
    */
   public final void update(MessageUnitWrapper messageWrapper) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "update");

      String oid = messageWrapper.getXmlKey().getKeyOid();
      db.addDocument(BEGCON_TOKEN + new String(messageWrapper.getMessageUnit().getContent()) + ENDCON_TOKEN, oid + XMLCON_TOKEN);
      // Store the sender as well:
      db.addDocument(messageWrapper.getPublishQos().toXml(), oid + XMLQOS_TOKEN);

      if (log.TRACE) log.trace(ME, "Successfully updated store " + messageWrapper.getUniqueKey());
   }


   /**
    * Allows to fetch one message by oid from the persistence Layer.
    * <p />
    * @param   oid   The message oid (key oid="...")
    * @return The MessageUnit, which is persistent.
    */
   public final MessageUnit fetch(String oid) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "fetch");
      MessageUnit msgUnit = null;

      String xmlKey_literal = db.retrieveDocument(oid + XMLKEY_TOKEN);
      String xmlQos_literal = db.retrieveDocument(oid + XMLQOS_TOKEN);
      //byte[] content = db.retrieveDocument(oid + XMLCON_TOKEN).getBytes();
      String con = db.retrieveDocument(oid + XMLCON_TOKEN);

      /*
      // if (log.TRACE) log.trace(ME, " 1 con: '" + con + "'");
      if ( con.startsWith(XML_PI_TOKEN) )
         con = con.substring(XML_PI_TOKEN.length() + 1 , con.length() );

      // if (log.TRACE) log.trace(ME, " 2 con: '" + con + "'");

      if ( con.startsWith(BEGCON_TOKEN) && con.endsWith(ENDCON_TOKEN) )
         con = con.substring(BEGCON_TOKEN.length(), con.length() - ENDCON_TOKEN.length() );

      // if (log.TRACE) log.trace(ME, " 3 con: '" + con + "'");
      if (log.TRACE) log.trace(ME, "con: '" + con + "'");
      */
      //if ( con.startsWith(XML_PI_TOKEN) )
      //   if ( con.startsWith(BEGCON_TOKEN) && con.endsWith(ENDCON_TOKEN) )
      //      con = con.substring(XML_PI_TOKEN.length() + 1 + BEGCON_TOKEN.length(), con.length() - ENDCON_TOKEN.length() );

      // if (log.TRACE) log.trace(ME, " 4 con: '" + con + "'");
      // if (log.TRACE) log.trace(ME, "con: '" + con + "'");

      byte[] content =
         con.substring( XML_PI_TOKEN.length() + 1 + BEGCON_TOKEN.length(),
         con.length() - ENDCON_TOKEN.length() ).getBytes();

      msgUnit = new MessageUnit(xmlKey_literal, content, xmlQos_literal);

      if (log.TRACE) log.trace(ME, "Successfully fetched message " + oid);
      if (log.TRACE) log.trace(ME, "    content '" + content + "'");
      if (log.TRACE) log.trace(ME, "       key '" + xmlKey_literal + "'");
      if (log.TRACE) log.trace(ME, "       qos '" + xmlQos_literal + "'");

      return msgUnit;
   }


   /**
    * Fetches all oid's of the messages from the persistence.
    * <p />
    * It is a helper method to invoke 'fetch(String oid)'.
    * @return a Enumeration of oids of all persistent MessageUnits. The oid is a String-Type.
    */
    public final Enumeration fetchAllOids() throws XmlBlasterException
    {
      if (log.CALL) log.call(ME, "fetchAllOids");

      Vector oidContainer = new Vector();

      String[] arr = db.listDocuments();

      for ( int  ii=0 ; ii < arr.length ; ii++ ) {
         if ( arr[ii].endsWith(XMLKEY_TOKEN) ) {
            // Strip the XMLKEY_TOKEN ...
            String oid = arr[ii].substring(0, arr[ii].length() - XMLKEY_TOKEN.length());
            // and load the messages in a vector ...
            oidContainer.addElement(oid);
         }
      }

      log.info(ME, "Successfully got " + oidContainer.size() + " stored message-oids from " + driverPath + "/" + colName);

      return oidContainer.elements();
    }


   /**
    * Allows a stored message to be deleted.
    * <p />
    * @param xmlKey  To identify the message
    */
   public final void erase(XmlKey xmlKey) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "erase");

      String oid = xmlKey.getKeyOid();

      db.deleteDocument(oid + XMLKEY_TOKEN);
      db.deleteDocument(oid + XMLCON_TOKEN);
      db.deleteDocument(oid + XMLQOS_TOKEN);

      if (log.TRACE) log.trace(ME, "Successfully erased; oid= " + oid);
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
    * Allows to test the XMLDBPlugin
    * <p />
    */
   private static void XMLDBPluginTest() throws Exception
   {
      LogChannel log = Global.instance().getLog(null);
      log.info(ME, "invoking XMLDBPluginTest");

      String test_data =
      "<product product_id=\"7042001\">" +
      "    <description>Smoked Ham</description>" +
      "</product>";

      String test_index = "7042001";

      Global glob = new Global();
      // String colPath = "xmldb:xindice:///db";
      // String colName = "xmlBlaster_test";
      String colPath = glob.getProperty().get("Persistence.Path", "xmldb:xindice:///db");
      String colName = glob.getProperty().get("Persistence.Collection", "xmlBlaster_test");

      try {
         if (log.CALL) log.call(ME, "in try");
         XindiceProxy db = new XindiceProxy(glob, colPath);
         // log.info(ME, "colPath: " + colPath);
         // log.info(ME, "colName: " + colName);

         // open a path, create, list and remove a collection
         // db.openCollection(colPath);
         db.createCollection("to_remove_test_only");
         db.listCollection();
         db.deleteCollection("to_remove_test_only");
         db.closeCollection();

         log.info(ME, "--- new test starts here ---");

         // create collection manual by using:
         // xindiceadmin ac -c /db -n xmlBlaster_test

         // open a existent collection, add a Document, retrieve it, remove it, close collection
         db.openCollection(colPath + "/" + colName);
         db.addDocument(test_data, test_index);
         // retrive by shell:
         // xindice rd -c /db/xmlBlaster_test -n 7042001
         // xindice xpath -c /db/xmlBlaster_test -q /product
         // xindice xpath -c /db/xmlBlaster_test -q /product[@product_id="7042001"]
         // list all documents:
         // xindice ld -c /db/xmlBlaster_test

         log.info(ME, "Found: " + db.retrieveDocument(test_index) + "\n");
         db.deleteDocument(test_index);
         db.closeCollection();

      //Thread.currentThread().sleep(100000);
      } catch (Exception e) {
         log.error(ME, e.toString());
         e.printStackTrace();
      }

      log.info(ME, "end of test");
   }

   // javac -classpath $CLASSPATH:. *.java
   // java org.xmlBlaster.engine.persistence.xmldb.XMLDBPlugin -call true -trace true
   // java -cp $CLASSPATH:. XMLDBPlugin
   // java -cp $CLASSPATH:. org.xmlBlaster.engine.persistence.xmldb.XMLDBPlugin

   /** Invoke:  java -cp $CLASSPATH:. org.xmlBlaster.engine.persistence.xmldb.XMLDBPlugin */
   public static void main(String[]args) throws Exception
   {
      new Global(args); // initialize log level

      XMLDBPluginTest();
      XMLDBPluginTest();
   } // end of public static void main(String[]args) throws Exception

} // end of class

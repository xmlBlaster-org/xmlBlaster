/*------------------------------------------------------------------------------
Name:      XindiceDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a Xindice (former dbXML) Connector
Version:   $Id: XindiceDriver.java,v 1.1 2002/01/13 22:14:57 goetzger Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.xmldb.xindice;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;
import org.xmlBlaster.engine.persistence.xmldb.XMLDBProxy;

import org.xmldb.api.base.XMLDBException;

import java.util.Enumeration;
import java.util.Vector;

/**
 * A simple, XML:DB based, persistence manager.
 * <br />
 * This driver stores messages in the <a href="http://www.dbxml.org">Xindice</a> database, one message to one entry (plus a key and a qos entry).
 * <br />
 * All methods are marked final, in hope to have some performance gain (could be changed to allow a customized driver)
 * <br />
 * CAUTION: This driver may not be suitable for production purposes.
 * The Testsuite has to be extended and there haven't been performance measurements.
 * <br />
 * TODO: Extend interface to support caching!<br />
 * TODO: Is the Xindice stuff thread save or do we need to add some synchronize?
 * <br />
 * Invoke (for testing only):<br />
 * <code>
 *    java -cp $CLASSPATH:. org.xmlBlaster.engine.persistence.xmldb.xindice.XindiceDriver
 * </code>
 */
public class XindiceDriver implements I_PersistenceDriver
{
   private static final String ME = "XindiceDriver";
   private String driverPath = null;
   private String colName = null;
   private XMLDBProxy db = null;

   private final String XMLKEY_TOKEN = "-XmlKey.xml";
   private final String XMLQOS_TOKEN = "-XmlQos.xml";
   private final String XMLCON_TOKEN = "-XmlCon.xml";
   private final String BEGCON_TOKEN = "<content><![CDATA[";
   private final String ENDCON_TOKEN = "]]></content>";
   private final String XML_PI_TOKEN = "<?xml version=\"1.0\"?>";

   /**
    * Constructs and opens the XindiceDriver object (reflection constructor).
    */
   public XindiceDriver() throws XmlBlasterException {

      if (Log.CALL) Log.call(ME, "XindiceDriver (reflection constructor)");

      db = new XMLDBProxy();

      driverPath = XmlBlasterProperty.get("Persistence.Path", "xmldb:dbxml:///db");
      colName = XmlBlasterProperty.get("Persistence.Collection", "xmlBlaster");

      // Create Collection manually by using shell-commands
      // dbxmladmin ac -c /db -n xmlBlaster
      // check with: dbxmladmin lc -c /db

      // Open&Set Collection
      db.openCollection(driverPath + "/" + colName);

      Log.warn(ME, "* * * This Driver is under development, it may not be used for production environment! * * *");
      Log.info(ME, "using collectionPath '" + driverPath + "/" + colName + "'");
      if (Log.TRACE) Log.trace(ME, "Successfully constructed");
   }

   /**
   * Closes the Xindice-Collection
   */
   protected final void finalize() throws XmlBlasterException {

      if (Log.CALL) Log.call(ME, "finalize");
      db.closeCollection();

   }


   /**
    * Allows a message to be stored.
    * <p />
    * It only stores the xml-form of the message, using KeyOid as key.
    * @param messageWrapper The container with all necessary message info.
    */
   public final void store(MessageUnitWrapper messageWrapper) throws XmlBlasterException {

      if (Log.CALL) Log.call(ME, "store");

      /*
      XmlKey xmlKey = messageWrapper.getXmlKey();
      PublishQoS qos = messageWrapper.getPublishQoS();
      String mime = messageWrapper.getContentMime();
      byte[] content = messageWrapper.getMessageUnit().content;
      */

      // TODO is getMessageUnit().key more performant, but is it complete ???
      // qos as well

      String oid = messageWrapper.getXmlKey().getKeyOid();
      String key = messageWrapper.getXmlKey().toXml();

      db.addDocument(key, oid + XMLKEY_TOKEN);
      db.addDocument(BEGCON_TOKEN + new String(messageWrapper.getMessageUnit().content) + ENDCON_TOKEN, oid + XMLCON_TOKEN);
      db.addDocument(messageWrapper.getPublishQoS().toXml(), oid + XMLQOS_TOKEN);

      // Log.trace(ME, "<content><![CDATA["+ new String(messageWrapper.getMessageUnit().content)+"]]></content>");
      //	Log.trace(ME, new String(messageWrapper.getMessageUnit().content));

      if (Log.TRACE) Log.trace(ME, "Successfully stored; oid= " + oid + "key= " + key );
   }


   /**
    * Allows a already stored message content to be updated.
    * <p />
    * It only stores the content, so the store() method needs to be called first if this message is new.
    * @param messageWrapper The container with all necessary message info.
    */
   public final void update(MessageUnitWrapper messageWrapper) throws XmlBlasterException {

      if (Log.CALL) Log.call(ME, "update");

      String oid = messageWrapper.getXmlKey().getKeyOid();
      db.addDocument(BEGCON_TOKEN + new String(messageWrapper.getMessageUnit().content) + ENDCON_TOKEN, oid + XMLCON_TOKEN);
      // Store the sender as well:
      db.addDocument(messageWrapper.getPublishQoS().toXml(), oid + XMLQOS_TOKEN);
      //store(messageWrapper);

      if (Log.TRACE) Log.trace(ME, "Successfully updated store " + messageWrapper.getUniqueKey());

   }


   /**
    * Allows to fetch one message by oid from the persistence Layer.
    * <p />
    * @param   oid   The message oid (key oid="...")
    * @return The MessageUnit, which is persistent.
    */
   public final MessageUnit fetch(String oid) throws XmlBlasterException
   {

      if (Log.CALL) Log.call(ME, "fetch");
      MessageUnit msgUnit = null;

      String xmlKey_literal = db.retrieveDocument(oid + XMLKEY_TOKEN);
      String con = db.retrieveDocument(oid + XMLCON_TOKEN);
      //byte[] content = db.retrieveDocument(oid + XMLCON_TOKEN).getBytes();
      String xmlQos_literal = db.retrieveDocument(oid + XMLQOS_TOKEN);

      /*
      // if (Log.TRACE) Log.trace(ME, " 1 con: '" + con + "'");
      if ( con.startsWith(XML_PI_TOKEN) )
         con = con.substring(XML_PI_TOKEN.length() + 1 , con.length() );

      // if (Log.TRACE) Log.trace(ME, " 2 con: '" + con + "'");

      if ( con.startsWith(BEGCON_TOKEN) && con.endsWith(ENDCON_TOKEN) )
         con = con.substring(BEGCON_TOKEN.length(), con.length() - ENDCON_TOKEN.length() );

      // if (Log.TRACE) Log.trace(ME, " 3 con: '" + con + "'");
      if (Log.TRACE) Log.trace(ME, "con: '" + con + "'");
      */
      if ( con.startsWith(XML_PI_TOKEN) )
         if ( con.startsWith(BEGCON_TOKEN) && con.endsWith(ENDCON_TOKEN) )
            con = con.substring(XML_PI_TOKEN.length() + 1 + BEGCON_TOKEN.length(), con.length() - ENDCON_TOKEN.length() );

      // if (Log.TRACE) Log.trace(ME, " 3 con: '" + con + "'");
      if (Log.TRACE) Log.trace(ME, "con: '" + con + "'");

      byte[] content = con.getBytes();
      msgUnit = new MessageUnit(xmlKey_literal, content, xmlQos_literal);

      if (Log.TRACE) Log.trace(ME, "Successfully fetched message " + oid);
      if (Log.TRACE) Log.trace(ME, "	content '" + content + "'");
      if (Log.TRACE) Log.trace(ME, "       key '" + xmlKey_literal + "'");
      if (Log.TRACE) Log.trace(ME, "       qos '" + xmlQos_literal + "'");

      return msgUnit;
   }


   /**
    * Fetches all oid's of the messages from the persistence.
    * <p />
    * It is a helper method to invoke 'fetch(String oid)'.
    * @return a Enumeration of oids of all persistent MessageUnits. The oid is a String-Type.
    */
    public final Enumeration fetchAllOids() throws XmlBlasterException {

      if (Log.CALL) Log.call(ME, "fetchAllOids");

      Vector oidContainer = new Vector();

      String[] arr = db.listDocuments();

      for ( int  ii=0 ; ii < arr.length ; ii++ )
      {
         if ( arr[ii].endsWith(XMLKEY_TOKEN) ) {
            // Strip the XMLKEY_TOKEN ...
            String oid = arr[ii].substring(0, arr[ii].length() - XMLKEY_TOKEN.length());
            // and load the messages in a vector ...
            oidContainer.addElement(oid);
         }
      }
       /*
       File pp = new File(path);
       String[] fileArr = pp.list(new XmlKeyFilter());
       for (int ii=0; ii<fileArr.length; ii++) {
          // Strip the XMLKEY_TOKEN ...
          String oid = fileArr[ii].substring(0, fileArr[ii].length() - XMLKEY_TOKEN.length());
          // and load the messages in a vector ...
          oidContainer.addElement(oid);
       }
       Log.info(ME, "Successfully got " + oidContainer.size() + " stored message-oids from " + path);

       */
       Log.info(ME, "Successfully got " + oidContainer.size() + " stored message-oids from " + driverPath + "/" + colName);

       return oidContainer.elements();
    }



   /**
    * Allows a stored message to be deleted.
    * <p />
    * @param xmlKey  To identify the message
    */
   public final void erase(XmlKey xmlKey) throws XmlBlasterException {

      if (Log.CALL) Log.call(ME, "erase");

      String oid = xmlKey.getKeyOid();

      db.deleteDocument(oid + XMLKEY_TOKEN);
      db.deleteDocument(oid + XMLCON_TOKEN);
      db.deleteDocument(oid + XMLQOS_TOKEN);

      if (Log.TRACE) Log.trace(ME, "Successfully erased; oid= " + oid);
   }


/*   private static void XindiceDriverTest() throws Exception {

      Log.info(ME, "invoking XindiceDriverTest");

      String message =
      "<key oid='4711' contentMime='text/xml'> " +
      " <AGENT id='192.168.124.20' subId='1' type='generic'> " +
      "         <DRIVER id='FileProof' pollingFreq='10'>" +
      "         </DRIVER>" +
      " </AGENT>" +
      "</key>";

      String oid = "4711";

      try {
         XMLDBProxy db = new XMLDBProxy();
         // db.setCollection("xmlBlaster");

         // collection needs to be added (created) manually by:
         // dbxmladmin ac -c /db -n xmlBlaster
         // db.createCollection();

         // db.listCollection();
         db.openCollection("xmlBlaster");
         db.addDocument(message, oid);

         // retrive by shell:
         // dbxml rd -c /db/xmlBlaster -n 4711
         // dbxml xpath -c /db/xmlBlaster -q /DRIVER
         // dbxml xpath -c /db/xmlBlaster -q /DRIVER[@id='FileProof']

         Log.info(ME, "Found: " + db.retrieveDocument(oid) + "\n");
         db.deleteDocument(oid);

         // db.deleteCollection();
         // don't delete Collection!
         // do it manually:
         // dbxmladmin dc -c /db -n xmlBlaster -y

      } catch (Exception e) {
         Log.error(ME, e.toString());
         e.printStackTrace();
      }

      Log.info(ME, "end of test");

   }*/

   private static void XMLDBProxyTest() throws Exception {

      Log.info(ME, "invoking XMLDBProxyTest");

      String test_data =
      "<product product_id=\"7042001\">" +
      "    <description>Smoked Ham</description>" +
      "</product>";

      String test_index = "7042001";

      // String colPath = "xmldb:dbxml:///db";
      // String colName = "xmlBlaster_test";
      String colPath = XmlBlasterProperty.get("Persistence.Path", "xmldb:dbxml:///db");
      String colName = XmlBlasterProperty.get("Persistence.Collection", "xmlBlaster_test");

      try {
         if (Log.CALL) Log.call(ME, "in try");
         XMLDBProxy db = new XMLDBProxy();
         // Log.info(ME, "colPath: " + colPath);
         // Log.info(ME, "colName: " + colName);

         // open a path, create, list and remove a collection
         db.openCollection(colPath);
         db.createCollection("to_remove_test_only");
         db.listCollection();
         db.deleteCollection("to_remove_test_only");
         db.closeCollection();

         Log.info(ME, "--- new test starts here ---");

         // open a existent collection, add a Document, retrieve it, remove it, close collection
         db.openCollection(colPath + "/" + colName);
         db.addDocument(test_data, test_index);
         // retrive by shell:
         // dbxml rd -c /db/xmlBlaster_test -n 7042001
         // dbxml xpath -c /db/xmlBlaster_test -q /product
         // dbxml xpath -c /db/xmlBlaster_test -q /product[@product_id="7042001"]
         // list all documents:
         // dbxml ld -c /db/xmlBlaster_test

         Log.info(ME, "Found: " + db.retrieveDocument(test_index) + "\n");
         db.deleteDocument(test_index);
         db.closeCollection();

      } catch (Exception e) {
         Log.error(ME, e.toString());
         e.printStackTrace();
      }

      Log.info(ME, "end of test");

   }

   /** Invoke:  java -cp $CLASSPATH:. org.xmlBlaster.engine.persistence.xmldb.xindice.XindiceDriver */
   // javac -classpath $CLASSPATH:. *.java
   // java -cp $CLASSPATH:. org.xmlBlaster.engine.persistence.xmldb.xindice.XindiceDriver
   // java org.xmlBlaster.engine.persistence.xmldb.xindice.XindiceDriver -call true -trace true
   // java org.xmlBlaster.engine.persistence.xmldb..xindice.XindiceDriver
   // java -cp $CLASSPATH:. XindiceDriver
   // java -cp $CLASSPATH:. org.xmlBlaster.engine.persistence.xmldb.xindice.XindiceDriver
   public static void main(String[]args) throws Exception {

      Log.setLogLevel(args); // initialize log level

      XMLDBProxyTest();
//      XindiceDriverTest();

   } // end of public static void main(String[]args) throws Exception

} // end of class



/*------------------------------------------------------------------------------
Name:      XindiceProxy.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a Xindice Proxy
Version:   $Id: XindiceProxy.java,v 1.3 2002/05/11 08:08:52 ruff Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.xmldb.xindice;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;

import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;
import org.xmldb.api.DatabaseManager;

// For the XMLDB specific CollectionManager service
import org.apache.xindice.client.xmldb.services.*;
import org.apache.xindice.xml.dom.*;

// for starting Xindice ebedded
import org.apache.xindice.server.Xindice;
import org.apache.xindice.server.Kernel;

/**
 * This class provides the connection to Xindice (former dbXML) which is a XML:DB-database.
 * <p />
 *
 * @author $Author: ruff $
 */
public class XindiceProxy {

   private static final String ME = "XindiceProxy";
   private final Global glob;

   // private static Xindice Xdb = null; // instance of Xindice Database

   private Collection col = null; // instance of collection
   private String colPath = null; // path to collection
   private boolean isOpen = false; // is db open?
   // private boolean isRunning = false; // is Xindice running?

   private String xindiceDriver = null;
   private String xindiceFilterClass = null;

   /**
    * Constructs and opens the XindiceProxy object.
    *
    * It gets the driverclass and the XindiceDriver from xmlBlaster.properties, if desired.
    * <br />
    * CAUTION: This Proxy is under development, it may not be used for production environment!
    * <br />
    */
   public XindiceProxy(Global glob)
   {
      this.glob = glob;
      if (Log.CALL) Log.call(ME, "Constructor for XindiceProxy");

      xindiceDriver = glob.getProperty().get("Persistence.xindiceDriver", "org.apache.xindice.client.xmldb.DatabaseImpl");
      xindiceFilterClass = glob.getProperty().get("Persistence.xindiceFilterClass", "org.apache.xindice.core.filer.BTreeFiler");

      // start the db here, if it's not running already
      // this.startXindice();

      if (Log.TRACE) Log.trace(ME, "xindiceDriver " + xindiceDriver);
      if (Log.TRACE) Log.trace(ME, "xindiceFilterClass " + xindiceFilterClass);
   } // end of  public XindiceProxy()

   /**
    * Constructs and opens the XindiceProxy object and opens the collection.
    *
    * It gets the driverclass and the XindiceDriver from xmlBlaster.properties, if desired.
    * <br />
    * CAUTION: This Proxy is under development, it may not be used for production environment!
    * <br />
    * @param path The Path of the collection i.e. xmldb:xindice:///db/xmlBlaster
    */
   public XindiceProxy(Global glob, String path)  throws XmlBlasterException
   {
      this.glob = glob;
      if (Log.CALL) Log.call(ME, "Constructor for XindiceProxy, using path: " + path);

      xindiceDriver = glob.getProperty().get("Persistence.xindiceDriver", "org.apache.xindice.client.xmldb.DatabaseImpl");
      xindiceFilterClass = glob.getProperty().get("Persistence.xindiceFilterClass", "org.apache.xindice.core.filer.BTreeFiler");

      // start the db her, if it's not running already
      // this.startXindice();

      if (Log.TRACE) Log.trace(ME, "xindiceDriver " + xindiceDriver);
      if (Log.TRACE) Log.trace(ME, "xindiceFilterClass " + xindiceFilterClass);

      openCollection(path);

   } // end of  public XindiceProxy()

   /*
   private void startXindice()
   {
      if (Log.CALL) Log.call(ME, "startXindice");

      if (isRunning) {
         Log.error(ME, "Cannot start Xindice, it's running already!");
         return;
        }
      Xdb = new Xindice();

      //System.out.println();
      Log.info(ME, Xdb.Title+" "+Xdb.Version+" ("+Xdb.Codename+")");
      //System.out.println();
      new Kernel("Xindice/system.xml");

      isRunning = true;
   } // end of startXindice
   */

   /*
   private void shutdownXindice()
   {
      if (Log.CALL) Log.call(ME, "shutdownXindice");

      if (!isRunning) {
         Log.error(ME, "Cannot shutdown Xindice, it's not running!");
         return;
        }

      isRunning = false;

   } // end of startXindice
   */

   /**
    * Allows to create a collection
    *
    * <p />
    * The database needs to be running see <a href="http://www.dbxml.org">Xindice</a> for details.
    * <p />
    * This method stops with an Log.error, if the database isn't open already.
    * <p />
    * @param colName The Name of the collection
    */
   public void createCollection(String colName) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "createCollection");
      String colConfig = null;

      if (!isOpen) {
         Log.error(ME, "Cannot create collection '" + colName + "', db needs to be opened first!");
         return;
        }

      try {
         CollectionManager service = (CollectionManager) col.getService("CollectionManager", "1.0");

         colConfig =
            "<collection compressed='true' name='" + colName + "'>" +
            "   <filer class='" + xindiceFilterClass + "' gzip='true'/>" +
            "</collection>";

         service.createCollection(colName, DOMParser.toDocument(colConfig));

      } catch (XMLDBException e1) {
         throw new XmlBlasterException( String.valueOf(e1.errorCode), e1.toString() );
      } catch (org.apache.xindice.util.XindiceException e2) {
         throw new XmlBlasterException( "org.apache.xindice.util.XindiceException occured", e2.toString() );
      }

      if (Log.TRACE) Log.trace(ME, "Collection '" + colName + "' created using '" + colConfig + "'");
   } // end of public createCollection()

   /**
    * Allows to delete a collection
    *
    * <p />
    * The database needs to be running see <a href="http://www.dbxml.org">Xindice</a> for details.
    * <p />
    * This method stops with an Log.error, if the database isn't open already.
    * <p />
    * @param colName The Name of the collection
    */
   public void deleteCollection(String colName) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "deleteCollection");

      if (!isOpen) {
         Log.error(ME, "Cannot delete collection, collection needs to be opened first!");
         return;
        }

      try {
         CollectionManager service = (CollectionManager) col.getService("CollectionManager", "1.0");
         service.dropCollection(colName);
      } catch (XMLDBException e1) {
         throw new XmlBlasterException( String.valueOf(e1.errorCode), e1.toString() );
      }

      if (Log.TRACE) Log.trace(ME, "Collection '" + colName + "' removed");
   } // end of deleteCollection()

   /**
    * Allows to set the path of a collection and to open it.
    *
    * <p />
    * The database needs to be running see <a href="http://www.dbxml.org">Xindice</a> for details.
    * <p />
    * This method stops with an Log.error, if the database is open already.
    * <p />
    * @param path The Path of the collection i.e. xmldb:xindice:///db/xmlBlaster
    */
   public void openCollection(String path) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "invoking openCollection: " + path);
      this.setCollection(path);
      this.openCollection();
   } // end of openCollection

   /**
    * Allows to open a collection.
    *
    * <p />
    * The database needs to be running see <a href="http://www.dbxml.org">Xindice</a> for details.
    * <p />
    * This method stops with an Log.error, if the database is open already.
    */
   public void openCollection() throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "openCollection");
      if (Log.TRACE) Log.trace(ME, "colPath " + colPath);
      if (Log.TRACE) Log.trace(ME, "xindiceDriver " + xindiceDriver);

      if (isOpen) {
         Log.error(ME, "Cannot open collection, collection is open already!");
         return;
        }

      try {
         Class c = Class.forName(xindiceDriver);
        //Log.info(ME, "Here I am 1");
         Database database = (Database) c.newInstance();
        //Log.info(ME, "Here I am 2");
         DatabaseManager.registerDatabase(database);
        //Log.info(ME, "Here I am 3");
         col = DatabaseManager.getCollection(colPath);
        //Log.info(ME, "Here I am 4");
      } catch (XMLDBException e1) {
         e1.printStackTrace();
         throw new XmlBlasterException( String.valueOf(e1.errorCode), e1.toString() );
      } catch (java.lang.ClassNotFoundException e2) {
         throw new XmlBlasterException( "ClassNotFoundException occured ", e2.toString() );
      } catch (java.lang.InstantiationException e3) {
         throw new XmlBlasterException( "java.lang.InstantiationException occured ", e3.toString() );
      } catch (java.lang.IllegalAccessException e4) {
         throw new XmlBlasterException( "java.lang.IllegalAccessException occured ", e4.toString() );
      }

      if (Log.TRACE) Log.trace(ME, "Collection '" + colPath + "' opened");
      isOpen = true;

   } // end of openCollection()

   /**
    * Allows to close a collection
    *
    * <p />
    * The database needs to be running see <a href="http://www.dbxml.org">Xindice</a> for details.
    * <p />
    * This method stops with an Log.error, if the database isn't open already.
    */
   public void closeCollection() throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "closeCollection");

      if (!isOpen) {
         Log.error(ME, "Cannot close collection, collection needs to be opened first!");
         return;
      } else {
         try {
            col.close();
         } catch (XMLDBException e) {
            Log.error(ME, e.toString());
            throw new XmlBlasterException( String.valueOf(e.errorCode), e.toString() );
         }
         if (Log.TRACE) Log.trace(ME, "Collection closed");
         isOpen = false;
      }
   } // end of closeCollection()

   /**
    * Allows to list a collection
    *
    * <p />
    * The database needs to be running see <a href="http://www.dbxml.org">Xindice</a> for details.
    * <p />
    * This method stops with an Log.error, if the database isn't open already.
    */
   public void listCollection() throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "listCollections:");

      if (!isOpen) {
         Log.error(ME, "Cannot list collection, collection needs to be opened first!");
         return;
        }
      String[] colArray = null;

      try {
         if ( col != null ) {
            colArray = col.listChildCollections();

            for ( int i=0 ; i < colArray.length ; i++ ) {
                Log.info(ME, "\t - " + colArray[i] );
            }
            Log.info(ME, "Total collections: " + colArray.length);
         }
         else {
            Log.error(ME, "Collection not found");
         }
      } catch (XMLDBException e1) {
         throw new XmlBlasterException( String.valueOf(e1.errorCode), e1.toString() );
      }
   } // end of listCollection()

   /**
    * Allows to set the name of the collection
    *
    * <p />
    * @param path The Path of the collection i.e. xmldb:xindice:///db/xmlBlaster
    */
   public void setCollection(String path)
   {
      if (Log.CALL) Log.call(ME, "setCollection");

      if (Log.TRACE) Log.trace(ME, "path = " + path);
      colPath = path;
   } // end of setCollection()

   /**
    * Allows to get the name of a collection
    *
    * <p />
    * <p />
    * @return The Name of the collection
    */
   public String getCollection()
   {
      if (Log.CALL) Log.call(ME, "getCollection");

      if (!isOpen) {
         Log.error(ME, "Collection closed, no collection name available!");
         return "";
        }
      return colPath;
   } // end of getCollection()

   /**
    * Allows to add a document to the collection
    *
    * <p />
    * The database needs to be running see <a href="http://www.dbxml.org">Xindice</a> for details.
    * <p />
    * This method stops with an Log.error, if the database isn't open already.
    * <p />
    * @param data The data to be stored
    * @param id The unique id of the stored data
    */
   public void addDocument(String data, String id) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "addDocument");
      if (Log.TRACE) Log.info(ME, "Add document   '" + colPath + "/" + data + "' as: " + id );

      if (!isOpen) {
         Log.error(ME, "Cannot add document, collection needs to be opened first!");
         return;
        }
      try {
         XMLResource resource = (XMLResource) col.createResource(id, "XMLResource");

         resource.setContent(data);
         col.storeResource(resource);

         if (Log.TRACE) Log.trace(ME, "Added document '" + colPath + "/" + data + "' as: " + resource.getId() );
         resource = null;

      } catch (XMLDBException e1) {
         throw new XmlBlasterException( String.valueOf(e1.errorCode), e1.toString() );
      }
   } // end of addDocument()

   /**
    * Allows to retrieve a document from the collection
    *
    * <p />
    * The database needs to be running see <a href="http://www.dbxml.org">Xindice</a> for details.
    * <p />
    * This method stops with an Log.error, if the database isn't open already.
    * <p />
    * @param id The unique id of the stored data
    * @return The stored data belonging to the id, null otherwise
    */
   public String retrieveDocument(String id) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "retrieveDocument key='" + id +"'");

      if (!isOpen) {
         Log.error(ME, "Cannot retrieve document, collection needs to be opened first!");
         return "";
        }
      String ret = null;
      try {
         XMLResource resource = (XMLResource) col.getResource(id);

         // Verify that we were able to find the document
         if ( resource == null ) {
            Log.error(ME, "Document not found!");
            return ret;
         }

         String documentstr = (String)resource.getContent();

         if (Log.TRACE) Log.info(ME, "retrieved document " + documentstr );

         ret = documentstr;

      } catch (XMLDBException e1) {
         throw new XmlBlasterException( String.valueOf(e1.errorCode), e1.toString() );
      }

      return ret;
   } // end of retrieveDocument()

   /**
    * Allows to delete a document in the collection
    *
    * <p />
    * The database needs to be running see <a href="http://www.dbxml.org">Xindice</a> for details.
    * <p />
    * This method stops with an Log.error, if the database isn't open already.
    * <p />
    * @param id The unique id of the document to delete
    */
   public void deleteDocument(String id) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "deleteDocument");

      if (!isOpen) {
         Log.error(ME, "Cannot delete document, collection needs to be opened first!");
         return;
        }

      try {
         Resource colresource = col.getResource(id);

         col.removeResource(colresource);

         if (Log.TRACE) Log.trace(ME, "DELETED: " + id);

      } catch (XMLDBException e1) {
         throw new XmlBlasterException( String.valueOf(e1.errorCode), e1.toString() );
      }
   } // end of deleteDocument()

   /**
    * Allows to list all document of the collection
    *
    * <p />
    * The database needs to be running see <a href="http://www.dbxml.org">Xindice</a> for details.
    * <p />
    * This method stops with an Log.error, if the database isn't open already.
    * <p />
    * @return String[] Array containing the id's (unique key's) of all stored data
    */
   public String[] listDocuments() throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "listDocuments");

      if (!isOpen) {
         Log.error(ME, "Cannot list documents, collection needs to be opened first!");
         return null;
        }

      String[] docArray = null;

      try {
         docArray = col.listResources();
      } catch (XMLDBException e1) {
         throw new XmlBlasterException( String.valueOf(e1.errorCode), e1.toString() );
      }

      return docArray;
   } // end of listDocuments()

}; // end of public class XindiceProxy {


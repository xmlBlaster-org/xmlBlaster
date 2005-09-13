/*------------------------------------------------------------------------------
Name:      I_DbSpecific.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.sql.Connection;
import java.util.Map;

import org.xmlBlaster.contrib.dbwriter.I_ContribPlugin;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoDescription;

public interface I_DbSpecific extends I_ContribPlugin {

   /** 
    * key for the property defining if the publisher is needed on the implementation of this interface.
    * This is used since this interface can be used on both master and slave. On the slave however it will
    * never need a publisher.
    */
   final static String NEEDS_PUBLISHER_KEY = "_replication.specific.needsPublisher";

   /**
    * This method is invoked for the initial setup of the database. In production this method is probably
    * never called.
    * Initial configuration (for example procedural configuration), creation of 'system tables' , 
    * 'system functions' and triggers (if any) is done here.
    * We use the term 'system' to specify such elements which are used internally for the replication.
    * 
    * @param conn
    * @throws Exception
    */
   void bootstrap(Connection conn) throws Exception;
   
   /**
    * This method is invoked for the cleanup while testing. In production this method is probably
    * never called.
    * Cleanup of initial configuration (for example procedural configuration), dropping of 'system tables' , 
    * 'system functions' and triggers (if any) is done here.
    * We use the term 'system' to specify such elements which are used internally for the replication.
    * 
    * @param conn
    * @throws Exception
    */
   void cleanup(Connection conn) throws Exception;
   
   /**
    * Reads the metadata and content of the specified table. This is needed for database implementations which can
    * not detect synchronously if a CREATE has been done or which can not register the necessary triggers on that
    * newly created table.
    * 
    * @param catalog
    * @param schema
    * @param table
    * @param attrs the attributes to pass to the description object when publishing. It basically contains the 
    * information about the metadata of an entry (the columns in repl_items without the old and new contents).
    * Note that the values of such map are normally Strings, while the DbUpdateInfo objects contain attributes where
    * the value is a ClientProperty. That conversion is handled by the DbUpdateInfoDescription object. 
    * 
    * @throws Exception
    */
   void readNewTable(String catalog, String schema, String table, Map attrs) throws Exception;
   
   /**
    * Returns the statement necessary to create a new table.
    * @param infoDescription The description from which to create the statement.
    * @param mapper the mapper to convert the table name (and in future the column names). Can be null,
    * if null, no mapping is done.
    * 
    * @return
    */
   String getCreateTableStatement(DbUpdateInfoDescription infoDescription, I_Mapper mapper);

   /**
    * Creates a string containing the function which has to be added to the trigger of the table to be watched and 
    * the associated trigger.
    * .
    * @param infoDescription
    * @return
    */
   String createTableFunctionAndTrigger(DbUpdateInfoDescription infoDescription);

   
   /**
    * Invokes the function to check wether a table has been created, dropped or altered.
    * @throws Exception
    */
   void forceTableChangeCheck() throws Exception;
   
}

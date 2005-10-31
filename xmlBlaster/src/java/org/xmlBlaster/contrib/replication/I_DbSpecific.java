/*------------------------------------------------------------------------------
Name:      I_DbSpecific.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.sql.Connection;
import java.util.Map;

import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoColDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoDescription;

public interface I_DbSpecific extends I_ContribPlugin {

   /** 
    * key for the property defining if the publisher is needed on the implementation of this interface.
    * This is used since this interface can be used on both master and slave. On the slave however it will
    * never need a publisher.
    */
   public final static String NEEDS_PUBLISHER_KEY = "_replication.specific.needsPublisher";

   /**
    * This method is invoked for the initial setup of the database. In production this method is probably
    * never called.
    * Initial configuration (for example procedural configuration), creation of 'system tables' , 
    * 'system functions' and triggers (if any) is done here.
    * We use the term 'system' to specify such elements which are used internally for the replication.
    * 
    * @param conn
    * @param doWarn if false no warning is given on excrption.
    * @param force if true, then everything is cleaned up, if false, then tables and sequences are
    * only rebuilt if not existing.
    * @throws Exception
    */
   void bootstrap(Connection conn, boolean doWarn, boolean force) throws Exception;
   
   /**
    * This method is invoked for the cleanup while testing. In production this method is probably
    * never called.
    * Cleanup of initial configuration (for example procedural configuration), dropping of 'system tables' , 
    * 'system functions' and triggers (if any) is done here.
    * We use the term 'system' to specify such elements which are used internally for the replication.
    * 
    * @param conn
    * @param doWarn if false no warning is given on excrption.
    * @throws Exception
    */
   void cleanup(Connection conn, boolean doWarn) throws Exception;
   
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
    * Creates a string containing the trigger of the table to be watched. 
    * .
    * @param infoDescription
    * @param triggerName the name to give to the trigger associated with this table,
    * @return
    */
   String createTableTrigger(DbUpdateInfoDescription infoDescription, String triggerName);

   
   /**
    * Invokes the function to check wether a table has been created, dropped or altered.
    * @throws Exception
    */
   void forceTableChangeCheck() throws Exception;

   
   /**
    * Increments and retreives the repl_key sequence counter. The connection must not be null.
    * This method has been made available to the interface for the sole purpose to test it in the 
    * testsuite. Otherwise it is used internally by the implementations of this interface.
    * 
    * @param conn the connection (it must not be null)
    * @return the new sequence value for the repl_key sequence.
    * @throws Exception if an exception occurs.
    */
   long incrementReplKey(Connection conn) throws Exception;
   
   /**
    * Adds a table to be watcher/replicated. It adds the entry to the repl_tables and makes sure that
    * it is added with the correct case (depending upon how case sensitivity is handled by the implementation
    * of the database).
    * 
    * @param catalog the name of the catalog to use. If null, an empty string is stored (since part of the PK)
    * @param schema the name of the schema to use. If null, an empty string is stored (since part of the PK).
    * @param tableName the name of the table to be added.
    * @param doReplicate 'false' if it does not need to replicate, 'true' otherwise.
    * @param triggerName is the name which will be given to the trigger to add to the table. Can be null. If null
    * is passed, then a name is choosed by the application. It is good practice to provide with a unique name.
    * 
    * @throws Exception if an exception occurs on the backend. For example if the table already has been
    * added, it will throw an exception.
    */
   void addTableToWatch(String catalog, String schema, String tableName, boolean doReplicate, String triggerName) throws Exception;
   
   /**
    * Adds a schema to be watched. By Oracle it would add triggers to the schema. 
    * @param catalog
    * @param schema
    * @throws Exception
    */
   void addSchemaToWatch(Connection conn, String catalog, String schema) throws Exception;

   /**
    * Removes a table from the repl_tables. This method will make sure that the correct case sensitivity
    * for the table name will be used.
    * @param tableName
    * @throws Exception
    */
   void removeTableToWatch(String catalog, String schema, String tableName) throws Exception;

   /**
    * This method should actually be protected since it is not used on the outside. It is part of this interface
    * since the handling for the different databases can be different.
    *  
    * @param colInfoDescription The info object describing this column.
    * @return A String Buffer containing the part of the CREATE statement which is specific to this column.
    */
   StringBuffer getColumnStatement(DbUpdateInfoColDescription colInfoDescription);
 
   void initiateUpdate(String topic, String destination, String slaveName) throws Exception;
 
   /**
    * This is the intial command which is invoked on the OS. It is basically used for the
    * import and export of the DB. Could also be used for other operations on the OS.
    * It is a helper method.
    * 
    * @param argument the argument to execute. It is normally the absolute file name to be
    * exported/imported.
    * 
    * @throws Exception
    */
   void initialCommand(String argument) throws Exception;
   
}

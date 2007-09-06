/*------------------------------------------------------------------------------
Name:      I_DbSpecific.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.sql.Connection;
import java.util.Map;

import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.dbwatcher.convert.I_AttributeTransformer;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;

public interface I_DbSpecific extends I_ContribPlugin {
   
   public final static int WIPEOUT_TRIGGERS = 0;
   public final static int WIPEOUT_SEQUENCES = 1;
   public final static int WIPEOUT_FUNCTIONS = 2;
   public final static int WIPEOUT_PACKAGES = 3;
   public final static int WIPEOUT_PROCEDURES = 4;
   public final static int WIPEOUT_VIEWS = 5;
   public final static int WIPEOUT_TABLES = 6;
   public final static int WIPEOUT_SYNONYMS = 7;
   public final static int WIPEOUT_INDEXES = 8;

   public final static boolean[] WIPEOUT_ALL = new boolean[] {true, true, true, true, true, true, true, true, true };
   public final static boolean[] WIPEOUT_ONLY_TABLES = new boolean[] {false, false, false, false, false, false, true, false, false};
   public final static boolean[] WIPEOUT_NO_TABLES = new boolean[] {true, true, true, true, true, true, false, true, true };

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
    * @param catalog can be null
    * @param schema can be null
    * @param table can not be null
    * @param attrs can be null
    * @param sendInitialContents if true it will send all the contents of the added table, if false it
    * will not send anything. So normally if you made a dump of the Database you don't want
    * to send all the content of the tables again.
    *  
    * @param attrs the attributes to pass to the description object when publishing. It basically contains the 
    * information about the metadata of an entry (the columns in repl_items without the old and new contents).
    * Note that the values of such map are normally Strings, while the SqlInfo objects contain attributes where
    * the value is a ClientProperty. That conversion is handled by the SqlDescription object. 
    * 
    * @throws Exception
    */
   void readNewTable(String catalog, String schema, String table, Map attrs, boolean sendInitialContents) throws Exception;
   
   /**
    * Returns the statement necessary to create a new table.
    * @param infoDescription The description from which to create the statement.
    * @param mapper the mapper to convert the table name (and in future the column names). Can be null,
    * if null, no mapping is done.
    * 
    * @return
    */
   String getCreateTableStatement(SqlDescription infoDescription, I_Mapper mapper);

   /**
    * Creates a string containing the trigger of the table to be watched. 
    * .
    * @param infoDescription
    * @param triggerName the name to give to the trigger associated with this table,
    * @return
    */
   // String createTableTrigger(SqlDescription infoDescription, String triggerName, String replFlags);
   String createTableTrigger(SqlDescription infoDescription, TableToWatchInfo tableToWatch);

   
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
    * @param replFlags is a String containing a combination of 'IDU' (I)nsert, (U)pdate and (D)elete.
    * If null or emtpy, then the trigger used to detect stuff to be replicated is not put anywhere, it
    * is equivalent to not to replicate. If you pass I, then only inserts are performed. 
    * You normally will pass IDU' here.
    * @param triggerName is the name which will be given to the trigger to add to the table. Can be null. If null
    * is passed, then a name is choosed by the application. It is good practice to provide with a unique name.
    * @param force if true, then the trigger is added even if it exists already.
    * @param destinations can be null. If it has to be sent to an individual client you pass its session name here. 
    * If you pass null, then it is published in pub/sub modus. If you want to publish it to more than one client,
    * you pass more destinations.
    * @param forceSend if true, all content data of the table will be sent.
    * @return true if the table was added, false otherwise. If the table already was registered (added), then
    * it will not add it anymore.
    * @throws Exception if an exception occurs on the backend. For example if the table already has been
    * added, it will throw an exception.
    */
   boolean addTableToWatch(TableToWatchInfo tableToWatch, boolean force, String[] destinations, boolean forceSend) throws Exception;
   // boolean addTableToWatch(String catalog, String schema, String tableName, String replFlags, String triggerName, boolean force, String destination, boolean forceSend) throws Exception;
   
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
    * @param tableToWatch
    * @param removeAlsoSchemaTrigger if true it will also remove the associated schema trigger.
    * @throws Exception
    */
   void removeTableToWatch(TableToWatchInfo tableToWatch, boolean removeAlsoSchemaTrigger) throws Exception;

   /**
    * This method should actually be protected since it is not used on the outside. It is part of this interface
    * since the handling for the different databases can be different.
    *  
    * @param colInfoDescription The info object describing this column.
    * @return A String Buffer containing the part of the CREATE statement which is specific to this column.
    */
   StringBuffer getColumnStatement(SqlColumn colInfoDescription);


   /**
    * Initiates an initial update. It is invoked by the InitialUpdater in asynchronous mode.
    * @param topic
    * @param replManagerAddress the address to which to send acknowleges.
    * @param slaveName the name of the slave interested in these updates
    * @param version the version for which to start replication. If null the current version
    * @param initialFilesLocation tells where to store the initial files in case the replication data
    * has to be transfered manually by means of copying files.
    * is ment.
    * @throws Exception
    */
   void initiateUpdate(String topic, String replManagerAddress, String[] slaveName, String version, String initialFilesLocation) throws Exception;

   /**
    * This is the intial command which is invoked on the OS. It is basically used for the
    * import and export of the DB. Could also be used for other operations on the OS.
    * It is a helper method.
    * 
    * @param slaveNames an array containing the slaveNames to be updated. can be null.
    * @param completeFilename the filename to be used to execute.
    * 
    * @param version the version for which to start replication. If null the current version
    * is ment.
    * @throws Exception
    */
   void initialCommand(String[] slaveNames, String completeFilename, String version) throws Exception;

   /**
    * This is the command/script which is invoked before cleaning up resources on the initial update
    * on the slave side (before the wipeout of the schema to be replicated).
    * 
    * @throws Exception if an exception occurs when executing the script.
    */
   public void initialCommandPre() throws Exception;
   
   /**
    * removes the specified trigger from the specified table.
    * @param triggerName
    * @param tableName
    * @param isSchemaTrigger true if the trigger to be removed is a schema trigger.
    * @return true if the trigger has been removed, false otherwise.
    * @throws Exception
    */
   boolean removeTrigger(String triggerName, String tableName, boolean isSchemaTrigger);
 
   /**
    * Cleans up the complete schema. It cleans tables, sequences, views, functions,
    * procedures, triggers and indexes.
    *  
    * @param catalog
    * @param schema
    * @param objectsToWipeout a boolean[] array containing 9 elements telling wether the specified objects of a certain
    * type have to be wiped out or not. For the relationship between position and meaning see the static variables.
    * If you pass null here all object types are wiped out.
    * @return the number of entries removed.
    * @throws Exception
    */
   int wipeoutSchema(String catalog, String schema, boolean[] objectsToWipeout) throws Exception;

   /**
    * This is used for cases where it was not possible to retrieve the (new) content 
    * of an entry synchronously in the PL/SQL Code.
    *  
    * @param guid the unique Id identifying this entry. This is needed. Can not be null 
    * @param catalog can be null
    * @param schema can be null
    * @param table must be defined (can not be null).
    * @param transformer An optional plugin
    * @return the String containing the serialized entry.
    * @throws Exception
    */
   String getContentFromGuid(String guid, String catalog, String schema, String table, I_AttributeTransformer transformer) throws Exception;
   
   String getName();

   /**
    * Creates the necessary triggers if needed, i.e. if the triggers are not existing already.
    * @param force to force recreation of triggers even if they exist already.
    * @throws Exception
    */
   void addTriggersIfNeeded(boolean force, String[] destinations, boolean forceSend) throws Exception;
   
   /**
    * Checks wether a trigger really exists or not. This method is normally implemented
    * by the vendor specific implementation. It is used to detect inconsistencies.
    * 
    * @param tableToWatch The object containing the table to be checked for trigger
    * @return true if the trigger really exists, false otherwise.
    * @throws Exception If an exception occurs in the backend.
    */
   boolean triggerExists(Connection conn, TableToWatchInfo tableToWatch) throws Exception;

   /**
    * Checks if the triggers are consistent, i.e. if the description found in the table is consistent with the
    * real values.
    * 
    * @param doFix
    * @throws Exception
    */
   void checkTriggerConsistency(boolean doFix) throws Exception;

   /**
    * broadcasts a statement to be replicated.
    *
    * @param sql
    * @param maxResponseEntries
    * @param isHighPrio
    * @param isMaster true if it is on the master side, i.e. in the DbWatcher. Then it will first put an entry in the
    * ITEMS table and thereafter execute the statement.
    * @param sqlTopic the topic on which the response will be published.
    * @param statementId The unique Id identifying this statement. This will also be the topic on which the response is sent.
    * @return a byte[] containing the response (an xml literal) 
    * 
    * @throws Exception
    */
   byte[] broadcastStatement(String sql, long maxResponseEntries, boolean isHighPrio, boolean isMaster, String sqlTopic, String statementId) throws Exception;
   
   /**
    * Tells the DbSpecific to cancel the ongoing initial update for the given slave.
    * @param replSlave the String identifying the slave name.
    */
   void cancelUpdate(String replSlave);
   
   /**
    * Tells the DbSpecific to clear the cancel flag for ongoing updates for the given slave.
    * @param replSlave the String identifying the slave name.
    */
   void clearCancelUpdate(String replSlave);
   
   void setAttributeTransformer(I_AttributeTransformer transformer);
   
   boolean isDatasourceReadonly();
   
   void addTrigger(Connection conn, String catalog, String schema, String tableName) throws Exception;
   
}

/*------------------------------------------------------------------------------
Name:      ReplicationConstants.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

public interface ReplicationConstants {

   // special topics and session names
   public final static String REPL_MANAGER_SESSION = "replManager/1";
   public final static String REPL_MANAGER_TOPIC = "replManagerTopic";
   public final static String REPL_MANAGER_REGISTER = "REGISTER";
   public final static String REPL_MANAGER_UNREGISTER = "UNREGISTER";
   public final static String REPL_PREFIX_KEY = "replication.prefix";
   public final static String REPL_REQUEST_UPDATE = "REQUEST_UPDATE";

   public final static String SLAVE_NAME = "_slaveName";
   
   // attributes 
   public final static String TABLE_NAME_ATTR = "tableName";
   public final static String REPL_KEY_ATTR = "replKey";
   public final static String EXTRA_REPL_KEY_ATTR = "extraReplKey";
   public final static String TRANSACTION_ATTR = "transaction";
   public final static String DB_ID_ATTR = "dbId";
   public final static String GUID_ATTR = "guid";
   public final static String CATALOG_ATTR = "catalog";
   public final static String SCHEMA_ATTR = "schema";
   public final static String VERSION_ATTR = "version";
   public final static String ACTION_ATTR = "action";
   public final static String OLD_CONTENT_ATTR = "oldContent";
   public final static String STATEMENT_ATTR = "statement";
   public final static String DUMP_FILENAME = "dumpName";
   
   // commands
   public final static String REPLICATION_CMD = "REPLICATION";
   public final static String INSERT_ACTION = "INSERT";
   public final static String UPDATE_ACTION = "UPDATE";
   public final static String DELETE_ACTION = "DELETE";
   public final static String DROP_ACTION = "DROP";
   public final static String CREATE_ACTION = "CREATE";
   public final static String ALTER_ACTION = "ALTER";
   public final static String DUMP_ACTION = "DUMP";
   public final static String STATEMENT_ACTION = "STATEMENT"; // that is a generic SQL statement
   
   
}

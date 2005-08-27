/*------------------------------------------------------------------------------
Name:      ReplicationConstants.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

public interface ReplicationConstants {

   // attributes 
   public final static String TABLE_NAME_ATTR = "tableName";
   public final static String REPL_KEY_ATTR = "replKey";
   public final static String TRANSACTION_ATTR = "transaction";
   public final static String DB_ID_ATTR = "dbId";
   public final static String GUID_ATTR = "guid";
   public final static String SCHEMA_ATTR = "schema";
   public final static String VERSION_ATTR = "version";
   public final static String ACTION_ATTR = "action";
   
   // commands
   public final static String REPLICATION_CMD = "REPLICATION";
   public final static String INSERT_ACTION = "INSERT";
   public final static String UPDATE_ACTION = "UPDATE";
   public final static String DELETE_ACTION = "DELETE";
   public final static String DROP_ACTION = "DROP";
   public final static String CREATE_ACTION = "CREATE";
   
}

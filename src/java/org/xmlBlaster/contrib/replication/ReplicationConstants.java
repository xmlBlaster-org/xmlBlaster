/*------------------------------------------------------------------------------
Name:      ReplicationConstants.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import org.xmlBlaster.contrib.ContribConstants;

public interface ReplicationConstants extends ContribConstants {
   // special topics and session names
    final static String REPL_MANAGER_SESSION = "replManager/1";
    final static String REPL_MANAGER_TOPIC = "replManagerTopic";
   
    final static String PURPOSE_REPLICATION = "REPLICATION"; 
   
    final static String REPL_REQUEST_UPDATE = "REQUEST_UPDATE";
    final static String CONTRIB_PERSISTENT_MAP = "contribPersistentMap";
   
   /** invoked to cancel an ongoing initial update */
    final static String REPL_REQUEST_CANCEL_UPDATE = "REQUEST_CANCEL_UPDATE";
   /** invoked to recreate the triggers for the specified DbWatcher. */
    final static String REPL_REQUEST_RECREATE_TRIGGERS = "REQUEST_RECREATE_TRIGGERS";

    final static String SLAVE_NAME = "_slaveName";
    final static String REPL_VERSION = "_replVersion";
   
   // attributes 
    final static String TABLE_NAME_ATTR = "tableName";
   /** This is the identity (unique Key of the replication entry from DbWatcher) */
    final static String REPL_KEY_ATTR = "replKey";
    final static String EXTRA_REPL_KEY_ATTR = "extraReplKey";
    final static String TRANSACTION_ATTR = "transaction";
    final static String DB_ID_ATTR = "dbId";
    final static String GUID_ATTR = "guid";
    final static String CATALOG_ATTR = "catalog";
    final static String SCHEMA_ATTR = "schema";
    final static String VERSION_ATTR = "version";
    final static String ACTION_ATTR = "action";
    final static String DUMP_CONTENT_ATTR = "_dumpContent"; // used for xml initial update dump
    final static String OLD_CONTENT_ATTR = "oldContent";
    final static String STATEMENT_ATTR = "statement";
    final static String STATEMENT_PRIO_ATTR = "statementPrio";
    final static String STATEMENT_ID_ATTR = "statementId";
    final static String SQL_TOPIC_ATTR = "sqlTopic";
    final static String MAX_ENTRIES_ATTR = "maxEntries";
    final static String MASTER_ATTR = "isMaster";
    final static String DUMP_FILENAME = "dumpName";
    final static String ALREADY_PROCESSED_ATTR = "alreadyProcessed"; // Used to mark a message which has already been processed and which has been sent twice.
    final static String EXCEPTION_ATTR = "exception"; 
   // commands
    final static String REPLICATION_CMD = "REPLICATION";
    final static String INITIAL_XML_CMD = "INITIAL_DUMP_AS_XML";
    final static String INSERT_ACTION = "INSERT";
    final static String UPDATE_ACTION = "UPDATE";
    final static String DELETE_ACTION = "DELETE";
    final static String DROP_ACTION = "DROP";
    final static String CREATE_ACTION = "CREATE";
    final static String ALTER_ACTION = "ALTER";
    final static String DUMP_ACTION = "DUMP";
    final static String STATEMENT_ACTION = "STATEMENT"; // that is a generic SQL statement
   
    final static String END_OF_TRANSITION = "_END_OF_TRANSITION"; // sent to determine that the transition status is finished
    final static String INITIAL_DATA_END = "_INITIAL_DATA_END"; // sent to determine when to re-close the dispatcher (manual transfer)
    final static String INITIAL_DATA_END_TO_REMOTE = "_INITIAL_DATA_END_TO_REMOTE"; // sent to client
    final static String INITIAL_DUMP_AS_XML = "_INITIAL_DATA_AS_XML"; // sent to client

    final static String VERSION_TOKEN = "_Ver_";
    final static String DUMP_POSTFIX = ".dump";
    final static String SUPPORTED_VERSIONS = "_supportedVersions";
    final static String INITIAL_FILES_LOCATION = "_initialFilesLocation";
    final static String INITIAL_UPDATE_ONLY_REGISTER = "_initialUpdateOnlyRegister";
    final static String INITIAL_UPDATE_START_BATCH = "_initialUpdateStartBatch";
    /** 
     * Signal sent to the initialUpdater to tell him to collect initialUpdate requests until
     * an INITIAL_UPDATE_START_BATCH has come.
     */
    final static String INITIAL_UPDATE_COLLECT = "_initialUpdateCollect";
    final static String INITIAL_DATA_ID = "_initialDataId"; // used for the directory name where to store the initial dump by manual transfer
    final static String KEEP_TRANSACTION_OPEN = "_keepTransactionOpen";
    
    final static String REPL_PREFIX_DEFAULT = "REPL_";
    final static String TRANSACTION_SEQ = "_TRANS_SEQ";
    final static String MESSAGE_SEQ = "_MESSAGE_SEQ";
   
   // properties
    final static String REPL_PREFIX_KEY = "replication.prefix";
   //  final static String REPLICATION_PREFIX = "replication.prefix";
    final static String REPLICATION_VERSION = "replication.version";
    final static String REPLICATION_FORCE_SENDING = "replication.forceSending";
    final static String REPLICATION_SEND_UNCHANGED_UPDATES = "replication.sendUnchangedUpdates";
   
    final static String REPLICATION_MAX_ENTRIES_KEY = "replication.maxEntries";
    final static int REPLICATION_MAX_ENTRIES_DEFAULT = 1;
    
    // topics
    final static String REQUEST_INITIAL_DATA_TOPIC = "replRequestInitialData";
    final static String REQUEST_CANCEL_UPDATE_TOPIC = "replRequestCancelUpdate";
    final static String REQUEST_BROADCAST_SQL_TOPIC = "replRequestBroadcastSQL";
    final static String REQUEST_RECREATE_TRIGGERS = "replRequestRecreateTriggers";

    final static String SIMPLE_MESSAGE = "replSimpleMessage";
    final static String RESPONSE_INITIAL_DATA_TOPIC = "replResponseInitialData";
    
    
}

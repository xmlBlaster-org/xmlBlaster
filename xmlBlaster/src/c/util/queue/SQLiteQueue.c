/*----------------------------------------------------------------------------
Name:      SQLiteQueue.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   A persistent queue implementation based on the SQLite relational database
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      04/2004
Compile:   
 build c -> creates libxmlBlasterClientC.so
 export LD_LIBRARY_PATH=/opt/sqlite-bin/lib:/home/xmlblast/xmlBlaster/lib
 gcc -g -Wall -DQUEUE_MAIN=1 -I../../ -o SQLiteQueue SQLiteQueue.c -I/opt/sqlite-bin/include -L/opt/sqlite-bin/lib -lsqlite -L/home/xmlblast/xmlBlaster/lib -lxmlBlasterClientC -lpthread
See:       http://www.sqlite.org/
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.queue.html
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/queue.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdarg.h> /* vsnprintf */
#include <string.h>
#include <malloc.h>
#include <unistd.h> /* sleep */
#include "util/queue/I_Queue.h"
#include "sqlite.h"

#include <pthread.h>
#define XB_USE_PTHREADS 1

#if defined(_WINDOWS)
#  define SNPRINTF _snprintf
#  define VSNPRINTF _vsnprintf
#else
#  define SNPRINTF snprintf
#  define VSNPRINTF vsnprintf
#endif
#define _INLINE_FUNC

static void persistentQueueInitialize(I_Queue *queueP, QueueException *exception);
static void persistentQueuePut(I_Queue *queueP, QueueEntry *queueEntry, QueueException *exception);
static QueueEntryArr *persistentQueuePeekWithSamePriority(I_Queue *queueP, int32_t maxNumOfEntries, int64_t maxNumOfBytes, QueueException *exception);
static int32_t persistentQueueRandomRemove(I_Queue *queueP, QueueEntryArr *queueEntryArr, QueueException *exception);
static bool persistentQueueClear(I_Queue *queueP, QueueException *exception);
static bool persistentQueueEmpty(I_Queue *queueP, QueueException *exception);
static void persistentQueueShutdown(I_Queue *queueP, QueueException *exception);
static bool checkArgs(I_Queue *queueP, const char *methodName, bool checkIsConnected, QueueException *exception);
static char *strncpy0(char * const to, const char * const from, const size_t maxLen);
static void initializeQueueException(QueueException *exception);
static bool createTables(I_Queue *queueP, QueueException *exception);
static bool execSilent(I_Queue *queueP, const char *sqlStatement, const char *comment, QueueException *exception);

static Dll_Export char *strFromBlobAlloc(const char *blob, const size_t len);

#define PREFIX_MAX 20
#define DBNAME_MAX 128
#define ID_MAX 256
/**
 * Holds Prepared statements for better performance. 
 * @see http://web.utk.edu/~jplyon/sqlite/SQLite_optimization_FAQ.html
 */
typedef struct DbInfoStruct {
   char dbname[DBNAME_MAX];      /** "xmlBlaster.db" */
   char tablePrefix[PREFIX_MAX]; /** "XB_" */
   char nodeId[ID_MAX];
   char queueName[ID_MAX];
   int32_t numOfEntries;
   int64_t numOfBytes;
   sqlite *db;
   sqlite_vm *pVm_put;
   sqlite_vm *pVm_peekWithSamePriority;
   sqlite_vm *pVm_randomRemove;
} DbInfo;

/**
 * Create a new persistent queue instance. 
 * <br />
 * This is usually the first call of a client, thereafter you need to call queueP->initialize(). 
 * <br />
 * NOTE: Our properties point on the passed argv memory, so you should
 * not free the original argv memory before you free XmlBlasterAccessUnparsed.
 * @param argc The number of argv properties
 * @param argv The command line properties, see <code>Properties *createProperties(int argc, const char* const* argv)</code>
 *             for a specification
 * @param logFp Your logging implementation or NULL if no logging callbacks are desired
 * @return NULL if bootstrapping failed. If not NULL you need to free() it when you are done
 *         usually by calling freeQueue().
 */
Dll_Export I_Queue *createQueue(int argc, const char* const* argv, I_QueueLogging logFp)
{
   I_Queue *queueP = (I_Queue *)calloc(1, sizeof(I_Queue));
   if (queueP == 0) return queueP;
   queueP->argc = argc;
   queueP->argv = argv;
   queueP->isInitialized = false;
   queueP->initialize = persistentQueueInitialize;
   queueP->put = persistentQueuePut;
   queueP->peekWithSamePriority = persistentQueuePeekWithSamePriority;
   queueP->randomRemove = persistentQueueRandomRemove;
   queueP->clear = persistentQueueClear;
   queueP->empty = persistentQueueEmpty;
   queueP->shutdown = persistentQueueShutdown;
   queueP->log = logFp;
   queueP->privateObject = calloc(1, sizeof(DbInfo));
   if (queueP->log) queueP->log(__FILE__, "Persistent queue is created");
   return queueP;
}

/** Access the DB handle, queueP pointer is not checked */
static DbInfo *getDbInfo(I_Queue *queueP) {
   return (DbInfo *)(queueP->privateObject);
}
static sqlite *getDb(I_Queue *queueP) {
   return ((DbInfo *)(queueP->privateObject))->db;
}
/*
static const char *getDbname(I_Queue *queueP) {
   return ((DbInfo *)(queueP->privateObject))->dbname;
}
static sqlite_vm *getVmPut(I_Queue *queueP) {
   return ((DbInfo *)(queueP->privateObject))->pVm_put;
}
static sqlite_vm *getVmPeekWithSamePriority(I_Queue *queueP) {
   return ((DbInfo *)(queueP->privateObject))->pVm_peekWithSamePriority;
}
static sqlite_vm *getVmRandomRemove(I_Queue *queueP) {
   return ((DbInfo *)(queueP->privateObject))->pVm_randomRemove;
}
*/

Dll_Export void freeQueue(I_Queue *queueP)
{
   if (queueP == 0) {
      fprintf(stderr, "[%s:%d] Please provide a valid I_Queue pointer to freeQueue()", __FILE__, __LINE__);
      return;
   }

   if (queueP->log) queueP->log(__FILE__, "freeQueue() called");

   if (queueP->privateObject) {
      sqlite *db = getDb(queueP);
      if (db) {
         sqlite_close(db);
      }
      free(queueP->privateObject);
      queueP->privateObject = 0;
   }

   free(queueP);
   queueP = 0;
}

static void persistentQueueInitialize(I_Queue *queueP, QueueException *exception)
{
   char *errMsg = 0;
   const char *dbname = "xmlBlasterClient.db";
   const char *nodeId = "clientJoe1081594557415";
   const char *queueName = "connection_clientJoe";
   bool retOk;
   const int OPEN_RW = 0;
   sqlite *db;
   DbInfo *dbInfo;

   if (checkArgs(queueP, "initialize", false, exception) == false ) return;

   dbInfo = getDbInfo(queueP);
   strncpy0(dbInfo->dbname, dbname, DBNAME_MAX);
   strncpy0(dbInfo->tablePrefix, "XB_", PREFIX_MAX);
   strncpy0(dbInfo->nodeId, nodeId, ID_MAX);
   strncpy0(dbInfo->queueName, queueName, ID_MAX);
   dbInfo->numOfEntries = 10000000l;
   dbInfo->numOfBytes =   1000000000ll;

   db = sqlite_open(dbInfo->dbname, OPEN_RW, &errMsg);
   dbInfo->db = db;

   if (db==0) {
      queueP->isInitialized = false;
      if(queueP->log) {
         if (errMsg)
            queueP->log(__FILE__, "%s", errMsg);
         else
            queueP->log(__FILE__, "Unable to open database '%s'", dbname);
      }
      else {
        if (errMsg)
           fprintf(stderr,"[%s] %s\n", __FILE__, errMsg);
        else
           fprintf(stderr,"[%s] Unable to open database %s\n", __FILE__, dbname);
      }
      strncpy0(exception->errorCode, "resource.db.unavailable", I_QUEUE_EXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, I_QUEUE_EXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Creating SQLiteQueue '%s' failed: %s", __FILE__, __LINE__, dbname, (errMsg==0)?"":errMsg);
      if (errMsg != 0) sqlite_freemem(errMsg);
      return;
   }

   queueP->isInitialized = true;

   retOk = createTables(queueP, exception);

   if (retOk) {
      char queryString[512];
      const char *tablePrefix = ((DbInfo *)(queueP->privateObject))->tablePrefix;
      sprintf(queryString, "INSERT INTO %.20sNODES VALUES ('%.200s');", tablePrefix, nodeId);
      retOk = execSilent(queueP, queryString, "Insert node", exception);
   }

   if (retOk) {
      char queryString[512];
      const char *tablePrefix = ((DbInfo *)(queueP->privateObject))->tablePrefix;
      sprintf(queryString, "INSERT INTO %.20sQUEUES VALUES ('%s','%s',%lld,%d);",
              tablePrefix, queueName, nodeId, dbInfo->numOfBytes, dbInfo->numOfEntries);
      retOk = execSilent(queueP, queryString, "Insert queue", exception);
   }

   if (queueP->log) queueP->log(__FILE__, "initialize(%s) %s", dbname, retOk?"successful":"failed");
}

/**
 * Create the necessary DB table if not already existing. 
 * @param nodeId "clientJoe1081594557415"
 * @param queueName "connection_clientJoe"
 * @return true on success
 */
static bool createTables(I_Queue *queueP, QueueException *exception)
{
   char queryString[512];
   bool retOk;
   const char *tablePrefix = ((DbInfo *)(queueP->privateObject))->tablePrefix;

   sprintf(queryString, "CREATE TABLE %.20sNODES (nodeId text , PRIMARY KEY (nodeId));", tablePrefix);  /* XB_NODES */
   retOk = execSilent(queueP, queryString, "Creating NODES table", exception);

   sprintf(queryString, "CREATE TABLE %.20sQUEUES (queueName text , nodeId text , numOfBytes bigint, numOfEntries bigint, PRIMARY KEY (queueName, nodeId), FOREIGN KEY (nodeId) REFERENCES XB_NODES (nodeId));",
           tablePrefix);
   retOk = execSilent(queueP, queryString, "Creating QUEUES table", exception);

   sprintf(queryString, "CREATE TABLE %.20sENTRIES (dataId bigint , nodeId text , queueName text , prio integer, flag text, durable char(1), byteSize bigint, blob bytea, PRIMARY KEY (dataId, queueName), FOREIGN KEY (queueName, nodeId) REFERENCES XB_QUEUES (queueName , nodeId));",
           tablePrefix);
   retOk = execSilent(queueP, queryString, "Creating ENTRIES table", exception);
   return retOk;
}

/**
 * Invoke SQL query. 
 * @param queueP Is not checked, must not be 0
 * @param queryString The SQL to execute
 * @param comment For logging or exception text
 * @return true on success
 */
static bool execSilent(I_Queue *queueP, const char *queryString, const char *comment, QueueException *exception)
{
   int rc = 0;
   char *errMsg = 0;
   bool retOk;
   DbInfo *dbInfo = getDbInfo(queueP);

   rc = sqlite_exec(dbInfo->db, queryString, NULL, NULL, &errMsg);
   switch (rc) {
      case SQLITE_OK:
         if (queueP->log) queueP->log(__FILE__, "SQL '%s' success", comment);
         retOk = true;
         break;
      default:
         if (errMsg && strstr(errMsg, "already exists")) {
            if (queueP->log) queueP->log(__FILE__, "OK, '%s' [%d]: %s", comment, rc, (errMsg==0)?"":errMsg);
            retOk = true;
         }
         else if (rc == SQLITE_CONSTRAINT && errMsg && strstr(errMsg, " not unique")) {
            if (queueP->log) queueP->log(__FILE__, "OK, '%s' entry existed already [%d]: %s", comment, rc, (errMsg==0)?"":errMsg);
            retOk = true;
         }
         else {
            if (queueP->log) queueP->log(__FILE__, "SQL error '%s' [%d]: %s", comment, rc, (errMsg==0)?"":errMsg);
            strncpy0(exception->errorCode, "resource.db.unknown", I_QUEUE_EXCEPTION_ERRORCODE_LEN);
            SNPRINTF(exception->message, I_QUEUE_EXCEPTION_MESSAGE_LEN,
                     "[%.100s:%d] SQL error '%s' [%d]: %s", __FILE__, __LINE__, comment, rc, (errMsg==0)?"":errMsg);
            retOk = false;
         }
         break;
   }
   if (errMsg != 0) sqlite_freemem(errMsg);
   return retOk;
}

/*
 * This is the callback routine that the SQLite library
 * invokes for each row of a query result.
static int callback(void *pArg, int nArg, char **azArg, char **azCol){
   int i;
   struct callback_data *p = (struct callback_data*)pArg;
   int w = 5;
   if (p==0) {} // Suppress compiler warning
   if( azArg==0 ) return 0;
   for(i=0; i<nArg; i++){
      int len = strlen(azCol[i]);
      if( len>w ) w = len;
   }
   printf("\n");
   for(i=0; i<nArg; i++){
      printf("%*s = %s\n", w, azCol[i], azArg[i] ? azArg[i] : "NULL");
   }
  return 0;
}
*/

static void persistentQueuePut(I_Queue *queueP, QueueEntry *queueEntry, QueueException *exception)
{
   int iRetry, numRetry=10;
   char *errMsg = 0;
   int rc = 0;
   const char *pzTail = 0;   /* OUT: uncompiled tail of zSql */
   bool stateOk = true;
   DbInfo *dbInfo;

   if (checkArgs(queueP, "put", true, exception) == false ) return;

   if (queueP->log) queueP->log(__FILE__, "put() entering ...");

   dbInfo = getDbInfo(queueP);

   if (dbInfo->pVm_put == 0) {  /* Compile prepared query only once */
      char queryString[256];
      /*INSERT INTO XB_ENTRIES VALUES ( 1081317015888000000, 'xmlBlaster_192_168_1_4_3412', 'topicStore_xmlBlaster_192_168_1_4_3412', 5, 'TOPIC_XML', 'T', 670, '\\254...')*/
      sprintf(queryString, "INSERT INTO %.20sENTRIES VALUES ( ?, ?, ?, ?, ?, ?, ?, ?)", dbInfo->tablePrefix);
      //sprintf(queryString, "SELECT name, type FROM sqlite_master WHERE type like ?");
      for (iRetry = 0; iRetry < numRetry; iRetry++) {
         rc = sqlite_compile(dbInfo->db, queryString, &pzTail, &dbInfo->pVm_put, &errMsg);
         switch (rc) {
            case SQLITE_BUSY:
               if (queueP->log) queueP->log(__FILE__, "put(%lld) Sleeping as other thread holds DB %s", queueEntry->uniqueId, (errMsg==0)?"":errMsg);
               if (errMsg != 0) { sqlite_freemem(errMsg); errMsg = 0; }
               sleep(1);
               break;
            case SQLITE_OK:
               iRetry = numRetry; /* We're done */
               if (errMsg != 0) { sqlite_freemem(errMsg); errMsg = 0; }
               break;
            default:
               fprintf(stderr, "SQL error: %s\n", (errMsg==0)?"":errMsg);
               if (queueP->log) queueP->log(__FILE__, "put(%lld) SQL error: %s", queueEntry->uniqueId, (errMsg==0)?"":errMsg);
               strncpy0(exception->errorCode, "resource.db.unknown", I_QUEUE_EXCEPTION_ERRORCODE_LEN);
               SNPRINTF(exception->message, I_QUEUE_EXCEPTION_MESSAGE_LEN,
                        "[%.100s:%d] put(%lld) SQL error: %s", __FILE__, __LINE__, queueEntry->uniqueId, (errMsg==0)?"":errMsg);
               iRetry = numRetry; /* We're done */
               if (errMsg != 0) { sqlite_freemem(errMsg); errMsg = 0; }
               stateOk = false;
               break;
         }
      }
   }
   if (dbInfo->pVm_put == 0) stateOk = false;

   if (stateOk) { /* set prepared statement tokens */
      char tmp[256];
      int index = 0;
      int len = -1; /* Calculated by sqlite_bind */
      rc = SQLITE_OK;

      sprintf(tmp, "%lld", queueEntry->uniqueId);
      if (queueP->log) queueP->log(__FILE__, "put uniqueId as string '%s'", tmp);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, tmp, len, true);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, dbInfo->nodeId, len, false);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, dbInfo->queueName, len, false);
      sprintf(tmp, "%d", queueEntry->priority);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, tmp, len, true);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, queueEntry->embeddedType, len, false);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, queueEntry->isPersistent?"T":"F", len, false);
      sprintf(tmp, "%d", (int32_t)queueEntry->embeddedBlob.dataLen);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, tmp, len, true);
      if (rc == SQLITE_OK) {
         if (queueEntry->embeddedBlob.dataLen == 0 || queueEntry->embeddedBlob.data == 0) {
            rc = sqlite_bind(dbInfo->pVm_put, ++index, "", 1, false);
         }
         else { /* As SQLite does only store strings we encode our blob to a string */
            int estimatedSize = 2 +(257 * queueEntry->embeddedBlob.dataLen )/254;
            unsigned char *out = (unsigned char *)malloc(estimatedSize*sizeof(char));
            int newSize =
              sqlite_encode_binary((const unsigned char *)queueEntry->embeddedBlob.data,
                                 (int)queueEntry->embeddedBlob.dataLen, out);
            if (queueP->log) queueP->log(__FILE__, "put(%lld) Blob size=%d encoded to size=%d: %.100s",
                             queueEntry->uniqueId, queueEntry->embeddedBlob.dataLen, newSize, out);
            rc = sqlite_bind(dbInfo->pVm_put, ++index, (const char *)out, newSize, true);
            free(out);
         }
      }

      switch (rc) {
         case SQLITE_OK:
            if (queueP->log) queueP->log(__FILE__, "put(%lld) Bound to prepared statement [sqlCode=%d]", queueEntry->uniqueId, rc);
            break;
         default:
            fprintf(stderr, "SQL error from sqlite_bind: %d\n", rc);
            if (queueP->log) queueP->log(__FILE__, "put(%lld) SQL error: %d", queueEntry->uniqueId, rc);
            strncpy0(exception->errorCode, "resource.db.unknown", I_QUEUE_EXCEPTION_ERRORCODE_LEN);
            SNPRINTF(exception->message, I_QUEUE_EXCEPTION_MESSAGE_LEN,
                     "[%.100s:%d] put(%lld) SQL error: %d", __FILE__, __LINE__, queueEntry->uniqueId, rc);
            stateOk = false;
            break;
      }
   }

   if (stateOk) { /* start the query */
      int iCol;
      int iRow = 0;
      int numCol = 0;
      const char **pazValue = 0;
      const char **pazColName = 0;
      bool done = false;
      while (!done) {
         rc = sqlite_step(dbInfo->pVm_put, &numCol, &pazValue, &pazColName);
         switch( rc ){
            case SQLITE_DONE:
               if (queueP->log) queueP->log(__FILE__, "put(%lld) executed.", queueEntry->uniqueId);
               done = true;
            break;
            case SQLITE_BUSY:
               if (queueP->log) queueP->log(__FILE__, "put(%lld) Sleeping as other thread holds DB.", queueEntry->uniqueId);
               sleep(1);
            break;
            case SQLITE_ROW:
               iRow++;
               printf("RESULT[%d]\n", iRow);
               for (iCol = 0; iCol < numCol; iCol++) {
                  printf("%10.10s = %s\n", pazColName[iCol], pazValue[iCol]);
               }
            break;
            case SQLITE_ERROR:   /* If exists already */
            case SQLITE_MISUSE:
            default: /* case SQLITE_ERROR: case SQLITE_MISUSE: */
               if (queueP->log) queueP->log(__FILE__, "put(%lld) SQL execution problem [sqlCode=%d]", queueEntry->uniqueId, rc);
               done = true;
            break;
         }
      }
   }

   if (stateOk) { /* Reset prepared statement */
      rc = sqlite_reset(dbInfo->pVm_put, &errMsg);
      if (errMsg != 0) sqlite_freemem(errMsg);
   }

   /*
   if (stateOk) {
      rc = sqlite_finalize(dbInfo->pVm_put, &errMsg);
      if (errMsg != 0) sqlite_freemem(errMsg);
      dbInfo->pVm_put = 0;
   }
   */

   if (queueP->log) queueP->log(__FILE__, "put(%lld) %s", queueEntry->uniqueId, stateOk ? "done" : "failed");
}

/**
 * Access queue entries without removing them. 
 */
static QueueEntryArr *persistentQueuePeekWithSamePriority(I_Queue *queueP, int32_t maxNumOfEntries, int64_t maxNumOfBytes, QueueException *exception)
{
   int iRetry, numRetry=10;
   char *errMsg = 0;
   int rc = 0;
   const char *pzTail = 0;   /* OUT: uncompiled tail of zSql */
   bool stateOk = true;
   DbInfo *dbInfo;
   QueueEntryArr *queueEntryArr = (QueueEntryArr *)calloc(1, sizeof(QueueEntryArr));;

   if (checkArgs(queueP, "peekWithSamePriority", true, exception) == false ) return 0;

   if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() of max %d entries (%lld bytes) ...", (int)maxNumOfEntries, maxNumOfBytes);

   dbInfo = getDbInfo(queueP);

   if (dbInfo->pVm_peekWithSamePriority == 0) {  /* Compile prepared  query */
      char queryString[512];
      //"SELECT * FROM XB_ENTRIES where queueName='connection_clientJoe' and nodeId='clientJoe1081594557415' and prio=(select max(prio) from XB_ENTRIES where queueName='connection_clientJoe' AND nodeId='clientJoe1081594557415') ORDER BY dataId ASC";
      sprintf(queryString, 
           "SELECT * FROM %.20sENTRIES where queueName=? and nodeId=?"
           " and prio=(select max(prio) from %.20sENTRIES where queueName=?  AND nodeId=?)"
           " ORDER BY dataId ASC",
           dbInfo->tablePrefix, dbInfo->tablePrefix);

      for (iRetry = 0; iRetry < numRetry; iRetry++) {
         rc = sqlite_compile(dbInfo->db, queryString, &pzTail, &dbInfo->pVm_peekWithSamePriority, &errMsg);
         switch (rc) {
            case SQLITE_BUSY:
               if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() Sleeping as other thread holds DB %s", (errMsg==0)?"":errMsg);
               if (errMsg != 0) { sqlite_freemem(errMsg); errMsg = 0; }
               sleep(1);
               break;
            case SQLITE_OK:
               iRetry = numRetry; /* We're done */
               if (errMsg != 0) { sqlite_freemem(errMsg); errMsg = 0; }
               break;
            default:
               fprintf(stderr, "SQL error: %s\n", (errMsg==0)?"":errMsg);
               if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() SQL error: %s", (errMsg==0)?"":errMsg);
               strncpy0(exception->errorCode, "resource.db.unknown", I_QUEUE_EXCEPTION_ERRORCODE_LEN);
               SNPRINTF(exception->message, I_QUEUE_EXCEPTION_MESSAGE_LEN,
                        "[%.100s:%d] peekWithSamePriority() SQL error: %s", __FILE__, __LINE__, (errMsg==0)?"":errMsg);
               iRetry = numRetry; /* We're done */
               if (errMsg != 0) { sqlite_freemem(errMsg); errMsg = 0; }
               stateOk = false;
               break;
         }
      }
   }
   if (dbInfo->pVm_peekWithSamePriority == 0) stateOk = false;

   if (stateOk) { /* set prepared statement tokens */
      int index = 0;
      int len = -1; /* Calculated by sqlite_bind */
      rc = SQLITE_OK;

      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_peekWithSamePriority, ++index, dbInfo->queueName, len, false);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_peekWithSamePriority, ++index, dbInfo->nodeId, len, false);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_peekWithSamePriority, ++index, dbInfo->queueName, len, false);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_peekWithSamePriority, ++index, dbInfo->nodeId, len, false);

      switch (rc) {
         case SQLITE_OK:
            if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() Bound to prepared statement [sqlCode=%d]", rc);
            break;
         default:
            fprintf(stderr, "SQL error from sqlite_bind: %d\n", rc);
            if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() SQL error: %d", rc);
            strncpy0(exception->errorCode, "resource.db.unknown", I_QUEUE_EXCEPTION_ERRORCODE_LEN);
            SNPRINTF(exception->message, I_QUEUE_EXCEPTION_MESSAGE_LEN,
                     "[%.100s:%d] peekWithSamePriority() SQL error: %d", __FILE__, __LINE__, rc);
            stateOk = false;
            break;
      }
   }

   if (stateOk) { /* start the query */
      QueueEntry *queueEntry = 0;
      size_t currIndex = 0;
      int numAssigned;
      //int iCol;
      //int iRow = 0;
      int numCol = 0;
      const char **pazValue = 0;
      const char **pazColName = 0;
      bool done = false;
      int decodeSize = 0;
      queueEntryArr->len = 10;
      queueEntryArr->queueEntryArr = (QueueEntry *)calloc(queueEntryArr->len, sizeof(QueueEntry));
      while (!done) {
         rc = sqlite_step(dbInfo->pVm_peekWithSamePriority, &numCol, &pazValue, &pazColName);
         switch( rc ){
            case SQLITE_DONE:
               if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() executed.");
               done = true;
            break;
            case SQLITE_BUSY:
               if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() Sleeping as other thread holds DB.");
               sleep(1);
            break;
            case SQLITE_ROW:
               /*
               iRow++;
               printf("RESULT[%d]\n", iRow);
               for (iCol = 0; iCol < numCol; iCol++) {
                  printf("%10.10s = %s\n", pazColName[iCol], pazValue[iCol]);
               }
               */
               if (currIndex >= queueEntryArr->len) {
                  queueEntryArr->len += 10;
                  queueEntryArr->queueEntryArr = (QueueEntry *)realloc(queueEntryArr->queueEntryArr, queueEntryArr->len * sizeof(QueueEntry));
               }
               queueEntry = &queueEntryArr->queueEntryArr[currIndex++];
               memset(queueEntry, 0, sizeof(QueueEntry));

               numAssigned = sscanf(pazValue[0], "%lld", &queueEntry->uniqueId);  /* TODO: handle error */
               if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority uniqueId as int '%lld' as string '%s', ret=%d", queueEntry->uniqueId, pazValue[0], numAssigned);
               /* strncpy0(dbInfo->nodeId, pazValue[1], ID_MAX); TODO: assert() */
               /* strncpy0(dbInfo->queueName, pazValue[2], ID_MAX); */
               numAssigned = sscanf(pazValue[3], "%hd", &queueEntry->priority);
               strncpy0(queueEntry->embeddedType, pazValue[4], QUEUE_ENTRY_EMBEDDEDTYPE_LEN);
               queueEntry->isPersistent = *pazValue[5] == 'T' ? true : false;
               {
                  int64_t ival = 0;
                  numAssigned = sscanf(pazValue[6], "%lld", &ival);
                  queueEntry->embeddedBlob.dataLen = (size_t)ival;
               }

               // TODO!!! in Java the length is the size in RAM and not strlen(data)
               //queueEntry->embeddedBlob.data = (char *)malloc(queueEntry->embeddedBlob.dataLen*sizeof(char));
               queueEntry->embeddedBlob.data = (char *)malloc(strlen(pazValue[7])*sizeof(char)); // we spoil some 2 %
               decodeSize = sqlite_decode_binary((const unsigned char *)pazValue[7], (unsigned char *)queueEntry->embeddedBlob.data);
               if (decodeSize != queueEntry->embeddedBlob.dataLen) {
                  if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() ERROR: Returned blob len=%d but expected len=%d.",
                                   decodeSize, queueEntry->embeddedBlob.dataLen);
                  // TODO: create exception
               }
               if (false) {
                  char *dump = queueEntryToXmlLimited(queueEntry, 200);
                  if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() Read:%s", dump);
                  free(dump);
               }
            break;
            case SQLITE_ERROR:   /* If exists already */
            case SQLITE_MISUSE:
            default:
               if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority(%lld) SQL execution problem [sqlCode=%d]", queueEntry->uniqueId, rc);
               done = true;
            break;
         }
      }
      if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() Read %lu entries", (unsigned long)currIndex);
      if (currIndex == 0) {
         free(queueEntryArr->queueEntryArr);
         queueEntryArr->len = 0;
      }
      else if (currIndex < queueEntryArr->len) {
         queueEntryArr->queueEntryArr = (QueueEntry *)realloc(queueEntryArr->queueEntryArr, currIndex * sizeof(QueueEntry));
         queueEntryArr->len = currIndex; 
      }
   }

   if (stateOk) { /* Reset prepared statement */
      rc = sqlite_reset(dbInfo->pVm_peekWithSamePriority, &errMsg);
      if (errMsg != 0) sqlite_freemem(errMsg);
   }

   if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() %s", stateOk ? "done" : "failed");
   return queueEntryArr;
}

static int32_t persistentQueueRandomRemove(I_Queue *queueP, QueueEntryArr *queueEntryArr, QueueException *exception)
{
   if (queueP->log) queueP->log(__FILE__, "randomRemove() of %d entries done", (queueEntryArr==0) ? 0 : (int)queueEntryArr->len);
   return 0;
}

static bool persistentQueueClear(I_Queue *queueP, QueueException *exception)
{
   if (queueP->log) queueP->log(__FILE__, "clear() done");
   return true;
}

static bool persistentQueueEmpty(I_Queue *queueP, QueueException *exception)
{
   if (checkArgs(queueP, "empty", true, exception) == false ) return true;

   {
      int iRetry, numRetry=20;
      char *errMsg = 0;
      int retVal;
      DbInfo *dbInfo = getDbInfo(queueP);
      char queryString[512]; //"SELECT count(dataId) FROM XB_ENTRIES where queueName='connection_clientJoe' and nodeId='clientJoe1081594557415'"

      sprintf(queryString, 
              "SELECT count(dataId) FROM %.20sENTRIES where queueName='%s' and nodeId='%s'",
              dbInfo->tablePrefix, dbInfo->queueName, dbInfo->nodeId);

      if (queueP->log) queueP->log(__FILE__, "empty() called");
      for (iRetry = 0; iRetry < numRetry; iRetry++) {
         retVal = sqlite_exec(dbInfo->db, queryString, 0, 0, &errMsg);
         switch (retVal) {
         case SQLITE_BUSY:
            if (queueP->log) queueP->log(__FILE__, "empty() Sleeping as other thread holds DB %s", (errMsg==0)?"":errMsg);
            sleep(1);
            break;
         case SQLITE_OK:
            if (queueP->log) queueP->log(__FILE__, "empty() Queried");
            iRetry = numRetry;
            break;
         default:
            if (queueP->log) queueP->log(__FILE__, "empty() ERROR !!!");
            iRetry = numRetry;
            break;
         }
         if (errMsg != 0) { sqlite_freemem(errMsg); errMsg = 0; }
      }
   }
   return true;
}

static void persistentQueueShutdown(I_Queue *queueP, QueueException *exception)
{
   if (checkArgs(queueP, "shutdown()", false, exception) == false ) return;
   {
      DbInfo *dbInfo = getDbInfo(queueP);
      if(dbInfo && dbInfo->db) {
         if (dbInfo->pVm_put) {
            char *errMsg = 0;
            /*int rc =*/ sqlite_finalize(dbInfo->pVm_put, &errMsg);
            if (errMsg != 0) sqlite_freemem(errMsg);
            dbInfo->pVm_put = 0;
         }
         if (dbInfo->pVm_peekWithSamePriority) {
            char *errMsg = 0;
            sqlite_finalize(dbInfo->pVm_peekWithSamePriority, &errMsg);
            if (errMsg != 0) sqlite_freemem(errMsg);
            dbInfo->pVm_peekWithSamePriority = 0;
         }
         if (dbInfo->pVm_randomRemove) {
            char *errMsg = 0;
            sqlite_finalize(dbInfo->pVm_randomRemove, &errMsg);
            if (errMsg != 0) sqlite_freemem(errMsg);
            dbInfo->pVm_randomRemove = 0;
         }
         sqlite_close(dbInfo->db);
         dbInfo->db = 0;
         if (queueP->log) queueP->log(__FILE__, "shutdown() done");
      }
   }
}

/**
 * Guarantees a '\0' terminated string
 * @param to The destination string must be big enough
 * @param from The source to be copied
 * @param (maxLen-1) of 'to' will be filled with a '\0',
 *        so effectively only maxLen-1 from 'from' are copied.
 * @return The destination string 'to'
 */
static char *strncpy0(char * const to, const char * const from, const size_t maxLen)
{
   char *ret=strncpy(to, from, maxLen-1);
   *(to+maxLen-1) = '\0';
   return ret;
}

/**
 * Frees everything inside QueueEntryArr and the struct QueueEntryArr itself
 * @param queueEntryArr The struct to free, passing NULL is OK
 */
Dll_Export void freeQueueEntryArr(QueueEntryArr *queueEntryArr)
{
   if (queueEntryArr == (QueueEntryArr *)0) return;
   freeQueueEntryArrInternal(queueEntryArr);
   free(queueEntryArr);
}

/**
 * Frees everything inside QueueEntryArr but NOT the struct QueueEntryArr itself
 * @param queueEntryArr The struct internals to free, passing NULL is OK
 */
Dll_Export void freeQueueEntryArrInternal(QueueEntryArr *queueEntryArr)
{
   size_t i;
   if (queueEntryArr == (QueueEntryArr *)0) return;
   for (i=0; i<queueEntryArr->len; i++) {
      freeQueueEntryData(&queueEntryArr->queueEntryArr[i]);
   }
   free(queueEntryArr->queueEntryArr);
   queueEntryArr->len = 0;
}

/**
 * Does not free the queueEntry itself
 */
Dll_Export void freeQueueEntryData(QueueEntry *queueEntry)
{
   if (queueEntry == (QueueEntry *)0) return;
   if (queueEntry->embeddedBlob.data != 0) {
      free((char *)queueEntry->embeddedBlob.data);
      queueEntry->embeddedBlob.data = 0;
   }
   queueEntry->embeddedBlob.dataLen = 0;
}

/**
 * Frees everything. 
 */
Dll_Export void freeQueueEntry(QueueEntry *queueEntry)
{
   if (queueEntry == (QueueEntry *)0) return;
   freeQueueEntryData(queueEntry);
   free(queueEntry);
}

/**
 * NOTE: You need to free the returned pointer with free()!
 *
 * @param maxContentDumpLen for -1 get the complete content, else limit the
 *        content to the given number of bytes
 * @return A ASCII XML formatted entry or NULL if out of memory
 */
Dll_Export char *queueEntryToXmlLimited(QueueEntry *queueEntry, int maxContentDumpLen)
{
   if (queueEntry == (QueueEntry *)0) return 0;
   char *contentStr = strFromBlobAlloc(queueEntry->embeddedBlob.data, queueEntry->embeddedBlob.dataLen);
   size_t len = 200 + QUEUE_ENTRY_EMBEDDEDTYPE_LEN + queueEntry->embeddedBlob.dataLen;
   char *xml = (char *)calloc(len, sizeof(char));
   if (xml == 0) {
      free(contentStr);
      return 0;
   }
   if (maxContentDumpLen == 0)
      *contentStr = 0;
   else if (maxContentDumpLen > 0 && queueEntry->embeddedBlob.dataLen > 5 &&
            (size_t)maxContentDumpLen < (queueEntry->embeddedBlob.dataLen-5))
      strcpy(contentStr+maxContentDumpLen, " ...");

   SNPRINTF(xml, len, "\n <QueueEntry id='%lld' priority='%hd' persistent='%s' type='%s'>"
                      "\n  <content size='%lu'><![CDATA[%s]]></content>"
                      "\n <QueueEntry>",
                        queueEntry->uniqueId, queueEntry->priority,
                        queueEntry->isPersistent?"true":"false",
                        queueEntry->embeddedType,
                        (unsigned long)queueEntry->embeddedBlob.dataLen, contentStr);
   free(contentStr);
   return xml;
}

/**
 * Allocates the string with malloc for you. 
 * NOTE: If your given blob or len is 0 an empty string of size 1 is returned
 * @return The string, never null.
 *         You need to free it with free()
 */
static Dll_Export char *strFromBlobAlloc(const char *blob, const size_t len)
{
   char *dest;
   size_t i;
   if (blob == 0 || len < 1) {
      dest = (char *)malloc(1*sizeof(char));
      if (dest == 0) return 0;
      *dest = 0;
      return dest;
   }

   dest = (char *)malloc((len+1)*sizeof(char));
   if (dest == 0) return 0;
   for (i=0; i<len; i++) {
      dest[i] = (char)blob[i];
   }
   dest[len] = '\0';
   return dest;
}

/**
 * NOTE: You need to free the returned pointer with free()!
 *
 * @return A ASCII XML formatted message or NULL if out of memory
 */
Dll_Export char *queueEntryToXml(QueueEntry *queueEntry)
{
   return queueEntryToXmlLimited(queueEntry, -1);
}

/**
 * Checks the given arguments to be valid. 
 * @return false if the parameters are not usable,
 *         in this case 'exception' is filled with detail informations
 */
static bool checkArgs(I_Queue *queueP, const char *methodName,
                      bool checkIsConnected, QueueException *exception)
{
   if (queueP == 0) {
      if (exception == 0) {
         printf("[%s:%d] Please provide a valid I_Queue pointer to %s()",
                  __FILE__, __LINE__, methodName);
      }
      else {
         strncpy0(exception->errorCode, "resource.db.unavailable", I_QUEUE_EXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception->message, I_QUEUE_EXCEPTION_MESSAGE_LEN,
                  "[%.100s:%d] Please provide a valid I_Queue pointer to %.16s()",
                   __FILE__, __LINE__, methodName);
         if (queueP->log) queueP->log(__FILE__, "%s: %s", exception->errorCode, exception->message);
      }
      return false;
   }

   if (exception == 0) {
      queueP->log( __FILE__, "[%s:%d] Please provide valid exception pointer to %s()",
                   __FILE__, __LINE__, methodName);
      return false;
   }

   if (checkIsConnected) {
      if (queueP->privateObject==0 ||
          ((DbInfo *)(queueP->privateObject))->db==0 ||
          !queueP->isInitialized) {
         strncpy0(exception->errorCode, "resource.db.unavailable", I_QUEUE_EXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception->message, I_QUEUE_EXCEPTION_MESSAGE_LEN,
                  "[%.100s:%d] Not connected to database, %s() failed",
                   __FILE__, __LINE__, methodName);
         queueP->log(__FILE__, "%s: %s", exception->errorCode, exception->message);
         return false;
      }
   }

   initializeQueueException(exception);

   return true;
}

/**
 * Should be called on any QueueException before using it. 
 */
Dll_Export _INLINE_FUNC void initializeQueueException(QueueException *exception)
{
   exception->remote = false;
   *exception->errorCode = (char)0;
   *exception->message = (char)0;
}


/*=================== TESTCODE =======================*/
# ifdef QUEUE_MAIN
#include <stdio.h>
#include <stdarg.h>
static void defaultLogging(const char *location, const char *fmt, ...);

static void testRun(int argc, char **argv) {
   QueueException exception;
   I_Queue *queueP = createQueue(argc, (const char* const*)argv, defaultLogging);
   queueP->initialize(queueP, &exception);

   {
      int64_t idArr[] =   { 1081492136826000000ll, 1081492136856000000ll, 1081492136876000000ll };
      int16_t prioArr[] = { 5                    , 1                    , 5 };
      char *data[] =      { "Hello"              , " World"             , "!!!" };
      int i;
      for (i=0; i<sizeof(idArr)/sizeof(int64_t); i++) {
         QueueEntry queueEntry;
         queueEntry.priority = prioArr[i];
         queueEntry.isPersistent = true;
         queueEntry.uniqueId = idArr[i];
         strncpy0(queueEntry.embeddedType, "MSG_RAW|publish", QUEUE_ENTRY_EMBEDDEDTYPE_LEN);
         queueEntry.embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN-1] = 0;
         queueEntry.embeddedBlob.data = data[i];
         queueEntry.embeddedBlob.dataLen = strlen(queueEntry.embeddedBlob.data);

         queueP->put(queueP, &queueEntry, &exception);
      }
   }
   
   QueueEntryArr *entries = queueP->peekWithSamePriority(queueP, -1, -1, &exception);
   if (entries != 0) {
      int i;
      if (queueP->log) queueP->log(__FILE__, "peekWithSamePriority() dump %lu entries:", (unsigned long)entries->len);
      for (i=0; i<entries->len; i++) {
         QueueEntry *queueEntry = &entries->queueEntryArr[i];
         char *dump = queueEntryToXmlLimited(queueEntry, 200);
         if (queueP->log) queueP->log(__FILE__, "%s", dump);
         free(dump);
      }
   }
   freeQueueEntryArr(entries);

   QueueEntryArr queueEntryArr;
   queueEntryArr.len = 0;
   queueP->randomRemove(queueP, &queueEntryArr, &exception);
   
   queueP->clear(queueP, &exception);
   queueP->empty(queueP, &exception);
   queueP->shutdown(queueP, &exception);
   freeQueue(queueP);
}

int main(int argc, char **argv) {
   int i;
   for (i=0; i<1; i++) {
      testRun(argc, argv);
   }
   return 0;
}

#include <time.h>
/**
 * Default logging output is handled by this method. 
 * The logging output is to console.
 * <p>
 * If you have your own logging device you need to implement this method
 * yourself and register it with 
 * </p>
 * <pre>
 * queueP->log = myLoggingHandler;
 * </pre>
 * @param location A string describing the code place
 * @param fmt The formatting string
 * @param ... Other variables to log, corresponds to 'fmt'
 */
static void defaultLogging(const char *location, const char *fmt, ...)
{
   /* Guess we need no more than 200 bytes. */
   int n, size = 200;
   char *p = 0;
   va_list ap;
   char *stackTrace = 0;

   if ((p = (char *)malloc (size)) == 0)
      return;

   for (;;) {
      /* Try to print in the allocated space. */
      va_start(ap, fmt);
      n = VSNPRINTF(p, size, fmt, ap);
      va_end(ap);
      /* If that worked, print the string to console. */
      if (n > -1 && n < size) {
         time_t t1;
         char timeStr[128];
         (void) time(&t1);
#        if defined(_WINDOWS)
            strcpy(timeStr, ctime(&t1));
#        elif defined(__sun)
            ctime_r(&t1, (char *)timeStr, 126);
#        else
            ctime_r(&t1, (char *)timeStr);
#        endif
         *(timeStr + strlen(timeStr) - 1) = '\0'; /* strip \n */
#        ifdef XB_USE_PTHREADS
            printf("[%s %s thread0x%x] %s %s\n", timeStr, location,
                                    (int)pthread_self(), p,
                                    (stackTrace != 0) ? stackTrace : "");
#        else
            printf("[%s %s] %s %s\n", timeStr, location, p,
                                    (stackTrace != 0) ? stackTrace : "");
#        endif
         free(p);
         free(stackTrace);
         return;
      }
      /* Else try again with more space. */
      if (n > -1)    /* glibc 2.1 */
         size = n+1; /* precisely what is needed */
      else           /* glibc 2.0 */
         size *= 2;  /* twice the old size */
      if ((p = (char *)realloc (p, size)) == 0) {
         free(stackTrace);
         return;
      }
   }
}
#endif /*QUEUE_MAIN*/
/*=================== TESTCODE =======================*/


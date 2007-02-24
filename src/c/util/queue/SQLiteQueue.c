/*----------------------------------------------------------------------------
Name:      SQLiteQueue.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   A persistent queue implementation based on the SQLite relational database
           Depends only on I_Queue.h and ../helper.c and ../helper.h (which includes basicDefs.h)
           and can easily be used outside of xmlBlaster.
           Further you need sqlite.h and the sqlite library (dll,so,sl)
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      04/2004
Compile:   Compiles at least on Windows, Linux, Solaris. Further porting should be simple.
           Needs pthread.h but not the pthread library (for exact times)

            export LD_LIBRARY_PATH=/opt/sqlite-bin/lib
            gcc -g -Wall -DQUEUE_MAIN=1 -I../../ -o SQLiteQueue SQLiteQueue.c ../helper.c -I/opt/sqlite-bin/include -L/opt/sqlite-bin/lib -lsqlite
            (use optionally  -ansi -pedantic -Wno-long-long
            (Intel C: icc -wd981 ...)

           Compile inside xmlBlaster:
            build -DXMLBLASTER_PERSISTENT_QUEUE=true c-delete c
           expects xmlBlaster/src/c/util/queue/sqlite.h and xmlBlaster/lib/libsqlite.so


           Compile Test main()
            Replace /I\c\sqlite for your needs ( says where sqlite.h resides ):
				and \pialibs\sqlite.lib as well ( sqlite.lib is created from sqlite.def via lib /DEF:sqlite.def)
           cl /MD /DQUEUE_MAIN /DDLL_IGNORE /DXB_NO_PTHREADS /D_WINDOWS /I\c\sqlite /I..\.. SqliteQueue.c ..\helper.c /link \pialibs\sqlite.lib


Table layout XB_ENTRIES:
           dataId bigint
           queueName text
           prio integer
           flag text
           durable char(1)
           byteSize bigint
           blob bytea
           PRIMARY KEY (dataId, queueName)

Todo:      Tuning:
            - Add prio to PRIMARY KEY
            - In persistentQueuePeekWithSamePriority() add queueName to statement as it never changes

@see:      http://www.sqlite.org/
@see:      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.queue.html
@see:      http://www.xmlblaster.org/xmlBlaster/doc/requirements/queue.html
Testsuite: xmlBlaster/testsuite/src/c/TestQueue.c
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <string.h>
#include <malloc.h>
#if !defined(_WINDOWS)
# include <unistd.h>   /* unlink() */
# include <errno.h>    /* unlink() */
#endif
#include "util/queue/QueueInterface.h"
#include "sqlite.h"

static bool persistentQueueInitialize(I_Queue *queueP, const QueueProperties *queueProperties, ExceptionStruct *exception);
static const QueueProperties *getProperties(I_Queue *queueP);
static void persistentQueuePut(I_Queue *queueP, const QueueEntry *queueEntry, ExceptionStruct *exception);
static QueueEntryArr *persistentQueuePeekWithSamePriority(I_Queue *queueP, int32_t maxNumOfEntries, int64_t maxNumOfBytes, ExceptionStruct *exception);
static int32_t persistentQueueRandomRemove(I_Queue *queueP, const QueueEntryArr *queueEntryArr, ExceptionStruct *exception);
static bool persistentQueueClear(I_Queue *queueP, ExceptionStruct *exception);
static int32_t getNumOfEntries(I_Queue *queueP);
static int32_t getMaxNumOfEntries(I_Queue *queueP);
static int64_t getNumOfBytes(I_Queue *queueP);
static int64_t getMaxNumOfBytes(I_Queue *queueP);
static bool persistentQueueEmpty(I_Queue *queueP);
static void persistentQueueShutdown(I_Queue **queuePP, ExceptionStruct *exception);
static bool persistentQueueDestroy(I_Queue **queuePP, ExceptionStruct *exception);
static bool checkArgs(I_Queue *queueP, const char *methodName, bool checkIsConnected, ExceptionStruct *exception);
static bool createTables(I_Queue *queueP, ExceptionStruct *exception);
static bool execSilent(I_Queue *queueP, const char *sqlStatement, const char *comment, ExceptionStruct *exception);
static bool compilePreparedQuery(I_Queue *queueP, const char *methodName, sqlite_vm **ppVm, const char *queryString, ExceptionStruct *exception);
static bool fillCache(I_Queue *queueP, ExceptionStruct *exception);
static void shutdownInternal(I_Queue **queuePP, ExceptionStruct *exception);
static void freeQueueEntryData(QueueEntry *queueEntry);

/**
 * Called for each SQL result row and does the specific result parsing depending on the query. 
 * @param userP Pointer on a data struct which contains the parsed data
 * @return true->to continue, false->to break execution or on error exception->errorCode is not null
 */
typedef bool ( * ParseDataFp)(I_Queue *queueP, size_t currIndex, void *userP,
                               const char **pazValue, const char **pazColName, ExceptionStruct *exception);
static int32_t getResultRows(I_Queue *queueP, const char *methodName,
                             sqlite_vm *pVm, 
                             ParseDataFp parseDataFp, void *userP, bool finalize,
                             ExceptionStruct *exception);

/* Shortcut for:
    if (queueP->log) queueP->log(queueP, XMLBLASTER_LOG_TRACE, XMLBLASTER_LOG_TRACE, __FILE__, "Persistent queue is created");
   is
    LOG __FILE__, "Persistent queue is created");
*/
#define LOG if (queueP && queueP->log) queueP->log(queueP, queueP->logLevel, XMLBLASTER_LOG_TRACE, 

#define LEN512 512  /* ISO C90 forbids variable-size array: const int LEN512=512; */
#define LEN256 256  /* ISO C90 forbids variable-size array: const int LEN256=256; */

#define DBNAME_MAX 128
#define ID_MAX 256
/**
 * Holds Prepared statements for better performance. 
 * @see http://web.utk.edu/~jplyon/sqlite/SQLite_optimization_FAQ.html
 */
typedef struct DbInfoStruct {
   QueueProperties prop;         /** Meta information */
   size_t numOfEntries;          /** Cache for current number of entries */
   int64_t numOfBytes;           /** Cache for current number of bytes */
   sqlite *db;                   /** Database handle for SQLite */
   sqlite_vm *pVm_put;           /** SQLite virtual machine to hold a prepared query */
   sqlite_vm *pVm_peekWithSamePriority;
   sqlite_vm *pVm_fillCache;
} DbInfo;

/**
 * Used temporary for peekWithSamePriority(). 
 */
typedef struct {
   QueueEntryArr **queueEntryArrPP;
   int32_t currEntries;
   int64_t currBytes;
   int32_t maxNumOfEntries; /** The max wanted number of entries for this peek() */
   int64_t maxNumOfBytes;   /** The max wanted bytes during peek() */
} TmpHelper;

static char int64Str_[INT64_STRLEN_MAX];
static char * const int64Str = int64Str_;   /* to make the pointer address const */

/** Column index into XB_ENTRIES table */
enum {
   XB_ENTRIES_DATA_ID = 0,
   XB_ENTRIES_QUEUE_NAME,
   XB_ENTRIES_PRIO,
   XB_ENTRIES_TYPE_NAME,
   XB_ENTRIES_PERSISTENT,
   XB_ENTRIES_SIZE_IN_BYTES,
   XB_ENTRIES_BLOB
};


/**
 * Create a new persistent queue instance. 
 * <br />
 * @return NULL if bootstrapping failed. If not NULL you need to free() it when you are done
 *         usually by calling shutdown().
 * @throws exception
 */
Dll_Export I_Queue *createQueue(const QueueProperties* queueProperties, ExceptionStruct *exception)
{
   bool stateOk = true;
   I_Queue *queueP = (I_Queue *)calloc(1, sizeof(I_Queue));
   if (queueP == 0) return queueP;
   queueP->isInitialized = false;
   queueP->initialize = persistentQueueInitialize;
   queueP->getProperties = getProperties;
   queueP->put = persistentQueuePut;
   queueP->peekWithSamePriority = persistentQueuePeekWithSamePriority;
   queueP->randomRemove = persistentQueueRandomRemove;
   queueP->clear = persistentQueueClear;
   queueP->getNumOfEntries = getNumOfEntries;
   queueP->getMaxNumOfEntries = getMaxNumOfEntries;
   queueP->getNumOfBytes = getNumOfBytes;
   queueP->getMaxNumOfBytes = getMaxNumOfBytes;
   queueP->empty = persistentQueueEmpty;
   queueP->shutdown = persistentQueueShutdown;
   queueP->destroy = persistentQueueDestroy;
   queueP->privateObject = calloc(1, sizeof(DbInfo));
   {
      DbInfo *dbInfo = (DbInfo *)queueP->privateObject;
      dbInfo->numOfEntries = -1;
      dbInfo->numOfBytes = -1;
   }
   stateOk = queueP->initialize(queueP, queueProperties, exception);
   if (stateOk) {
      LOG __FILE__, "Persistent queue SQLite version " SQLITE_VERSION " is created");
   }
   else {
      ExceptionStruct ex;
      queueP->shutdown(&queueP, &ex);
      if (*ex.errorCode != 0) {
         embedException(exception, ex.errorCode, ex.message, exception);
      }
      queueP = 0;
   }
   return queueP;
}

/** Access the DB handle, queueP pointer is not checked */
static _INLINE_FUNC DbInfo *getDbInfo(I_Queue *queueP) {
   return (queueP==0) ? 0 : (DbInfo *)(queueP->privateObject);
}

/**
 * Access the queue configuration. 
 * @param queueP The this pointer
 * @return Read only access, 0 on error
 */
static const QueueProperties *getProperties(I_Queue *queueP)
{
   ExceptionStruct exception;
   if (checkArgs(queueP, "getProperties", false, &exception) == false ) return 0;
   return &getDbInfo(queueP)->prop;
}

/**
 */
static void freeQueue(I_Queue **queuePP)
{
   I_Queue *queueP = (queuePP == 0) ? 0 : *queuePP;
   if (queueP == 0) {
      fprintf(stderr, "[%s:%d] [user.illegalArgument] Please provide a valid I_Queue pointer to freeQueue()\n", __FILE__, __LINE__);
      return;
   }

   LOG __FILE__, "freeQueue() called");

   if (queueP->privateObject) {
      free(queueP->privateObject);
      queueP->privateObject = 0;
   }

   free(queueP);
   *queuePP = 0;
}

/**
 * Called internally by createQueue(). 
 * @param queueP The this pointer
 * @param queueProperties The configuration
 * @param exception Can contain error information (out parameter)
 * @return true on success
 */
static bool persistentQueueInitialize(I_Queue *queueP, const QueueProperties *queueProperties, ExceptionStruct *exception)
{
   char *errMsg = 0;
   bool retOk;
   const int OPEN_RW = 0;
   sqlite *db;
   DbInfo *dbInfo;

   if (checkArgs(queueP, "initialize", false, exception) == false ) return false;
   if (queueProperties == 0) {
      strncpy0(exception->errorCode, "user.illegalArgument", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] Please provide a valid QueueProperties pointer to initialize()", __FILE__, __LINE__);
      /* LOG __FILE__, "%s: %s", exception->errorCode, exception->message); */
      fprintf(stderr, "[%s:%d] %s: %s", __FILE__, __LINE__, exception->errorCode, exception->message);
      return false;
   }

   queueP->log = queueProperties->logFp;
   queueP->logLevel = queueProperties->logLevel;
   queueP->userObject = queueProperties->userObject;

   if (*queueProperties->dbName == 0 || *queueProperties->queueName == 0 ||
       queueProperties->maxNumOfEntries == 0 || queueProperties->maxNumOfBytes == 0) {
      char dbName[QUEUE_DBNAME_MAX];
      char queueName[QUEUE_ID_MAX];
      strncpy0(exception->errorCode, "user.illegalArgument", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      if (queueProperties->dbName == 0)
         strncpy0(dbName, "NULL", QUEUE_DBNAME_MAX);
      else
         strncpy0(dbName, queueProperties->dbName, QUEUE_DBNAME_MAX);
      if (queueProperties->queueName == 0)
         strncpy0(queueName, "NULL", QUEUE_ID_MAX);
      else
         strncpy0(queueName, queueProperties->queueName, QUEUE_ID_MAX);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] Please provide a proper initialized QueueProperties pointer to initialize(): dbName='%s', queueName='%s',"
               " maxNumOfEntries=%ld, maxNumOfBytes=%ld", __FILE__, __LINE__,
               dbName, queueName, (long)queueProperties->maxNumOfEntries, (long)queueProperties->maxNumOfBytes);
      LOG __FILE__, "%s: %s", exception->errorCode, exception->message);
      return false;
   }

   dbInfo = getDbInfo(queueP);
   memcpy(&dbInfo->prop, queueProperties, sizeof(QueueProperties));

   /* Never trust a queue property you haven't overflowed yourself :-) */
   dbInfo->prop.dbName[QUEUE_DBNAME_MAX-1] = 0;
   dbInfo->prop.queueName[QUEUE_ID_MAX-1] = 0;
   dbInfo->prop.tablePrefix[QUEUE_PREFIX_MAX-1] = 0;

   LOG __FILE__, "dbName          = %s", dbInfo->prop.dbName);
   LOG __FILE__, "queueName       = %s", dbInfo->prop.queueName);
   LOG __FILE__, "tablePrefix     = %s", dbInfo->prop.tablePrefix);
   LOG __FILE__, "maxNumOfEntries = %ld",dbInfo->prop.maxNumOfEntries);
   LOG __FILE__, "maxNumOfBytes   = %ld",(long)dbInfo->prop.maxNumOfBytes);
   /*LOG __FILE__, "logFp           = %d", (int)dbInfo->prop.logFp);*/
   LOG __FILE__, "logLevel        = %d", (int)dbInfo->prop.logLevel);
   /*LOG __FILE__, "userObject      = %d", (void*)dbInfo->prop.userObject);*/

   db = sqlite_open(dbInfo->prop.dbName, OPEN_RW, &errMsg);
   dbInfo->db = db;

   if (db==0) {
      queueP->isInitialized = false;
      if(queueP->log) {
         if (errMsg) {
            LOG __FILE__, "%s", errMsg);
         }
         else {
            LOG __FILE__, "Unable to open database '%s'", dbInfo->prop.dbName);
         }
      }
      else {
        if (errMsg)
           fprintf(stderr,"[%s] %s\n", __FILE__, errMsg);
        else
           fprintf(stderr,"[%s] Unable to open database %s\n", __FILE__, dbInfo->prop.dbName);
      }
      strncpy0(exception->errorCode, "resource.db.unavailable", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] Creating SQLiteQueue '%s' failed: %s", __FILE__, __LINE__, dbInfo->prop.dbName, (errMsg==0)?"":errMsg);
      if (errMsg != 0) sqlite_freemem(errMsg);
      return false;
   }

   queueP->isInitialized = true;

   retOk = createTables(queueP, exception);

   fillCache(queueP, exception);

   LOG __FILE__, "initialize(%s) %s", dbInfo->prop.dbName, retOk?"successful":"failed");
   return true;
}

/**
 * Create the necessary DB table if not already existing. 
 * @param queueP 
 * @param exception Can contain error information (out parameter)
 * @return true on success
 */
static bool createTables(I_Queue *queueP, ExceptionStruct *exception)
{
   char queryString[LEN512];
   bool retOk;
   const char *tablePrefix = ((DbInfo *)(queueP->privateObject))->prop.tablePrefix;

   SNPRINTF(queryString, LEN512, "CREATE TABLE %.20sENTRIES (dataId bigint , queueName text , prio integer, flag text, durable char(1), byteSize bigint, blob bytea, PRIMARY KEY (dataId, queueName));",
           tablePrefix);
   retOk = execSilent(queueP, queryString, "Creating ENTRIES table", exception);

   SNPRINTF(queryString, LEN512, "CREATE INDEX %.20sENTRIES_IDX ON %.20sENTRIES (prio);",
           tablePrefix, tablePrefix);
   retOk = execSilent(queueP, queryString, "Creating PRIO index", exception);
   return retOk;
}

/**
 * Invoke SQL query. 
 * @param queueP Is not checked, must not be 0
 * @param queryString The SQL to execute
 * @param comment For logging or exception text
 * @param exception Can contain error information (out parameter)
 * @return true on success
 */
static bool execSilent(I_Queue *queueP, const char *queryString, const char *comment, ExceptionStruct *exception)
{
   int rc = 0;
   char *errMsg = 0;
   bool retOk;
   DbInfo *dbInfo = getDbInfo(queueP);

   rc = sqlite_exec(dbInfo->db, queryString, NULL, NULL, &errMsg);
   switch (rc) {
      case SQLITE_OK:
         LOG __FILE__, "SQL '%s' success", comment);
         retOk = true;
         break;
      default:
         if (errMsg && strstr(errMsg, "already exists")) {
            LOG __FILE__, "OK, '%s' [%d]: %s %s", comment, rc, sqlite_error_string(rc), (errMsg==0)?"":errMsg);
            retOk = true;
         }
         else if (rc == SQLITE_CONSTRAINT && errMsg && strstr(errMsg, " not unique")) {
            LOG __FILE__, "OK, '%s' entry existed already [%d]: %s %s", comment, rc, sqlite_error_string(rc), (errMsg==0)?"":errMsg);
            retOk = true;
         }
         else {
            LOG __FILE__, "SQL error '%s' [%d]: %s %s", comment, rc, sqlite_error_string(rc), (errMsg==0)?"":errMsg);
            strncpy0(exception->errorCode, "resource.db.unknown", EXCEPTIONSTRUCT_ERRORCODE_LEN);
            SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
                     "[%.100s:%d] SQL error '%s' [%d]: %s %s", __FILE__, __LINE__, comment, rc, sqlite_error_string(rc), (errMsg==0)?"":errMsg);
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

/**
 * @param queueP The queue instance
 * @param queueEntry The entry
 * @param exception The exception is set to *exception->errorCode==0 on success, else to != 0
 */
static void persistentQueuePut(I_Queue *queueP, const QueueEntry *queueEntry, ExceptionStruct *exception)
{
   int rc = 0;
   bool stateOk = true;
   DbInfo *dbInfo;
   char embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN]; /* To protect against buffer overflow */

   if (checkArgs(queueP, "put", true, exception) == false ) return;
   if (queueEntry == 0) {
      strncpy0(exception->errorCode, "user.illegalArgument", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] Please provide a valid queueEntry pointer to function put()", __FILE__, __LINE__);
      return;
   }
   if (queueEntry->uniqueId == 0) {
      strncpy0(exception->errorCode, "user.illegalArgument", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] Please provide a valid queueEntry->uniqueId to function put()", __FILE__, __LINE__);
      return;
   }
   if (*queueEntry->embeddedType == 0) {
      strncpy0(exception->errorCode, "user.illegalArgument", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] Please provide a valid queueEntry->embeddedType to function put()", __FILE__, __LINE__);
      return;
   }
   strncpy0(embeddedType, queueEntry->embeddedType, QUEUE_ENTRY_EMBEDDEDTYPE_LEN);

   if (queueEntry->embeddedBlob.dataLen > 0 && queueEntry->embeddedBlob.data == 0) {
      strncpy0(exception->errorCode, "user.illegalArgument", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] Please provide a valid queueEntry->embeddedBlob to function put()", __FILE__, __LINE__);
      return;
   }

   dbInfo = getDbInfo(queueP);

   if ((int64_t)dbInfo->numOfEntries >= dbInfo->prop.maxNumOfEntries) {
      strncpy0(exception->errorCode, "resource.overflow.queue.entries", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] The maximum number of queue entries = %d is exhausted", __FILE__, __LINE__, dbInfo->prop.maxNumOfEntries);
      return;
   }
   if (dbInfo->numOfBytes >= dbInfo->prop.maxNumOfBytes) {
      strncpy0(exception->errorCode, "resource.overflow.queue.bytes", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] The maximum queue size of %s bytes is exhausted", __FILE__, __LINE__, int64ToStr(int64Str, dbInfo->prop.maxNumOfBytes));
      return;
   }


   if (dbInfo->pVm_put == 0) {  /* Compile prepared query only once */
      char queryString[LEN256];    /*INSERT INTO XB_ENTRIES VALUES ( 1081317015888000000, 'xmlBlaster_192_168_1_4_3412', 'topicStore_xmlBlaster_192_168_1_4_3412', 5, 'TOPIC_XML', 'T', 670, '\\254...')*/
      SNPRINTF(queryString, LEN256, "INSERT INTO %.20sENTRIES VALUES ( ?, ?, ?, ?, ?, ?, ?)", dbInfo->prop.tablePrefix);
      stateOk = compilePreparedQuery(queueP, "put", &dbInfo->pVm_put, queryString, exception);
   }

   if (stateOk) { /* set prepared statement tokens */
      char intStr[INT64_STRLEN_MAX];
      int index = 0;
      const int len = -1; /* Calculated by sqlite_bind */
      rc = SQLITE_OK;

      int64ToStr(intStr, queueEntry->uniqueId);
      /*LOG __FILE__, "put uniqueId as string '%s'", intStr);*/
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, intStr, len, true);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, dbInfo->prop.queueName, len, false);
      SNPRINTF(intStr, INT64_STRLEN_MAX, "%d", queueEntry->priority);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, intStr, len, true);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, embeddedType, len, false);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, queueEntry->isPersistent?"T":"F", len, false);
      SNPRINTF(intStr, INT64_STRLEN_MAX, "%d", (int32_t)queueEntry->embeddedBlob.dataLen);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_put, ++index, intStr, len, true);
      if (rc == SQLITE_OK) {
         /* As SQLite does only store strings we encode our blob to a string */
         size_t estimatedSize = 2 +(257 * queueEntry->embeddedBlob.dataLen )/254;
         unsigned char *out = (unsigned char *)malloc(estimatedSize*sizeof(char));
         int encodedSize = sqlite_encode_binary((const unsigned char *)queueEntry->embeddedBlob.data,
                              (int)queueEntry->embeddedBlob.dataLen, out);
         rc = sqlite_bind(dbInfo->pVm_put, ++index, (const char *)out, encodedSize+1, true);
         free(out);
      }

      if (rc != SQLITE_OK) {
         LOG __FILE__, "put(%s) SQL error: %d %s", int64ToStr(int64Str, queueEntry->uniqueId), rc, sqlite_error_string(rc));
         strncpy0(exception->errorCode, "resource.db.unknown", EXCEPTIONSTRUCT_ERRORCODE_LEN);
         SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
                  "[%.100s:%d] put(%s) SQL error: %d %s", __FILE__, __LINE__, int64ToStr(int64Str, queueEntry->uniqueId), rc, sqlite_error_string(rc));
         stateOk = false;
      }
   }

   if (stateOk) { /* start the query, process results */
      int countRows = getResultRows(queueP, "put", dbInfo->pVm_put, 0, 0, false, exception);
      stateOk = countRows >= 0;
   }

   if (stateOk) {
      dbInfo->numOfEntries += 1;
      dbInfo->numOfBytes += ((queueEntry->sizeInBytes > 0) ? queueEntry->sizeInBytes : queueEntry->embeddedBlob.dataLen);
   }

   LOG __FILE__, "put(%s) %s", int64ToStr(int64Str, queueEntry->uniqueId), stateOk ? "done" : "failed");
}


/**
 * Compile a prepared query. 
 * No parameters are checked, they must be valid
 * @param queueP The queue instance to use
 * @param methodName A nice string for logging
 * @param ppVm The virtual machine will be initialized if still 0
 * @param queryString
 * @param exception The exception is set to *exception->errorCode==0 on success, else to != 0
 * @return false on error and exception->errorCode is not null
 */
static bool compilePreparedQuery(I_Queue *queueP, const char *methodName,
                    sqlite_vm **ppVm, const char *queryString, ExceptionStruct *exception)
{
   int iRetry, numRetry=100;
   char *errMsg = 0;
   int rc = 0;
   const char *pzTail = 0;   /* OUT: uncompiled tail of zSql */
   bool stateOk = true;
   DbInfo *dbInfo = getDbInfo(queueP);

   if (*ppVm == 0) {  /* Compile prepared  query */
      for (iRetry = 0; iRetry < numRetry; iRetry++) {
         rc = sqlite_compile(dbInfo->db, queryString, &pzTail, ppVm, &errMsg);
         switch (rc) {
            case SQLITE_BUSY:
               if (iRetry == (numRetry-1)) {
                  strncpy0(exception->errorCode, "resource.db.block", EXCEPTIONSTRUCT_ERRORCODE_LEN);
                  SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
                           "[%.100s:%d] SQL error #%d in %s(): %s %s", __FILE__, __LINE__, rc, sqlite_error_string(rc), methodName, (errMsg==0)?"":errMsg);
               }
               LOG __FILE__, "%s() Sleeping as other thread holds DB %s", methodName, (errMsg==0)?"":errMsg);
               if (errMsg != 0) { sqlite_freemem(errMsg); errMsg = 0; }
               sleepMillis(10);
               break;
            case SQLITE_OK:
               iRetry = numRetry; /* We're done */
               LOG __FILE__, "%s() Pre-compiled prepared query '%s'", methodName, queryString);
               if (errMsg != 0) { sqlite_freemem(errMsg); errMsg = 0; }
               break;
            default:
               LOG __FILE__, "SQL error #%d %s in %s(): %s: %s", rc, sqlite_error_string(rc), methodName, (errMsg==0)?"":errMsg);
               strncpy0(exception->errorCode, "resource.db.unknown", EXCEPTIONSTRUCT_ERRORCODE_LEN);
               SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
                        "[%.100s:%d] SQL error #%d %s in %s(): %s", __FILE__, __LINE__, rc, sqlite_error_string(rc), methodName, (errMsg==0)?"":errMsg);
               iRetry = numRetry; /* We're done */
               if (errMsg != 0) { sqlite_freemem(errMsg); errMsg = 0; }
               stateOk = false;
               break;
         }
      }
   }
   if (*ppVm == 0) stateOk = false;
   return stateOk;
}

/**
 * For each SQL result row parse it into a QueueEntry. 
 * No parameters are checked, they must be valid
 * Implements a ParseDataFp (function pointer)
 * @param queueP The 'this' pointer
 * @param currIndex
 * @param userP
 * @param pazValue
 * @param pazColName
 * @param exception The exception is set to *exception->errorCode==0 on success, else to != 0
 * @return false on error and exception->errorCode is not null
 */
static bool parseQueueEntryArr(I_Queue *queueP, size_t currIndex, void *userP,
                               const char **pazValue, const char **pazColName, ExceptionStruct *exception)
{
   bool doContinue = true;
   int numAssigned;
   bool stateOk = true;
   int decodeSize = 0;
   QueueEntry *queueEntry = 0;
   QueueEntryArr *queueEntryArr;
   TmpHelper *helper = (TmpHelper*)userP;
   QueueEntryArr **queueEntryArrPP = helper->queueEntryArrPP;

   if (currIndex == 0) {
      helper->currEntries = 0;
      helper->currBytes = 0;
   }

   if (*queueEntryArrPP == 0) {
      *queueEntryArrPP = (QueueEntryArr *)calloc(1, sizeof(QueueEntryArr));;
      if (helper->maxNumOfEntries == 0) {
         doContinue = false;
         return doContinue;
      }
   }
   queueEntryArr = *queueEntryArrPP;

   if (queueEntryArr->len == 0) {
      queueEntryArr->len = 10;
      queueEntryArr->queueEntryArr = (QueueEntry *)calloc(queueEntryArr->len, sizeof(QueueEntry));
   }
   else if (currIndex >= queueEntryArr->len) {
      queueEntryArr->len += 10;
      queueEntryArr->queueEntryArr = (QueueEntry *)realloc(queueEntryArr->queueEntryArr, queueEntryArr->len * sizeof(QueueEntry));
   }
   queueEntry = &queueEntryArr->queueEntryArr[currIndex];
   memset(queueEntry, 0, sizeof(QueueEntry));

   stateOk = strToInt64(&queueEntry->uniqueId, pazValue[XB_ENTRIES_DATA_ID]);
   if (!stateOk) {
      LOG __FILE__, "peekWithSamePriority() ERROR: Can't parse pazValue[0] '%.20s' to uniqueId, ignoring entry.", pazValue[XB_ENTRIES_DATA_ID]);
      strncpy0(exception->errorCode, "resource.db.unknown", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] peekWithSamePriority() ERROR: Can't parse pazValue[0] '%.20s' col=%s to uniqueId, ignoring entry.", __FILE__, __LINE__, pazValue[XB_ENTRIES_DATA_ID], pazColName[XB_ENTRIES_DATA_ID]);
      doContinue = false;
      return doContinue;
   }

   LOG __FILE__, "peekWithSamePriority(%s) currIndex=%d", int64ToStr(int64Str, queueEntry->uniqueId), currIndex);
   /* strncpy0(dbInfo->prop.queueName, pazValue[2], ID_MAX); */
   numAssigned = sscanf(pazValue[XB_ENTRIES_PRIO], "%hd", &queueEntry->priority);
   if (numAssigned != 1) {
      LOG __FILE__, "peekWithSamePriority(%s) ERROR: Can't parse pazValue[XB_ENTRIES_PRIO] '%.20s' to priority, setting it to NORM", int64ToStr(int64Str, queueEntry->uniqueId), pazValue[XB_ENTRIES_PRIO]);
      queueEntry->priority = 4;
   }
   strncpy0(queueEntry->embeddedType, pazValue[XB_ENTRIES_TYPE_NAME], QUEUE_ENTRY_EMBEDDEDTYPE_LEN);
   queueEntry->isPersistent = *pazValue[XB_ENTRIES_PERSISTENT] == 'T' ? true : false;
   {
      int64_t ival = 0;
      stateOk = strToInt64(&ival, pazValue[XB_ENTRIES_SIZE_IN_BYTES]);
      queueEntry->embeddedBlob.dataLen = (size_t)ival;
   }

   /* TODO!!! in Java the length is the size in RAM and not strlen(data) */
   /* queueEntry->embeddedBlob.data = (char *)malloc(queueEntry->embeddedBlob.dataLen*sizeof(char)); */
   queueEntry->embeddedBlob.data = (char *)malloc(strlen(pazValue[XB_ENTRIES_BLOB])*sizeof(char)); /* we spoil some 2 % */
   decodeSize = sqlite_decode_binary((const unsigned char *)pazValue[XB_ENTRIES_BLOB], (unsigned char *)queueEntry->embeddedBlob.data);
   if (decodeSize == -1 || (size_t)decodeSize != queueEntry->embeddedBlob.dataLen) {
      *(queueEntry->embeddedBlob.data + strlen(pazValue[XB_ENTRIES_BLOB]) - 1) = 0; 
      LOG __FILE__, "peekWithSamePriority(%s) ERROR: Returned blob encoded='%s', decodeSize=%d"
                        " but expected decoded len=%d: '%s'",
                    int64ToStr(int64Str, queueEntry->uniqueId), pazValue[XB_ENTRIES_BLOB], decodeSize,
                    queueEntry->embeddedBlob.dataLen, queueEntry->embeddedBlob.data);
   }

   helper->currEntries += 1;
   helper->currBytes += queueEntry->embeddedBlob.dataLen;

   /* Limit the number of entries */
   if ((helper->maxNumOfEntries != -1 && helper->currEntries >= helper->maxNumOfEntries) ||
       (helper->maxNumOfBytes != -1 && helper->currBytes >= helper->maxNumOfBytes)) {
      /* sqlite_interrupt(dbInfo->db); -> sets rc==SQLITE_ERROR on next sqlite-step() which i can't distinguish from a real error */
      doContinue = false;
   }

   return doContinue;
}

/**
 * Execute the query and get the query result. 
 * No parameters are checked, they must be valid
 * @param queueP  The this pointer
 * @param methodName The method called
 * @param pVm sqlite virtual machine
 * @param parseDataFp The function which is called for each SQL result row
 *                    or 0 if no function shall be called
 * @param userP The pointer which is passed to parseDataFp
 * @param finalize true to call sqlite_finalize which deletes the virtual machine,
 *                 false to call  sqlite_reset to reuse the prepared query
 * @param exception The exception is set to *exception->errorCode==0 on success, else to != 0
 * @return < 0 on error and exception->errorCode is not null
 *         otherwise the number of successfully parsed rows is returned
 * @todo For INSERT and DELETE return the number of touched entries !!!
 */
static int32_t getResultRows(I_Queue *queueP, const char *methodName,
                             sqlite_vm *pVm, 
                             ParseDataFp parseDataFp, void *userP,
                             bool finalize,
                             ExceptionStruct *exception)
{
   char *errMsg = 0;
   int32_t currIndex = 0;
   int numCol = 0;
   const char **pazValue = 0;
   const char **pazColName = 0;
   bool done = false;
   bool stateOk = true;
   int rc;
   while (!done) {
      rc = sqlite_step(pVm, &numCol, &pazValue, &pazColName);
      switch( rc ){
         case SQLITE_DONE:
            done = true;
         break;
         case SQLITE_BUSY:
            LOG __FILE__, "%s() Sleeping as other thread holds DB.", methodName);
            sleepMillis(10);
         break;
         case SQLITE_ROW:
         {
            bool doContinue = true;
            if (parseDataFp) {
               /* @return true->to continue, false->to break execution or on error exception->errorCode is not null */
               doContinue = parseDataFp(queueP, currIndex, userP, pazValue, pazColName, exception);
               stateOk = *exception->errorCode == 0;
            }
            else {
               /*
               printf("RESULT[%d]\n", iRow);
               for (iCol = 0; iCol < numCol; iCol++) {
                  printf("%10.10s = %s\n", pazColName[iCol], pazValue[iCol]);
               }
               */
            }
            currIndex++;
            if (!stateOk || !doContinue) done = true;
         }
         break;
         case SQLITE_ERROR:   /* If exists already */
            LOG __FILE__, "%s() SQL execution problem [sqlCode=%d], entry already exists", methodName, rc);
            done = true;
            stateOk = false;
         break;
         case SQLITE_MISUSE:
         default:
            LOG __FILE__, "%s() SQL execution problem [sqlCode=%d %s]", methodName, rc, sqlite_error_string(rc));
            done = true;
            stateOk = false;
         break;
      }
   }
   LOG __FILE__, "%s() Processed %lu entries.", methodName, (unsigned long)currIndex);

   if (finalize) {
      sqlite_finalize(pVm, &errMsg);
      if (rc != SQLITE_OK && rc != SQLITE_DONE) {
         LOG __FILE__, "WARN: getResultRows() sqlCode=%d %s is not handled. %s", rc, sqlite_error_string(rc), errMsg==0?"":errMsg);
      }
      if (errMsg != 0) sqlite_freemem(errMsg);
   }
   else { /* Reset prepared statement */
      rc = sqlite_reset(pVm, &errMsg);
      if (rc == SQLITE_SCHEMA) {
         LOG __FILE__, "WARN: getResultRows() sqlCode=%d %s is not handled %s", rc, sqlite_error_string(rc), errMsg==0?"":errMsg);
      }
      if (errMsg != 0) sqlite_freemem(errMsg);
   }

   return stateOk ? currIndex : (-1)*rc;
}

/**
 * Access queue entries without removing them. 
 */
static QueueEntryArr *persistentQueuePeekWithSamePriority(I_Queue *queueP, int32_t maxNumOfEntries, int64_t maxNumOfBytes, ExceptionStruct *exception)
{
   int rc = 0;
   bool stateOk = true;
   DbInfo *dbInfo;
   QueueEntryArr *queueEntryArr = 0;

   if (checkArgs(queueP, "peekWithSamePriority", true, exception) == false ) return 0;

   LOG __FILE__, "peekWithSamePriority(maxNumOfEntries=%d, maxNumOfBytes=%s) ...", (int)maxNumOfEntries, int64ToStr(int64Str, maxNumOfBytes));

   dbInfo = getDbInfo(queueP);

   if (dbInfo->pVm_peekWithSamePriority == 0) {  /* Compile prepared  query */
      char queryString[LEN512];
      /*"SELECT * FROM XB_ENTRIES where queueName='connection_clientJoe' and prio=(select max(prio) from XB_ENTRIES where queueName='connection_clientJoe') ORDER BY dataId ASC";*/
      SNPRINTF(queryString, LEN512,
           "SELECT * FROM %.20sENTRIES where queueName=?"
           " and prio=(select max(prio) from %.20sENTRIES where queueName=?)"
           " ORDER BY dataId ASC",
           dbInfo->prop.tablePrefix, dbInfo->prop.tablePrefix);
      stateOk = compilePreparedQuery(queueP, "peekWithSamePriority",
                    &dbInfo->pVm_peekWithSamePriority , queryString, exception);
   }

   if (stateOk) { /* set prepared statement tokens */
      int index = 0;
      int len = -1; /* Calculated by sqlite_bind */
      rc = SQLITE_OK;

      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_peekWithSamePriority, ++index, dbInfo->prop.queueName, len, false);
      if (rc == SQLITE_OK) rc = sqlite_bind(dbInfo->pVm_peekWithSamePriority, ++index, dbInfo->prop.queueName, len, false);

      switch (rc) {
         case SQLITE_OK:
            LOG __FILE__, "peekWithSamePriority() Bound to prepared statement [sqlCode=%d]", rc);
            break;
         default:
            LOG __FILE__, "peekWithSamePriority() SQL error: %d %s", rc, sqlite_error_string(rc));
            strncpy0(exception->errorCode, "resource.db.unknown", EXCEPTIONSTRUCT_ERRORCODE_LEN);
            SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
                     "[%.100s:%d] peekWithSamePriority() SQL error: %d %s", __FILE__, __LINE__, rc, sqlite_error_string(rc));
            stateOk = false;
            break;
      }
   }

   if (stateOk) { /* start the query */
      TmpHelper helper;
      int32_t currIndex = 0;
      helper.queueEntryArrPP = &queueEntryArr;
      helper.maxNumOfEntries = maxNumOfEntries;
      helper.maxNumOfBytes = maxNumOfBytes;
      currIndex = getResultRows(queueP, "peekWithSamePriority",
                        dbInfo->pVm_peekWithSamePriority, parseQueueEntryArr,
                        &helper, false, exception);
      stateOk = currIndex >= 0;
      if (!stateOk) {
         if (queueEntryArr) {
            free(queueEntryArr->queueEntryArr);
            queueEntryArr->len = 0;
         }
      }
      else {
         if (!queueEntryArr)
            queueEntryArr = (QueueEntryArr *)calloc(1, sizeof(QueueEntryArr));
         else if ((size_t)currIndex < queueEntryArr->len) {
            queueEntryArr->queueEntryArr = (QueueEntry *)realloc(queueEntryArr->queueEntryArr, currIndex * sizeof(QueueEntry));
            queueEntryArr->len = currIndex; 
         }
      }
   }

   LOG __FILE__, "peekWithSamePriority() %s", stateOk ? "done" : "failed");
   return queueEntryArr;
}

/**
 * Removes the given entries from persistence. 
 * @return The number of removed entries
 */
static int32_t persistentQueueRandomRemove(I_Queue *queueP, const QueueEntryArr *queueEntryArr, ExceptionStruct *exception)
{
   bool stateOk = true;
   int64_t numOfBytes = 0;
   int32_t countDeleted = 0;
   sqlite_vm *pVm = 0;
   DbInfo *dbInfo;
   if (checkArgs(queueP, "randomRemove", true, exception) == false || queueEntryArr == 0 ||
                 queueEntryArr->len == 0 || queueEntryArr->queueEntryArr == 0)
      return 0;

   LOG __FILE__, "randomRemove(%d) ...", (int)queueEntryArr->len);

   dbInfo = getDbInfo(queueP);

   {
      size_t i;
      const size_t qLen = 128 + 2*ID_MAX + queueEntryArr->len*(INT64_STRLEN_MAX+6);
      char *queryString = (char *)calloc(qLen, sizeof(char));
      /*  DELETE FROM xb_entries WHERE queueName = 'connection_clientJoe' AND dataId in ( 1081492136876000000, 1081492136856000000 ); */
      SNPRINTF(queryString, qLen, 
           "DELETE FROM %.20sENTRIES WHERE queueName='%s'"
           " AND dataId in ( ",
           dbInfo->prop.tablePrefix, dbInfo->prop.queueName);

      for (i=0; i<queueEntryArr->len; i++) {
         strcat(queryString, int64ToStr(int64Str, queueEntryArr->queueEntryArr[i].uniqueId));
         if (i<(queueEntryArr->len-1)) strcat(queryString, ",");
         numOfBytes += ((queueEntryArr->queueEntryArr[i].sizeInBytes > 0) ? queueEntryArr->queueEntryArr[i].sizeInBytes : queueEntryArr->queueEntryArr[i].embeddedBlob.dataLen);
      }
      strcat(queryString, " )");
      stateOk = compilePreparedQuery(queueP, "randomRemove", &pVm, queryString, exception);
      free(queryString);
   }


   if (stateOk) { /* start the query */
      int32_t currIndex = getResultRows(queueP, "randomRemove",
                              pVm, 0, 0, true, exception);
      stateOk = currIndex >= 0;
   }

   if (stateOk) {
      countDeleted = (int32_t)sqlite_last_statement_changes(dbInfo->db);
      if (countDeleted < 0 || (size_t)countDeleted != queueEntryArr->len) {
         fillCache(queueP, exception); /* calculate numOfBytes again */
      }
      else {
         dbInfo->numOfEntries -= queueEntryArr->len;
         dbInfo->numOfBytes -= numOfBytes;
      }
   }

   return countDeleted;
}

/**
 * Destroy all entries in queue and releases all resources in memory and on HD. 
 */
static bool persistentQueueDestroy(I_Queue **queuePP, ExceptionStruct *exception)
{
   bool stateOk = true;
   I_Queue *queueP = (queuePP == 0) ? 0 : *queuePP;
   if (checkArgs(queueP, "destroy", false, exception) == false ) return false;

   shutdownInternal(queuePP, exception);

   {
      DbInfo *dbInfo = getDbInfo(queueP);
      const char *dbName = dbInfo->prop.dbName;
      stateOk = unlink(dbName) == 0; /* Delete old db file */
      if (!stateOk) {
         strncpy0(exception->errorCode, "resource.db.unknown", EXCEPTIONSTRUCT_ERRORCODE_LEN);
         SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
                  "[%.100s:%d] destroy() ERROR: Can't destroy database '%s', errno=%d.", __FILE__, __LINE__, dbName, errno);
      }
   }

   freeQueue(queuePP);
   
   return stateOk;
}

/**
 * Destroy all entries in queue. 
 */
static bool persistentQueueClear(I_Queue *queueP, ExceptionStruct *exception)
{
   int stateOk = true;
   char queryString[LEN256];
   sqlite_vm *pVm = 0;
   DbInfo *dbInfo;
   if (checkArgs(queueP, "clear", true, exception) == false) return false;
   dbInfo = getDbInfo(queueP);

   SNPRINTF(queryString, LEN256, "DELETE FROM %.20sENTRIES", dbInfo->prop.tablePrefix);
   stateOk = compilePreparedQuery(queueP, "clear", &pVm, queryString, exception);

   if (stateOk) {
      int32_t currIndex = getResultRows(queueP, "clear", pVm, 0, 0, true, exception);
      stateOk = currIndex >= 0;
   }

   if (stateOk) {
      dbInfo->numOfEntries = 0;
      dbInfo->numOfBytes = 0;
   }

   LOG __FILE__, "clear() done");
   return stateOk;
}

/**
 * Parse response of "SELECT count(dataId), sum(byteSize) FROM %.20sENTRIES where queueName='%s'",
 */
static bool parseCacheInfo(I_Queue *queueP, size_t currIndex, void *userP,
                           const char **pazValue, const char **pazColName, ExceptionStruct *exception)
{
   int64_t ival = 0;
   bool stateOk;
   DbInfo *dbInfo = getDbInfo(queueP);

   stateOk = strToInt64(&ival, pazValue[XB_ENTRIES_DATA_ID]);
   if (!stateOk) {
      strncpy0(exception->errorCode, "resource.db.unknown", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] parseCacheInfo() ERROR: Can't parse %s='%.20s' to numOfEntries, ignoring entry.", __FILE__, __LINE__, pazColName[XB_ENTRIES_DATA_ID], pazValue[XB_ENTRIES_DATA_ID]);
      return false;
   }
   dbInfo->numOfEntries = (int32_t)ival;

   stateOk = strToInt64(&dbInfo->numOfBytes, pazValue[1]);
   if (!stateOk) {
      strncpy0(exception->errorCode, "resource.db.unknown", EXCEPTIONSTRUCT_ERRORCODE_LEN);
      SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
               "[%.100s:%d] parseCacheInfo() ERROR: Can't parse %s='%.20s' to numOfBytes, ignoring entry.", __FILE__, __LINE__, pazColName[1], pazValue[1]);
      if (currIndex) {} /* Just to avoid compiler warning about unused variable */
      if (userP) {};
      return false;
   }

   return true;
}

/**
 * Reload cached information from database. 
 * @param queueP The this pointer
 * @param exception Returns error
 * @return false on error
 */
static bool fillCache(I_Queue *queueP, ExceptionStruct *exception)
{
   bool stateOk = true;
   DbInfo *dbInfo = 0;

   char queryString[LEN512]; /* "SELECT count(dataId) FROM XB_ENTRIES where queueName='connection_clientJoe'" */

   if (checkArgs(queueP, "fillCache", true, exception) == false ) return true;
   dbInfo = getDbInfo(queueP);

   SNPRINTF(queryString, LEN512, 
            "SELECT count(dataId), sum(byteSize) FROM %.20sENTRIES where queueName='%s'",
            dbInfo->prop.tablePrefix, dbInfo->prop.queueName);
   stateOk = compilePreparedQuery(queueP, "fillCache",
                  &dbInfo->pVm_fillCache, queryString, exception);

   if (stateOk) { /* start the query, calls parseCacheInfo() */
      int32_t currIndex = getResultRows(queueP, "fillCache",
                              dbInfo->pVm_fillCache, parseCacheInfo,
                              0, false, exception);
      stateOk = currIndex > 0;
   }

   LOG __FILE__, "fillCache() numOfEntries=%d numOfBytes=%s", dbInfo->numOfEntries, int64ToStr(int64Str, dbInfo->numOfBytes));
   return stateOk;
}

static bool persistentQueueEmpty(I_Queue *queueP)
{
   return getNumOfEntries(queueP) <= 0;
}

static int32_t getNumOfEntries(I_Queue *queueP)
{
   DbInfo *dbInfo;
   bool stateOk = true;
   ExceptionStruct exception;
   if (checkArgs(queueP, "getNumOfEntries", false, &exception) == false ) return -1;
   dbInfo = getDbInfo(queueP);
   if (dbInfo->numOfEntries == -1) {
      stateOk = fillCache(queueP, &exception);
   }
   return (stateOk) ? (int32_t)dbInfo->numOfEntries : -1;
}

static int32_t getMaxNumOfEntries(I_Queue *queueP)
{
   DbInfo *dbInfo;
   ExceptionStruct exception;
   if (checkArgs(queueP, "getMaxNumOfEntries", false, &exception) == false ) return -1;
   dbInfo = getDbInfo(queueP);
   return dbInfo->prop.maxNumOfEntries;
}

static int64_t getNumOfBytes(I_Queue *queueP)
{
   DbInfo *dbInfo;
   ExceptionStruct exception;
   bool stateOk = true;
   if (checkArgs(queueP, "getNumOfBytes", false, &exception) == false ) return -1;
   dbInfo = getDbInfo(queueP);
   if (dbInfo->numOfBytes == -1) {
      stateOk = fillCache(queueP, &exception);
   }
   return (stateOk) ? dbInfo->numOfBytes : -1;
}

static int64_t getMaxNumOfBytes(I_Queue *queueP)
{
   DbInfo *dbInfo;
   ExceptionStruct exception;
   if (checkArgs(queueP, "getMaxNumOfBytes", false, &exception) == false ) return -1;
   dbInfo = getDbInfo(queueP);
   return dbInfo->prop.maxNumOfBytes;
}

/**
 * Shutdown without destroying any entry. 
 * Clears all open DB resources.
 */
static void persistentQueueShutdown(I_Queue **queuePP, ExceptionStruct *exception)
{
   I_Queue *queueP = (queuePP == 0) ? 0 : *queuePP;
   if (checkArgs(queueP, "shutdown", false, exception) == false ) return;
   shutdownInternal(queuePP, exception);
   freeQueue(queuePP);
}

/**
 * Shutdown used internally without calling freeQueue(). 
 */
static void shutdownInternal(I_Queue **queuePP, ExceptionStruct *exception)
{
   I_Queue *queueP = (queuePP == 0) ? 0 : *queuePP;
   if (checkArgs(queueP, "shutdown", false, exception) == false ) return;
   {
      DbInfo *dbInfo = getDbInfo(queueP);
      queueP->isInitialized = false;
      if(dbInfo) {
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
         if (dbInfo->pVm_fillCache) {
            char *errMsg = 0;
            sqlite_finalize(dbInfo->pVm_fillCache, &errMsg);
            if (errMsg != 0) sqlite_freemem(errMsg);
            dbInfo->pVm_fillCache = 0;
         }
         if (dbInfo->db) {
            sqlite_close(dbInfo->db);
            dbInfo->db = 0;
         }
         LOG __FILE__, "shutdown() done");
      }
   }
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
static void freeQueueEntryData(QueueEntry *queueEntry)
{
   if (queueEntry == (QueueEntry *)0) return;
   if (queueEntry->embeddedBlob.data != 0) {
      free((char *)queueEntry->embeddedBlob.data);
      queueEntry->embeddedBlob.data = 0;
   }
   queueEntry->embeddedBlob.dataLen = 0;
}

/**
 * Frees the internal blob and the queueEntry itself. 
 * @param queueEntry Its memory is freed, it is not usable anymore after this call
 */
Dll_Export void freeQueueEntry(QueueEntry *queueEntry)
{
   if (queueEntry == (QueueEntry *)0) return;
   freeQueueEntryData(queueEntry);
   free(queueEntry);
}

/**
 * NOTE: You need to free the returned pointer with xmlBlasterFree() (which calls free())!
 *
 * @param queueEntry The data to put to the queue
 * @param maxContentDumpLen for -1 get the complete content, else limit the
 *        content to the given number of bytes
 * @return A ASCII XML formatted entry or NULL if out of memory
 */
Dll_Export char *queueEntryToXml(QueueEntry *queueEntry, int maxContentDumpLen)
{
   if (queueEntry == (QueueEntry *)0) return 0;
   {
   char *contentStr = strFromBlobAlloc(queueEntry->embeddedBlob.data, queueEntry->embeddedBlob.dataLen);
   const size_t blobLen = (maxContentDumpLen >= 0) ? maxContentDumpLen : queueEntry->embeddedBlob.dataLen;
   const size_t len = 200 + QUEUE_ENTRY_EMBEDDEDTYPE_LEN + blobLen;
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

   SNPRINTF(xml, len, "\n <QueueEntry id='%s' priority='%hd' persistent='%s' type='%s'>"
                      "\n  <content size='%lu'><![CDATA[%s]]></content>"
                      "\n <QueueEntry>",
                        int64ToStr(int64Str, queueEntry->uniqueId), queueEntry->priority,
                        queueEntry->isPersistent?"true":"false",
                        queueEntry->embeddedType,
                        (unsigned long)queueEntry->embeddedBlob.dataLen, contentStr);
   free(contentStr);
   return xml;
   }
}

Dll_Export void freeEntryDump(char *entryDump)
{
   if (entryDump) free(entryDump);
}

/**
 * Checks the given arguments to be valid.
 * @param queueP The queue instance
 * @param methodName For logging
 * @param checkIsConnected If true does check the connection state as well 
 * @param exception Transporting errors 
 * @return false if the parameters are not usable,
 *         in this case 'exception' is filled with detail informations
 */
static bool checkArgs(I_Queue *queueP, const char *methodName,
                      bool checkIsConnected, ExceptionStruct *exception)
{
   if (queueP == 0) {
      if (exception == 0) {
         printf("[%s:%d] [user.illegalArgument] Please provide a valid I_Queue pointer to %s()\n",
                  __FILE__, __LINE__, methodName);
      }
      else {
         strncpy0(exception->errorCode, "user.illegalArgument", EXCEPTIONSTRUCT_ERRORCODE_LEN);
         SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
                  "[%.100s:%d] Please provide a valid I_Queue pointer to %.16s()",
                   __FILE__, __LINE__, methodName);
         LOG __FILE__, "%s: %s", exception->errorCode, exception->message);
      }
      return false;
   }

   if (exception == 0) {
      LOG __FILE__, "[%s:%d] Please provide valid exception pointer to %s()", __FILE__, __LINE__, methodName);
      return false;
   }

   if (checkIsConnected) {
      if (queueP->privateObject==0 ||
          ((DbInfo *)(queueP->privateObject))->db==0 ||
          !queueP->isInitialized) {
         strncpy0(exception->errorCode, "resource.db.unavailable", EXCEPTIONSTRUCT_ERRORCODE_LEN);
         SNPRINTF(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
                  "[%.100s:%d] Not connected to database, %s() failed",
                   __FILE__, __LINE__, methodName);
         LOG __FILE__, "%s: %s", exception->errorCode, exception->message);
         return false;
      }
   }

   initializeExceptionStruct(exception);

   LOG __FILE__, "%s() entering ...", methodName);

   return true;
}

/*=================== TESTCODE =======================*/
# ifdef QUEUE_MAIN
#include <stdio.h>
static void testRun(int argc, char **argv) {
   ExceptionStruct exception;
   QueueEntryArr *entries = 0;
   QueueProperties queueProperties;
   I_Queue *queueP = 0;

   memset(&queueProperties, 0, sizeof(QueueProperties));
   strncpy0(queueProperties.dbName, "xmlBlasterClient.db", QUEUE_DBNAME_MAX);
   strncpy0(queueProperties.queueName, "connection_clientJoe", QUEUE_ID_MAX);
   strncpy0(queueProperties.tablePrefix, "XB_", QUEUE_PREFIX_MAX);
   queueProperties.maxNumOfEntries = 10000000L;
   queueProperties.maxNumOfBytes = 1000000000LL;
   queueProperties.logFp = xmlBlasterDefaultLogging;
   queueProperties.logLevel = XMLBLASTER_LOG_TRACE;
   queueProperties.userObject = 0;

   queueP = createQueue(&queueProperties, &exception);
   /* DbInfo *dbInfo = (DbInfo *)queueP->privateObject; */
   if (argc || argv) {} /* to avoid compiler warning */

   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");

   {
      int64_t idArr[] =   { 1081492136826000000ll, 1081492136856000000ll, 1081492136876000000ll };
      int16_t prioArr[] = { 5                    , 1                    , 5 };
      char *data[] =      { "Hello"              , " World"             , "!!!" };
      size_t i;
      for (i=0; i<sizeof(idArr)/sizeof(int64_t); i++) {
         QueueEntry queueEntry;
         memset(&queueEntry, 0, sizeof(QueueEntry));
         queueEntry.priority = prioArr[i];
         queueEntry.isPersistent = true;
         queueEntry.uniqueId = idArr[i];
         strncpy0(queueEntry.embeddedType, "MSG_RAW|publish", QUEUE_ENTRY_EMBEDDEDTYPE_LEN);
         queueEntry.embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN-1] = 0;
         queueEntry.embeddedBlob.data = data[i];
         queueEntry.embeddedBlob.dataLen = strlen(queueEntry.embeddedBlob.data);

         queueP->put(queueP, &queueEntry, &exception);
         if (*exception.errorCode != 0) {
            LOG __FILE__, "TEST FAILED: [%s] %s\n", exception.errorCode, exception.message);
         }
      }
   }
   
   entries = queueP->peekWithSamePriority(queueP, -1, 6, &exception);
   if (*exception.errorCode != 0) {
      LOG __FILE__, "TEST FAILED: [%s] %s\n", exception.errorCode, exception.message);
   }
   if (entries != 0) {
      size_t i;
      printf("testRun after peekWithSamePriority() dump %lu entries:\n", (unsigned long)entries->len);
      for (i=0; i<entries->len; i++) {
         QueueEntry *queueEntry = &entries->queueEntryArr[i];
         char *dump = queueEntryToXml(queueEntry, 200);
         printf("%s\n", dump);
         free(dump);
      }
   }

   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");
   queueP->randomRemove(queueP, entries, &exception);
   if (*exception.errorCode != 0) {
      LOG __FILE__, "TEST FAILED: [%s] %s\n", exception.errorCode, exception.message);
   }

   freeQueueEntryArr(entries);
   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");
   
   queueP->clear(queueP, &exception);
   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");

   queueP->shutdown(&queueP, &exception);
}

int main(int argc, char **argv) {
   int i;
   for (i=0; i<1; i++) {
      testRun(argc, argv);
   }
   return 0;
}
#endif /*QUEUE_MAIN*/
/*=================== TESTCODE =======================*/


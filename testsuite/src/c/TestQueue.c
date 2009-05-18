/*----------------------------------------------------------------------------
Name:      xmlBlaster/testsuite/src/c/TestQueue.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test C client library
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build -DXMLBLASTER_PERSISTENT_QUEUE_SQLITE3=1 c-test
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.queue.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <util/queue/QueueInterface.h>
#include "test.h"

#if defined(XMLBLASTER_PERSISTENT_QUEUE) || defined(XMLBLASTER_PERSISTENT_QUEUE_SQLITE3)

static int argc = 0;
static char** argv = 0;
#define  ERRORSTR_LEN EXCEPTIONSTRUCT_ERRORCODE_LEN + EXCEPTIONSTRUCT_MESSAGE_LEN + 64
static char errorString[ERRORSTR_LEN];
static char int64Str_[32];
static char * const int64Str = int64Str_;   /* to make the pointer address const */
static char int64StrX_[32];
static char * const int64StrX = int64StrX_;   /* a second one */
/* Try switch on/off logging with using function pointer 'xmlBlasterDefaultLogging' or '0' */
static XmlBlasterLogging loggingFp = xmlBlasterDefaultLogging;

#define mu_assert_checkException(message, exception) \
      do {\
         if (*exception.errorCode != 0) {\
            sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %s: Caught exception: '%s'", __FILE__, __LINE__, message, getExceptionStr(errorString, ERRORSTR_LEN, &exception));\
            return MU_ASSERT_TEXT;\
         }\
      } while (0)

#define mu_assert_checkWantException(message, exception) \
      do {\
         if (*exception.errorCode == 0) {\
            sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %s: Missing exception", __FILE__, __LINE__, message);\
            return MU_ASSERT_TEXT;\
         }\
         else {\
            char out[1024];\
            printf("OK, expected exception: %s\n", getExceptionStr(out, 1024, &exception));\
         }\
      } while (0)

static bool destroy(const char *dbName);

/**
 * Kill complete DB on HD
 */
static bool destroy(const char *dbName)
{
   bool stateOk;
   I_Queue *queueP;
   ExceptionStruct exception;
   QueueProperties queueProperties;
   memset(&queueProperties, 0, sizeof(QueueProperties));
   strncpy0(queueProperties.dbName, dbName, QUEUE_DBNAME_MAX);
   strncpy0(queueProperties.queueName, "b", QUEUE_ID_MAX);
   strncpy0(queueProperties.tablePrefix, "c", QUEUE_PREFIX_MAX);
   queueProperties.maxNumOfEntries = 10;
   queueProperties.maxNumOfBytes = 10;

   queueP = createQueue(&queueProperties, &exception);
   
   stateOk = queueP->destroy(&queueP, &exception);

   if (!stateOk) {
      printf("Ignoring problem during destroy: %s", getExceptionStr(errorString, ERRORSTR_LEN, &exception));
   }
   return stateOk;
}

/**
 * Test illegal function call parameters. 
 */
static const char * test_illegal()
{
   ExceptionStruct exception;
   QueueEntry queueEntry;
   QueueEntryArr *entries = 0;
   QueueProperties queueProperties;
   I_Queue *queueP = 0;
   const char *dbName = "xmlBlasterClient-C-Test.db";
   int32_t numRemoved;

   printf("\n--------test_illegal---------------------\n");
   destroy(dbName); /* Delete old db file */

   memset(&queueProperties, 0, sizeof(QueueProperties));

   queueP = createQueue(0, 0);
   mu_assert("create() Wrong properties", queueP == 0);

   queueP = createQueue(&queueProperties, &exception);
   mu_assert("create()", queueP == 0);
   mu_assert_checkWantException("create()", exception);

   strncpy0(queueProperties.dbName, dbName, QUEUE_DBNAME_MAX);
   strncpy0(queueProperties.queueName, "connection_clientJoe", QUEUE_ID_MAX);
   strncpy0(queueProperties.tablePrefix, "XB_", QUEUE_PREFIX_MAX);
   queueProperties.maxNumOfEntries = 0;
   queueProperties.maxNumOfBytes = 0;
   queueP = createQueue(&queueProperties, &exception);
   mu_assert("create()", queueP == 0);
   mu_assert_checkWantException("create()", exception);

   queueProperties.maxNumOfEntries = 10;
   queueProperties.maxNumOfBytes = 100;
   queueP = createQueue(&queueProperties, &exception);
   mu_assert("create()", queueP != 0);
   mu_assert_checkException("create()", exception);

   queueP->put(0, 0, 0);
   queueP->put(0, 0, &exception);
   mu_assert_checkWantException("put()", exception);

   queueP->put(queueP, 0, &exception);
   mu_assert_checkWantException("put()", exception);

   memset(&queueEntry, 0, sizeof(QueueEntry));
   queueP->put(queueP, &queueEntry, &exception);
   mu_assert_checkWantException("put()", exception);

   entries = queueP->peekWithSamePriority(0, 0, 0, 0);
   entries = queueP->peekWithSamePriority(0, 0, 0, &exception);
   mu_assert_checkWantException("peekWithSamePriority()", exception);

   entries = queueP->peekWithSamePriority(queueP, -1, -1, &exception);
   mu_assert_checkException("peekWithSamePriority()", exception);
   mu_assert("peekWithSamePriority() entries", entries != 0);
   mu_assertEqualsInt("peekWithSamePriority() entries", 0, entries->len);
   mu_assert("peekWithSamePriority() entries", entries->queueEntryArr == 0);
   freeQueueEntryArr(entries);
   entries = 0;

   numRemoved = queueP->randomRemove(0, 0, 0);
   numRemoved = queueP->randomRemove(0, 0, &exception);
   mu_assert_checkWantException("randomRemove()", exception);
   numRemoved = queueP->randomRemove(queueP, 0, &exception);
   mu_assert_checkException("randomRemove()", exception);

   numRemoved = queueP->randomRemove(queueP, entries, &exception);
   mu_assert_checkException("randomRemove()", exception);
   mu_assertEqualsInt("numRemoved", 0, (int)numRemoved);

   entries = (QueueEntryArr *)calloc(1, sizeof(QueueEntryArr));;
   numRemoved = queueP->randomRemove(queueP, entries, &exception);
   mu_assert_checkException("randomRemove()", exception);
   mu_assertEqualsInt("numRemoved", 0, (int)numRemoved);
   freeQueueEntryArr(entries);
   entries = 0;

   mu_assertEqualsBool("put() empty", true, queueP->empty(queueP));
   queueP->clear(0, 0);
   queueP->clear(0, &exception);
   mu_assert_checkWantException("clear()", exception);
   queueP->clear(queueP, &exception);
   mu_assert_checkException("clear()", exception);

   queueP->empty(0);

   mu_assertEqualsInt("numOfEntries", 0, queueP->getNumOfEntries(queueP));
   mu_assertEqualsInt("numOfBytes", 0, (int)queueP->getNumOfBytes(queueP));

   mu_assertEqualsInt("numOfEntries", -1, queueP->getNumOfEntries(0));
   mu_assertEqualsInt("numOfBytes", -1, (int)queueP->getNumOfBytes(0));

   queueP->shutdown(&queueP, &exception);
   printf("Testing test_illegal DONE\n");
   printf("-----------------------------------------\n");
   return 0;
}

/**
 * Test overflow of maxNumOfBytes and maxNumOfEntries. 
 */
static const char * test_overflow()
{
   ExceptionStruct exception;
   QueueProperties queueProperties;
   I_Queue *queueP = 0;
   const char *dbName = "xmlBlasterClient-C-Test.db";

   const int64_t idArr[] =   { 1081492136826000000ll, 1081492136856000000ll, 1081492136876000000ll, 1081492136911000000ll, 1081492136922000000ll };
   const int16_t prioArr[] = { 5                    , 1                    , 9                    , 9                    , 5 };
   const char *data[] =      { ""                   , ""                   , ""                   , ""                   , ""};
   const size_t numPut = sizeof(idArr)/sizeof(int64_t);

   printf("\n---------test_overflow-------------------\n");
   destroy(dbName); /* Delete old db file */

   memset(&queueProperties, 0, sizeof(QueueProperties));
   strncpy0(queueProperties.dbName, dbName, QUEUE_DBNAME_MAX);
   strncpy0(queueProperties.queueName, "connection_clientJoe", QUEUE_ID_MAX);
   strncpy0(queueProperties.tablePrefix, "XB_", QUEUE_PREFIX_MAX);
   queueProperties.maxNumOfEntries = 4L;
   queueProperties.maxNumOfBytes = 25LL;
   queueProperties.logFp = loggingFp;
   queueProperties.logLevel = XMLBLASTER_LOG_TRACE;
   queueProperties.userObject = 0;

   queueP = createQueue(&queueProperties, &exception);
   mu_assertEqualsLong("create() maxNumOfEntries", 4L, (long)queueP->getMaxNumOfEntries(queueP));
   mu_assertEqualsString("create() maxNumOfBytes", int64ToStr(int64Str, 25LL), int64ToStr(int64StrX, queueP->getMaxNumOfBytes(queueP)));
   mu_assertEqualsBool("put() empty", true, queueP->empty(queueP));

   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");

   {  /* Test entry overflow */
      size_t i;
      size_t lenPut = 0;
      for (i=0; i<numPut; i++) {
         QueueEntry queueEntry;
         memset(&queueEntry, 0, sizeof(QueueEntry));
         queueEntry.priority = prioArr[i];
         queueEntry.isPersistent = true;
         queueEntry.uniqueId = idArr[i];
         strncpy0(queueEntry.embeddedType, "MSG_RAW|publish", QUEUE_ENTRY_EMBEDDEDTYPE_LEN);
         queueEntry.embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN-1] = 0;
         queueEntry.embeddedBlob.data = (char *)data[i];
         queueEntry.embeddedBlob.dataLen = strlen(queueEntry.embeddedBlob.data);

         queueP->put(queueP, &queueEntry, &exception);
         if (i < 4) {
            mu_assert_checkException("put()", exception);
            lenPut += strlen(queueEntry.embeddedBlob.data);
         }
         else {
            mu_assert_checkWantException("put() numOfEntries overflow", exception);
         }

      }
      mu_assertEqualsInt("put() numOfEntries", 4, queueP->getNumOfEntries(queueP));
      mu_assertEqualsInt("put() numOfBytes", lenPut, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool("put() empty", false, queueP->empty(queueP));

   }
   queueP->clear(queueP, &exception);

   {  /* Test byte overflow */
      size_t i;
      size_t lenPut = 0;
      for (i=0; i<numPut; i++) {
         QueueEntry queueEntry;
         memset(&queueEntry, 0, sizeof(QueueEntry));
         queueEntry.priority = prioArr[i];
         queueEntry.isPersistent = true;
         queueEntry.uniqueId = idArr[i];
         strncpy0(queueEntry.embeddedType, "MSG_RAW|publish", QUEUE_ENTRY_EMBEDDEDTYPE_LEN);
         queueEntry.embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN-1] = 0;
         queueEntry.embeddedBlob.data = (char *)"0123456789";
         queueEntry.embeddedBlob.dataLen = strlen(queueEntry.embeddedBlob.data);

         queueP->put(queueP, &queueEntry, &exception);
         if (i < 3) {
            mu_assert_checkException("put()", exception);
            lenPut += strlen(queueEntry.embeddedBlob.data);
         }
         else {
            mu_assert_checkWantException("put() numOfBytes overflow", exception);
         }

      }
      mu_assertEqualsInt("put() numOfEntries", 3, queueP->getNumOfEntries(queueP));
      mu_assertEqualsInt("put() numOfBytes", lenPut, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool("put() empty", false, queueP->empty(queueP));

   }
   queueP->clear(queueP, &exception);

   {  /* Test byte overflow with preset entry size */
      size_t i;
      size_t lenPut = 0;
      for (i=0; i<numPut; i++) {
         QueueEntry queueEntry;
         memset(&queueEntry, 0, sizeof(QueueEntry));
         queueEntry.priority = prioArr[i];
         queueEntry.isPersistent = true;
         queueEntry.uniqueId = idArr[i];
         queueEntry.sizeInBytes = 20;
         strncpy0(queueEntry.embeddedType, "MSG_RAW|publish", QUEUE_ENTRY_EMBEDDEDTYPE_LEN);
         queueEntry.embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN-1] = 0;
         queueEntry.embeddedBlob.data = (char *)"0123456789";
         queueEntry.embeddedBlob.dataLen = strlen(queueEntry.embeddedBlob.data);

         queueP->put(queueP, &queueEntry, &exception);
         if (i < 2) { /* (index 0 and 1): 2 entries * 20 bytes = 40 bytes */
            mu_assert_checkException("put()", exception);
            lenPut += (int)queueEntry.sizeInBytes;
         }
         else {
            mu_assert_checkWantException("put() numOfBytes overflow", exception);
         }

      }
      mu_assertEqualsInt("put() numOfEntries", 2, queueP->getNumOfEntries(queueP));
      mu_assertEqualsInt("put() numOfBytes", lenPut, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool("put() empty", false, queueP->empty(queueP));

   }
   queueP->clear(queueP, &exception);

   queueP->shutdown(&queueP, &exception);
   return 0;
}

/**
 * Test invocation of all queue methods. 
 */
static const char * test_queue()
{
   ExceptionStruct exception;
   QueueEntryArr *entries = 0;
   QueueProperties queueProperties;
   I_Queue *queueP = 0;
   const char *dbName = "xmlBlasterClient-C-Test.db";
   const char *dummy = "bla";

   const int64_t idArr[] =   { 1081492136826000000ll, 1081492136856000000ll, 1081492136876000000ll, 1081492136911000000ll, 1081492136922000000ll };
   const int16_t prioArr[] = { 5                    , 1                    , 9                    , 9                    , 5 };
   const char *data[] =      { "1. Hello"           , "2. World"           , "3. High Prio 1"     , "4. High Prio 2"     , "5. done"};
   const size_t numPut = sizeof(idArr)/sizeof(int64_t);
   size_t lenPut = 0;

   printf("\n---------test_queue----------------------\n");
   destroy(dbName); /* Delete old db file */

   memset(&queueProperties, 0, sizeof(QueueProperties));
   strncpy0(queueProperties.dbName, dbName, QUEUE_DBNAME_MAX);
   strncpy0(queueProperties.queueName, "connection_clientJoe", QUEUE_ID_MAX);
   strncpy0(queueProperties.tablePrefix, "XB_", QUEUE_PREFIX_MAX);
   queueProperties.maxNumOfEntries = 10000000L;
   queueProperties.maxNumOfBytes = 1000000000LL;
   queueProperties.logFp = loggingFp;
   queueProperties.logLevel = XMLBLASTER_LOG_TRACE;
   queueProperties.userObject = (void *)dummy;

   queueP = createQueue(&queueProperties, &exception);
   mu_assert("create()", queueP != 0);
   mu_assert("create() QueueProperty", queueP->getProperties(queueP) != 0);
   mu_assert("create() userObject", queueP->userObject == dummy);
   mu_assertEqualsBool("create() isInitialized", true, queueP->isInitialized);
   mu_assertEqualsString("create() dbName", queueProperties.dbName, queueP->getProperties(queueP)->dbName);
   mu_assertEqualsString("create() tablePrefix", queueProperties.tablePrefix, queueP->getProperties(queueP)->tablePrefix);
   mu_assertEqualsString("create() queueName", queueProperties.queueName, queueP->getProperties(queueP)->queueName);
   mu_assertEqualsLong("create() maxNumOfEntries", 10000000L, (long)queueP->getMaxNumOfEntries(queueP));
   mu_assertEqualsString("create() maxNumOfBytes", int64ToStr(int64Str, 1000000000LL), int64ToStr(int64StrX, queueP->getMaxNumOfBytes(queueP)));
   mu_assertEqualsBool("put() empty", true, queueP->empty(queueP));

   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");

   {
      size_t i;
      for (i=0; i<numPut; i++) {
         QueueEntry queueEntry;
         memset(&queueEntry, 0, sizeof(QueueEntry));
         queueEntry.priority = prioArr[i];
         queueEntry.isPersistent = true;
         queueEntry.uniqueId = idArr[i];
         strncpy0(queueEntry.embeddedType, "MSG_RAW|publish", QUEUE_ENTRY_EMBEDDEDTYPE_LEN);
         queueEntry.embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN-1] = 0;
         queueEntry.embeddedBlob.data = (char *)data[i];
         queueEntry.embeddedBlob.dataLen = strlen(queueEntry.embeddedBlob.data);
         lenPut += strlen(queueEntry.embeddedBlob.data);

         queueP->put(queueP, &queueEntry, &exception);
         mu_assert_checkException("put()", exception);
      }
      mu_assertEqualsInt("put() numOfEntries", (int)numPut, queueP->getNumOfEntries(queueP));
      mu_assertEqualsInt("put() numOfBytes", lenPut, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool("put() empty", false, queueP->empty(queueP));

      printf("-----------------------------------------\n");
      printf("Testing shutdown and restart ...\n");
      queueP->shutdown(&queueP, &exception);
      mu_assert("shutdown()", queueP == 0);

      queueP = createQueue(&queueProperties, &exception);
      mu_assertEqualsInt("put() numOfEntries", (int)numPut, queueP->getNumOfEntries(queueP));
      mu_assertEqualsInt("put() numOfBytes", lenPut, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool("put() empty", false, queueP->empty(queueP));
      printf("-----------------------------------------\n");
   }

   {
      int j;
      printf("-----------------------------------------\n");
      printf("Testing peekWithSamePriority 9 ...\n");
      for (j=0; j<10; j++) {
         entries = queueP->peekWithSamePriority(queueP, 0, 0, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 0, entries->len);
         freeQueueEntryArr(entries);

         entries = queueP->peekWithSamePriority(queueP, 1, -1, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 1, entries->len);
         freeQueueEntryArr(entries);

         entries = queueP->peekWithSamePriority(queueP, -1, 3, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 1, entries->len);
         freeQueueEntryArr(entries);

         mu_assertEqualsInt("put() numOfEntries", (int)numPut, queueP->getNumOfEntries(queueP));
         mu_assertEqualsInt("put() numOfBytes", lenPut, (int)queueP->getNumOfBytes(queueP));
         mu_assertEqualsBool("put() empty", false, queueP->empty(queueP));
      }
      {
         size_t i;
         entries = queueP->peekWithSamePriority(queueP, -1, -1, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 2, entries->len);
         printf("testRun after peekWithSamePriority() dump %lu entries:\n", (unsigned long)entries->len);
         for (i=0; i<entries->len; i++) {
            char *tmp;
            size_t expectedIndex = i+2;
            QueueEntry *queueEntry = &entries->queueEntryArr[i];
            char *dump = queueEntryToXml(queueEntry, 200);
            printf("%s\n", dump);
            freeEntryDump(dump);
                           mu_assert("uniqueId fail", queueEntry->uniqueId == idArr[expectedIndex]);
            mu_assertEqualsString("uniqueId", int64ToStr(int64Str, idArr[expectedIndex]), int64ToStr(int64StrX, queueEntry->uniqueId));
            mu_assertEqualsInt("priority", 9, queueEntry->priority);
            mu_assertEqualsBool("persistent", true, queueEntry->isPersistent);
            mu_assertEqualsInt("bloblen", strlen(data[expectedIndex]), queueEntry->embeddedBlob.dataLen);
            tmp = strFromBlobAlloc(queueEntry->embeddedBlob.data, queueEntry->embeddedBlob.dataLen);
            mu_assertEqualsString("blob", data[expectedIndex], tmp);
            freeEntryDump(tmp);
         }
         freeQueueEntryArr(entries);
      }
      printf("-----------------------------------------\n");
   }

   {
      int32_t numRemoved;
      printf("-----------------------------------------\n");
      printf("Testing randomRemove prio=9 ...\n");
      printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");
      entries = queueP->peekWithSamePriority(queueP, -1, -1, &exception);

      numRemoved = queueP->randomRemove(queueP, entries, &exception);
      mu_assert_checkException("randomRemove()", exception);
      mu_assertEqualsInt("numRemoved", 2, (int)numRemoved);

      numRemoved = queueP->randomRemove(queueP, entries, &exception);
      mu_assert_checkException("randomRemove()", exception);
      mu_assertEqualsInt("numRemoved", 0, (int)numRemoved);

      freeQueueEntryArr(entries);

      mu_assertEqualsInt("put() numOfEntries", 3, queueP->getNumOfEntries(queueP));
      lenPut = strlen(data[0]) + strlen(data[1]) + strlen(data[4]);
      mu_assertEqualsInt("put() numOfBytes", lenPut, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool("put() empty", false, queueP->empty(queueP));
      printf("-----------------------------------------\n");
   }


   {
      int j;
      printf("-----------------------------------------\n");
      printf("Testing peekWithSamePriority 5 ...\n");
      for (j=0; j<10; j++) {
         entries = queueP->peekWithSamePriority(queueP, 0, 0, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 0, entries->len);
         freeQueueEntryArr(entries);

         entries = queueP->peekWithSamePriority(queueP, 1, -1, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 1, entries->len);
         freeQueueEntryArr(entries);

         entries = queueP->peekWithSamePriority(queueP, -1, 3, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 1, entries->len);
         freeQueueEntryArr(entries);
      }
      {
         size_t i;
         entries = queueP->peekWithSamePriority(queueP, -1, -1, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 2, entries->len);
         printf("testRun after peekWithSamePriority() dump %lu entries:\n", (unsigned long)entries->len);
         for (i=0; i<entries->len; i++) {
            char *tmp;
            int expectedIndex = (i==0) ? 0 : 4;
            QueueEntry *queueEntry = &entries->queueEntryArr[i];
            char *dump = queueEntryToXml(queueEntry, 200);
            printf("%s\n", dump);
            freeEntryDump(dump);
            mu_assertEqualsString("uniqueId", int64ToStr(int64Str, idArr[expectedIndex]), int64ToStr(int64StrX, queueEntry->uniqueId));
            mu_assertEqualsInt("priority", 5, queueEntry->priority);
            mu_assertEqualsBool("persistent", true, queueEntry->isPersistent);
            mu_assertEqualsInt("bloblen", strlen(data[expectedIndex]), queueEntry->embeddedBlob.dataLen);
            tmp = strFromBlobAlloc(queueEntry->embeddedBlob.data, queueEntry->embeddedBlob.dataLen);
            mu_assertEqualsString("blob", data[expectedIndex], tmp);
            freeEntryDump(tmp);
         }
         freeQueueEntryArr(entries);
      }

      mu_assertEqualsInt("put() numOfEntries", 3, queueP->getNumOfEntries(queueP));
      lenPut = strlen(data[0]) + strlen(data[1]) + strlen(data[4]);
      mu_assertEqualsInt("put() numOfBytes", lenPut, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool("put() empty", false, queueP->empty(queueP));
      printf("-----------------------------------------\n");
   }

   {
      int32_t numRemoved;
      printf("-----------------------------------------\n");
      printf("Testing randomRemove prio=5 ...\n");
      printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");
      entries = queueP->peekWithSamePriority(queueP, -1, -1, &exception);

      numRemoved = queueP->randomRemove(queueP, entries, &exception);
      mu_assert_checkException("randomRemove()", exception);
      mu_assertEqualsInt("numRemoved", 2, (int)numRemoved);

      numRemoved = queueP->randomRemove(queueP, entries, &exception);
      mu_assert_checkException("randomRemove()", exception);
      mu_assertEqualsInt("numRemoved", 0, (int)numRemoved);

      freeQueueEntryArr(entries);

      mu_assertEqualsInt("put() numOfEntries", 1, queueP->getNumOfEntries(queueP));
      lenPut = strlen(data[1]);
      mu_assertEqualsInt("put() numOfBytes", lenPut, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool("put() empty", false, queueP->empty(queueP));
      printf("-----------------------------------------\n");
   }

   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");

   {
      int j;
      printf("-----------------------------------------\n");
      printf("Testing peekWithSamePriority 1 ...\n");
      for (j=0; j<10; j++) {
         entries = queueP->peekWithSamePriority(queueP, 0, 0, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 0, entries->len);
         freeQueueEntryArr(entries);

         entries = queueP->peekWithSamePriority(queueP, 1, -1, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 1, entries->len);
         freeQueueEntryArr(entries);

         entries = queueP->peekWithSamePriority(queueP, -1, 3, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 1, entries->len);
         freeQueueEntryArr(entries);
      }
      {
         size_t i;
         entries = queueP->peekWithSamePriority(queueP, -1, -1, &exception);
         mu_assert_checkException("peekWithSamePriority()", exception);
         mu_assert(" peekWithSamePriority()", entries != 0);
         mu_assertEqualsInt("peekWithSamePriority() numOfEntries", 1, entries->len);
         printf("testRun after peekWithSamePriority() dump %lu entries:\n", (unsigned long)entries->len);
         for (i=0; i<entries->len; i++) {
            char *tmp;
            int expectedIndex = (i==0) ? 1 : 0;
            QueueEntry *queueEntry = &entries->queueEntryArr[i];
            char *dump = queueEntryToXml(queueEntry, 200);
            printf("%s\n", dump);
            freeEntryDump(dump);
            mu_assertEqualsString("uniqueId", int64ToStr(int64Str, idArr[expectedIndex]), int64ToStr(int64StrX, queueEntry->uniqueId));
            mu_assertEqualsInt("priority", 1, queueEntry->priority);
            mu_assertEqualsBool("persistent", true, queueEntry->isPersistent);
            mu_assertEqualsInt("bloblen", strlen(data[expectedIndex]), queueEntry->embeddedBlob.dataLen);
            tmp = strFromBlobAlloc(queueEntry->embeddedBlob.data, queueEntry->embeddedBlob.dataLen);
            mu_assertEqualsString("blob", data[expectedIndex], tmp);
            freeEntryDump(tmp);
         }
         freeQueueEntryArr(entries);
      }

      mu_assertEqualsInt("put() numOfEntries", 1, queueP->getNumOfEntries(queueP));
      lenPut = strlen(data[1]);
      mu_assertEqualsInt("put() numOfBytes", lenPut, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool("put() empty", false, queueP->empty(queueP));
      printf("-----------------------------------------\n");
   }
   
   queueP->clear(queueP, &exception);
   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");
   mu_assertEqualsInt("put() numOfEntries", 0, queueP->getNumOfEntries(queueP));
   mu_assertEqualsInt("put() numOfBytes", 0, (int)queueP->getNumOfBytes(queueP));
   mu_assertEqualsBool("put() empty", true, queueP->empty(queueP));

   queueP->shutdown(&queueP, &exception);

   destroy(dbName); /* Delete the db file */

   return 0;
}


static const char *all_tests()
{
   mu_run_test(test_illegal);
   mu_run_test(test_queue);
   mu_run_test(test_overflow);
   return 0;
}

int main(int argc_, char **argv_)
{
   const char *result;
   argc = argc_;
   argv = argv_;
   if (argc || argv) {} /* to avoid compiler warning */

   result = all_tests();

   if (result != 0) {
      printf("%s\n", result);
   }
   else {
      printf("ALL TESTS PASSED\n");
   }
   printf("Tests run: %d\n", tests_run);

   return result != 0;
}


#else /* XMLBLASTER_PERSISTENT_QUEUE_SQLITE3 */

int main(int argc_, char **argv_)
{
   printf("C-client is compiled without -XMLBLASTER_PERSISTENT_QUEUE or -XMLBLASTER_PERSISTENT_QUEUE_SQLITE3=1, no persistent queue tested\n");
   if (*MU_ASSERT_TEXT || tests_run) {} /* To suppress compiler warning */
   return 0;
}

#endif /* XMLBLASTER_PERSISTENT_QUEUE XMLBLASTER_PERSISTENT_QUEUE_SQLITE3 */


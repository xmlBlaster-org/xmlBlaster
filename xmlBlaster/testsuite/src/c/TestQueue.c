/*----------------------------------------------------------------------------
Name:      xmlBlaster/testsuite/src/c/TestQueue.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test C client library
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build c
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.queue.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <util/queue/I_Queue.h>
#include <unistd.h>   /* unlink */
#include "test.h"

#ifdef XMLBLASTER_PERSISTENT_QUEUE

static int argc = 0;
static char** argv = 0;
#define  ERRORSTR_LEN EXCEPTIONSTRUCT_ERRORCODE_LEN + EXCEPTIONSTRUCT_MESSAGE_LEN + 64
static char errorString[ERRORSTR_LEN];
static char int64Str_[32];
static char * const int64Str = int64Str_;   /* to make the pointer address const */
static char int64StrX_[32];
static char * const int64StrX = int64StrX_;   /* a second one */

#define mu_assert_checkException(message, exception) \
      do {\
         if (*exception.errorCode != 0) {\
            sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %s: Caught exception: '%s'", __FILE__, __LINE__, message, getExceptionStr(errorString, ERRORSTR_LEN, &exception));\
            return MU_ASSERT_TEXT;\
         }\
      } while (0)


/**
 * Invoke: TestQueue -logLevel TRACE
 */
static const char * test_illegal()
{
   return 0;
}

static const char * test_queue()
{
   ExceptionStruct exception;
   QueueEntryArr *entries = 0;
   QueueProperties queueProperties;
   I_Queue *queueP = 0;
   const char *dbName = "xmlBlasterClient-C-Test.db";

   const int64_t idArr[] =   { 1081492136826000000ll, 1081492136856000000ll, 1081492136876000000ll, 1081492136911000000ll, 1081492136922000000ll };
   const int16_t prioArr[] = { 5                    , 1                    , 9                    , 9                    , 5 };
   const char *data[] =      { "1. Hello"           , "2. World"           , "3. High Prio 1"     , "4. High Prio 2"     , "5. done"};
   const int numPut = sizeof(idArr)/sizeof(int64_t);
   int lenPut = 0;

   unlink(dbName); /* Delete old db file */

   strncpy0(queueProperties.dbName, dbName, QUEUE_DBNAME_MAX);
   strncpy0(queueProperties.nodeId, "clientJoe1081594557415", QUEUE_ID_MAX);
   strncpy0(queueProperties.queueName, "connection_clientJoe", QUEUE_ID_MAX);
   strncpy0(queueProperties.tablePrefix, "XB_", QUEUE_PREFIX_MAX);
   queueProperties.maxNumOfEntries = 10000000L;
   queueProperties.maxNumOfBytes = 1000000000LL;

   if (argc || argv) {} /* to avoid compiler warning */

   queueP = createQueue(&queueProperties, xmlBlasterDefaultLogging, LOG_TRACE, &exception);
   mu_assert("create()", queueP != 0);
   mu_assert("create() QueueProperty", queueP->getProperties(queueP) != 0);
   mu_assert("create() userObject", queueP->userObject == 0);
   mu_assertEqualsBool("create() isInitialized", true, queueP->isInitialized);
   mu_assertEqualsString("create() dbName", queueProperties.dbName, queueP->getProperties(queueP)->dbName);
   mu_assertEqualsString("create() nodeId", queueProperties.nodeId, queueP->getProperties(queueP)->nodeId);
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
      mu_assertEqualsInt("put() numOfEntries", numPut, queueP->getNumOfEntries(queueP));
      mu_assertEqualsInt("put() numOfBytes", lenPut, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool("put() empty", false, queueP->empty(queueP));

      printf("-----------------------------------------\n");
      printf("Testing shutdown and restart ...\n");
      queueP->shutdown(queueP, &exception);
      mu_assertEqualsBool("create() isInitialized", false, queueP->isInitialized);
      mu_assert("create() userObject", queueP->userObject == 0);

      entries = queueP->peekWithSamePriority(queueP, -1, -1, &exception);
      mu_assertEqualsString("create() isShutdown", "resource.db.unavailable", exception.errorCode);
      freeQueueEntryArr(entries);
      freeQueue(queueP);

      queueP = createQueue(&queueProperties, xmlBlasterDefaultLogging, LOG_TRACE, &exception);
      mu_assertEqualsInt("put() numOfEntries", numPut, queueP->getNumOfEntries(queueP));
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

         mu_assertEqualsInt("put() numOfEntries", numPut, queueP->getNumOfEntries(queueP));
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
            int expectedIndex = i+2;
            QueueEntry *queueEntry = &entries->queueEntryArr[i];
            char *dump = queueEntryToXmlLimited(queueEntry, 200);
            printf("%s\n", dump);
            free(dump);
            mu_assertEqualsString("uniqueId", int64ToStr(int64Str, idArr[expectedIndex]), int64ToStr(int64StrX, queueEntry->uniqueId));
            mu_assertEqualsInt("priority", 9, queueEntry->priority);
            mu_assertEqualsBool("persistent", true, queueEntry->isPersistent);
            mu_assertEqualsInt("bloblen", strlen(data[expectedIndex]), queueEntry->embeddedBlob.dataLen);
            tmp = strFromBlobAlloc(queueEntry->embeddedBlob.data, queueEntry->embeddedBlob.dataLen);
            mu_assertEqualsString("blob", data[expectedIndex], tmp);
            free(tmp);
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
            char *dump = queueEntryToXmlLimited(queueEntry, 200);
            printf("%s\n", dump);
            free(dump);
            mu_assertEqualsString("uniqueId", int64ToStr(int64Str, idArr[expectedIndex]), int64ToStr(int64StrX, queueEntry->uniqueId));
            mu_assertEqualsInt("priority", 5, queueEntry->priority);
            mu_assertEqualsBool("persistent", true, queueEntry->isPersistent);
            mu_assertEqualsInt("bloblen", strlen(data[expectedIndex]), queueEntry->embeddedBlob.dataLen);
            tmp = strFromBlobAlloc(queueEntry->embeddedBlob.data, queueEntry->embeddedBlob.dataLen);
            mu_assertEqualsString("blob", data[expectedIndex], tmp);
            free(tmp);
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
            char *dump = queueEntryToXmlLimited(queueEntry, 200);
            printf("%s\n", dump);
            free(dump);
            mu_assertEqualsString("uniqueId", int64ToStr(int64Str, idArr[expectedIndex]), int64ToStr(int64StrX, queueEntry->uniqueId));
            mu_assertEqualsInt("priority", 1, queueEntry->priority);
            mu_assertEqualsBool("persistent", true, queueEntry->isPersistent);
            mu_assertEqualsInt("bloblen", strlen(data[expectedIndex]), queueEntry->embeddedBlob.dataLen);
            tmp = strFromBlobAlloc(queueEntry->embeddedBlob.data, queueEntry->embeddedBlob.dataLen);
            mu_assertEqualsString("blob", data[expectedIndex], tmp);
            free(tmp);
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

   queueP->shutdown(queueP, &exception);
   freeQueue(queueP);

   unlink(dbName); /* Delete the db file */

   return 0;
}


static const char *all_tests()
{
   mu_run_test(test_illegal);
   mu_run_test(test_queue);
   return 0;
}

int main(int argc_, char **argv_)
{
   const char *result;
   argc = argc_;
   argv = argv_;

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


#else /* XMLBLASTER_PERSISTENT_QUEUE */

int main(int argc_, char **argv_)
{
   printf("C-client is compiled without -DXMLBLASTER_PERSISTENT_QUEUE=1, no persistent queue tested\n");
   if (*MU_ASSERT_TEXT || tests_run) {} /* To suppress compiler warning */
   return 0;
}

#endif /* XMLBLASTER_PERSISTENT_QUEUE */


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

static int argc = 0;
static char** argv = 0;
#define  ERRORSTR_LEN EXCEPTIONSTRUCT_ERRORCODE_LEN + EXCEPTIONSTRUCT_MESSAGE_LEN + 64
static char errorString[ERRORSTR_LEN];
static char int64Str_[32];
static char * const int64Str = int64Str_;   /* to make the pointer address const */
static char int64StrX_[32];
static char * const int64StrX = int64StrX_;   /* a second one */

#define mu_assert_checkException(file, line, message, exception) \
      do {\
         if (*exception.errorCode != 0) {\
            sprintf(MU_ASSERT_TEXT, "[TEST FAIL]%s:%d %s: Caught exception: '%s'", file, line, message, getExceptionStr(errorString, ERRORSTR_LEN, &exception));\
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

   unlink(dbName); /* Delete old db file */

   strncpy0(queueProperties.dbName, dbName, QUEUE_DBNAME_MAX);
   strncpy0(queueProperties.nodeId, "clientJoe1081594557415", QUEUE_ID_MAX);
   strncpy0(queueProperties.queueName, "connection_clientJoe", QUEUE_ID_MAX);
   strncpy0(queueProperties.tablePrefix, "XB_", QUEUE_PREFIX_MAX);
   queueProperties.maxNumOfEntries = 10000000L;
   queueProperties.maxNumOfBytes = 1000000000LL;

   if (argc || argv) {} /* to avoid compiler warning */

   queueP = createQueue(&queueProperties, xmlBlasterDefaultLogging, LOG_TRACE, &exception);
   mu_assert(__FILE__, __LINE__, "create()", queueP != 0);
   mu_assert(__FILE__, __LINE__, "create() QueueProperty", queueP->getProperties(queueP) != 0);
   mu_assert(__FILE__, __LINE__, "create() userObject", queueP->userObject == 0);
   mu_assertEqualsBool(__FILE__, __LINE__, "create() isInitialized", true, queueP->isInitialized);
   mu_assertEqualsString(__FILE__, __LINE__, "create() dbName", queueProperties.dbName, queueP->getProperties(queueP)->dbName);
   mu_assertEqualsString(__FILE__, __LINE__, "create() nodeId", queueProperties.nodeId, queueP->getProperties(queueP)->nodeId);
   mu_assertEqualsString(__FILE__, __LINE__, "create() tablePrefix", queueProperties.tablePrefix, queueP->getProperties(queueP)->tablePrefix);
   mu_assertEqualsString(__FILE__, __LINE__, "create() queueName", queueProperties.queueName, queueP->getProperties(queueP)->queueName);
   mu_assertEqualsLong(__FILE__, __LINE__, "create() maxNumOfEntries", 10000000L, (long)queueP->getMaxNumOfEntries(queueP));
   mu_assertEqualsString(__FILE__, __LINE__, "create() maxNumOfBytes", int64ToStr(int64Str, 1000000000LL), int64ToStr(int64StrX, queueP->getMaxNumOfBytes(queueP)));
   mu_assertEqualsBool(__FILE__, __LINE__, "put() empty", true, queueP->empty(queueP));

   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");

   {
      int64_t idArr[] =   { 1081492136826000000ll, 1081492136856000000ll, 1081492136876000000ll, 1081492136911000000ll, 1081492136922000000ll };
      int16_t prioArr[] = { 5                    , 1                    , 9                    , 9                    , 5 };
      char *data[] =      { "1. Hello"           , "2. World"           , "3. High Prio 1"     , "4. High Prio 2"     , "5. done"};
      size_t i;
      int numPut = sizeof(idArr)/sizeof(int64_t);
      int len = 0;
      for (i=0; i<numPut; i++) {
         QueueEntry queueEntry;
         queueEntry.priority = prioArr[i];
         queueEntry.isPersistent = true;
         queueEntry.uniqueId = idArr[i];
         strncpy0(queueEntry.embeddedType, "MSG_RAW|publish", QUEUE_ENTRY_EMBEDDEDTYPE_LEN);
         queueEntry.embeddedType[QUEUE_ENTRY_EMBEDDEDTYPE_LEN-1] = 0;
         queueEntry.embeddedBlob.data = data[i];
         queueEntry.embeddedBlob.dataLen = strlen(queueEntry.embeddedBlob.data);
         len += strlen(queueEntry.embeddedBlob.data);

         queueP->put(queueP, &queueEntry, &exception);
         mu_assert_checkException(__FILE__, __LINE__, "put()", exception);
      }
      mu_assertEqualsInt(__FILE__, __LINE__, "put() numOfEntries", numPut, queueP->getNumOfEntries(queueP));
      mu_assertEqualsInt(__FILE__, __LINE__, "put() numOfBytes", len, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool(__FILE__, __LINE__, "put() empty", false, queueP->empty(queueP));

      printf("-----------------------------------------\n");
      printf("Testing shutdown and restart ...\n");
      queueP->shutdown(queueP, &exception);
      mu_assertEqualsBool(__FILE__, __LINE__, "create() isInitialized", false, queueP->isInitialized);
      mu_assert(__FILE__, __LINE__, "create() userObject", queueP->userObject == 0);

      entries = queueP->peekWithSamePriority(queueP, -1, -1, &exception);
      mu_assertEqualsString(__FILE__, __LINE__, "create() isShutdown", "resource.db.unavailable", exception.errorCode);
      freeQueue(queueP);

      queueP = createQueue(&queueProperties, xmlBlasterDefaultLogging, LOG_TRACE, &exception);
      mu_assertEqualsInt(__FILE__, __LINE__, "put() numOfEntries", numPut, queueP->getNumOfEntries(queueP));
      mu_assertEqualsInt(__FILE__, __LINE__, "put() numOfBytes", len, (int)queueP->getNumOfBytes(queueP));
      mu_assertEqualsBool(__FILE__, __LINE__, "put() empty", false, queueP->empty(queueP));
      printf("-----------------------------------------\n");
   }

   
   entries = queueP->peekWithSamePriority(queueP, 0, 0, &exception);
   mu_assert_checkException(__FILE__, __LINE__, "peekWithSamePriority()", exception);
   mu_assert(__FILE__, __LINE__, " peekWithSamePriority()", queueP != 0);
   mu_assertEqualsInt(__FILE__, __LINE__, "peekWithSamePriority() numOfEntries", 1, entries->len);

   entries = queueP->peekWithSamePriority(queueP, 1, -1, &exception);
   mu_assert_checkException(__FILE__, __LINE__, "peekWithSamePriority()", exception);
   mu_assert(__FILE__, __LINE__, " peekWithSamePriority()", queueP != 0);
   mu_assertEqualsInt(__FILE__, __LINE__, "peekWithSamePriority() numOfEntries", 1, entries->len);

   entries = queueP->peekWithSamePriority(queueP, -1, 3, &exception);
   mu_assert_checkException(__FILE__, __LINE__, "peekWithSamePriority()", exception);
   mu_assert(__FILE__, __LINE__, " peekWithSamePriority()", queueP != 0);
   mu_assertEqualsInt(__FILE__, __LINE__, "peekWithSamePriority() numOfEntries", 1, entries->len);

   entries = queueP->peekWithSamePriority(queueP, -1, -1, &exception);
   mu_assert_checkException(__FILE__, __LINE__, "peekWithSamePriority()", exception);
   mu_assert(__FILE__, __LINE__, " peekWithSamePriority()", queueP != 0);
   mu_assertEqualsInt(__FILE__, __LINE__, "peekWithSamePriority() numOfEntries", 2, entries->len);
   if (entries != 0) {
      size_t i;
      printf("testRun after peekWithSamePriority() dump %lu entries:\n", (unsigned long)entries->len);
      for (i=0; i<entries->len; i++) {
         QueueEntry *queueEntry = &entries->queueEntryArr[i];
         char *dump = queueEntryToXmlLimited(queueEntry, 200);
         printf("%s\n", dump);
         free(dump);
      }
   }

   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");
   queueP->randomRemove(queueP, entries, &exception);
   mu_assert_checkException(__FILE__, __LINE__, "randomRemove()", exception);

   freeQueueEntryArr(entries);
   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");
   
   queueP->clear(queueP, &exception);
   printf("Queue numOfEntries=%d, numOfBytes=%s, empty=%s\n", queueP->getNumOfEntries(queueP), int64ToStr(int64Str, queueP->getNumOfBytes(queueP)), queueP->empty(queueP) ? "true" : "false");

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

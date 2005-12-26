/*----------------------------------------------------------------------------
Name:      xmlBlaster/testsuite/src/c/TestStress.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test C client library
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build c
Invoke:    Start 'java org.xmlBlaster.Main' and then 'TestStress'
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/c.client.socket.html
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterAccessUnparsed.h>
#include "test.h"

static int argc = 0;
static char** argv = 0;
#define  ERRORSTR_LEN 4096
static char errorString[ERRORSTR_LEN+1];
static char updateContent[256];
static void *updateUserData;
static const char *CONTENT = "Some message payload";
static size_t updateCounter = 0;

/**
 * Here we receive the callback messages from xmlBlaster
 * mu_assert() does not help here as it is another thread
 */
static bool myUpdate(MsgUnitArr *msgUnitArr, void *userData, XmlBlasterException *xmlBlasterException)
{
   size_t i;
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userData;
   if (xmlBlasterException != 0) ;  /* Supress compiler warning */
   updateUserData = xa;
   updateCounter += msgUnitArr->len;
   for (i=0; i<msgUnitArr->len; i++) {
      MsgUnit *msg = &msgUnitArr->msgUnitArr[i];
      if (xa->logLevel>=XMLBLASTER_LOG_TRACE)
         xa->log(0, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "CALLBACK update(): Asynchronous message update arrived\n"); 
      strncpy0(updateContent, msg->content, msg->contentLen+1); /* Adds '\0' to the end */
      msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc("<qos><state id='OK'/></qos>");
   }
   return true;
}

/**
 * Invoke: TestStress -logLevel TRACE
 */
static const char * test_stress()
{
   char *response = (char *)0;
   /*
      * callbackSessionId:
      * Is created by the client and used to validate callback messages in update. 
      * This is sent on connect in ConnectQos.
      * (Is different from the xmlBlaster secret session ID)
      */
   const char *callbackSessionId = "topSecret";
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xa = 0;
   bool retBool;
   int iPub, iWait, numPublish, maxQueueEntries, maxSessions=10;
   const char *sessionName = "joe";

   xa = getXmlBlasterAccessUnparsed(argc, (const char* const*)argv);
   if (xa->initialize(xa, myUpdate, &xmlBlasterException) == false) {
      freeXmlBlasterAccessUnparsed(xa);
      mu_fail("[TEST FAIL] Connection to xmlBlaster failed, please start the server or check your configuration");
   }

   numPublish      = xa->props->getInt(xa->props, "numPublish", 2500);
   maxQueueEntries = xa->props->getInt(xa->props, "queue/callback/maxEntries", numPublish);
   sessionName     = xa->props->getString(xa->props, "session.name", sessionName);
   maxSessions     = xa->props->getInt(xa->props, "session.maxSessions", maxSessions);

   {  /* connect */
      char connectQos[2048];
      char callbackQos[1024];
      sprintf(callbackQos,
               "<queue relating='callback' maxEntries='%d' maxEntriesCache='%d'>"
               "  <callback type='SOCKET' sessionId='%s'>"
               "    socket://%.120s:%d"
               "  </callback>"
               "</queue>",
               maxQueueEntries, maxQueueEntries, callbackSessionId, xa->callbackP->hostCB, xa->callbackP->portCB);
      sprintf(connectQos,
               "<qos>"
               " <securityService type='htpasswd' version='1.0'>"
               "  <![CDATA["
               "   <user>fritz</user>"
               "   <passwd>secret</passwd>"
               "  ]]>"
               " </securityService>"
               " <session name='%.120s' timeout='3600000' maxSessions='%d' clearSessions='false' reconnectSameClientOnly='false'/>"
               "%.1024s"
               "</qos>", sessionName, maxSessions, callbackQos);

      response = xa->connect(xa, connectQos, myUpdate, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != '\0') {
         SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception during connect errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         mu_assert(errorString, false);
      }
      xmlBlasterFree(response);
      printf("[client] Connected to xmlBlaster, do some tests ...\n");
   }

   { /* subscribe ... */
      const char *key = "<key oid='TestStress'/>";
      const char *qos = "<qos/>";
      printf("[client] Subscribe message 'TestStress' ...\n");
      response = xa->subscribe(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception in subscribe errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         mu_assert(errorString, false);
      }
      printf("[client] Subscribe success\n");
      mu_assert("Subscribe response is invalid", strstr(response, "subscribe id=")!=0);
      mu_assert("Subscribe response is invalid", strstr(response, "WARNING")==0);
      mu_assert("Subscribe response is invalid", strstr(response, "ERROR")==0);
      xmlBlasterFree(response);
   }

   printf("[client] Publishing %d messages 'TestStress' ...\n", numPublish);
   for (iPub=0; iPub<numPublish; iPub++) {
      char tmp[200];
      MsgUnit msgUnit;
      memset(&msgUnit, 0, sizeof(MsgUnit));
      msgUnit.key = strcpyAlloc("<key oid='TestStress'/>");
      sprintf(tmp, "#%d %s", (iPub+1), CONTENT);
      msgUnit.content = strcpyAlloc(tmp);
      msgUnit.contentLen = strlen(msgUnit.content);
      msgUnit.qos =strcpyAlloc("<qos><persistent>false</persistent></qos>");
      response = xa->publish(xa, &msgUnit, &xmlBlasterException);
      freeMsgUnitData(&msgUnit);
      if (*xmlBlasterException.errorCode != '\0') {
         SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception in publish #%d errorCode=%s, message=%s\n",
                  iPub, xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         mu_assert(errorString, false);
      }
      if (xa->logLevel>=XMLBLASTER_LOG_TRACE)
         xa->log(0, xa->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Publish #%d messages success\n", iPub); 
      mu_assert("Publish response is invalid", strstr(response, "rcvTimestamp nanos=")!=0);
      xmlBlasterFree(response);
   }

   for (iWait=0; iWait<10; iWait++) {
      printf("[client] Publish of %d messages success, received %d updates\n", numPublish, (int32_t)updateCounter);
      if ((int)updateCounter >= numPublish)
         break;
      sleepMillis(500);
   }

   mu_assert("No update arrived", *updateContent != '\0');
   if ((int)updateCounter < numPublish) {
      freeXmlBlasterAccessUnparsed(xa);
      mu_assert("Missing updates", updateCounter == numPublish);
   }
   else if ((int)updateCounter > numPublish) {
      printf("[client] WARN: Publish of %d messages but received %d updates\n", numPublish, (int32_t)updateCounter);
   }
   printf("[client] updateContent = %s, CONTENT = %s\n", updateContent, CONTENT);
   mu_assert("Received wrong message in update()", strstr(updateContent, CONTENT) != 0);
   *updateContent = '\0';

   mu_assert("UserData from update() is invalid", updateUserData == xa);


   {  /* erase ... */
      QosArr* responseArrP;
      const char *key = "<key oid='TestStress'/>";
      const char *qos = "<qos/>";
      printf("[client] Erasing message 'TestStress' ...\n");
      responseArrP = xa->erase(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != '\0') {
         SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception in erase() errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         mu_assert(errorString, false);
      }
      printf("[client] Erase success\n");
      freeQosArr(responseArrP);
   }

   retBool = xa->disconnect(xa, 0, &xmlBlasterException);
   if (*xmlBlasterException.errorCode != '\0') {
      SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception in erase() errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
      freeXmlBlasterAccessUnparsed(xa);
      mu_assert(errorString, false);
   }
   mu_assert("disconnect() returned false", retBool == true);

   if (*updateContent != '\0') { /* The erase event is sent as update as well */
      *updateContent = '\0';
   }

   freeXmlBlasterAccessUnparsed(xa);
   printf("[client] Good bye.\n");
   return 0;
}


static const char *all_tests()
{
   mu_run_test(test_stress);
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

/*----------------------------------------------------------------------------
Name:      xmlBlaster/testsuite/src/c/TestMethods.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test C client library
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build c
Invoke:    Start 'java org.xmlBlaster.Main' and then 'TestMethods'
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/c.client.socket.html
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterAccessUnparsed.h>
#include "test.h"

int tests_run = 0;
static int argc = 0;
static char** argv = 0;
#define  ERRORSTR_LEN 4096
static char errorString[ERRORSTR_LEN+1];
static char *updateContent = 0;
static void *updateUserData;
static const char *CONTENT = "Some message payload";

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
   for (i=0; i<msgUnitArr->len; i++) {
      MsgUnit *msg = &msgUnitArr->msgUnitArr[i];
      printf("[client] CALLBACK update(): Asynchronous message update arrived\n");
      updateContent = strFromBlobAlloc(msg->content, msg->contentLen);
      msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc("<qos><state id='OK'/></qos>");
   }
   return true;
}

/**
 * Invoke: TestMethods -logLevel TRACE
 */
static const char * test_methods()
{
   int iarg;
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

   printf("[client] Try option '-help' if you need usage informations\n");

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
         char usage[XMLBLASTER_MAX_USAGE_LEN];
         const char *pp =
         "\n  -logLevel            ERROR | WARN | INFO | TRACE [WARN]"
         "\n\nExample:"
         "\n  TestMethods -logLevel TRACE"
         " -dispatch/connection/plugin/socket/hostname 192.168.2.9";
         printf("Usage:\nXmlBlaster C SOCKET client %s\n%s%s\n",
                  getXmlBlasterVersion(), xmlBlasterAccessUnparsedUsage(usage), pp);
         exit(EXIT_FAILURE);
      }
   }

   xa = getXmlBlasterAccessUnparsed(argc, (const char* const*)argv);
   if (xa->initialize(xa, myUpdate, &xmlBlasterException) == false) {
      freeXmlBlasterAccessUnparsed(xa);
      mu_assert("[TEST FAIL] Connection to xmlBlaster failed, please start the server or check your configuration",
                false);
   }

   {  /* connect */
      char connectQos[2048];
      char callbackQos[1024];
      sprintf(callbackQos,
               "<queue relating='callback' maxEntries='100' maxEntriesCache='100'>"
               "  <callback type='SOCKET' sessionId='%s'>"
               "    socket://%.120s:%d"
               "  </callback>"
               "</queue>",
               callbackSessionId, xa->callbackP->hostCB, xa->callbackP->portCB);
      sprintf(connectQos,
               "<qos>"
               " <securityService type='htpasswd' version='1.0'>"
               "  <![CDATA["
               "   <user>fritz</user>"
               "   <passwd>secret</passwd>"
               "  ]]>"
               " </securityService>"
               "%.1024s"
               "</qos>", callbackQos);

      response = xa->connect(xa, connectQos, myUpdate, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != '\0') {
         SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception during connect errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         mu_assert(errorString, false);
      }
      free(response);
      printf("[client] Connected to xmlBlaster, do some tests ...\n");
   }

   response = xa->ping(xa, 0, &xmlBlasterException);
   mu_assert("[TEST FAIL] Pinging a connected server failed", response != (char *)0);
   mu_assert("[TEST FAIL] Pinging a connected server failed", *xmlBlasterException.errorCode == 0);
   printf("[client] Pinging a connected server, response=%s\n", response);
   free(response);

   { /* subscribe ... */
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] Subscribe message 'HelloWorld' ...\n");
      response = xa->subscribe(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception in subscribe errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         mu_assert(errorString, false);
      }
      printf("[client] Subscribe success\n");
      mu_assert("[TEST FAIL] Subscribe response is invalid", strstr(response, "subscribe id=")!=0);
      mu_assert("[TEST FAIL] Subscribe response is invalid", strstr(response, "WARNING")==0);
      mu_assert("[TEST FAIL] Subscribe response is invalid", strstr(response, "ERROR")==0);
      free(response);
   }

   {  /* publish ... */
      MsgUnit msgUnit;
      printf("[client] Publishing message 'HelloWorld' ...\n");
      msgUnit.key = strcpyAlloc("<key oid='HelloWorld'/>");
      msgUnit.content = strcpyAlloc(CONTENT);
      msgUnit.contentLen = strlen(msgUnit.content);
      msgUnit.qos =strcpyAlloc("<qos><persistent/></qos>");
      response = xa->publish(xa, &msgUnit, &xmlBlasterException);
      freeMsgUnitData(&msgUnit);
      if (*xmlBlasterException.errorCode != '\0') {
         SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception in publish errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         mu_assert(errorString, false);
      }
      printf("[client] Publish success");
      mu_assert("[TEST FAIL] Publish response is invalid", strstr(response, "rcvTimestamp nanos=")!=0);
      free(response);
   }

   mu_assert("[TEST FAIL] No update arrived", updateContent != 0);
   mu_assert("[TEST FAIL] Received wrong message in update()", strcmp(CONTENT, updateContent) == 0);
   free(updateContent);
   updateContent = 0;

   mu_assert("[TEST FAIL] UserData from update() is invalid", updateUserData == xa);


   {  /* unSubscribe ... */
      QosArr* response;
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] UnSubscribe message 'HelloWorld' ...\n");
      response = xa->unSubscribe(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != '\0') {
         SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception in unSubscribe errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         mu_assert(errorString, false);
      }
      printf("[client] Unsubscribe success\n");
      freeQosArr(response);
   }

   {  /* get synchronous ... */
      size_t i;
      const char *key = "<key queryType='XPATH'>//key</key>";
      const char *qos = "<qos/>";
      MsgUnitArr *msgUnitArr;
      printf("[client] Get synchronous messages with XPath '//key' ...\n");
      msgUnitArr = xa->get(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[TEST FAIL] Caught exception in get errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      if (*xmlBlasterException.errorCode != '\0') {
         SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception in get() errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         mu_assert(errorString, false);
      }
      mu_assert("[TEST FAIL] Empty get() return", msgUnitArr != (MsgUnitArr *)0);
      for (i=0; i<msgUnitArr->len; i++) {
         char *contentStr = strFromBlobAlloc(msgUnitArr->msgUnitArr[i].content,
                                          msgUnitArr->msgUnitArr[i].contentLen);
         printf("[client] Received message#%lu/%lu\n", (unsigned long)(i+1), (unsigned long)msgUnitArr->len);
         free(contentStr);
      }
      freeMsgUnitArr(msgUnitArr);
   }


   {  /* erase ... */
      QosArr* response;
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] Erasing message 'HelloWorld' ...\n");
      response = xa->erase(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != '\0') {
         SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception in erase() errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         mu_assert(errorString, false);
      }
      printf("[client] Erase success\n");
      freeQosArr(response);
   }

   retBool = xa->disconnect(xa, 0, &xmlBlasterException);
   if (*xmlBlasterException.errorCode != '\0') {
      SNPRINTF(errorString, ERRORSTR_LEN, "[TEST FAIL] Caught exception in erase() errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
      freeXmlBlasterAccessUnparsed(xa);
      mu_assert(errorString, false);
   }
   mu_assert("[TEST FAIL] disconnect() returned false", retBool == true);

   if (updateContent != 0) { /* The erase event is sent as update as well */
      free(updateContent);
      updateContent = 0;
   }

   freeXmlBlasterAccessUnparsed(xa);
   printf("[client] Good bye.\n");
   return 0;
}


static const char *all_tests()
{
   mu_run_test(test_methods);
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

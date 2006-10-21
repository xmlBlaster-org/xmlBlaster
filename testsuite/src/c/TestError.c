/*----------------------------------------------------------------------------
Name:      xmlBlaster/testsuite/src/c/TestError.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test C client library
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build c
Invoke:    Start 'java org.xmlBlaster.Main' and then 'TestError'
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/c.client.socket.html
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterAccessUnparsed.h>
#include "test.h"

/**
 * Here we receive the callback messages from xmlBlaster
 * mu_assert() does not help here as it is another thread
 */
static bool myUpdate(MsgUnitArr *msgUnitArr, void *userData, XmlBlasterException *xmlBlasterException)
{
   if (msgUnitArr != 0) ;  /* Supress compiler warnings */
   if (userData != 0) ;
   if (xmlBlasterException != 0) ;
   return true;
}

/**
 * Test illegal arguments
 */
static const char * test_illegalSubscribe()
{
   char *response = (char *)0;
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xa = 0;

   printf("\n[client] test_illegalSubscribe() ...\n");

   xa = getXmlBlasterAccessUnparsed(0, 0);

   { /* subscribe: With illegal arguments ... */
      printf("[client] Subscribe with all illegal args ...\n");
      response = xa->subscribe(0, 0, 0, 0);
      if (response == 0) {
         printf("[client] Subscribe response==NULL is OK, we provided illegal arguments\n");
      }
      else {
         xmlBlasterFree(response);
         mu_fail("[TEST FAIL] Subscribe response was not expected, we provided illegal arguments");
      }
   }

   { /* subscribe: With illegal arguments ... */
      printf("[client] Subscribe with illegal exception pointer ...\n");
      response = xa->subscribe(xa, 0, 0, 0);
      if (response == 0) {
         printf("[client] Subscribe response==NULL is OK, we provided illegal arguments\n");
      }
      else {
         xmlBlasterFree(response);
         mu_fail("[TEST FAIL] Subscribe response was not expected, we provided illegal arguments");
      }
   }

   { /* subscribe: With illegal arguments ... */
      printf("[client] Subscribe message 'NULL' ...\n");
      response = xa->subscribe(xa, 0, 0, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != '\0') {
         printf("[client] Subscribe exception is OK, we provided illegal arguments: Caught exception in subscribe errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
      }
      else {
         xmlBlasterFree(response);
         mu_fail("[TEST FAIL] Subscribe response was not expected, we provided illegal arguments\n");
      }
   }

   { /* subscribe: We are not connected yet !... */
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] Subscribe message 'HelloWorld' ...\n");
      response = xa->subscribe(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != '\0') {
         printf("[client] Subscribe exception is OK, we are not connected: Caught exception in subscribe errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
      }
      else {
         xmlBlasterFree(response);
         mu_fail("[TEST FAIL] Subscribe response was not expected, we are not connected");
      }
   }

   freeXmlBlasterAccessUnparsed(xa);
   xa = 0;
   freeXmlBlasterAccessUnparsed(xa);
   printf("[client] SUCCESS test_illegalSubscribe()\n");
   return 0;
}


/**
 * Test illegal arguments
 */
static const char * test_illegalDisconnect()
{
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xa = 0;
   char *response = 0;

   initializeXmlBlasterException(&xmlBlasterException);

   printf("\n[client] test_illegalDisconnect() ...\n");

   xa = getXmlBlasterAccessUnparsed(0, 0);
   
   printf("[client] Testing disconnect behavior\n");
   if (xa->disconnect(0, 0, 0) == false) {
      printf("[client] Disconnect exception is OK, we are not connected: Caught exception in disconnect errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
   }
   else {
      xmlBlasterFree(response);
      mu_fail("[TEST FAIL] disconnect true was not expected, we are not connected");
   }

   if (xa->disconnect(xa, 0, &xmlBlasterException) == false) {
      printf("[client] Disconnect exception is OK, we are not connected: Caught exception in disconnect errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
   }
   else {
      xmlBlasterFree(response);
      mu_fail("[TEST FAIL] disconnect true was not expected, we are not connected");
   }


   freeXmlBlasterAccessUnparsed(xa);
   printf("[client] SUCCESS test_illegalDisconnect()\n");
   return 0;
}

/**
 * Test illegal arguments
 */
static const char * test_illegalConnect()
{
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xa = 0;
   char *response = 0;
   
   initializeXmlBlasterException(&xmlBlasterException);

   printf("\n[client] test_illegalConnect() ...\n");

   xa = getXmlBlasterAccessUnparsed(0, 0);
   
   printf("[client] Testing initialize() behavior\n");
   if (xa->initialize(0, 0, 0) == false) {
      printf("[client] Initialize exception is OK, we are not connected: errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
   }
   else {
      xmlBlasterFree(response);
      mu_fail("[TEST FAIL] initialize true was not expected, we are not connected");
   }

   if (xa->initialize(xa, 0, &xmlBlasterException) == false) {
      mu_assert("initialize false was not expected (check if a server is running), we provided a NULL callback", false);
   }
   else {
      printf("[client] Initialize with updateP is NULL is OK, the default handler is used.");
      xmlBlasterFree(response);
   }

   printf("[client] Testing connect() behavior\n");
   response = xa->connect(0, 0, 0, 0);
   if (response == 0) {
      printf("[client] connect() return NULL is OK with NULL args\n");
   }
   else {
      xmlBlasterFree(response);
      mu_fail("[TEST FAIL] connect() return was not expected, we are not connected");
   }

   response = xa->connect(xa, 0, 0, 0);
   if (response == 0) {
      printf("[client] connect() return NULL is OK with NULL exception\n");
   }
   else {
      xmlBlasterFree(response);
      mu_fail("[TEST FAIL] connect() return was not expected, we are not connected");
   }

   response = xa->connect(xa, 0, myUpdate, &xmlBlasterException);
   if (response == 0) {
      printf("[client] connect() return NULL is OK with NULL ConnectQos: , errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
   }
   else {
      xmlBlasterFree(response);
      mu_fail("[TEST FAIL] connect() return was not expected for ConnectQos==NULL");
   }

   freeXmlBlasterAccessUnparsed(xa);
   printf("[client] SUCCESS test_illegalConnect()\n");
   return 0;
}

static const char *all_tests()
{
   mu_run_test(test_illegalSubscribe);
   mu_run_test(test_illegalDisconnect);
   mu_run_test(test_illegalConnect);
   printf("[client] Good bye.\n");
   return 0;
}

int main(int argc, char **argv)
{
   const char *result = all_tests();

   if (result != 0) {
      printf("%s\n", result);
   }
   else {
      printf("ALL TESTS PASSED\n");
   }
   printf("Tests run: %d\n", tests_run);

   return result != 0;
}

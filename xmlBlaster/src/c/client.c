/*----------------------------------------------------------------------------
Name:      client.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   client connects with raw socket to xmlBlaster
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -Wall -g -Isocket -I. -o client *.c socket/*.c -lpthread
Compile-Win: cl /MT /W3 /Wp64 -D_WINDOWS client.c callbackServer.c ws2_32.lib
Invoke:    client -socket.hostCB develop -socket.portCB 7607
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <unistd.h>
#include <sys/types.h>
#ifdef _WINDOWS
#else
#  include <pthread.h>
#endif
#include "xmlBlasterAccessUnparsed.h"


/**
 * Here we asynchronous receive the callback from xmlBlaster
 * msg = char *key, char *content, int contentLen, char *qos
 *
 * NOTE: After this call the memory of msg is freed immediately by callbackServer.c
 *       So you need to take a copy of all msg members if needed.
 */
void update(MsgUnit *msg)
{
   char *xml = messageUnitToXml(msg);
   if (XMLBLASTER_DEBUG) printf("client.update(): Asynchronous message update arrived:\n%s\n", xml);
   free(xml);
   /*
   char content[msg->contentLen+1];
   contentToString(content, msg);
   if (XMLBLASTER_DEBUG)
      printf("client.update(): Asynchronous message update arrived:\nkey=%s\ncontent=%s\nqos=%s\n",
             msg->xmlKey, content, msg->qos);
   */
}

/**
 * Test the baby
 */
int main(int argc, char** argv)
{
#ifndef _WINDOWS
   pthread_t tid;
#endif
   int iarg;
   char *data = (char *)0;
   char *response = (char *)0;
   bool startCallback = false;
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xb;

   callbackData cbArgs;

   cbArgs.hostCB = 0;
   cbArgs.portCB = 7611;
   cbArgs.update = update;

   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-socket.hostCB") == 0)
         cbArgs.hostCB = argv[++iarg];
      else if (strcmp(argv[iarg], "-socket.portCB") == 0)
         cbArgs.portCB = atoi(argv[++iarg]);
      else if (strcmp(argv[iarg], "-startCallback") == 0)
         startCallback = true;
   }

#  ifndef _WINDOWS
   if (startCallback) {
      int ret;
      ret = pthread_create(&tid, 0, (cbFp)initCallbackServer, &cbArgs);
   }
#  endif

   xb = getXmlBlasterAccessUnparsed(argc, argv);
   if (xb == (XmlBlasterAccessUnparsed *)0) {
      printf("Connection failed, please start xmlBlaster server first\n");
      exit(1);
   }

   if (xb->ping(xb, 0) == (char *)0) {
      printf("Pinging a not connected server failed -> this is OK\n");
   }
   else {
      printf("ERROR: Pinging a not connected server should not be possible\n");
   }

   {  // connect
      data = "<qos>"
             " <securityService type='htpasswd' version='1.0'>"
             "  <![CDATA["
             "   <user>fritz</user>"
             "   <passwd>secret</passwd>"
             "  ]]>"
             " </securityService>"
             "</qos>";

      response = xb->connect(xb, data, &xmlBlasterException);
      free(response);
      if (strlen(xmlBlasterException.errorCode) > 0) {
         printf("Caught exception during connect, errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   {
      response = xb->ping(xb, 0);
      if (response == (char *)0) {
         printf("ERROR: Pinging a connected server failed\n");
      }
      else {
         printf("SUCCESS: Pinging a connected server, response=%s\n", response);
         free(response);
      }
   }

   {  // publish ...
      MsgUnit msgUnit;
      printf("Connected to xmlBlaster, publishing a message ...\n");
      msgUnit.key = "<key oid='HelloWorld'/>";
      msgUnit.content = "Some message payload";
      msgUnit.contentLen = strlen("Some message payload");
      msgUnit.qos = "<qos><persistent/></qos>";
      response = xb->publish(xb, &msgUnit, &xmlBlasterException);
      if (response) {
         printf("Publish success, returned status is '%s'\n", response);
         free(response);
      }
      else {
         printf("Caught exception in publish, errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   if (false) {  // subscribe ...  CALLBACK NOT YET IMPLEMENTED (subscribe make no sense)
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("Subscribe a message ...\n");
      response = xb->subscribe(xb, key, qos, &xmlBlasterException);
      if (response) {
         printf("Erase success, returned status is '%s'\n", response);
         free(response);
      }
      else {
         printf("Caught exception in subscribe errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   {  // unSubscribe ...
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("UnSubscribe a message ...\n");
      response = xb->unSubscribe(xb, key, qos, &xmlBlasterException);
      if (response) {
         printf("Erase success, returned status is '%s'\n", response);
         free(response);
      }
      else {
         printf("Caught exception in unSubscribe errorCode=%s, message=%s\n", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   {  // get synchnronous ...
      size_t i;
      const char *key = "<key queryType='XPATH'>//key</key>";
      const char *qos = "<qos/>";
      MsgUnitArr *msgUnitArr;
      printf("Get synchronous a message ...\n");
      msgUnitArr = xb->get(xb, key, qos, &xmlBlasterException);
      if (msgUnitArr != (MsgUnitArr *)0) {
         for (i=0; i<msgUnitArr->len; i++) {
            char *contentStr = strFromBlobAlloc(msgUnitArr->msgUnitArr[i].content, msgUnitArr->msgUnitArr[i].contentLen);
            printf("GET-RECEIVED message#%u/%u\n%s\n<content>%.100s...</content>%s\n",
                   i+1, msgUnitArr->len,
                   msgUnitArr->msgUnitArr[i].key,
                   contentStr,
                   msgUnitArr->msgUnitArr[i].qos);
            free(contentStr);
         }
         freeMsgUnitArr(msgUnitArr);
      }
      else {
         printf("Caught exception in get errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }


   {  // erase ...
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("Erasing a message ...\n");
      response = xb->erase(xb, key, qos, &xmlBlasterException);
      if (response) {
         printf("Erase success, returned status is '%s'\n", response);
         free(response);
      }
      else {
         printf("Caught exception in erase errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   {  // disconnect ...
      if (xb->disconnect(xb, 0, &xmlBlasterException) == false) {
         printf("Caught exception in disconnect, errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: going to sleep 100 sec ...\n");
#  ifndef _WINDOWS
   if (startCallback) {
      sleep(10000);
   }
#  endif

   freeXmlBlasterAccessUnparsed(xb);
   exit(0);
}


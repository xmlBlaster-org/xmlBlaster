/*----------------------------------------------------------------------------
Name:      client.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   client connects with raw socket to xmlBlaster
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -Wall -g -D_REENTRANT -Isocket -I. -o client *.c socket/*.c -lpthread
Compile-Win: cl /MT /W3 /Wp64 -D_WINDOWS -I. client.c msgUtil.c socket\*.c ws2_32.lib
Invoke:    client -dispatch/callback/plugin/socket/hostname develop -dispatch/callback/plugin/socket/port 7607 -debug true
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
Date:      05/2003
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <sys/types.h>
#ifdef _WINDOWS
#else
#  include <unistd.h>
#  include <pthread.h>
#endif
#include <XmlBlasterAccessUnparsed.h>
#include <CallbackServerUnparsed.h>

static bool debug = false;
static bool help = false;


/**
 * Here we asynchronous receive the callback from xmlBlaster
 * msg = char *key, char *content, int contentLen, char *qos
 *
 * NOTE: After this call the memory of msg is freed immediately by callbackServer.c
 *       So you need to take a copy of all msg members if needed out of the scope of this function.
 * @param msg The message from the server
 * @param xmlBlasterException This points on a valid struct, so you only need to fill errorCode with strcpy
 *        and the returned pointer is ignored and the exception is thrown to xmlBlaster.
 * @return The update return QoS, XML formatted. You need to allocate it with malloc() and
 *         the library will free it for you. Returning NULL is OK as well.
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.update.html
 */
char *update(MsgUnit *msg, XmlBlasterException *xmlBlasterException)
{
   // Do something useful with the arrived message
   char *xml = messageUnitToXml(msg);
   if (debug) printf("client.update(): Asynchronous message update arrived:\n%s\n", xml);
   free(xml);

   /*
   char content[msg->contentLen+1];
   contentToString(content, msg);
   if (debug)
      printf("client.update(): Asynchronous message update arrived:\nkey=%s\ncontent=%s\nqos=%s\n",
             msg->xmlKey, content, msg->qos);
   */

   if (false) { // How to throw an exception
      strncpy0(xmlBlasterException->errorCode, "user.notWanted", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(xmlBlasterException->message, "I don't want this message", XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return 0;
   }

   return strcpyAlloc("<qos/>"); // Everything is OK
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
   char *connectQos = (char *)0;
   char *response = (char *)0;
   bool startCallback = false;
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xb = 0;
   CallbackServerUnparsed *cb = 0;

   printf("[client] Try option '-help' if you need usage informations\n");

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0)
         help = true;
   }
   if (help) {
      const char *pp =
      "\n  -startCallback       true/false [false]"
      "\n  -debug               true/false [false]"
      "\n\nExample:"
      "\n  client -debug true -startCallback true -dispatch/connection/plugin/socket/hostname server.mars.universe";
      printf("Usage:\n%s%s%s\n", xmlBlasterAccessUnparsedUsage(), callbackServerRawUsage(), pp);
      exit(1);
   }

   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-startCallback") == 0)
         startCallback = !strcmp(argv[++iarg], "true");
      else if (strcmp(argv[iarg], "-debug") == 0)
         debug = !strcmp(argv[++iarg], "true");
   }

#  ifndef _WINDOWS
   if (startCallback) {
      int ret;
      cb = getCallbackServerUnparsed(argc, argv, update);
      ret = pthread_create(&tid, 0, (cbFp)cb->initCallbackServer, cb);
      printf("[client] Created callback server thread listening on sokcet://%s:%d\n", cb->hostCB, cb->portCB);
   }
#  endif

   xb = getXmlBlasterAccessUnparsed(argc, argv);
   if (xb == (XmlBlasterAccessUnparsed *)0) {
      printf("[client] Connection failed, please start xmlBlaster server first\n");
      exit(1);
   }
   xb->debug = debug;

   if (xb->ping(xb, 0) == (char *)0) {
      printf("[client] Pinging a not connected server failed -> this is OK\n");
   }
   else {
      printf("[client] ERROR: Pinging a not connected server should not be possible\n");
   }

   {  // connect
      connectQos =
             "<qos>"
             " <securityService type='htpasswd' version='1.0'>"
             "  <![CDATA["
             "   <user>fritz</user>"
             "   <passwd>secret</passwd>"
             "  ]]>"
             " </securityService>"
             "</qos>";

      response = xb->connect(xb, connectQos, &xmlBlasterException);
      free(response);
      if (strlen(xmlBlasterException.errorCode) > 0) {
         printf("[client] Caught exception during connect, errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   {
      response = xb->ping(xb, 0);
      if (response == (char *)0) {
         printf("[client] ERROR: Pinging a connected server failed\n");
      }
      else {
         printf("[client] SUCCESS: Pinging a connected server, response=%s\n", response);
         free(response);
      }
   }

   {  // publish ...
      MsgUnit msgUnit;
      printf("[client] Connected to xmlBlaster, publishing a message ...\n");
      msgUnit.key = "<key oid='HelloWorld'/>";
      msgUnit.content = "Some message payload";
      msgUnit.contentLen = strlen("Some message payload");
      msgUnit.qos = "<qos><persistent/></qos>";
      response = xb->publish(xb, &msgUnit, &xmlBlasterException);
      if (response) {
         printf("[client] Publish success, returned status is '%s'\n", response);
         free(response);
      }
      else {
         printf("[client] Caught exception in publish, errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   if (false) {  // subscribe ...  CALLBACK NOT YET IMPLEMENTED (subscribe make no sense)
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] Subscribe a message ...\n");
      response = xb->subscribe(xb, key, qos, &xmlBlasterException);
      if (response) {
         printf("[client] Subscribe success, returned status is '%s'\n", response);
         free(response);
      }
      else {
         printf("[client] Caught exception in subscribe errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   {  // unSubscribe ...
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] UnSubscribe a message ...\n");
      response = xb->unSubscribe(xb, key, qos, &xmlBlasterException);
      if (response) {
         printf("[client] Unsbscribe success, returned status is '%s'\n", response);
         free(response);
      }
      else {
         printf("[client] Caught exception in unSubscribe errorCode=%s, message=%s\n", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   {  // get synchnronous ...
      size_t i;
      const char *key = "<key queryType='XPATH'>//key</key>";
      const char *qos = "<qos/>";
      MsgUnitArr *msgUnitArr;
      printf("[client] Get synchronous a message ...\n");
      msgUnitArr = xb->get(xb, key, qos, &xmlBlasterException);
      if (msgUnitArr != (MsgUnitArr *)0) {
         for (i=0; i<msgUnitArr->len; i++) {
            char *contentStr = strFromBlobAlloc(msgUnitArr->msgUnitArr[i].content, msgUnitArr->msgUnitArr[i].contentLen);
            printf("[client] GET-RECEIVED message#%u/%u\n%s\n<content>%.100s...</content>%s\n",
                   i+1, msgUnitArr->len,
                   msgUnitArr->msgUnitArr[i].key,
                   contentStr,
                   msgUnitArr->msgUnitArr[i].qos);
            free(contentStr);
         }
         freeMsgUnitArr(msgUnitArr);
      }
      else {
         printf("[client] Caught exception in get errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }


   {  // erase ...
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] Erasing a message ...\n");
      response = xb->erase(xb, key, qos, &xmlBlasterException);
      if (response) {
         printf("[client] Erase success, returned status is '%s'\n", response);
         free(response);
      }
      else {
         printf("[client] Caught exception in erase errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

   {  // disconnect ...
      if (xb->disconnect(xb, 0, &xmlBlasterException) == false) {
         printf("[client] Caught exception in disconnect, errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         exit(1);
      }
   }

#  ifndef _WINDOWS
   if (startCallback) {
      if (xb->debug) printf("[client] going to sleep 10000 seconds as the callback server is active ...\n");
      sleep(10000);
   }
#  endif

   freeCallbackServerUnparsed(cb);
   freeXmlBlasterAccessUnparsed(xb);
   exit(0);
}


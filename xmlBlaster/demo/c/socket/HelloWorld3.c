/*----------------------------------------------------------------------------
Name:      xmlBlaster/demo/c/socket/HelloWorld3.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Example for all remote method invocations.
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build.sh c
Invoke:    HelloWorld3 -help
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterAccessUnparsed.h>

/**
 * Here we receive the callback messages from xmlBlaster
 */
bool myUpdate(MsgUnitArr *msgUnitArr, XmlBlasterException *xmlBlasterException)
{
   size_t i;
   bool testException = false;
   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",
             xml);
      free(xml);
      msgUnitArr->msgUnitArr[i].responseQos = strcpyAlloc("<qos><state id='OK'/></qos>");
      /* Return QoS: Everything is OK */
   }
   if (testException) {
      strncpy0(xmlBlasterException->errorCode, "user.clientCode",
               XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(xmlBlasterException->message, "I don't want these messages",
               XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }
   return true;
}

/**
 * Invoke: HelloWorld3 -logLevel TRACE
 */
int main(int argc, char** argv)
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

   printf("[client] Try option '-help' if you need usage informations\n");

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
         char usage[XMLBLASTER_MAX_USAGE_LEN];
         const char *pp =
         "\n  -logLevel            ERROR | WARN | INFO | TRACE [WARN]"
         "\n\nExample:"
         "\n  HelloWorld3 -logLevel TRACE"
         " -dispatch/connection/plugin/socket/hostname 192.168.2.9";
         printf("Usage:\nXmlBlaster C SOCKET client %s\n%s%s\n",
                  getXmlBlasterVersion(), xmlBlasterAccessUnparsedUsage(usage), pp);
         exit(EXIT_FAILURE);
      }
   }

   xa = getXmlBlasterAccessUnparsed(argc, argv);
   if (xa->initialize(xa, myUpdate) == false) {
      printf("[client] Connection to xmlBlaster failed,"
             " please start the server or check your configuration\n");
      freeXmlBlasterAccessUnparsed(xa);
      exit(EXIT_FAILURE);
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
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception during connect errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      free(response);
      printf("[client] Connected to xmlBlaster, do some tests ...\n");
   }

   response = xa->ping(xa, 0);
   if (response == (char *)0) {
      printf("[client] ERROR: Pinging a connected server failed\n");
   }
   else {
      printf("[client] Pinging a connected server, response=%s\n", response);
      free(response);
   }

   { /* subscribe ... */
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] Subscribe message 'HelloWorld' ...\n");
      response = xa->subscribe(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in subscribe errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      printf("[client] Subscribe success, returned status is '%s'\n", response);
      free(response);
   }

   {  /* publish ... */
      MsgUnit msgUnit;
      printf("[client] Publishing message 'HelloWorld' ...\n");
      msgUnit.key = strcpyAlloc("<key oid='HelloWorld'/>");
      msgUnit.content = strcpyAlloc("Some message payload");
      msgUnit.contentLen = strlen(msgUnit.content);
      msgUnit.qos =strcpyAlloc("<qos><persistent/></qos>");
      response = xa->publish(xa, &msgUnit, &xmlBlasterException);
      freeMsgUnitData(&msgUnit);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in publish errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      printf("[client] Publish success, returned status is '%s'\n", response);
      free(response);
   }

   {  /* unSubscribe ... */
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] UnSubscribe message 'HelloWorld' ...\n");
      response = xa->unSubscribe(xa, key, qos, &xmlBlasterException);
      if (response) {
         printf("[client] Unsubscribe success, returned status is '%s'\n", response);
         free(response);
      }
      else {
         printf("[client] Caught exception in unSubscribe errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
   }

   {  /* get synchnronous ... */
      size_t i;
      const char *key = "<key queryType='XPATH'>//key</key>";
      const char *qos = "<qos/>";
      MsgUnitArr *msgUnitArr;
      printf("[client] Get synchronous messages with XPath '//key' ...\n");
      msgUnitArr = xa->get(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in get errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      if (msgUnitArr != (MsgUnitArr *)0) {
         for (i=0; i<msgUnitArr->len; i++) {
            char *contentStr = strFromBlobAlloc(msgUnitArr->msgUnitArr[i].content,
                                             msgUnitArr->msgUnitArr[i].contentLen);
            const char *dots = (msgUnitArr->msgUnitArr[i].contentLen > 96) ?
                                 " ..." : "";
            printf("\n[client] Received message#%u/%u:\n"
                     "-------------------------------------"
                     "%s\n <content>%.100s%s</content>%s\n"
                     "-------------------------------------\n",
                     i+1, msgUnitArr->len,
                     msgUnitArr->msgUnitArr[i].key,
                     contentStr, dots,
                     msgUnitArr->msgUnitArr[i].qos);
            free(contentStr);
         }
         freeMsgUnitArr(msgUnitArr);
      }
      else {
         printf("[client] Caught exception in get errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
   }


   {  /* erase ... */
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[client] Erasing message 'HelloWorld' ...\n");
      response = xa->erase(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in erase errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      printf("[client] Erase success, returned status is '%s'\n", response);
      free(response);
   }

   if (xa->disconnect(xa, 0, &xmlBlasterException) == false) {
      printf("[client] Caught exception in disconnect, errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
      freeXmlBlasterAccessUnparsed(xa);
      exit(EXIT_FAILURE);
   }

   freeXmlBlasterAccessUnparsed(xa);
   printf("[client] Good bye.\n");
   return 0;
}


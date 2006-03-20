/*----------------------------------------------------------------------------
Name:      xmlBlaster/demo/c/socket/HelloWorldUdp.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Example for one calls with UDP.
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build c
           (Win: copy xmlBlaster\src\c\socket\pthreadVC2.dll to your PATH)
Invoke:    HelloWorldUdp -help
See:    http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterAccessUnparsed.h>

/**
 * Here we receive the callback messages from xmlBlaster
 * @param msgUnitArr The received messages
 * @param userData Is the corresponding XmlBlasterAccessUnparsed * pointer
 * @param exception An OUT parameter to transport back an exception
 * @see UpdateFp in XmlBlasterAccessUnparsed.h
 * @see UpdateFp in CallbackServerUnparsed.h
 */
static bool myUpdate(MsgUnitArr *msgUnitArr, void *userData,
                     XmlBlasterException *exception)
{
   size_t i;
   /*XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userData;*/
   if (userData != 0) ;   /* Supress compiler warning */
   if (exception != 0) ;  /* Supress compiler warning */

   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",
             xml);
      xmlBlasterFree(xml);
   }

   return true;
}

/**
 * Invoke: HelloWorldUdp -logLevel TRACE
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
   int sleepInterval = 1000;
   bool updateOneway = true;

   printf("[client] XmlBlaster %s C SOCKET client, try option '-help' if you need"
          " usage informations\n", getXmlBlasterVersion());

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
         char usage[XMLBLASTER_MAX_USAGE_LEN];
         const char *pp =
         "\n   -logLevel            ERROR | WARN | INFO | TRACE | DUMP [WARN]"
         "\n   -sleepInterval       Milliseconds to wait on callback messages [1000]"
         "\n\nExample:"
         "\n  HelloWorldUdp -logLevel TRACE"
         " -dispatch/connection/plugin/socket/enableUdp true"
         " -dispatch/connection/plugin/socket/hostname 192.168.2.9"
         " -updateOneway true";
         printf("Usage:\nXmlBlaster C SOCKET client %s\n%s%s\n",
                  getXmlBlasterVersion(), xmlBlasterAccessUnparsedUsage(usage), pp);
         exit(EXIT_FAILURE);
      }
      else if (strcmp(argv[iarg], "-sleepInterval") == 0 && iarg < argc-1)
         sleepInterval = atoi(argv[++iarg]);
      else if (strcmp(argv[iarg], "-updateOneway") == 0 && iarg < argc-1)
         updateOneway = (!strcmp(argv[++iarg], "true")) ? true : false;
   }

   xa = getXmlBlasterAccessUnparsed(argc, (const char* const*)argv);
   if (xa->initialize(xa, myUpdate, &xmlBlasterException) == false) {
      printf("[client] Connection to xmlBlaster failed,"
             " please start the server or check your configuration\n");
      freeXmlBlasterAccessUnparsed(xa);
      exit(EXIT_FAILURE);
   }

   {  /* connect */
      char connectQos[2048];
      char callbackQos[1024];
      /*  oneway='true' as a general callback attribute is not used here */
      sprintf(callbackQos,
               "<queue relating='callback' maxEntries='50000' maxEntriesCache='10000'>"
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
      xmlBlasterFree(response);
      printf("[client] Connected to xmlBlaster, do some tests ...\n");
   }

   { /* subscribe ... */
      const char *key = "<key oid='HelloWorld'/>";
      char qos[1024];
      if (updateOneway)
         strcpy(qos, "<qos><updateOneway/><notify>false</notify></qos>");
      else
         strcpy(qos, "<qos><notify>false</notify></qos>");
      printf("[client] Subscribe message 'HelloWorld' with updateOneway=%s callback ...\n", updateOneway?"true":"false");
      response = xa->subscribe(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in subscribe errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      printf("[client] Subscribe success, returned status is '%s'\n", response);
      xmlBlasterFree(response);
   }
   
   {  /* publishOneway */
      MsgUnitArr holder;
      memset(&holder, 0, sizeof(MsgUnitArr));
      printf("[client] Publishing oneway messages 'HelloWorld' ...\n");
      holder.len = 2;
      holder.msgUnitArr = (MsgUnit *)calloc(holder.len, sizeof(MsgUnit));
      holder.msgUnitArr[0].key = strcpyAlloc("<key oid='HelloWorld'/>");
      holder.msgUnitArr[0].content = strcpyAlloc("Some message payload");
      holder.msgUnitArr[0].contentLen = strlen(holder.msgUnitArr[0].content);
      holder.msgUnitArr[0].qos =strcpyAlloc("<qos><expiration lifeTime='1000'/><forceUpdate>false</forceUpdate></qos>");

      holder.msgUnitArr[1].key = strcpyAlloc("<key oid='HelloWorld'/>");
      holder.msgUnitArr[1].content = strcpyAlloc("Some other message payload");
      holder.msgUnitArr[1].contentLen = strlen(holder.msgUnitArr[1].content);
      holder.msgUnitArr[1].qos =strcpyAlloc("<qos><expiration lifeTime='1000'/><forceUpdate>false</forceUpdate></qos>");

      xa->publishOneway(xa, &holder, &xmlBlasterException);
      
      freeMsgUnitArrInternal(&holder);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in publishOneway errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
   }

   printf("[client] Waiting one second on update messages ...\n");
   sleepMillis(sleepInterval);
 
   {  /* erase ... */
      QosArr* resp;
      const char *key = "<key oid='HelloWorld'/>";
      /*const char *key = "<key oid='' queryType='XPATH'>//key</key>";*/
      const char *qos = "<qos/>";
      printf("[client] Erasing message 'HelloWorld' ...\n");
      resp = xa->erase(xa, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception in erase errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      if (resp != 0) {
         size_t i;
         for (i=0; i<resp->len; i++) {
            printf("[client] Erase success, returned status is '%s'\n", resp->qosArr[i]);
         }
         freeQosArr(resp);
      }
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


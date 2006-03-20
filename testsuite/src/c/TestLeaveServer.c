/*----------------------------------------------------------------------------
Name:      xmlBlaster/demo/c/socket/TestLeaveServer.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Example for all remote method invocations.
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build c
           (Win: copy xmlBlaster\src\c\socket\pthreadVC2.dll to your PATH)
See: http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
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
   bool testException = false;
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userData;
   if (userData != 0) ;  /* Supress compiler warning */

   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",
             xml);
      xmlBlasterFree(xml);
      msgUnitArr->msgUnitArr[i].responseQos = 
                  strcpyAlloc("<qos><state id='OK'/></qos>");
      /* Return QoS: Everything is OK */
   }
   if (testException) {
      strncpy0(exception->errorCode, "user.clientCode",
               XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(exception->message, "I don't want these messages",
               XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }

   if (false /*xa->callbackMultiThreaded == true*/) {
      /* publish from inside the update thread,
         see -plugin/socket/multiThreaded true */
      char *response = (char *)0;
      XmlBlasterException xmlBlasterException;
      MsgUnit msgUnit;
                memset(&msgUnit, 0, sizeof(MsgUnit));
      printf("[client] Publishing message 'HelloWorldCb from update thread' ...\n");
      msgUnit.key = strcpyAlloc("<key oid='HelloWorldCb'/>");
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
      xmlBlasterFree(response);
   }
 
   return true;
}

/**
 * Invoke: TestLeaveServer -logLevel TRACE
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
   int sleepInterval = 0;
/*
        int tmpDbgFlag;

   _CrtSetReportMode( _CRT_ERROR, _CRTDBG_MODE_FILE );
   _CrtSetReportFile( _CRT_ERROR, _CRTDBG_FILE_STDERR );
   tmpDbgFlag = _CrtSetDbgFlag(_CRTDBG_REPORT_FLAG);
   tmpDbgFlag |= _CRTDBG_DELAY_FREE_MEM_DF;
        tmpDbgFlag |= _CRTDBG_CHECK_ALWAYS_DF;
        tmpDbgFlag |= _CRTDBG_ALLOC_MEM_DF;
   tmpDbgFlag |= _CRTDBG_LEAK_CHECK_DF;
   _CrtSetDbgFlag(tmpDbgFlag);
*/

   printf("[client] XmlBlaster %s C SOCKET client, try option '-help' if you need"
          " usage informations\n", getXmlBlasterVersion());

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
         char usage[XMLBLASTER_MAX_USAGE_LEN];
         const char *pp =
         "\n   -logLevel            ERROR | WARN | INFO | TRACE | DUMP [WARN]"
         "\n   -sleepInterval       Milliseconds to wait on callback messages [0]"
         "\n\nExample:"
         "\n  TestLeaveServer -logLevel TRACE"
         " -dispatch/connection/plugin/socket/hostname 192.168.2.9"
         " -sleepInterval 100000";
         printf("Usage:\nXmlBlaster C SOCKET client %s\n%s%s\n",
                  getXmlBlasterVersion(), xmlBlasterAccessUnparsedUsage(usage), pp);
         exit(EXIT_FAILURE);
      }
      else if (strcmp(argv[iarg], "-sleepInterval") == 0 && iarg < argc-1)
         sleepInterval = atoi(argv[++iarg]);
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

   response = xa->ping(xa, 0, &xmlBlasterException);
   if (response == (char *)0) {
      printf("[client] ERROR: Pinging a connected server failed: errorCode=%s, message=%s\n",
             xmlBlasterException.errorCode, xmlBlasterException.message);
      freeXmlBlasterAccessUnparsed(xa);
      exit(EXIT_FAILURE);
   }
   else {
      printf("[client] Pinging a connected server, response=%s\n", response);
      xmlBlasterFree(response);
   }

   { /* subscribe ... */
      const char *key = "<key oid='HelloWorld'/>";
      /*const char *key = "<key queryType='XPATH'>//key</key>";*/
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
      xmlBlasterFree(response);
   }
   
   if (sleepInterval > 0) {
      printf("[client] Sleeping now and wait on 'HelloWorld' updates (start a publisher somewhere) ...\n");
      sleepMillis(sleepInterval);
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
      xmlBlasterFree(response);
   }
 
 /*
   if (xa->disconnect(xa, 0, &xmlBlasterException) == false) {
      printf("[client] Caught exception in disconnect, errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
      freeXmlBlasterAccessUnparsed(xa);
      exit(EXIT_FAILURE);
   }
*/
   freeXmlBlasterAccessUnparsed(xa);
   printf("[client] Good bye.\n");
   return 0;
}


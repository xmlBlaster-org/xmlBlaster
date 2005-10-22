/*----------------------------------------------------------------------------
Name:      XmlBlasterConnectionUnparsedMain.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo client for synchronous access.
           XmlBlasterConnectionUnparsedMain connects with raw socket to xmlBlaster.
           No callbacks are coded, we only have synchronous access and we don't need
           multi threading in this case.
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:
  LINUX:   gcc -Wall -g -Wno-long-long -D_REENTRANT -I. -o XmlBlasterConnectionUnparsedMain XmlBlasterConnectionUnparsedMain.c util/helper.c util/msgUtil.c util/Properties.c socket/xmlBlasterSocket.c socket/xmlBlasterZlib.c socket/XmlBlasterConnectionUnparsed.c
  WIN:     cl /MT /W3 /Wp64 -D_WINDOWS -I. XmlBlasterConnectionUnparsedMain.c util\msgUtil.c util\Properties.c socket\XmlBlasterConnectionUnparsed.c socket\xmlBlasterSocket.c socket\xmlBlasterZlib.c ws2_32.lib
  Solaris: cc -Xc -g -I. -o XmlBlasterConnectionUnparsedMain XmlBlasterConnectionUnparsedMain.c util/msgUtil.c util/Properties.c socket/XmlBlasterConnectionUnparsed.c socket/xmlBlasterSocket.c socket/xmlBlasterZlib.c -lsocket -lnsl 
Invoke:    XmlBlasterConnectionUnparsedMain -dispatch/callback/plugin/socket/hostname develop -dispatch/callback/plugin/socket/port 7607
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
Date:      05/2003
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterConnectionUnparsed.h>

static bool help = false;

/**
 * Test the baby
 */
int main(int argc, char** argv)
{
   int iarg;
   char *response = (char *)0;
   XmlBlasterException xmlBlasterException;
   XmlBlasterConnectionUnparsed *xb = 0;

   printf("[XmlBlasterConnectionUnparsedMain] Try option '-help' if you need usage informations\n");

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0)
         help = true;
   }
   if (help) {
      const char *pp =
      "\n\nExample:"
      "\n  XmlBlasterConnectionUnparsedMain -logLevel TRACE -dispatch/connection/plugin/socket/hostname server.mars.universe";
      printf("Usage:\n%s%s\n", xmlBlasterConnectionUnparsedUsage(), pp);
      exit(EXIT_FAILURE);
   }

   xb = getXmlBlasterConnectionUnparsed(argc, (const char* const*)argv);

   if (xb->ping(xb, 0, &xmlBlasterException) == (char *)0) {
      printf("[XmlBlasterConnectionUnparsedMain] Pinging a not connected server failed -> this is OK\n");
   }
   else {
      printf("[XmlBlasterConnectionUnparsedMain] ERROR: Pinging a not connected server should not be possible\n");
   }

   {  /* connect */
      char connectQos[2048];
      strncpy(connectQos,
             "<qos>"
             " <securityService type='htpasswd' version='1.0'>"
             "  <![CDATA["
             "   <user>fritz</user>"
             "   <passwd>secret</passwd>"
             "  ]]>"
             " </securityService>"
             "</qos>", 2047);

      response = xb->connect(xb, connectQos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != '\0') {
         printf("[XmlBlasterConnectionUnparsedMain] Caught exception during connect errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterConnectionUnparsed(xb);
         exit(EXIT_FAILURE);
      }
      free(response);
      printf("[XmlBlasterConnectionUnparsedMain] Connected to xmlBlaster, do some tests ...\n");
   }

   {
      response = xb->ping(xb, 0, &xmlBlasterException);
      if (response == (char *)0) {
         printf("[XmlBlasterConnectionUnparsedMain] ERROR: Pinging a connected server failed:  errorCode=%s, message=%s\n",
                 xmlBlasterException.errorCode, xmlBlasterException.message);
      }
      else {
         printf("[XmlBlasterConnectionUnparsedMain] Pinging a connected server, response=%s\n", response);
         free(response);
      }
   }

   if (false) { /* subscribe ... -> we have no callback, we ommit subscribe */
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[XmlBlasterConnectionUnparsedMain] Subscribe message 'HelloWorld' ...\n");
      response = xb->subscribe(xb, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != '\0') {
         printf("[XmlBlasterConnectionUnparsedMain] Caught exception in subscribe errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterConnectionUnparsed(xb);
         exit(EXIT_FAILURE);
      }
      printf("[XmlBlasterConnectionUnparsedMain] Subscribe success, returned status is '%s'\n", response);
      free(response);
   }

   {  /* publish ... */
      MsgUnit msgUnit;
      printf("[XmlBlasterConnectionUnparsedMain] Publishing message 'HelloWorld' ...\n");
      msgUnit.key = "<key oid='HelloWorld'/>";
      msgUnit.content = "Some message payload";
      msgUnit.contentLen = strlen(msgUnit.content);
      msgUnit.qos = "<qos><persistent/></qos>";
      response = xb->publish(xb, &msgUnit, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != '\0') {
         printf("[XmlBlasterConnectionUnparsedMain] Caught exception in publish errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterConnectionUnparsed(xb);
         exit(EXIT_FAILURE);
      }
      printf("[XmlBlasterConnectionUnparsedMain] Publish success, returned status is '%s'\n", response);
      free(response);
   }

   {  /* unSubscribe ... */
   	QosArr *resp;
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[XmlBlasterConnectionUnparsedMain] UnSubscribe message 'HelloWorld' ...\n");
      resp = xb->unSubscribe(xb, key, qos, &xmlBlasterException);
      if (resp) {
         printf("[XmlBlasterConnectionUnparsedMain] Unsbscribe success\n");
         free(resp);
      }
      else {
         printf("[XmlBlasterConnectionUnparsedMain] Caught exception in unSubscribe errorCode=%s, message=%s\n", xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterConnectionUnparsed(xb);
         exit(EXIT_FAILURE);
      }
   }

   {  /* get synchnronous ... */
      size_t i;
      const char *key = "<key queryType='XPATH'>//key</key>";
      const char *qos = "<qos/>";
      MsgUnitArr *msgUnitArr;
      printf("[XmlBlasterConnectionUnparsedMain] Get synchronous messages with XPath '//key' ...\n");
      msgUnitArr = xb->get(xb, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[XmlBlasterConnectionUnparsedMain] Caught exception in get errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterConnectionUnparsed(xb);
         exit(EXIT_FAILURE);
      }
      if (msgUnitArr != (MsgUnitArr *)0) {
         for (i=0; i<msgUnitArr->len; i++) {
            char *contentStr = strFromBlobAlloc(msgUnitArr->msgUnitArr[i].content, msgUnitArr->msgUnitArr[i].contentLen);
            const char *dots = (msgUnitArr->msgUnitArr[i].contentLen > 96) ? " ..." : "";
            printf("\n[XmlBlasterConnectionUnparsedMain] Received message#%u/%u:\n"
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
         printf("[XmlBlasterConnectionUnparsedMain] Caught exception in get errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterConnectionUnparsed(xb);
         exit(EXIT_FAILURE);
      }
   }


   {  /* erase ... */
   	QosArr *resp;
      const char *key = "<key oid='HelloWorld'/>";
      const char *qos = "<qos/>";
      printf("[XmlBlasterConnectionUnparsedMain] Erasing message 'HelloWorld' ...\n");
      resp = xb->erase(xb, key, qos, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != '\0') {
         printf("[XmlBlasterConnectionUnparsedMain] Caught exception in erase errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterConnectionUnparsed(xb);
         exit(EXIT_FAILURE);
      }
      printf("[XmlBlasterConnectionUnparsedMain] Erase success, returned status is '%s'\n", response);
      free(resp);
   }

   {  /* disconnect ... */
      if (xb->disconnect(xb, 0, &xmlBlasterException) == false) {
         printf("[XmlBlasterConnectionUnparsedMain] Caught exception in disconnect, errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterConnectionUnparsed(xb);
         exit(EXIT_FAILURE);
      }
   }

   freeXmlBlasterConnectionUnparsed(xb);
   printf("[XmlBlasterConnectionUnparsedMain] Good bye.\n");
   return 0;
}

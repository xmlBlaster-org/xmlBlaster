/*----------------------------------------------------------------------------
Name:      HelloWorld.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   HelloWorld connects with raw socket to xmlBlaster
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:
  Linux:   gcc -Wall -g -D_REENTRANT -I. -o HelloWorld HelloWorld.c msgUtil.c
           socket/xmlBlasterSocket.c socket/XmlBlasterConnectionUnparsed.c
  Win:  cl /MT /W3 /Wp64 -D_WINDOWS -I. HelloWorld.c msgUtil.c socket\*.c ws2_32.lib
  Sun:  cc -g -D_REENTRANT -I. -o HelloWorld HelloWorld.c msgUtil.c
        socket/xmlBlasterSocket.c socket/XmlBlasterConnectionUnparsed.c -lsocket -lnsl
Date:      05/2003
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterConnectionUnparsed.h>

/**
 * Access the free memory in the server. 
 */
int main(int argc, char** argv)
{
   MsgUnitArr *msgUnitArr;
   XmlBlasterException exception;
   char *connectQos, *response;
   
   XmlBlasterConnectionUnparsed *xb = getXmlBlasterConnectionUnparsed(argc, argv);

   connectQos =   "<qos>"
                  " <securityService type='htpasswd' version='1.0'>"
                  "   <user>fritz</user>"
                  "   <passwd>secret</passwd>"
                  " </securityService>"
                  "</qos>";
   response = xb->connect(xb, connectQos, &exception);
   free(response);
   if (*exception.errorCode != 0) {
      printf("[client] Caught exception during connect, errorCode=%s, message=%s",
             exception.errorCode, exception.message);
      freeXmlBlasterConnectionUnparsed(xb);
      exit(1);
   }

   printf("[HelloWorld] Connected to xmlBlaster, invoking now get() ...\n");
   
   msgUnitArr = xb->get(xb, "<key oid='__cmd:?freeMem'/>", 0, &exception);
   if (*exception.errorCode != 0) {
      printf("[HelloWorld] Caught exception in get errorCode=%s, message=%s",
             exception.errorCode, exception.message);
      freeXmlBlasterConnectionUnparsed(xb);
      exit(1);
   }
   if (msgUnitArr != (MsgUnitArr *)0 && msgUnitArr->len > 0) {
      char *contentStr = strFromBlobAlloc(msgUnitArr->msgUnitArr[0].content,
                                          msgUnitArr->msgUnitArr[0].contentLen);
      printf("[HelloWorld] xmlBlaster has %s bytes of free memory\n", contentStr);
      free(contentStr);
   }
   freeMsgUnitArr(msgUnitArr);
   
   xb->disconnect(xb, 0, &exception);

   freeXmlBlasterConnectionUnparsed(xb);
   printf("[HelloWorld] Good bye.\n");
   return 0;
}

/*----------------------------------------------------------------------------
Name:      HelloWorld.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   HelloWorld connects with raw socket to xmlBlaster
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -Wall -g -D_REENTRANT -I. -o HelloWorld HelloWorld.c msgUtil.c socket/xmlBlasterSocket.c socket/XmlBlasterConnectionUnparsed.c
Compile-Win: cl /MT /W3 /Wp64 -D_WINDOWS -I. HelloWorld.c msgUtil.c socket\*.c ws2_32.lib
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
   char *connectQos;
   char *response;
   
   XmlBlasterConnectionUnparsed *xb = getXmlBlasterConnectionUnparsed(argc, argv);
   if (xb == (XmlBlasterConnectionUnparsed *)0) {
      printf("[HelloWorld] Connection failed, please start xmlBlaster server first\n");
      exit(1);
   }

   connectQos =
            "<qos>"
            " <securityService type='htpasswd'>"
            /*"  <![CDATA["*/
            "   <user>fritz</user>"
            "   <passwd>secret</passwd>"
            /*"  ]]>"*/
            " </securityService>"
            "</qos>";

   response = xb->connect(xb, connectQos, &exception);
   free(response);
   if (strlen(exception.errorCode) > 0) {
      printf("[client] Caught exception during connect, errorCode=%s, message=%s", exception.errorCode, exception.message);
      exit(1);
   }

   printf("[HelloWorld] Connected to xmlBlaster, invoking now get() ...\n");
   
   msgUnitArr = xb->get(xb, "<key oid='__cmd:?freeMem'/>", 0, &exception);

   if (msgUnitArr != (MsgUnitArr *)0 && msgUnitArr->len > 0) {
      char *contentStr = strFromBlobAlloc(msgUnitArr->msgUnitArr[0].content, msgUnitArr->msgUnitArr[0].contentLen);
      printf("[HelloWorld] xmlBlaster has currently %s bytes of free memory\n", contentStr);
      free(contentStr);
      freeMsgUnitArr(msgUnitArr);
   }
   else {
      printf("[HelloWorld] Caught exception in get errorCode=%s, message=%s", exception.errorCode, exception.message);
      exit(1);
   }
   
   xb->disconnect(xb, 0, &exception);

   freeXmlBlasterConnectionUnparsed(xb);
   printf("[HelloWorld] Good bye.\n");
   exit(0);
}

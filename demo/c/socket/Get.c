/*----------------------------------------------------------------------------
Name:      xmlBlaster/demo/c/socket/Get.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo to get xmlBlaster messages from command line
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build.sh c
           (Win: copy xmlBlaster\src\c\socket\pthreadVC2.dll to your PATH)
Invoke:    Get -help
See:    http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterAccessUnparsed.h>

/**
 * Demo client to synchronous get messages. 
 * Not all GetQos functionality is implemented.
 *
 * Access all messages: 
 *   Get -xpath //key
 *
 * Access all user names:
 *   Get -oid __sys__UserList
 *
 * Access free memory:
 *   Get -oid __cmd:?freeMem
 *
 * Access silently, useful for scripting:
 *   Get -oid __cmd:?freeMem -verbose false -sleep 0  -dumpRawContent true -logLevel WARN
 *   Get -oid __cmd:?topicList -verbose false -sleep 0  -dumpRawContent true -logLevel WARN
 *   Get -oid __cmd:?clientList -verbose false -sleep 0  -dumpRawContent true -logLevel WARN
 */
int main(int argc, char** argv)
{
   int iarg;
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xa = 0;
   bool disconnect = true;
   bool verbose = true;

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
         char usage[XMLBLASTER_MAX_USAGE_LEN];
         const char *pp =
         "\n\nExample:"
         "\n  Get -logLevel TRACE"
         " -dispatch/connection/plugin/socket/hostname 192.168.2.9";
         printf("Usage:\nXmlBlaster C SOCKET client %s\n%s%s\n",
                  getXmlBlasterVersion(), xmlBlasterAccessUnparsedUsage(usage), pp);
         exit(EXIT_FAILURE);
      }
   }

   xa = getXmlBlasterAccessUnparsed(argc, (const char* const* )argv);
   if (xa->initialize(xa, 0, &xmlBlasterException) == false) {
      printf("[client] Connection to xmlBlaster failed,"
             " please start the server or check your configuration\n");
      freeXmlBlasterAccessUnparsed(xa);
      exit(EXIT_FAILURE);
   }

   verbose = xa->props->getBool(xa->props, "verbose", verbose);
   if (verbose)
      printf("[client] XmlBlaster %s C SOCKET client, try option '-help' if you need"
             " usage informations\n", getXmlBlasterVersion());

   disconnect = xa->props->getBool(xa->props, "disconnect", disconnect);

   {  /* connect */
      char *response = (char *)0;
      const char * const sessionName = xa->props->getString(xa->props, "session.name", "Get");
      int maxSessions = xa->props->getInt(xa->props, "session.maxSessions", 10);
      const bool persistent = xa->props->getBool(xa->props, "persistentConnection", false);
      char connectQos[4096];
      sprintf(connectQos,
               "<qos>"
               " <securityService type='htpasswd' version='1.0'>"
               "  <![CDATA["
               "   <user>%.80s</user>"
               "   <passwd>secret</passwd>"
               "  ]]>"
               " </securityService>"
               " <session name='%.80s' timeout='3600000' maxSessions='%d' clearSessions='false' reconnectSameClientOnly='false'/>"
               " %.20s"
               "</qos>", sessionName, sessionName, maxSessions, persistent?"<persistent/>":"");

      response = xa->connect(xa, connectQos, 0, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception during connect errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      xmlBlasterFree(response);
      if (verbose)
         printf("[client] Connected to xmlBlaster, do some tests ...\n");
   }

   {  /* get synchronous ... */
      size_t i;
      int iGet;
      const char *getToken = 0;

      char key[4098];
      const char *oid = xa->props->getString(xa->props, "oid", "Hello");
      const char *domain = xa->props->getString(xa->props, "domain", 0);
      const char *xpath = xa->props->getString(xa->props, "xpath", 0);

      char filterQos[2048];
      char qos[4098];
      long sleep = xa->props->getLong(xa->props, "sleep", -1L); /* -1 == interactive */
      int numGet = xa->props->getInt(xa->props, "numGet", 1);
      bool dumpRawContent = xa->props->getBool(xa->props, "dumpRawContent", false);
      bool persistent = xa->props->getBool(xa->props, "get/qos/persistent", true);
      int historyNumUpdates = xa->props->getInt(xa->props, "get/qos/historyNumUpdates", 1);
      bool historyNewestFirst = xa->props->getBool(xa->props, "get/qos/historyNewestFirst", true);
      bool wantContent = xa->props->getBool(xa->props, "get/qos/wantContent", true);
      const char *filterType = xa->props->getString(xa->props, "get/qos/filter.type", "GnuRegexFilter");
      const char *filterVersion = xa->props->getString(xa->props, "get/qos/filter.version", "1.0");
      const char *filterQuery = xa->props->getString(xa->props, "get/qos/filter.query", 0);  /* "^H.*$" */
      /*Map clientPropertyMap = xa->props->getInt(xa->props, "clientProperty", (Map)0); */

      if (domain) {
         sprintf(key, "<key domain='%.512s'/>", domain);
         getToken = domain;
      }
      else if (xpath) {
         sprintf(key, "<key queryType='XPATH'>%.512s</key>", xpath);
         getToken = xpath;
      }
      else {
         sprintf(key, "<key oid='%.512s' queryType='EXACT'/>", oid);
         getToken = oid;
      }

      if (filterQuery) {
         sprintf(filterQos, " <filter type='%.100s' version='%.50s'>%.1800s</filter>",
                 filterType, filterVersion, filterQuery);
      }
      else
         *filterQos = 0;

      sprintf(qos, "<qos>"
                   " <content>%.20s</content>"
                   " <persistent>%.20s</persistent>"
                   "%.2048s"
                   " <history numEntries='%d' newestFirst='%.20s'/>"
                   "</qos>",
                   wantContent?"true":"false",
                   persistent?"true":"false",
                   filterQos,
                   historyNumUpdates,
                   historyNewestFirst?"true":"false"
                   );

      if (verbose) {
         printf("[client] Get key: %s\n", key);
         printf("[client] Get qos: %s\n", qos);
      }

      for (iGet=0; iGet<numGet; iGet++) {
         MsgUnitArr *msgUnitArr;

         if (sleep < 0) { /* -1 */
            char msg[20];
            printf("[client] Hit a key to get '%s' #%d/%d ('b' to break) >> ", getToken, iGet, numGet);
            fgets(msg, 19, stdin);
            if (*msg == 'b') 
               break;
         }
         else if (sleep == 0) {
            if (verbose) printf("[client] Get '%s' #%d/%d", oid, iGet, numGet);
         }
         else {
            sleepMillis(sleep);
            if (verbose) printf("[client] Get '%s' #%d/%d", oid, iGet, numGet);
         }

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
               if (dumpRawContent) {
                  MsgUnit *msg = &msgUnitArr->msgUnitArr[i];
                  char *contentStr = strFromBlobAlloc(msg->content, msg->contentLen);
                  printf("%s", contentStr);
                  xmlBlasterFree(contentStr);
               }
               else {
                  char *m = messageUnitToXmlLimited(&msgUnitArr->msgUnitArr[i], 100);
                  printf("\n[client] Get synchronous returned message#%lu/%lu:\n"
                           "-------------------------------------"
                           "%s\n"
                           "-------------------------------------\n",
                           (unsigned long)(i+1), (unsigned long)msgUnitArr->len, m);
                  xmlBlasterFree(m);
               }
            }
            if (msgUnitArr->len == 0) {
               printf("\n[client] Get '%s' synchronous: No message found\n", getToken);
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
   }

   if (disconnect) {
      if (xa->disconnect(xa, 0, &xmlBlasterException) == false) {
         printf("[client] Caught exception in disconnect, errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
   }

   freeXmlBlasterAccessUnparsed(xa);
   if (verbose) printf("[client] Good bye.\n");
   return 0;
}


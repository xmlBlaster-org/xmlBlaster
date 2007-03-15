/*----------------------------------------------------------------------------
Name:      xmlBlaster/demo/c/socket/Publisher.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo to publish messages from command line
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build.sh c
           (Win: copy xmlBlaster\src\c\socket\pthreadVC2.dll to your PATH)
Invoke:    Publisher -help
See:    http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterAccessUnparsed.h>

static char* readFile(const char *fn);

#if defined(WINCE)
int _tmain(int argc, _TCHAR** argv_wcs) { /* wchar_t==_TCHAR */
   char **argv = convertWcsArgv(argv_wcs, argc);
#else
/**
 * Demo client to publish messages. 
 * Not all PublishQos functionality is implemented.
 * Invoke: Publisher -logLevel TRACE
 */
int main(int argc, const char* const* argv) {
#endif
   int iarg, iPublish;
   const char *callbackSessionId = "topSecret";
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xa = 0;
   bool disconnect = true;
   bool erase = true;
   const char *publishToken = 0;

   printf("[client] XmlBlaster %s C SOCKET client, try option '-help' if you need"
          " usage informations\n", getXmlBlasterVersion());

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
         char usage[XMLBLASTER_MAX_USAGE_LEN];
         const char *pp =
         "\n\nExample:"
         "\n  Publisher -logLevel TRACE"
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

   disconnect = xa->props->getBool(xa->props, "disconnect", disconnect);
   erase = xa->props->getBool(xa->props, "erase", erase);

   {  /* connect */
      char *response = (char *)0;
      const char * const sessionName = xa->props->getString(xa->props, "session.name", "Publisher");
      const char * const passwd = xa->props->getString(xa->props, "passwd", "publisher");
      long sessionTimeout = xa->props->getLong(xa->props, "session.timeout", 86400000L);
      int maxSessions = xa->props->getInt(xa->props, "session.maxSessions", 10);
      const bool persistent = xa->props->getBool(xa->props, "persistentConnection", false);
      char connectQos[4096];
      char callbackQos[1024];
      sprintf(callbackQos,
               "<queue relating='callback' maxEntries='10000000' maxEntriesCache='10000000'>"
               "  <callback type='SOCKET' sessionId='%.256s'>"
               "    socket://%.120s:%d"
               "  </callback>"
               "</queue>",
               callbackSessionId, xa->callbackP->hostCB, xa->callbackP->portCB);
      sprintf(connectQos,
               "<qos>"
               " <securityService type='htpasswd' version='1.0'>"
               "  <![CDATA["
               "   <user>%.80s</user>"
               "   <passwd>%.40s</passwd>"
               "  ]]>"
               " </securityService>"
               " <session name='%.80s' timeout='%ld' maxSessions='%d' clearSessions='false' reconnectSameClientOnly='false'/>"
               " %.20s"
               "%.1024s"
               "</qos>", sessionName, passwd, sessionName, sessionTimeout, maxSessions, persistent?"<persistent/>":"", callbackQos);

      response = xa->connect(xa, connectQos, 0, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception during connect errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
      }
      xmlBlasterFree(response);
      printf("[client] Connected to xmlBlaster, do some tests ...\n");
   }

   { /* publish ... */
      char *response = (char *)0;

      char key[4098];
      const char *oid = xa->props->getString(xa->props, "oid", "Hello");
      const char *domain = xa->props->getString(xa->props, "domain", 0);
      bool interactive = xa->props->getBool(xa->props, "interactive", true);

      char qos[4098];
      char topicQos[2048];
      char destinationQos[2048];
      bool oneway = xa->props->getBool(xa->props, "oneway", false);
      long sleep = xa->props->getLong(xa->props, "sleep", 1000L);
      int numPublish = xa->props->getInt(xa->props, "numPublish", 1);
      const char *clientTags = xa->props->getString(xa->props, "clientTags", "<org.xmlBlaster><demo-%counter/></org.xmlBlaster>");
      const char *content = xa->props->getString(xa->props, "content", "Hi-%counter");
      int priority = xa->props->getInt(xa->props, "priority", 5);
      bool persistentPublish = xa->props->getBool(xa->props, "persistent", true);
      long lifeTime = xa->props->getLong(xa->props, "lifeTime", -1L);
      bool verbose = xa->props->getBool(xa->props, "verbose", true);
      bool forceUpdate = xa->props->getBool(xa->props, "forceUpdate", true);
      bool forceDestroy = xa->props->getBool(xa->props, "forceDestroy", false);
      bool readonly = xa->props->getBool(xa->props, "readonly", false);
      long destroyDelay = xa->props->getLong(xa->props, "destroyDelay", -1L);
      bool createDomEntry = xa->props->getBool(xa->props, "createDomEntry", true);
      long historyMaxMsg = xa->props->getLong(xa->props, "queue/history/maxEntries", 10L);
      long historyMaxBytes = xa->props->getLong(xa->props, "queue/history/maxBytes", 2147483647L);
      bool forceQueuing = xa->props->getBool(xa->props, "forceQueuing", true);
      bool subscribable = xa->props->getBool(xa->props, "subscribable", true);
      const char *destination = xa->props->getString(xa->props, "destination", 0);
      int contentSize = xa->props->getInt(xa->props, "contentSize", -1);
      const char *contentFile = xa->props->getString(xa->props, "contentFile", 0);
      /*Map clientPropertyMap = xa->props->getInt(xa->props, "clientProperty", (Map)0); */

      publishToken = (domain == 0) ? oid : domain;

      sprintf(key, "<key oid='%.512s' domain='%.100s'>%.2000s</key>",
                  oid, ((domain==0)?"":domain), clientTags);

      sprintf(topicQos, 
                   " <topic readonly='%.20s' destroyDelay='%ld' createDomEntry='%.20s'>"
                   "  <persistence/>"
                   "  <queue relating='history' type='CACHE' version='1.0' maxEntries='%ld' maxBytes='%ld'/>"
                   " </topic>",
                   readonly?"true":"false",
                   destroyDelay,
                   createDomEntry?"true":"false",
                   historyMaxMsg,
                   historyMaxBytes
                   );
      if (destination!=0)
         sprintf(destinationQos, " <destination queryType='EXACT' forceQueuing='%.20s'>%.512s</destination>",
                 forceQueuing?"true":"false", destination);
      else
         *destinationQos = 0;

      for (iPublish=0; iPublish<numPublish || numPublish==-1; iPublish++) {
         char msg[20];
         const char *pp = strstr(key, "%counter");
         MsgUnit msgUnit;
         memset(&msgUnit, 0, sizeof(MsgUnit));

         if (interactive) {
            printf("[client] Hit a key to publish '%s' #%d/%d ('b' to break) >> ", oid, iPublish, numPublish);
            fgets(msg, 19, stdin);
            if (*msg == 'b') 
               break;
         }
         else {
            if (sleep > 0) {
               sleepMillis(sleep);
            }
            if (verbose) {
               if (contentFile != 0)
                  printf("[client] Publish to topic '%s' file '%s' #%d/%d\n", oid, contentFile, iPublish, numPublish);
               else
                  printf("[client] Publish to topic '%s' #%d/%d\n", oid, iPublish, numPublish);
            }
         }

         if (pp) { /* Replace '%counter' token by current index */
            char *k = (char *)malloc(strlen(key)+10);
            strncpy(k, key, pp-key);
            sprintf(k+(pp-key), "%d%s", iPublish, pp+strlen("%counter"));
            msgUnit.key = k;
         }
         else
            msgUnit.key = strcpyAlloc(key);
         
         if (iPublish == 1) *topicQos = 0;
         sprintf(qos, "<qos>"
                   " <priority>%d</priority>"
                   " <subscribable>%.20s</subscribable>"
                   " <expiration lifeTime='%ld'/>"
                   " <persistent>%.20s</persistent>"
                   " <forceUpdate>%.20s</forceUpdate>"
                   " <forceDestroy>%.20s</forceDestroy>"
                   " %.2048s"
                   " <clientProperty name='%.100s'>%.512s</clientProperty>"
                   " %.512s"
                   "</qos>",
                   priority,
                   subscribable?"true":"false",
                   lifeTime,
                   persistentPublish?"true":"false",
                   forceUpdate?"true":"false",
                   forceDestroy?"true":"false",
                   destinationQos,
                   "", "", /* ClientProperty */
                   topicQos
                   );

         /*if (iPublish == 0) printf("[client] publishQos is\n%s\n", qos);*/

         if (contentSize > 0) {
            int i;
            char *p = (char *)malloc(contentSize);
            for (i=0; i<contentSize; i++) {
               int ran = rand() % 100;
               p[i] = (char)(ran+28);
            }
            msgUnit.content = p;
            msgUnit.contentLen = contentSize;
         }
         else if (contentFile != 0) {
            char* p = readFile(contentFile);
            msgUnit.content = p;
            msgUnit.contentLen = strlen(msgUnit.content);
         }
         else {
            const char *pc = strstr(content, "%counter");
            if (pc) { /* Replace '%counter' token by current index */
               char *p = (char *)malloc(strlen(content)+10);
               strncpy(p, content, pc-content);
               sprintf(p+(pc-content), "%d%s", iPublish, pc+strlen("%counter"));
               msgUnit.content = p;
               msgUnit.contentLen = strlen(msgUnit.content);
            }
            else {
               msgUnit.content = strcpyAlloc(content);
               msgUnit.contentLen = strlen(msgUnit.content);
            }
         }
         msgUnit.qos =strcpyAlloc(qos);
         if (oneway) {
            MsgUnitArr msgUnitArr;
            msgUnitArr.len = 1;
            msgUnitArr.msgUnitArr = &msgUnit;
            xa->publishOneway(xa, &msgUnitArr, &xmlBlasterException);
         }
         else {
            response = xa->publish(xa, &msgUnit, &xmlBlasterException);
         }
         freeMsgUnitData(&msgUnit);
         if (*xmlBlasterException.errorCode != 0) {
            printf("[client] Caught exception in publish errorCode=%s, message=%s\n",
                     xmlBlasterException.errorCode, xmlBlasterException.message);
            xa->disconnect(xa, 0, &xmlBlasterException);
            freeXmlBlasterAccessUnparsed(xa);
            exit(EXIT_FAILURE);
         }
         if (verbose) {
           printf("[client] Publish success, returned status is '%s'\n", response);
         }
         xmlBlasterFree(response);
      }
   }

   while (true) {
      char msg[20];
      bool interactive = xa->props->getBool(xa->props, "interactiveQuit", true);
      if (!interactive) break;
                  
      printf("(Enter 'q' to exit) >> ");
      fgets(msg, 19, stdin);
      if (*msg == 'q') 
         break;
   }
    
   if (erase) {  /* erase ... */
      QosArr *resp;
      char key[256];
      const char *qos = "<qos/>";
      sprintf(key, "<key oid='%.200s'/>", publishToken); /* TODO: use subscriptionId */
      printf("[client] Erase topic '%s' ...\n", publishToken);
      resp = xa->erase(xa, key, qos, &xmlBlasterException);
      if (resp) {
         size_t i;
         for (i=0; i<resp->len; i++) {
            printf("[client] Erase success, returned status is '%s'\n", resp->qosArr[i]);
         }
         freeQosArr(resp);
      }
      else {
         printf("[client] Caught exception in erase errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
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
   printf("[client] Good bye.\n");
   return 0;
}

char* readFile(const char *fn) {
   FILE *fp;
   char *retbuf = NULL;
   size_t nchmax = 0;
   register int c;
   size_t nchread = 0;
   char *newbuf;

   if ((fp = fopen(fn, "r")) == NULL) {
      printf("Error Opening File %s.\n", fn);
      return 0;
   }

   while ((c = getc(fp)) != EOF) {
      if (nchread >= nchmax) {
         nchmax += 1024;
         if(nchread >= nchmax) { /* in case nchmax overflowed */
            free(retbuf);
            return NULL;
         }
#ifdef SAFEREALLOC
         newbuf = realloc(retbuf, nchmax + 1);
#else
         if (retbuf == NULL)      /* in case pre-ANSI realloc */
            newbuf = (char *)malloc(nchmax + 1);
         else    newbuf = (char *)realloc(retbuf, nchmax + 1);
#endif
         /* +1 for \0 */
         if (newbuf == NULL) {
            free(retbuf);
            return NULL;
         }
         retbuf = newbuf;
      }
      retbuf[nchread++] = c;
   }

   if(retbuf != NULL) {
      retbuf[nchread] = '\0';
      newbuf = (char *)realloc(retbuf, nchread + 1);
      if(newbuf != NULL)
         retbuf = newbuf;
   }

   return retbuf;
}

/*----------------------------------------------------------------------------
Name:      xmlBlaster/demo/c/socket/Subscriber.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Example for all remote method invocations.
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build.sh c
           (Win: copy xmlBlaster\src\c\socket\pthreadVC.dll to your PATH)
Invoke:    Subscriber -help
See:    http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterAccessUnparsed.h>

static const char *updateExceptionErrorCode = 0;
static const char *updateExceptionMessage = 0;
static const char *subscribeToken = 0;
static int message_counter = 1;
static long updateSleep = 0l;

/**
 * Here we receive the callback messages from xmlBlaster
 * @see UpdateFp in CallbackServerUnparsed.h
 */
static bool myUpdate(MsgUnitArr *msgUnitArr, void *userData,
                     XmlBlasterException *exception)
{
   size_t i;
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)userData;
   if (xa != 0) ;  /* Supress compiler warning */

   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = messageUnitToXmlLimited(&msgUnitArr->msgUnitArr[i], 100);
   
      printf("\n[client] CALLBACK update(): Asynchronous message [%d] update arrived:%s\n",
             message_counter++, xml);
      /*printf("arrived message :%d\n",message_counter++);*/
      free(xml);
      msgUnitArr->msgUnitArr[i].responseQos = 
                  strcpyAlloc("<qos><state id='OK'/></qos>");
      /* Return QoS: Everything is OK */

      if (updateSleep > 0) {
         printf("[client] CALLBACK update(): Sleeping for %ld millis ...\n", updateSleep);
         sleepMillis(updateSleep);
      }
   }
   if (updateExceptionErrorCode) {
      strncpy0(exception->errorCode, updateExceptionErrorCode,
               XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      strncpy0(exception->message, updateExceptionMessage,
               XMLBLASTEREXCEPTION_MESSAGE_LEN);
      return false;
   }


   return true;
}

/**
 * Invoke: Subscriber -logLevel TRACE
 */
int main(int argc, char** argv)
{
   int iarg;
   const char *callbackSessionId = "topSecret";
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xa = 0;

   printf("[client] XmlBlaster %s C SOCKET client, try option '-help' if you need"
          " usage informations\n", getXmlBlasterVersion());

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
         char usage[XMLBLASTER_MAX_USAGE_LEN];
         const char *pp =
         "\n  -logLevel            ERROR | WARN | INFO | TRACE [WARN]"
         "\n\nExample:"
         "\n  Subscriber -logLevel TRACE"
         " -dispatch/connection/plugin/socket/hostname 192.168.2.9";
         printf("Usage:\nXmlBlaster C SOCKET client %s\n%s%s\n",
                  getXmlBlasterVersion(), xmlBlasterAccessUnparsedUsage(usage), pp);
         exit(EXIT_FAILURE);
      }
   }

   xa = getXmlBlasterAccessUnparsed(argc, (const char* const* )argv);
   if (xa->initialize(xa, myUpdate, &xmlBlasterException) == false) {
      printf("[client] Connection to xmlBlaster failed,"
             " please start the server or check your configuration\n");
      freeXmlBlasterAccessUnparsed(xa);
      exit(EXIT_FAILURE);
   }

   updateSleep = xa->props->getLong(xa->props, "updateSleep", 0L);
   updateExceptionErrorCode = xa->props->getString(xa->props, "updateException.errorCode", 0); /* "user.clientCode" */
   updateExceptionMessage = xa->props->getString(xa->props, "updateException.message", 0);  /* "I don't want these messages" */

   {  /* connect */
      char *response = (char *)0;
      const char * const sessionName = xa->props->getString(xa->props, "session.name", "Subscriber");
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
               "   <passwd>subscriber</passwd>"
               "  ]]>"
               " </securityService>"
               " <session name='%.80s' timeout='3600000' maxSessions='10' clearSessions='false' reconnectSameClientOnly='false'/>"
               " %.20s"
               "%.1024s"
               "</qos>", sessionName, sessionName, persistent?"<persistent/>":"", callbackQos);

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
      char *response = (char *)0;

      char key[1024];
      const char *oid = xa->props->getString(xa->props, "oid", "Hello");
      const char *domain = xa->props->getString(xa->props, "domain", 0);
      const char *xpath = xa->props->getString(xa->props, "xpath", 0);

      char filterQos[2048];
      char qos[4098];
      bool multiSubscribe = xa->props->getBool(xa->props, "multiSubscribe", true);
      bool persistentSubscribe = xa->props->getBool(xa->props, "persistentSubscribe", false);
      bool notifyOnErase = xa->props->getBool(xa->props, "notifyOnErase", true);
      bool local = xa->props->getBool(xa->props, "local", true);
      bool initialUpdate = xa->props->getBool(xa->props, "initialUpdate", true);
      int historyNumUpdates = xa->props->getInt(xa->props, "historyNumUpdates", 1);
      bool historyNewestFirst = xa->props->getBool(xa->props, "historyNewestFirst", true);
      bool wantContent = xa->props->getBool(xa->props, "wantContent", true);
      const char *filterType = xa->props->getString(xa->props, "filter.type", "GnuRegexFilter");
      const char *filterVersion = xa->props->getString(xa->props, "filter.version", "1.0");
      const char *filterQuery = xa->props->getString(xa->props, "filter.query", 0);  /* "^H.*$" */

      if (domain) {
         sprintf(key, "<key domain='%.512s'/>", domain);
         subscribeToken = domain;
      }
      else if (xpath) {
         sprintf(key, "<key queryType='XPATH'>%.512s</key>", xpath);
         subscribeToken = xpath;
      }
      else {
         sprintf(key, "<key oid='%.512s'/>", oid);
         subscribeToken = oid;
      }

      if (filterQuery) {
         sprintf(filterQos, " <filter type='%.100s' version='%.50s'>%.1800s</filter>",
                 filterType, filterVersion, filterQuery);
      }
      else
         *filterQos = 0;

      sprintf(qos, "<qos>"
                   " <content>%.20s</content>"
                   " <multiSubscribe>%.20s</multiSubscribe>"
                   " <persistent>%.20s</persistent>"
                   " <local>%.20s</local>"
                   " <initialUpdate>%.20s</initialUpdate>"
                   " <notify>%.20s</notify>"
                   "%.2048s"
                   " <history numEntries='%d' newestFirst='%.20s'/>"
                   "</qos>",
                   wantContent?"true":"false",
                   multiSubscribe?"true":"false",
                   persistentSubscribe?"true":"false",
                   local?"true":"false",
                   initialUpdate?"true":"false",
                   notifyOnErase?"true":"false",
                   filterQos,
                   historyNumUpdates,
                   historyNewestFirst?"true":"false"
                   );
      printf("[client] Subscribe key: %s\n", key);
      printf("[client] Subscribe qos: %s\n", qos);
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

   while (true) {
      char msg[20];
                  
      printf("(Enter 'q' to exit) >> ");
      fgets(msg, 19, stdin);
      if (*msg == 'q') 
         break;
   }
    
   {  /* unSubscribe ... */
      QosArr *resp;
      char key[256];
      const char *qos = "<qos/>";
      sprintf(key, "<key oid='%.200s'/>", subscribeToken); /* TODO: use subscriptionId */
      printf("[client] UnSubscribe topic '%s' ...\n", subscribeToken);
      resp = xa->unSubscribe(xa, key, qos, &xmlBlasterException);
      if (resp) {
         size_t i;
         for (i=0; i<resp->len; i++) {
            printf("[client] Unsubscribe success, returned status is '%s'\n", resp->qosArr[i]);
         }
         freeQosArr(resp);
      }
      else {
         printf("[client] Caught exception in unSubscribe errorCode=%s, message=%s\n",
                  xmlBlasterException.errorCode, xmlBlasterException.message);
         xa->disconnect(xa, 0, &xmlBlasterException);
         freeXmlBlasterAccessUnparsed(xa);
         exit(EXIT_FAILURE);
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


/*----------------------------------------------------------------------------
Name:      xmlBlaster/demo/c/socket/Subscriber.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Example for all remote method invocations.
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   cd xmlBlaster; build.sh c
           (Win: copy xmlBlaster\src\c\socket\pthreadVC2.dll to your PATH)
Invoke:    Subscriber -help
See:    http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <XmlBlasterAccessUnparsed.h>
#include <util/Timestampc.h>

static const char *updateExceptionErrorCode = 0;
static const char *updateExceptionMessage = 0;
static const char *subscribeToken = 0;
static const char *queryType;
static int message_counter = 1;
static long updateSleep = 0l;
static bool reportUpdateProgress = false;
static int64_t startTimestamp = 0ll; /* In nano sec */
static bool verbose = true;

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

   if (startTimestamp == 0ll)
      startTimestamp = getTimestamp();

   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = messageUnitToXmlLimited(&msgUnitArr->msgUnitArr[i], 100);

      const int modulo = 100;
      if ((message_counter % modulo) == 0) {
         int64_t endTimestamp = getTimestamp();
         int rate = (int)(((int64_t)message_counter*1000*1000*1000)/(endTimestamp-startTimestamp));
         const char *persistent = (strstr(xml, "<persistent>true</persistent>")!=NULL||strstr(xml, "<persistent/>")!=NULL) ? "persistent" : "transient";
         xa->log(xa->logUserP, XMLBLASTER_LOG_INFO, XMLBLASTER_LOG_INFO, __FILE__,
             "Asynchronous %s message [%d] update arrived: average %d messages/second\n",
             persistent, message_counter, rate);
      }

      if (verbose) {
         printf("\n[client] CALLBACK update(): Asynchronous message [%d] update arrived:%s\n",
                message_counter, xml);
      }
      else {
         if (message_counter==1) {
            const char *persistent = (strstr(xml, "<persistent>true</persistent>")!=NULL||strstr(xml, "<persistent/>")!=NULL) ? "persistent" : "transient";
            xa->log(xa->logUserP, XMLBLASTER_LOG_INFO, XMLBLASTER_LOG_INFO, __FILE__,
             "Asynchronous %s message [%d] update arrived, we log every 100 again as verbose is set to false\n",
             persistent, message_counter);
         }
      }

      message_counter++;

      /*printf("arrived message :%d\n",message_counter);*/
      xmlBlasterFree(xml);
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
 * Access the read socket progress. 
 * You need to register this function pointer if you want to see the progress of huge messages
 * on slow connections.
 */
static void callbackProgressListener(void *userP, const size_t currBytesRead, const size_t nbytes) {
   XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed*)userP;
   xa->log(xa->logUserP, xa->logLevel, XMLBLASTER_LOG_WARN, __FILE__,
           "Update data progress currBytesRead=%ld nbytes=%ld", (long)currBytesRead, (long)nbytes);
   /*printf("[client] Update data progress currBytesRead=%ld nbytes=%ld\n", (long)currBytesRead, (long)nbytes);*/
}

#if defined(WINCE)
int _tmain(int argc, _TCHAR** argv_wcs) { /* wchar_t==_TCHAR */
   char **argv = convertWcsArgv(argv_wcs, argc);
#else
/**
 * Invoke examples:
 *
 * Subscriber -logLevel TRACE
 *
 * Subscriber -session.name Subscriber/1 -dispatch/callback/retries -1 -subscribe/qos/persistent true -interactiveSubscribe true 
 */
int main(int argc, const char* const* argv) {
#endif
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

   verbose = xa->props->getBool(xa->props, "verbose", verbose);
   updateSleep = xa->props->getLong(xa->props, "updateSleep", 0L);
   reportUpdateProgress = xa->props->getBool(xa->props, "reportUpdateProgress", false); /* Report update progress */
   updateExceptionErrorCode = xa->props->getString(xa->props, "updateException.errorCode", 0); /* "user.clientCode" */
   updateExceptionMessage = xa->props->getString(xa->props, "updateException.message", "");  /* "I don't want these messages" */

   {  /* connect */
      char *response = (char *)0;
      char connectQos[4096];
      char callbackQos[1024];
      const char * const sessionName = xa->props->getString(xa->props, "session.name", "Subscriber");
      const char * const password = xa->props->getString(xa->props, "passwd", "subscriber");
      long sessionTimeout = xa->props->getLong(xa->props, "session.timeout", 86400000L);
      int maxSessions = xa->props->getInt(xa->props, "session.maxSessions", 10);
      const bool persistent = xa->props->getBool(xa->props, "dispatch/connection/persistent", false);
      const long pingInterval = xa->props->getLong(xa->props, "dispatch/callback/pingInterval", 10000L);
      const long delay = xa->props->getLong(xa->props, "dispatch/callback/delay", 60000L);
      const long retries = xa->props->getLong(xa->props, "dispatch/callback/retries", 0L); /* Set to -1 to keep the session on server side during a missing client */
      callbackSessionId = xa->props->getString(xa->props, "dispatch/callback/sessionId", callbackSessionId);
      sprintf(callbackQos,
               "<queue relating='callback' maxEntries='10000000' maxEntriesCache='10000000'>"
               "  <callback type='SOCKET' sessionId='%.256s' pingInterval='%ld' retries='%ld' delay='%ld' oneway='false'>"
               "    socket://%.120s:%d"
               "  </callback>"
               "</queue>",
               callbackSessionId, pingInterval, retries, delay, xa->callbackP->hostCB, xa->callbackP->portCB);
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
               "</qos>", sessionName, password, sessionName, sessionTimeout, maxSessions, persistent?"<persistent/>":"", callbackQos);

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

   if (reportUpdateProgress && xa->callbackP != 0) {
      xa->callbackP->readFromSocket.numReadFuncP = callbackProgressListener;
      xa->callbackP->readFromSocket.numReadUserP = xa;
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
      bool persistent = xa->props->getBool(xa->props, "subscribe/qos/persistent", false);
      bool notifyOnErase = xa->props->getBool(xa->props, "notifyOnErase", true);
      bool local = xa->props->getBool(xa->props, "local", true);
      bool initialUpdate = xa->props->getBool(xa->props, "initialUpdate", true);
      bool updateOneway = xa->props->getBool(xa->props, "updateOneway", false);
      int historyNumUpdates = xa->props->getInt(xa->props, "historyNumUpdates", 1);
      bool historyNewestFirst = xa->props->getBool(xa->props, "historyNewestFirst", true);
      bool wantContent = xa->props->getBool(xa->props, "wantContent", true);
      const char *filterType = xa->props->getString(xa->props, "filter.type", "GnuRegexFilter");
      const char *filterVersion = xa->props->getString(xa->props, "filter.version", "1.0");
      const char *filterQuery = xa->props->getString(xa->props, "filter.query", 0);  /* "^H.*$" */
      bool interactiveSubscribe = xa->props->getBool(xa->props, "interactiveSubscribe", false);

      if (domain) {
         sprintf(key, "<key domain='%.512s'/>", domain);
         subscribeToken = domain;
         queryType = "DOMAIN";
      }
      else if (xpath) {
         sprintf(key, "<key queryType='XPATH'>%.512s</key>", xpath);
         subscribeToken = xpath;
         queryType = "XPATH";
      }
      else {
         sprintf(key, "<key oid='%.512s'/>", oid);
         subscribeToken = oid;
         queryType = "EXACT";
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
                   " <updateOneway>%.20s</updateOneway>"
                   " <notify>%.20s</notify>"
                   "%.2048s"
                   " <history numEntries='%d' newestFirst='%.20s'/>"
                   "</qos>",
                   wantContent?"true":"false",
                   multiSubscribe?"true":"false",
                   persistent?"true":"false",
                   local?"true":"false",
                   initialUpdate?"true":"false",
                   updateOneway?"true":"false",
                   notifyOnErase?"true":"false",
                   filterQos,
                   historyNumUpdates,
                   historyNewestFirst?"true":"false"
                   );

      printf("[client] Subscribe key: %s\n", key);
      printf("[client] Subscribe qos: %s\n", qos);

      if (interactiveSubscribe) {
         char msg[20];
         printf("(Hit a key to subscribe) >> ");
         fgets(msg, 19, stdin);
      }

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
      char key[512];
      const char *qos = "<qos/>";
      /* TODO: use subscriptionId */
      if (!strcmp(queryType, "EXACT"))
         sprintf(key, "<key oid='%.200s'/>", subscribeToken);
      else if (!strcmp(queryType, "DOMAIN"))
         SNPRINTF(key, 511, "<key domain='%.400s'/>", subscribeToken);
      else
         SNPRINTF(key, 511, "<key queryType='XPATH'>%.400s</key>", subscribeToken);
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


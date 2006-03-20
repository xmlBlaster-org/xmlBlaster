/*----------------------------------------------------------------------------
Name:      xmlBlaster/demo/c/socket/LogRedirect.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Tests redirect of logging
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:
  Linux with libxmlBlasterC.so:
          gcc  -D_REENTRANT -Wall -o LogRedirect LogRedirect.c -I../../../src/c
                -L../../../lib -lxmlBlasterClientC -Wl,-rpath=../../../lib -lpthread
See:      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <ctype.h>
#include <XmlBlasterAccessUnparsed.h>

static void myLogger(void *logUserP, 
                     XMLBLASTER_LOG_LEVEL currLevel,
                     XMLBLASTER_LOG_LEVEL level,
                     const char *location, const char *fmt, ...);
static bool myUpdate(MsgUnitArr *msgUnitArr, void *userData,
                     XmlBlasterException *xmlBlasterException);

/**
 * Invoke: LogRedirect -logLevel TRACE
 */
int main(int argc, char** argv)
{
   int iarg;
   char *response = (char *)0;
   char connectQos[2048];
   XmlBlasterException xmlBlasterException;
   XmlBlasterAccessUnparsed *xa = 0;

   printf("[client] Try option '-help' if you need usage informations\n");

   for (iarg=0; iarg < argc; iarg++) {
      if (strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "--help") == 0) {
         char usage[XMLBLASTER_MAX_USAGE_LEN];
         const char *pp =
         "\n  -logLevel            ERROR | WARN | INFO | TRACE [WARN]"
         "\n\nExample:"
         "\n  LogRedirect -logLevel TRACE"
               " -dispatch/connection/plugin/socket/hostname server.mars.universe";
         printf("Usage:\nXmlBlaster C SOCKET client %s\n%s%s\n",
                  getXmlBlasterVersion(), xmlBlasterAccessUnparsedUsage(usage), pp);
         exit(EXIT_FAILURE);
      }
   }

   xa = getXmlBlasterAccessUnparsed(argc, (const char* const*)argv);

   /* Register our own logging function */
   xa->log = myLogger;
   /* Optionally pass a pointer which we can use in myLogger again */
   xa->logUserP = xa;

   /* connect */
   sprintf(connectQos,
            "<qos>"
            " <securityService type='htpasswd' version='1.0'>"
            "  <![CDATA["
            "   <user>fritz</user>"
            "   <passwd>secret</passwd>"
            "  ]]>"
            " </securityService>"
            "</qos>");

   response = xa->connect(xa, connectQos, myUpdate, &xmlBlasterException);
   if (*xmlBlasterException.errorCode != 0) {
      printf("[client] Caught exception during connect errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
      freeXmlBlasterAccessUnparsed(xa);
      exit(EXIT_FAILURE);
   }
   xmlBlasterFree(response);
   printf("[client] Connected to xmlBlaster, do some tests ...\n");

   /* ping */
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

   /* disconnect */
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

/**
 * Customized logging output is handled by this method. 
 * <p>
 * We register this function with 
 * </p>
 * <pre>
 * xa->log = myLogger;
 * </pre>
 * @param logUserP 0 or pointing to one of your supplied data struct
 * @param currLevel The actual log level of the client
 * @param level The level of this log entry
 * @param location A string describing the code place
 * @param fmt The formatting string
 * @param ... Other variables to log, corresponds to 'fmt'
 * @see xmlBlaster/src/c/msgUtil.c: xmlBlasterDefaultLogging() is the default
 *      implementation
 */
static void myLogger(void *logUserP, 
                     XMLBLASTER_LOG_LEVEL currLevel,
                     XMLBLASTER_LOG_LEVEL level,
                     const char *location, const char *fmt, ...)
{
   /* Guess we need no more than 200 bytes. */
   int n, size = 200;
   char *p = 0;
   va_list ap;
   /*XmlBlasterAccessUnparsed *xa = (XmlBlasterAccessUnparsed *)logUserP;*/
   if (logUserP) {}  /* To avoid "logUserP was never referenced" compiler warning */

   if (level > currLevel) { /* XMLBLASTER_LOG_ERROR, XMLBLASTER_LOG_WARN, XMLBLASTER_LOG_INFO, XMLBLASTER_LOG_TRACE */
      return;
   }
   if ((p = (char *)malloc (size)) == NULL)
      return;

   for (;;) {
      /* Try to print in the allocated space. */
      va_start(ap, fmt);
      n = VSNPRINTF(p, size, fmt, ap); /* UNIX: vsnprintf(), WINDOWS: _vsnprintf() */
      va_end(ap);
      /* If that worked, print the string to console. */
      if (n > -1 && n < size) {
         if (level == XMLBLASTER_LOG_TRACE)
            printf("%s %s\n", getLogLevelStr(level), p);
         else
            printf("{%s-%s-%s} [%s] %s\n",
                   __DATE__, __TIME__, getLogLevelStr(level), location, p);
         free(p);
         return;
      }
      /* Else try again with more space. */
      if (n > -1)    /* glibc 2.1 */
         size = n+1; /* precisely what is needed */
      else           /* glibc 2.0 */
         size *= 2;  /* twice the old size */
      if ((p = (char *)realloc (p, size)) == NULL) {
         return;
      }
   }
}

/**
 * Here we receive the callback messages from xmlBlaster
 */
static bool myUpdate(MsgUnitArr *msgUnitArr, void *userData,
                     XmlBlasterException *xmlBlasterException)
{
   size_t i;
   if (xmlBlasterException != 0) ;  /* Supress compiler warnings */
   if (userData != 0) ;
   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",
             xml);
      xmlBlasterFree(xml);
      msgUnitArr->msgUnitArr[i].responseQos =
             strcpyAlloc("<qos><state id='OK'/></qos>");
      /* Return QoS: Everything is OK */
   }
   return true;
}


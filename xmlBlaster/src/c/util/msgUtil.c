/*----------------------------------------------------------------------------
Name:      xmlBlaster/src/c/util/msgUtil.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains helper functions for string and message manipulation
Compile:   gcc -Wall -g -o msgUtil msgUtil.c -DMSG_UTIL_MAIN -I..
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <ctype.h>
#include <assert.h>
#include <time.h>
#include "msgUtil.h"

#ifdef _ENABLE_STACK_TRACE_
# include <execinfo.h>
#endif

#ifdef _WINDOWS
#  include <Winsock2.h>       /* gethostbyname() */
#else
#  include <unistd.h>         /* sleep(), only used in main */
#  include <netdb.h>          /* gethostbyname_re() */
#  include <errno.h>          /* gethostbyname_re() */
#  include <sys/time.h>       /* sleep with select() */
#  include <sys/types.h>      /* sleep with select() */
#endif

#if defined(__GNUC__) || defined(__ICC)
   // To support query state with 'ident libxmlBlasterClientC.so' or 'what libxmlBlasterClientC.so'
   // or 'strings libxmlBlasterClientC.so  | grep msgUtil.c'
   static const char *rcsid_GlobalCpp  __attribute__ ((unused)) =  "@(#) $Id: msgUtil.c,v 1.10 2003/07/17 17:47:17 ruff Exp $ xmlBlaster @version@";
#elif defined(__SUNPRO_CC)
   static const char *rcsid_GlobalCpp  =  "@(#) $Id: msgUtil.c,v 1.10 2003/07/17 17:47:17 ruff Exp $ xmlBlaster @version@";
#endif

static const char *LOG_TEXT[] = { "NOLOG", "ERROR", "WARN", "INFO", "CALL", "TIME", "TRACE", "DUMP", "PLAIN" };
static const int numLOG_TEXT = 9; /* sizeof(LOG_TEXT) returns 36 which is not what we want */

#define ESC "\033[0m"; /* Reset color to original values */
#define BOLD "\033[1m"

#define RED_BLACK "\033[31;40m"
#define GREEN_BLACK "\033[32;40m"
#define YELLOW_BLACK "\033[33;40m"
#define BLUE_BLACK "\033[34;40m"
#define PINK_BLACK "\033[35;40m"
#define LTGREEN_BLACK "\033[36;40m"
#define WHITE_BLACK "\033[37;40m"

#define WHITE_RED "\033[37;41m"
#define BLACK_RED "\033[30;41m"
#define BLACK_GREEN "\033[40;42m"
#define BLACK_PINK "\033[40;45m"
#define BLACK_LTGREEN "\033[40;46m"

/* To support colored logging output in xterminals */
static const char *LOG_TEXT_ESCAPE[] = {
       "NOLOG",
        "\033[31;40mERROR\033[0m",
        "\033[33;40mWARN\033[0m",
        "\033[32;40mINFO\033[0m",
        "\033[34;40mCALL\033[0m",
        "\033[36;40mTIME\033[0m",
        "\033[37;40mTRACE\033[0m",
        "\033[35;40mDUMP\033[0m",
        "\033[37;40mPLAIN\033[0m"
        };

/**
 * @return e.g. "0.848"
 */
Dll_Export const char *getXmlBlasterVersion(void)
{
   /* Is replaced by xmlBlaster/build.xml ant task */
   return "@version@";
}

/**
 * Add for GCC compilation: "-rdynamic -export-dynamic -D_ENABLE_STACK_TRACE_"
 * @return The stack trace, you need to free() it.
 *         Returns NULL if out of memory.
 */
Dll_Export char *getStackTrace(int maxNumOfLines)
{
#ifdef _ENABLE_STACK_TRACE_
   int i;
   void** arr = (void **)calloc(maxNumOfLines, sizeof(void *));
   if (arr == 0) return (char *)0;
   {
      /*
      > +Currently, the function name and offset can only be obtained on systems
      > +that use the ELF binary format for programs and libraries.
      Perhaps a reference to the addr2line program can be added here.  It
      can be used to retrieve symbols even if the -rdynamic flag wasn't
      passed to the linker, and it should work on non-ELF targets as well.
      o  Under linux, gcc interprets it by setting the 
         "-export-dynamic" option for ld, which has that effect, according
         to the linux ld manpage.

      o Under IRIX it's ignored, and the program's happy as a clam.

      o Under SunOS-4.1, gcc interprets it by setting the -dc -dp
         options for ld, which again forces the allocation of the symbol
         table in the code produced (see ld(1) on a Sun).
      */
      int bt = backtrace(arr, maxNumOfLines);
      char** list = (char **)backtrace_symbols(arr, bt); /* malloc the return pointer, the entries don't need to be freed */
      char *ret = strcpyAlloc("");
      for (i=0; i<bt; i++) {
         if (list[i] != NULL) {
            strcatAlloc(&ret, list[i]);
            strcatAlloc(&ret, "\n");
         }
      }
      free(list);
      free(arr);
      if (strlen(ret) < 1) {
         strcatAlloc(&ret, ""); /* Creation of stackTrace failed */
      }
      return ret;
   }
#else
   if (maxNumOfLines > 0) ;      /* to make the compiler happy */
   return strcpyAlloc(""); /* No stack trace provided in this system */
#endif
}

#ifndef XMLBLASTER_SLEEP_FALLBACK 
#  define  XMLBLASTER_SLEEP_FALLBACK 0 /* Initialize to make icc happy */
#endif
#ifndef XMLBLASTER_SLEEP_NANO
#  define XMLBLASTER_SLEEP_NANO 0
#endif 

/**
 * Sleep for given milliseconds, on none real time systems expect ~ 10 millisecs tolerance. 
 */
Dll_Export void sleepMillis(long millisecs)
{
#ifdef _WINDOWS
   Sleep(millisecs);
#elif XMLBLASTER_SLEEP_FALLBACK /* rounded to seconds */
   if (millisecs < 1000)
      millisecs = 1000;
   sleep(millisecs/1000);
#elif XMLBLASTER_SLEEP_NANO
   TODO:
   int nanosleep(const struct timespec *rqtp,  struct  timespec *rmtp);
   struct timespec
   {
            time_t  tv_sec;         /* seconds */
            long    tv_nsec;        /* nanoseconds */
   };
   /*
   usleep()  deprecated
   */
   /*
   #include <time.h>
   void Sleep(clock_t wait)
   {
          clock_t goal;
          goal = wait * (CLOCKS_PER_SEC / 1000);
          while( goal >=  clock())
                  ;
   }
   */   
#else
   fd_set dummy;
   struct timeval toWait;
   int ret;

   FD_ZERO(&dummy);
   toWait.tv_sec = millisecs / 1000;
   toWait.tv_usec = (millisecs % 1000) * 1000;

   ret = select(0, &dummy, NULL, NULL, &toWait);
   if (ret == -1) {
      printf("[msgUtil.c] ERROR: sleepMillis(%ld) returned errnor %d", millisecs, errno);
   }
#endif
}

/**
 * Frees the pointer with free(). 
 * <p> 
 * Users of this library can use xmlBlasterFree() instead of free().
 * This can be helpful on Windows and if this client library is a DLL and compiled with /MT
 * but the client code is not (or vice versa).
 * In such a case the executable uses different runtime libraries
 * with different instances of malloc/free.
 * </p>
 * On UNIX we don't need this function but it doesn't harm either.
 */
Dll_Export void xmlBlasterFree(char *p)
{
   if (p != (char *)0) {
      free(p);
   }
}

/**
 * Frees everything inside MsgUnitArr and the struct MsgUnitArr itself
 */
Dll_Export void freeMsgUnitArr(MsgUnitArr *msgUnitArr)
{
   size_t i;
   if (msgUnitArr == (MsgUnitArr *)0) return;
   for (i=0; i<msgUnitArr->len; i++) {
      freeMsgUnitData(&msgUnitArr->msgUnitArr[i]);
   }
   free(msgUnitArr->msgUnitArr);
   msgUnitArr->len = 0;
   free(msgUnitArr);
}

/**
 * Does not free the msgUnit itself
 */
Dll_Export void freeMsgUnitData(MsgUnit *msgUnit)
{
   if (msgUnit == (MsgUnit *)0) return;
   if (msgUnit->key != 0) {
      free(msgUnit->key);
      msgUnit->key = 0;
   }
   if (msgUnit->content != 0) {
      free(msgUnit->content);
      msgUnit->content = 0;
   }
   msgUnit->contentLen = 0;
   if (msgUnit->qos != 0) {
      free(msgUnit->qos);
      msgUnit->qos = 0;
   }
   if (msgUnit->responseQos != 0) {
      free(msgUnit->responseQos);
      msgUnit->responseQos = 0;
   }
   /* free(msgUnit); -> not in this case, as the containing array has not allocated us separately */
}

/**
 * Frees everything. 
 */
Dll_Export void freeMsgUnit(MsgUnit *msgUnit)
{
   if (msgUnit == (MsgUnit *)0) return;
   freeMsgUnitData(msgUnit);
   free(msgUnit);
}

/**
 * NOTE: You need to free the returned pointer with free()!
 *
 * @return A ASCII XML formatted message or NULL if out of memory
 */
Dll_Export char *messageUnitToXml(MsgUnit *msg)
{
   if (msg->key == 0 && msg->contentLen < 1) {
      return strcpyAlloc(msg->qos);
   }
   else if (msg->contentLen < 1) {
      char *xml = strcpyAlloc(msg->key);
      if (xml == 0) return 0;
      return strcatAlloc(&xml, msg->qos);
   }
   else {
      char *contentStr = strFromBlobAlloc(msg->content, msg->contentLen);
      size_t len = 100 + strlen(msg->key) + msg->contentLen + strlen(msg->qos);
      char *xml = (char *)calloc(len, sizeof(char));
      if (xml == 0) {
         free(contentStr);
         return 0;
      }
      SNPRINTF(xml, len, "%s\n<content><![CDATA[%s]]></content>\n%s",
                         msg->key, contentStr, msg->qos);
      free(contentStr);
      return xml;
   }
}

/**
 * Allocates the string with malloc for you. 
 * You need to free it with free()
 * @param blob If null it is malloc()'d for you, else the given blob is used to be filled. 
 * @return The given blob (or a new malloc()'d if blob was NULL), the data is 0 terminated.
 *         We return NULL on out of memory.
 */
Dll_Export XmlBlasterBlob *blobcpyAlloc(XmlBlasterBlob *blob, const char *data, size_t dataLen)
{
   if (blob == 0) {
      blob = (XmlBlasterBlob *)calloc(1, sizeof(XmlBlasterBlob));
      if (blob == 0) return blob;
   }
   blob->dataLen = dataLen;
   blob->data = (char *)malloc((dataLen+1)*sizeof(char));
   if (blob->data == 0) {
      free(blob);
      return (XmlBlasterBlob *)0;
   }
   *(blob->data + dataLen) = 0;
   memcpy(blob->data, data, dataLen);
   return blob;
}

/**
 * free()'s the data in the given blob, does not free the blob itself. 
 * @param blob
 * @return The given blob
 */
Dll_Export XmlBlasterBlob *freeXmlBlasterBlobContent(XmlBlasterBlob *blob)
{
   if (blob->data != 0) {
      free(blob->data);
      blob->data = 0;
      blob->dataLen = 0;
   }
   return blob;
}

/**
 * Allocates the string with malloc for you. 
 * You need to free it with free()
 * @return The allocated string or NULL if out of memory
 */
Dll_Export char *strcpyAlloc(const char *src)
{
   char *dest;
   if (src == 0) return (char *)0;
   dest = (char *)malloc((strlen(src)+1)*sizeof(char));
   if (dest == 0) return 0;
   strcpy(dest, src);
   return dest;
}

/**
 * Same as strcat but reallocs the 'dest' string
 * @return The allocated string (*dest) or NULL if out of memory
 */
Dll_Export char *strcatAlloc(char **dest, const char *src)
{
   assert(dest != 0);
   if (src == 0) return (char *)0;
   (*dest) = (char *)realloc(*dest, (strlen(src)+strlen(*dest)+1)*sizeof(char));
   if ((*dest) == 0) return 0;
   strcat((*dest), src);
   return (*dest);
}

/**
 * Same as strcpyAlloc but if the given *dest != NULL this old allocation is freed first
 * @return *dest The allocated string filled with 'src',
 *         you need to free() it when not needed anymore.
 */
Dll_Export char *strcpyRealloc(char **dest, const char *src)
{
   if (*dest != 0)
      free(*dest);
   *dest = strcpyAlloc(src);
   return *dest;
}

/**
 * Allocates the string with malloc for you. 
 * NOTE: If your given blob or len is 0 an empty string of size 1 is returned
 * @return The string, never null.
 *         You need to free it with free()
 */
Dll_Export char *strFromBlobAlloc(const char *blob, const size_t len)
{
   char *dest;
   size_t i;
   if (blob == 0 || len < 1) {
      dest = (char *)malloc(1*sizeof(char));
      if (dest == 0) return 0;
      *dest = 0;
      return dest;
   }

   dest = (char *)malloc((len+1)*sizeof(char));
   if (dest == 0) return 0;
   for (i=0; i<len; i++) {
      dest[i] = (char)blob[i];
   }
   dest[len] = '\0';
   return dest;
}

/**
 * Guarantees a '\0' terminated string
 * @param to The destination string must be big enough
 * @param from The source to be copied
 * @param maxLen of 'to' will be filled with a '\0',
 *        so effectively only maxLen-1 from 'from' are copied.
 * @return The destination string 'to'
 */
Dll_Export char *strncpy0(char * const to, const char * const from, const size_t maxLen)
{
   char *ret=strncpy(to, from, maxLen-1);
   *(to+maxLen-1) = '\0';
   return ret;
}

/**
 * strip leading and trailing spaces of the given string
 */
Dll_Export void trim(char *s)
{
   size_t first=0;
   size_t len;
   int i;
   
   if (s == (char *)0) return;

   len = strlen((char *) s);

   {  /* find beginning of text */
      while (first<len) {
         if (!isspace(s[first]))
            break;
         first++;
      }
   }

   if (first>=len) {
      *s = '\0';
      return;
   }
   else
      strcpy((char *) s, (char *) s+first);

   for (i=(int)strlen((char *) s)-1; i >= 0; i--)
      if (!isspace(s[i])) {
         s[i+1] = '\0';
         return;
      }
   if (i<0) *s = '\0';
}

/**
 * Converts the given binary data to a more readable string,
 * the '\0' are replaced by '*'
 * @param blob The binary data
 * @return readable is returned, it must be free()'d
 */
Dll_Export char *blobDump(XmlBlasterBlob *blob)
{
   return toReadableDump(blob->data, blob->dataLen);
}

/**
 * Converts the given binary data to a more readable string,
 * the '\0' are replaced by '*'
 * @param len The length of the binary data
 * @return readable is returned, it must be free()'d.
 *         If allocation fails NULL is returned
 */
Dll_Export char *toReadableDump(char *data, size_t len)
{
   char *readable;
   size_t i;
   if (data == 0) {
      return (char *)0;
   }
   readable = (char *)malloc((len+1) * sizeof(char));
   if (readable == (char *)0) return (char *)0;
   for (i=0; i<len; i++) {
      if (data[i] == '\0')
         readable[i] = '*';
      else
         readable[i] = data[i];
   }
   readable[len] = '\0';
   return readable;
}

/**
 * Should be called on any xmlBlasterException before using it
 */
Dll_Export _INLINE_FUNC void initializeXmlBlasterException(XmlBlasterException *xmlBlasterException)
{
   xmlBlasterException->remote = false;
   *xmlBlasterException->errorCode = (char)0;
   *xmlBlasterException->message = (char)0;
}


#ifndef _WINDOWS   /* Windows does not support the reentrant ..._r() functions */
#  ifdef __sun
#    define HAVE_FUNC_GETHOSTBYNAME_R_5 /* SUN */
#  else
#    define HAVE_FUNC_GETHOSTBYNAME_R_6 /* Linux */
#  endif
#endif

/**
 * Thread safe host lookup. 
 * NOTE: If the return is not NULL you need to free(*tmphstbuf)
 * @author Caolan McNamara (2000) <caolan@skynet.ie> (with some leak fixes by Marcel)
 */
Dll_Export struct hostent * gethostbyname_re (const char *host,struct hostent *hostbuf,char **tmphstbuf,size_t *hstbuflen)
{
#ifdef _WINDOWS_FUTURE
  /* See  http://www.hmug.org/man/3/getaddrinfo.html for an example */
  /* #include Ws2tcpip.h
   typedef struct addrinfo {
      int ai_flags;
      int ai_family;
      int ai_socktype;
      int ai_protocol;
      size_t ai_addrlen;
      char* ai_canonname;
      struct sockaddr* ai_addr;
      struct addrinfo* ai_next;
   } addrinfo;

   struct sockaddr_in {
        short   sin_family;
        u_short sin_port;
        struct  in_addr sin_addr;
        char    sin_zero[8];
   };
   */
#   ifdef SOME_CLIENT_EXAMPLE
      struct addrinfo hints, *res, *res0;
      int error;
      int s;
      const char *cause = NULL;
      const char* servname = "7609"; /* or "http" */
      memset(&hints, 0, sizeof(hints));
      hints.ai_family = PF_UNSPEC;
      hints.ai_socktype = SOCK_STREAM;
      error = getaddrinfo(host, servname, &hints, &res0);
      if (error) {
         errx(1, "%s", gai_strerror(error));
         /*NOTREACHED*/
      }
      s = -1;
      cause = "no addresses";
      errno = EADDRNOTAVAIL;
      for (res = res0; res; res = res->ai_next) {
         s = socket(res->ai_family, res->ai_socktype,
             res->ai_protocol);
         if (s < 0) {
            cause = "socket";
            continue;
         }
         if (connect(s, res->ai_addr, res->ai_addrlen) < 0) {
            cause = "connect";
            close(s);
            s = -1;
            continue;
         }
         break;  /* okay we got one */
      }
      if (s < 0) {
         err(1, cause);
         /*NOTREACHED*/
      }
      freeaddrinfo(res0);
#   endif /* SOME_CLIENT_EXAMPLE */
#   ifdef SOME_SERVER_EXAMPLE
     /* The following example tries to open a wildcard listening socket onto ser-
     vice ``http'', for all the address families available. */

      struct addrinfo hints, *res, *res0;
      int error;
      int s[MAXSOCK];
      int nsock;
      const char* servname = "7609"; /* or "http" */
      const char *cause = NULL;
      memset(&hints, 0, sizeof(hints));
      hints.ai_family = PF_UNSPEC;
      hints.ai_socktype = SOCK_STREAM;
      hints.ai_flags = AI_PASSIVE;
      error = getaddrinfo(NULL, servname, &hints, &res0);
      if (error) {
         errx(1, "%s", gai_strerror(error));
         /*NOTREACHED*/
      }
      nsock = 0;
      for (res = res0; res && nsock < MAXSOCK; res = res->ai_next) {
         s[nsock] = socket(res->ai_family, res->ai_socktype,
             res->ai_protocol);
         if (s[nsock] < 0) {
            cause = "socket";
            continue;
         }
         if (bind(s[nsock], res->ai_addr, res->ai_addrlen) < 0) {
            cause = "bind";
            close(s[nsock]);
            continue;
         }
         if (listen(s[nsock], SOMAXCONN) < 0) {
            cause = "listen";
            close(s[nsock]);
            continue;
         }
         nsock++;
      }
      if (nsock == 0) {
         err(1, cause);
         /*NOTREACHED*/
      }
      freeaddrinfo(res0);
#   endif /* SOME_SERVER_EXAMPLE */

#else /* !_WINDOWS */
#ifdef HAVE_FUNC_GETHOSTBYNAME_R_6
   struct hostent *hp;
   int herr,res;

   assert(tmphstbuf != 0);

   if (*hstbuflen == 0)
   {
      *hstbuflen = 1024; 
      *tmphstbuf = (char *)malloc (*hstbuflen);
      if (*tmphstbuf == 0) return 0;
   }

   while (( res = 
      gethostbyname_r(host,hostbuf,*tmphstbuf,*hstbuflen,&hp,&herr))
      && (errno == ERANGE))
   {
      /* Enlarge the buffer. */
      *hstbuflen *= 2;
      *tmphstbuf = (char *)realloc (*tmphstbuf,*hstbuflen);
      if (*tmphstbuf == 0) return 0;
   }
   if (res != 0) {
      free(*tmphstbuf);
      *tmphstbuf = 0;
      return 0;
   }
   return hp;
#else
#  ifdef HAVE_FUNC_GETHOSTBYNAME_R_5
      struct hostent *hp;
      int herr;

      if (*hstbuflen == 0)
      {
         *hstbuflen = 1024;
         *tmphstbuf = (char *)malloc (*hstbuflen);
         if (*tmphstbuf == 0) return 0;
      }

      while ((NULL == ( hp = 
         gethostbyname_r(host,hostbuf,*tmphstbuf,*hstbuflen,&herr)))
         && (errno == ERANGE))
      {
         /* Enlarge the buffer. */
         *hstbuflen *= 2;
         *tmphstbuf = (char *)realloc (*tmphstbuf,*hstbuflen);
         if (*tmphstbuf == 0) return 0;
      }
      return hp;
#  else
#     ifdef HAVE_FUNC_GETHOSTBYNAME_R_3
         if (*hstbuflen == 0)
         {
            *hstbuflen = sizeof(struct hostent_data);
            *tmphstbuf = (char *)malloc (*hstbuflen);
            if (*tmphstbuf == 0) return 0;
         }
         else if (*hstbuflen < sizeof(struct hostent_data))
         {
            *hstbuflen = sizeof(struct hostent_data);
            *tmphstbuf = (char *)realloc(*tmphstbuf, *hstbuflen);
            if (*tmphstbuf == 0) return 0;
         }
         memset((void *)(*tmphstbuf),0,*hstbuflen);

         if (0 != gethostbyname_r(host,hostbuf,(struct hostent_data *)*tmphstbuf)) {
            free(*tmphstbuf);
            *tmphstbuf = 0;
            return 0;
         }
         return hostbuf;
#     else
         hostbuf = 0;  /* Do something with unused arguments to avoid compiler warning */
         tmphstbuf = 0;
         hstbuflen = 0;
         return gethostbyname(host); /* Not thread safe */
#     endif
#  endif
#endif /* !_WINDOWS */
#endif /* _WINDOWS */
}

/**
 * Default logging output is handled by this method: 
 * All logging is appended a time, the loglevel and the location string.
 * The logging output is to console.
 * <p>
 * If you have your own logging device you need to implement this method
 * yourself and register it with 
 * </p>
 * <pre>
 * xa->log = myXmlBlasterLoggingHandler;
 * </pre>
 * @param currLevel The actual log level of the client
 * @param level The level of this log entry
 * @param location A string describing the code place
 * @param fmt The formatting string
 * @param ... Other variables to log, corresponds to 'fmt'
 */
Dll_Export void xmlBlasterDefaultLogging(XMLBLASTER_LOG_LEVEL currLevel,
                              XMLBLASTER_LOG_LEVEL level,
                              const char *location, const char *fmt, ...)
{
   /* Guess we need no more than 200 bytes. */
   int n, size = 200;
   char *p = 0;
   va_list ap;
   char *stackTrace = 0;
#  ifdef _WINDOWS
   const char * const * logText = LOG_TEXT;
#  else
   const char * const * logText = LOG_TEXT_ESCAPE;
#  endif

   if (level > currLevel) {
      return;
   }
   if ((p = (char *)malloc (size)) == NULL)
      return;

   if (level <= LOG_ERROR) {
      stackTrace = getStackTrace(10);
   }

   for (;;) {
      /* Try to print in the allocated space. */
      va_start(ap, fmt);
      n = VSNPRINTF(p, size, fmt, ap);
      va_end(ap);
      /* If that worked, print the string to console. */
      if (n > -1 && n < size) {
         time_t t1;
         char timeStr[128];
         (void) time(&t1);
#        if defined(_WINDOWS)
            strcpy(timeStr, ctime(&t1));
#        elif defined(__sun)
            ctime_r(&t1, (char *)timeStr, 126);
#        else
            ctime_r(&t1, (char *)timeStr);
#        endif
         *(timeStr + strlen(timeStr) - 1) = '\0'; /* strip \n */
         printf("[%s %s %s] %s %s\n", timeStr, logText[level], location, p,
                                    (stackTrace != 0) ? stackTrace : "");
         free(p);
         free(stackTrace);
         return;
      }
      /* Else try again with more space. */
      if (n > -1)    /* glibc 2.1 */
         size = n+1; /* precisely what is needed */
      else           /* glibc 2.0 */
         size *= 2;  /* twice the old size */
      if ((p = (char *)realloc (p, size)) == NULL) {
         free(stackTrace);
         return;
      }
   }
}

/**
 * Parses the given string and returns the enum for it.
 * If logLevelStr is NULL or empty or unknown we return the default log level.
 * @param logLevelStr The level e.g. "WARN" or "warn" or "2"
 * @return The enum, e.g. LOG_WARN
 */
Dll_Export XMLBLASTER_LOG_LEVEL parseLogLevel(const char *logLevelStr)
{
   int i;
   int len = numLOG_TEXT;
   if (logLevelStr == 0 || *logLevelStr == '\0' ) {
      return LOG_WARN;
   }
   for (i=0; i<len; i++) {
#     ifdef _WINDOWS
      if (!strcmp(LOG_TEXT[i], logLevelStr)) {
#     else
      if (!strcasecmp(LOG_TEXT[i], logLevelStr)) {
#     endif
         return (XMLBLASTER_LOG_LEVEL)i;
      }
   }
   if (sscanf(logLevelStr, "%d", &i) == 1)
      return (XMLBLASTER_LOG_LEVEL)i;
   return LOG_WARN;
}

/**
 * @return A human readable log level, e.g. "ERROR"
 */
Dll_Export const char *getLogLevelStr(XMLBLASTER_LOG_LEVEL logLevel)
{
   return LOG_TEXT[logLevel];
}

/**
 * Check if logging is necessary. 
 * @param currLevel The actual log level of the client
 * @param level The level of this log entry
 * @return true If logging is desired
 */
Dll_Export _INLINE_FUNC bool doLog(XMLBLASTER_LOG_LEVEL currLevel, XMLBLASTER_LOG_LEVEL level)
{
   return (currLevel <= level) ? true : false;
}

# ifdef MSG_UTIL_MAIN
int main()
{
   const long millisecs = 500;
   const int currLevel = 3;
   const char *location = __FILE__;
   const char *p = "OOOO";
   int i = 3;
   xmlBlasterDefaultLogging(currLevel, LOG_WARN, location, "%s i=%d\n", p, i);

   printf("Sleeping now for %ld millis\n", millisecs);
   sleepMillis(millisecs);
   printf("Waiking up after %ld millis\n", millisecs);
   return 0;
}
# endif

/*----------------------------------------------------------------------------
Name:      msgUtil.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains helper functions for string and message manipulation
Compile:   gcc -Wall -g -c msgUtil.c
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include "msgUtil.h"

#ifdef _ENABLE_STACK_TRACE_
# include <execinfo.h>
#endif

#ifdef _WINDOWS
#  include <Winsock2.h> /* gethostbyname() */
#else
#  include <netdb.h>  /* gethostbyname_re() */
#  include <errno.h>  /* gethostbyname_re() */
#endif

/**
 * @return e.g. "0.848"
 */
const char *getXmlBlasterVersion()
{
   /* Is replaced by xmlBlaster/build.xml ant task */
   return "@version@";
}

/**
 * Add for GCC compilation: "-rdynamic -export-dynamic -D_ENABLE_STACK_TRACE_"
 * @return The stack trace, you need to free() it.
 */
const char *getStackTrace(int maxNumOfLines)
{
#ifdef _ENABLE_STACK_TRACE_
   int i;
   void** arr = (void **)calloc(maxNumOfLines, sizeof(void *));
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
      strcatAlloc(&ret, "Creation of stackTrace failed");
   }
   return ret;
#else
   return strcpyAlloc("No stack trace provided in this system");
#endif
}

/**
 * Frees everything inside MsgUnitArr and the struct MsgUnitArr itself
 */
void freeMsgUnitArr(MsgUnitArr *msgUnitArr)
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
void freeMsgUnitData(MsgUnit *msgUnit)
{
   if (msgUnit == (MsgUnit *)0) return;
   free(msgUnit->key);
   free(msgUnit->content);
   msgUnit->contentLen = 0;
   free(msgUnit->qos);
   free(msgUnit->responseQos);
   /* free(msgUnit); -> not in this case, as the containing array has not allocated us separately */
}

/**
 * Frees everything. 
 */
void freeMsgUnit(MsgUnit *msgUnit)
{
   if (msgUnit == (MsgUnit *)0) return;
   freeMsgUnitData(msgUnit);
   free(msgUnit);
}

/**
 * NOTE: You need to free the returned pointer with free()!
 *
 * @return A ASCII XML formatted message
 */
char *messageUnitToXml(MsgUnit *msg)
{
   if (msg->key == 0 && msg->contentLen < 1) {
      return strcpyAlloc(msg->qos);
   }
   else if (msg->contentLen < 1) {
      char *xml = strcpyAlloc(msg->key);
      return strcatAlloc(&xml, msg->qos);
   }
   else {
      char *content = (char *)malloc(msg->contentLen+1);
      size_t len = 100 + strlen(msg->key) + msg->contentLen + strlen(msg->qos);
      char *xml = (char *)malloc(len*sizeof(char));
      sprintf(xml, "%s\n<content><![CDATA[%s]]></content>\n%s",
                         msg->key,
                         contentToString(content, msg), /* append \0 */
                         msg->qos);
      free(content);
      return xml;
   }
}

char *contentToString(char *content, MsgUnit *msg)
{
   strncpy(content, msg->content, msg->contentLen);
   *(content + msg->contentLen) = 0;
   return content;
}

/**
 * Allocates the string with malloc for you. 
 * You need to free it with free()
 * @param blob If null it is malloc()'d for you, else the given blob is used to be filled. 
 * @return The given blob (or a new malloc()'d if blob was NULL), the data is 0 terminated.
 */
XmlBlasterBlob *blobcpyAlloc(XmlBlasterBlob *blob, const char *data, size_t dataLen)
{
   if (blob == 0) {
      blob = (XmlBlasterBlob *)calloc(1, sizeof(XmlBlasterBlob));
   }
   blob->dataLen = dataLen;
   blob->data = (char *)malloc((dataLen+1)*sizeof(char));
   *(blob->data + dataLen) = 0;
   memcpy(blob->data, data, dataLen);
   return blob;
}

/**
 * free()'s the data in the given blob, does not free the blob itself. 
 * @param blob
 * @return The given blob
 */
XmlBlasterBlob *freeXmlBlasterBlobContent(XmlBlasterBlob *blob)
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
 * @return The allocated string
 */
char *strcpyAlloc(const char *src)
{
   char *dest;
   if (src == 0) return (char *)0;
   dest = (char *)malloc((strlen(src)+1)*sizeof(char));
   strcpy(dest, src);
   return dest;
}

/**
 * Same as strcat but reallocs the 'dest' string
 */
char *strcatAlloc(char **dest, const char *src)
{
   if (src == 0) return (char *)0;
   (*dest) = (char *)realloc(*dest, (strlen(src)+strlen(*dest)+1)*sizeof(char));
   strcat((*dest), src);
   return (*dest);
}

/**
 * Allocates the string with malloc for you. 
 * NOTE: If your given blob or len is 0 an empty string of size 1 is returned
 * @return The string, never null.
 *         You need to free it with free()
 */
char *strFromBlobAlloc(const char *blob, const size_t len)
{
   char *dest;
   size_t i;
   if (blob == 0 || len < 1) {
      dest = (char *)malloc(1*sizeof(char));
      *dest = 0;
      return dest;
   }

   dest = (char *)malloc((len+1)*sizeof(char));
   for (i=0; i<len; i++) {
      dest[i] = (char)blob[i];
   }
   dest[len] = 0;
   return dest;
}

/**
 * Guarantees a '\0' terminated string
 * @param maxLen of 'to' will be filled with a '\0'
 * @return The destination string 'to'
 */
char *strncpy0(char * const to, const char * const from, const size_t maxLen)
{
   char *ret=strncpy(to, from, maxLen);
   *(to+maxLen-1) = '\0';
   return ret;
}

/**
 * strip leading and trailing spaces of the given string
 */
void trim(char *s)
{
   size_t first=0;
   size_t len;
   size_t i;
   
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
      *s = 0;
      return;
   }
   else
      strcpy((char *) s, (char *) s+first);

   for (i=strlen((char *) s)-1; i >= 0; i--)
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
char *blobDump(XmlBlasterBlob *blob)
{
   return toReadableDump(blob->data, blob->dataLen);
}

/**
 * Converts the given binary data to a more readable string,
 * the '\0' are replaced by '*'
 * @param len The length of the binary data
 * @return readable is returned, it must be free()'d
 */
char *toReadableDump(char *data, size_t len)
{
   char *readable;
   size_t i;
   if (data == 0) {
      return 0;
   }
   readable = (char *)malloc((len+1) * sizeof(char));
   for (i=0; i<len; i++) {
      if (data[i] == 0)
         readable[i] = '*';
      else
         readable[i] = data[i];
   }
   readable[len] = 0;
        return readable;
}

/**
 * Should be called on any xmlBlasterException before using it
 */
void initializeXmlBlasterException(XmlBlasterException *xmlBlasterException)
{
   xmlBlasterException->remote = false;
   *xmlBlasterException->errorCode = 0;
   *xmlBlasterException->message = 0;
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
struct hostent * gethostbyname_re (const char *host,struct hostent *hostbuf,char **tmphstbuf,size_t *hstbuflen)
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
     The following example tries to open a wildcard listening socket onto ser-
     vice ``http'', for all the address families available.

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

   if (*hstbuflen == 0)
   {
      *hstbuflen = 1024; 
      *tmphstbuf = (char *)malloc (*hstbuflen);
   }

   while (( res = 
      gethostbyname_r(host,hostbuf,*tmphstbuf,*hstbuflen,&hp,&herr))
      && (errno == ERANGE))
   {
      /* Enlarge the buffer. */
      *hstbuflen *= 2;
      *tmphstbuf = (char *)realloc (*tmphstbuf,*hstbuflen);
   }
   if (res) {
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
      }

      while ((NULL == ( hp = 
         gethostbyname_r(host,hostbuf,*tmphstbuf,*hstbuflen,&herr)))
         && (errno == ERANGE))
      {
         /* Enlarge the buffer. */
         *hstbuflen *= 2;
         *tmphstbuf = (char *)realloc (*tmphstbuf,*hstbuflen);
      }
      return hp;
#  else
#     ifdef HAVE_FUNC_GETHOSTBYNAME_R_3
         if (*hstbuflen == 0)
         {
            *hstbuflen = sizeof(struct hostent_data);
            *tmphstbuf = (char *)malloc (*hstbuflen);
         }
         else if (*hstbuflen < sizeof(struct hostent_data))
         {
            *hstbuflen = sizeof(struct hostent_data);
            *tmphstbuf = (char *)realloc(*tmphstbuf, *hstbuflen);
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


/*
#include <sys/types.h>
#include <sys/times.h>

main()
{
    struct tms before, after;

    times(&before);

    ... place code to be timed here ...

    times(&after);

    printf("User time: %ld seconds\n", after.tms_utime -
        before.tms_utime);
    printf("System time: %ld seconds\n", after.tms_stime -
        before.tms_stime);

    exit(0);
}

#include <stdio.h>
#include <sys/types.h>
#include <time.h>

main()
{ int i;
  time_t t1,t2;

  (void) time(&t1);
  
   for (i=1;i<=300;++i) printf("%d %d %d\n",i, i*i, i*i*i);
   
   
   (void) time(&t2);
   
   printf("\nTime to do 300 squares and cubes= %d seconds\n", (int) t2-t1);
}
*/

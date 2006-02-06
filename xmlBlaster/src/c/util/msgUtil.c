/*----------------------------------------------------------------------------
Name:      xmlBlaster/src/c/util/msgUtil.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains helper functions for string and message manipulation
Compile:   gcc -Wall -g -o msgUtil msgUtil.c -DMSG_UTIL_MAIN -I..
Testsuite: xmlBlaster/testsuite/src/c/TestUtil.c
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "helper.h"
#include "msgUtil.h"

#ifdef _ENABLE_STACK_TRACE_
# include <execinfo.h>
#endif

#ifdef _WINDOWS
#  include <Winsock2.h>       /* gethostbyname() */
#else
#  include <netdb.h>          /* gethostbyname_re() */
#  include <errno.h>          /* gethostbyname_re() */
#endif

#if !defined(XMLBLASTER_NO_RCSID)
/*
   Add the exact version of the C client library, this is for examination
   with for example the UNIX 'strings' command only.
   If it makes problem just set -DXMLBLASTER_NO_RCSID
*/
#if defined(__GNUC__) || defined(__ICC)
   /* To support query state with 'ident libxmlBlasterClientC.so' or 'what libxmlBlasterClientC.so'
      or 'strings libxmlBlasterClientC.so  | grep msgUtil.c' */
   static const char *rcsid_GlobalCpp  __attribute__ ((unused)) =  "@(#) $Id$ xmlBlaster @version@ #@revision.number@";
#elif defined(__SUNPRO_CC)
   static const char *rcsid_GlobalCpp  =  "@(#) $Id$ xmlBlaster @version@ #@revision.number@";
#endif
#endif

/**
 * @return e.g. "0.848 #1207M"
 */
Dll_Export const char *getXmlBlasterVersion(void)
{
   /* Is replaced by xmlBlaster/build.xml ant task */
   static const char *p1 = "@version@";
   static const char *p2 = "@version@ #@revision.number@";
        if (strstr(p2, "@") == 0 && strstr(p2, "${") == 0) { /* Verify that subversion replacement worked fine */
                return p2;
        }
   return p1;
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
 * Frees everything inside QosArr and the struct QosArr itself
 * @param qosArr The struct to free, passing NULL is OK
 */
Dll_Export void freeQosArr(QosArr *qosArr)
{
   size_t i;
   if (qosArr == (QosArr *)0) return;
   for (i=0; i<qosArr->len; i++) {
      free((char *)qosArr->qosArr[i]);
   }
   free((char *)qosArr->qosArr);
   qosArr->len = 0;
   free(qosArr);
}

/**
 * Frees everything inside MsgUnitArr and the struct MsgUnitArr itself
 * @param msgUnitArr The struct to free, passing NULL is OK
 */
Dll_Export void freeMsgUnitArr(MsgUnitArr *msgUnitArr)
{
   if (msgUnitArr == (MsgUnitArr *)0) return;
   freeMsgUnitArrInternal(msgUnitArr);
   free(msgUnitArr);
}

/**
 * Frees everything inside MsgUnitArr but NOT the struct MsgUnitArr itself
 * @param msgUnitArr The struct internals to free, passing NULL is OK
 */
Dll_Export void freeMsgUnitArrInternal(MsgUnitArr *msgUnitArr)
{
   size_t i;
   if (msgUnitArr == (MsgUnitArr *)0) return;
   for (i=0; i<msgUnitArr->len; i++) {
      freeMsgUnitData(&msgUnitArr->msgUnitArr[i]);
   }
   free(msgUnitArr->msgUnitArr);
   msgUnitArr->len = 0;
}

/**
 * Does not free the msgUnit itself
 */
Dll_Export void freeMsgUnitData(MsgUnit *msgUnit)
{
   if (msgUnit == (MsgUnit *)0) return;
   if (msgUnit->key != 0) {
      free((char *)msgUnit->key);
      msgUnit->key = 0;
   }
   if (msgUnit->content != 0) {
      free((char *)msgUnit->content);
      msgUnit->content = 0;
   }
   msgUnit->contentLen = 0;
   if (msgUnit->qos != 0) {
      free((char *)msgUnit->qos);
      msgUnit->qos = 0;
   }
   if (msgUnit->responseQos != 0) {
      free((char *)msgUnit->responseQos);
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
 * NOTE: You need to free the returned pointer with xmlBlasterFree() or directly with free()!
 *
 * @param maxContentDumpLen for -1 get the complete content, else limit the
 *        content to the given number of bytes
 * @return A ASCII XML formatted message or NULL if out of memory
 */
Dll_Export char *messageUnitToXmlLimited(MsgUnit *msg, int maxContentDumpLen)
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
      size_t len = 200 + strlen(msg->key) + msg->contentLen + strlen(msg->qos);
      char *xml = (char *)calloc(len, sizeof(char));
      if (xml == 0) {
         free(contentStr);
         return 0;
      }
      if (maxContentDumpLen == 0)
         *contentStr = 0;
      else if (maxContentDumpLen > 0 && msg->contentLen > 5 && (size_t)maxContentDumpLen < (msg->contentLen-5))
         strcpy(contentStr+maxContentDumpLen, " ...");
      SNPRINTF(xml, len, "%s\n <content size='%lu'><![CDATA[%s]]></content>%s",
                         msg->key, (unsigned long)msg->contentLen, contentStr, msg->qos);
      free(contentStr);
      return xml;
   }
}

/**
 * NOTE: You need to free the returned pointer with xmlBlasterFree() or directly with free()!
 *
 * @return A ASCII XML formatted message or NULL if out of memory
 */
Dll_Export char *messageUnitToXml(MsgUnit *msg)
{
   return messageUnitToXmlLimited(msg, -1);
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
#  if defined(__sun)
#    define HAVE_FUNC_GETHOSTBYNAME_R_5 /* SUN */
#  elif defined(__alpha)
#    define HAVE_FUNC_GETHOSTBYNAME_R_3 /* OSF1 V5.1 1885 alpha */
#  elif defined(__FreeBSD__)
#    define LOCAL_GETHOSTBYNAME_R /* FreeBSD */
/* this should actually work for other platforms... so long as they support pthreads */
#  elif defined(__hpux) /* with gcc 2.8 - 3.4.3 */
#  else
#    define HAVE_FUNC_GETHOSTBYNAME_R_6 /* Linux */
#  endif
#endif

/* a local version of the 6 argument call to gethostbyname_r 
   this is copied from http://www.cygwin.com/ml/cygwin/2004-04/msg00532.html
   thanks to Enzo Michelangeli for this
*/

#if defined(LOCAL_GETHOSTBYNAME_R)

/* since this is a 6 arg format... just define that here */
#define HAVE_FUNC_GETHOSTBYNAME_R_6
/* duh? ERANGE value copied from web... */
#define ERANGE 34
int gethostbyname_r (const char *name,
                     struct hostent *ret,
                     char *buf,
                     size_t buflen,
                     struct hostent **result,
                     int *h_errnop) {

  int hsave;
  struct hostent *ph;
  static pthread_mutex_t __mutex = PTHREAD_MUTEX_INITIALIZER;
  pthread_mutex_lock(&__mutex); /* begin critical area */
  hsave = h_errno;
  ph = gethostbyname(name);
  *h_errnop = h_errno; /* copy h_errno to *h_herrnop */

  if (ph == NULL) {
    *result = NULL;
  } else {
    char **p, **q;
    char *pbuf;
    int nbytes=0;
    int naddr=0, naliases=0;
    /* determine if we have enough space in buf */

    /* count how many addresses */
    for (p = ph->h_addr_list; *p != 0; p++) {
      nbytes += ph->h_length; /* addresses */
      nbytes += sizeof(*p); /* pointers */
      naddr++;
    }
    nbytes += sizeof(*p); /* one more for the terminating NULL */

    /* count how many aliases, and total length of strings */

    for (p = ph->h_aliases; *p != 0; p++) {
      nbytes += (strlen(*p)+1); /* aliases */
      nbytes += sizeof(*p);  /* pointers */
      naliases++;
    }
    nbytes += sizeof(*p); /* one more for the terminating NULL */

    /* here nbytes is the number of bytes required in buffer */
    /* as a terminator must be there, the minimum value is ph->h_length */
    if(nbytes > buflen) {
      *result = NULL;
      pthread_mutex_unlock(&__mutex); /* end critical area */
      return ERANGE; /* not enough space in buf!! */
    }

    /* There is enough space. Now we need to do a deep copy! */
    /* Allocation in buffer:
       from [0] to [(naddr-1) * sizeof(*p)]:
         pointers to addresses
       at [naddr * sizeof(*p)]:
         NULL
       from [(naddr+1) * sizeof(*p)] to [(naddr+naliases) * sizeof(*p)] :
         pointers to aliases
       at [(naddr+naliases+1) * sizeof(*p)]:
         NULL
       then naddr addresses (fixed length), and naliases aliases (asciiz).
    */

    *ret = *ph;   /* copy whole structure (not its address!) */

    /* copy addresses */
    q = (char **)buf; /* pointer to pointers area (type: char **) */
    ret->h_addr_list = q; /* update pointer to address list */
    pbuf = buf + ((naddr+naliases+2)*sizeof(*p)); /* skip that area */
    for (p = ph->h_addr_list; *p != 0; p++) {
      memcpy(pbuf, *p, ph->h_length); /* copy address bytes */
      *q++ = pbuf; /* the pointer is the one inside buf... */
      pbuf += ph->h_length; /* advance pbuf */
    }
    *q++ = NULL; /* address list terminator */

    /* copy aliases */

    ret->h_aliases = q; /* update pointer to aliases list */
    for (p = ph->h_aliases; *p != 0; p++) {
      strcpy(pbuf, *p); /* copy alias strings */
      *q++ = pbuf; /* the pointer is the one inside buf... */
      pbuf += strlen(*p); /* advance pbuf */
      *pbuf++ = 0; /* string terminator */
    }
    *q++ = NULL; /* terminator */

    strcpy(pbuf, ph->h_name); /* copy alias strings */
    ret->h_name = pbuf;
    pbuf += strlen(ph->h_name); /* advance pbuf */
    *pbuf++ = 0; /* string terminator */

    *result = ret;  /* and let *result point to structure */

  }
  h_errno = hsave;  /* restore h_errno */

  pthread_mutex_unlock(&__mutex); /* end critical area */

  return (*result == NULL);

}

#endif /* LOCAL_GETHOSTBYNAME_R */

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
#if defined(HAVE_FUNC_GETHOSTBYNAME_R_6)
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
#elif defined(HAVE_FUNC_GETHOSTBYNAME_R_5)
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
#elif defined(HAVE_FUNC_GETHOSTBYNAME_R_3)
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
#else
         hostbuf = 0;  /* Do something with unused arguments to avoid compiler warning */
         tmphstbuf = 0;
         hstbuflen = 0;
         return gethostbyname(host); /* Not thread safe */
#endif /* !_WINDOWS */
#endif /* _WINDOWS */
}

# ifdef MSG_UTIL_MAIN
/* On Linux defaults to HAVE_FUNC_GETHOSTBYNAME_R_6:
   gcc -g -Wall -o msgUtil msgUtil.c helper.c -I.. -DMSG_UTIL_MAIN=1
*/
int main()
{
   struct hostent hostbuf, *hostP = 0;
   char *tmphstbuf=0;
   size_t hstbuflen=0;
   char serverHostName[256];
   strcpy(serverHostName, "localhost");
   hostP = gethostbyname_re(serverHostName, &hostbuf, &tmphstbuf, &hstbuflen);
   printf("Hello '%s'\n", hostP->h_name);
   return 0;
}
# endif

/*----------------------------------------------------------------------------
Name:      XmlBlasterConnectionUnparsed.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Wraps raw socket connection to xmlBlaster
           for complete synchronous xmlBlaster access,
           without callbacks and not threading necessary
			  socket connect timeout may be specified ja, bj
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h> /* isalpha() */
#if defined(WINCE)
#  if defined(XB_USE_PTHREADS)
#     include <pthreads/pthread.h>
#  else
      /*#include <pthreads/need_errno.h> */
      static int errno=0; /* single threaded workaround*/
#  endif
#else
#  include <errno.h>
#  include <sys/types.h>
#endif
#include <socket/xmlBlasterSocket.h>
#include <socket/xmlBlasterZlib.h>
#include <XmlBlasterConnectionUnparsed.h>
#include <util/Timestampc.h>
#define SOCKET_TCP false

static bool initConnection(XmlBlasterConnectionUnparsed *xb, XmlBlasterException *exception);
static bool xmlBlasterInitQueue(XmlBlasterConnectionUnparsed *xb, QueueProperties *queueProperties, XmlBlasterException *exception);
static bool getResponse(XmlBlasterConnectionUnparsed *xb, SocketDataHolder *responseSocketDataHolder, XmlBlasterException *exception, bool udp);
static char *xmlBlasterConnect(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception);
static bool xmlBlasterDisconnect(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception);
static char *xmlBlasterPublish(XmlBlasterConnectionUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception);
static QosArr *xmlBlasterPublishArr(XmlBlasterConnectionUnparsed *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
static void xmlBlasterPublishOneway(XmlBlasterConnectionUnparsed *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception);
static char *xmlBlasterSubscribe(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static QosArr *xmlBlasterUnSubscribe(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static QosArr *xmlBlasterErase(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static MsgUnitArr *xmlBlasterGet(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception);
static char *xmlBlasterPing(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception);
static bool isConnected(XmlBlasterConnectionUnparsed *xb);
static void xmlBlasterConnectionShutdown(XmlBlasterConnectionUnparsed *xb);
static ssize_t writenPlain(void *xb, const int fd, const char *ptr, const size_t nbytes);
static ssize_t writenCompressed(void *xb, const int fd, const char *ptr, const size_t nbytes);
static ssize_t readnPlain(void *xb, const int fd, char *ptr, const size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP2);
static ssize_t readnCompressed(void *userP, const int fd, char *ptr, const size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP2);
static bool checkArgs(XmlBlasterConnectionUnparsed *xb, const char *methodName, bool checkIsConnected, XmlBlasterException *exception);

/**
 * Create a new instance to handle a synchronous connection to the server.
 * This is usually the first call of a client.
 * @return NULL if bootstrapping failed. If not NULL you need to free() it when you are done
 * usually by calling freeXmlBlasterConnectionUnparsed().
 */
XmlBlasterConnectionUnparsed *getXmlBlasterConnectionUnparsed(int argc, const char* const* argv) {
   XmlBlasterConnectionUnparsed *xb = (XmlBlasterConnectionUnparsed *)calloc(1, sizeof(XmlBlasterConnectionUnparsed));
   if (xb == 0) return xb;
   xb->argc = argc;
   xb->argv = argv;
   xb->props = createProperties(xb->argc, xb->argv);
   if (xb->props == 0) {
      freeXmlBlasterConnectionUnparsed(&xb);
      return (XmlBlasterConnectionUnparsed *)0;
   }
#ifdef __IPhoneOS__
      xb->cfSocketRef = nil;
      xb->readStream = nil;
      xb->writeStream = nil;
#endif
   xb->socketToXmlBlaster = -1;
   xb->socketToXmlBlasterUdp = -1;
   xb->isInitialized = false;
   *xb->secretSessionId = 0;
   xb->initConnection = initConnection;
   xb->initQueue = xmlBlasterInitQueue;
   xb->connect = xmlBlasterConnect;
   xb->disconnect = xmlBlasterDisconnect;
   xb->publish = xmlBlasterPublish;
   xb->publishArr = xmlBlasterPublishArr;
   xb->publishOneway = xmlBlasterPublishOneway;
   xb->subscribe = xmlBlasterSubscribe;
   xb->unSubscribe = xmlBlasterUnSubscribe;
   xb->erase = xmlBlasterErase;
   xb->get = xmlBlasterGet;
   xb->ping = xmlBlasterPing;
   xb->isConnected = isConnected;
   xb->shutdown = xmlBlasterConnectionShutdown;
   xb->preSendEvent = 0;
   xb->preSendEvent_userP = 0;
   xb->postSendEvent = 0;
   xb->postSendEvent_userP = 0;
   xb->queueP = 0;
   xb->logLevel = parseLogLevel(xb->props->getString(xb->props, "logLevel", "WARN"));
   xb->log = xmlBlasterDefaultLogging;
   xb->logUserP = 0;
   xb->useUdpForOneway = false;
   xb->writeToSocket.writeToSocketFuncP = 0;
   xb->writeToSocket.userP = xb;
   xb->zlibWriteBuf = 0;
   xb->readFromSocket.readFromSocketFuncP = 0;
   xb->readFromSocket.userP = xb;
   xb->zlibReadBuf = 0;
   return xb;
}

void freeXmlBlasterConnectionUnparsed(XmlBlasterConnectionUnparsed **xb_)
{
   XmlBlasterConnectionUnparsed *xb = *xb_;
   if (xb != 0) {
      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "freeXmlBlasterConnectionUnparsed 0x%x", xb);
      freeProperties(xb->props);
      if (xb->zlibWriteBuf) {
         xmlBlaster_endZlibWriter(xb->zlibWriteBuf);
         free(xb->zlibWriteBuf);
         xb->zlibWriteBuf = 0;
      }
      if (xb->zlibReadBuf) {
         xmlBlaster_endZlibReader(xb->zlibReadBuf);
         free(xb->zlibReadBuf);
         xb->zlibReadBuf = 0;
      }
      xmlBlasterConnectionShutdown(xb);
      free(xb);
      *xb_ = 0;
   }
}

/**
 * Connects on TCP/IP level to xmlBlaster
 * @return true If the low level TCP/IP connect to xmlBlaster succeeded
 */
static bool initConnection(XmlBlasterConnectionUnparsed *xb, XmlBlasterException *exception)
{
   const char *servTcpPort = 0;

   struct sockaddr_in xmlBlasterAddr;
   struct hostent hostbuf, *hostP = 0;
   struct servent *portP = 0;

   size_t hstbuflen=0;

   char serverHostName[256];
   char errP[MAX_ERRNO_LEN];

#if defined(_WINDOWS)
   WORD wVersionRequested;
   WSADATA wsaData;
   int err;
   wVersionRequested = MAKEWORD( 2, 2 );
   err = WSAStartup( wVersionRequested, &wsaData );
   if ( err != 0 ) {
      strncpy0(exception->errorCode, "resource.unavailable", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] Couldn't find a usable WinSock DLL", __FILE__, __LINE__);
      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return false;
   }

   if ( LOBYTE( wsaData.wVersion ) != 2 ||
   HIBYTE( wsaData.wVersion ) != 2 ) {
      WSACleanup( );
      strncpy0(exception->errorCode, "resource.unavailable", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] Couldn't find a usable WinSock DLL which supports version 2.2", __FILE__, __LINE__);
      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return false;
   }
# endif
   *errP = 0;

   if (xb->isInitialized) {
      return true;
   }

   {  /* Switch on compression? */
      const char *compressType = xb->props->getString(xb->props, "plugin/socket/compress/type", "");
      compressType = xb->props->getString(xb->props, "dispatch/connection/plugin/socket/compress/type", compressType);

      if (!strcmp(compressType, "zlib:stream")) {

         xb->zlibWriteBuf = (XmlBlasterZlibWriteBuffers *)malloc(sizeof(struct XmlBlasterZlibWriteBuffers));
         xb->zlibReadBuf = (XmlBlasterZlibReadBuffers *)malloc(sizeof(struct XmlBlasterZlibReadBuffers));

         if (xmlBlaster_initZlibWriter(xb->zlibWriteBuf) != 0/*Z_OK*/) {
            if (xb->logLevel>=XMLBLASTER_LOG_ERROR) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__,
                  "Failed switching on 'plugin/socket/compress/type=%s'", compressType);
            strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
            SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                     "[%.100s:%d] Failed switching on 'plugin/socket/compress/type=%s'",
                     __FILE__, __LINE__, compressType);
            free(xb->zlibWriteBuf);
            xb->zlibWriteBuf = 0;
            free(xb->zlibReadBuf);
            xb->zlibReadBuf = 0;
            return false;
         }

         if (xmlBlaster_initZlibReader(xb->zlibReadBuf) != 0/*Z_OK*/) {
            if (xb->logLevel>=XMLBLASTER_LOG_ERROR) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__,
                  "Failed switching on 'plugin/socket/compress/type=%s'", compressType);
            strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
            SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                     "[%.100s:%d] Failed switching on 'plugin/socket/compress/type=%s'",
                     __FILE__, __LINE__, compressType);
            free(xb->zlibWriteBuf);
            xb->zlibWriteBuf = 0;
            free(xb->zlibReadBuf);
            xb->zlibReadBuf = 0;
            return false;
         }

         if (xb->logLevel>=XMLBLASTER_LOG_DUMP) {
            xb->zlibWriteBuf->debug = true;
            xb->zlibReadBuf->debug = true;
         }

         if (!xb->writeToSocket.writeToSocketFuncP) {  /* Accept setting from XmlBlasterAccessUnparsed */
            xb->writeToSocket.writeToSocketFuncP = writenCompressed;
            xb->readFromSocket.readFromSocketFuncP = readnCompressed;
         }
      }
      else {
         if (strcmp(compressType, "")) {
            xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__, "Unsupported compression type 'plugin/socket/compress/type=%s', falling back to plain mode.", compressType);
         }
         if (!xb->writeToSocket.writeToSocketFuncP) {  /* Accept setting from XmlBlasterAccessUnparsed */
            xb->writeToSocket.writeToSocketFuncP = writenPlain;
            xb->readFromSocket.readFromSocketFuncP = readnPlain;
         }
      }
   }


   servTcpPort = xb->props->getString(xb->props, "plugin/socket/port", "7607");
   servTcpPort = xb->props->getString(xb->props, "dispatch/connection/plugin/socket/port", servTcpPort);

   strncpy0(serverHostName, "localhost", 250);
   gethostname(serverHostName, 250);
   {
      const char *hn = xb->props->getString(xb->props, "plugin/socket/hostname", serverHostName);
      memmove(serverHostName, hn, strlen(hn)+1);  /* including '\0' */
      hn = xb->props->getString(xb->props, "dispatch/connection/plugin/socket/hostname", serverHostName);
      memmove(serverHostName, hn, strlen(hn)+1);
   }

   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
      "Lookup xmlBlaster on -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %s ...",
      serverHostName, servTcpPort);

   *xb->secretSessionId = 0;
   memset((char *)&xmlBlasterAddr, 0, sizeof(xmlBlasterAddr));
   xmlBlasterAddr.sin_family=AF_INET;

# ifdef _WINDOWS_NOT_YET_PORTED /* Windows gethostbyname is deprecated */
   const struct addrinfo hints;
   struct addrinfo** res;
   int getaddrinfo(serverHostName, servTcpPort, &hints, res);
   res->ai_next : ai_family, ai_socktype, and ai_protocol

   ...

   void freeaddrinfo(*res);
# endif
        if (isalpha(serverHostName[0]) || strchr(serverHostName,':') != 0) { /* look for dns name or ipv6 */
           char *tmphstbuf=0;
           memset((char *)&hostbuf, 0, sizeof(struct hostent));
           xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_INFO, __FILE__, "Server DNS lookup of hostname '%s'", serverHostName);
           hostP = gethostbyname_re(serverHostName, &hostbuf, &tmphstbuf, &hstbuflen, errP);
           if (hostP == 0) {
              if (*errP != 0) {
                 strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
                 SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                    "[%.100s:%d] Connecting to xmlBlaster failed, can't determine hostname (hostP=0), -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %.10s: %s",
                          __FILE__, __LINE__, serverHostName, servTcpPort, errP);
                 xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__, exception->message);
                 *errP = 0;
              }
              else {
                 strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
                 SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                          "[%.100s:%d] Connecting to xmlBlaster failed, can't determine hostname (hostP=0), -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %.10s, errno=%d",
                          __FILE__, __LINE__, serverHostName, servTcpPort, errno);
                 xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__, exception->message);
              }
              return false;
           }
           xmlBlasterAddr.sin_addr.s_addr = ((struct in_addr *)(hostP->h_addr))->s_addr; /* inet_addr("192.168.1.2"); */
           free(tmphstbuf);
        }
        else {
           xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_INFO, __FILE__, "Server IP4 usage (without DNS lookup) of IP '%s'", serverHostName);
           /* use ip4 addr directly to avoid dns lookup */
           xmlBlasterAddr.sin_addr.s_addr = inet_addr(serverHostName);
        }

   portP = getservbyname(servTcpPort, "tcp");
   if (portP != 0)
                xmlBlasterAddr.sin_port = (u_short)portP->s_port;
   else
      xmlBlasterAddr.sin_port = htons((u_short)atoi(servTcpPort));
#ifdef __IPhoneOS__
   xb->socketToXmlBlaster = 0;
#else
   xb->socketToXmlBlaster = (int)socket(AF_INET, SOCK_STREAM, 0);
#endif
        if (xb->socketToXmlBlaster != -1) {
      int ret=0;
      const char *localHostName = xb->props->getString(xb->props, "plugin/socket/localHostname", 0);
      int localPort = xb->props->getInt(xb->props, "plugin/socket/localPort", 0);
      localHostName = xb->props->getString(xb->props, "dispatch/connection/plugin/socket/localHostname", localHostName);
      localPort = xb->props->getInt(xb->props, "dispatch/connection/plugin/socket/localPort", localPort);

      /* Sometimes a user may whish to force the local host/port setting (e.g. for firewall tunneling
         and on multi homed hosts */
      if (localHostName != 0 || localPort > 0) {
         struct sockaddr_in localAddr;
         struct hostent localHostbuf, *localHostP = 0;
         char *tmpLocalHostbuf=0;
         size_t localHostbuflen=0;
         memset(&localAddr, 0, sizeof(localAddr));
         localAddr.sin_family = AF_INET;
         if (localHostName) {
            if (isalpha(localHostName[0]) || strchr(localHostName,':') != 0) { /* look for dns name or ipv6 */
               if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Local hostname DNS lookup of hostname '%s'", localHostName);
               *errP = 0;
               localHostP = gethostbyname_re(localHostName, &localHostbuf, &tmpLocalHostbuf, &localHostbuflen, errP);
               if (*errP != 0) {
                  strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
                  SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                           "[%.100s:%d] Lookup of local IP failed, %s",
                           __FILE__, __LINE__, errP);
                  xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__, exception->message);
                  *errP = 0;
               }
               if (localHostP != 0) {
                  localAddr.sin_addr.s_addr = ((struct in_addr *)(localHostP->h_addr))->s_addr; /* inet_addr("192.168.1.2"); */
                  free(tmpLocalHostbuf);
               }
            }
            else {
                if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Local IP4 usage (without DNS lookup) of IP '%s'", localHostName);
               /* use ip4 addr directly to avoid dns lookup */
               localAddr.sin_addr.s_addr = inet_addr(localHostName);
            }
         }
         if (localPort > 0) {
            localAddr.sin_port = htons((unsigned short)localPort);
         }
         if (bind(xb->socketToXmlBlaster, (struct sockaddr *)&localAddr, sizeof(localAddr)) < 0) {
            if (xb->logLevel>=XMLBLASTER_LOG_WARN) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__,
               "Failed binding local port -dispatch/connection/plugin/socket/localHostname %s -dispatch/connection/plugin/socket/localPort %d",
                  localHostName, localPort);
         }
         else {
            xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_INFO, __FILE__,
               "Bound local port -dispatch/connection/plugin/socket/localHostname %s -dispatch/connection/plugin/socket/localPort %d",
                  localHostName, localPort);
         }
      }

      /* int retval = fcntl(xb->socketToXmlBlaster, F_SETFL, O_NONBLOCK); */ /* Switch on none blocking mode: we then should use select() to be notified when the kernel succeeded with connect() */
#ifdef __IPhoneOS__
      globalIPhoneXb = xb;
      {
         CFStringRef hostnameRef =CFStringCreateWithCString (kCFAllocatorDefault, serverHostName, kCFStringEncodingUTF8);
         CFHostRef hostRef = CFHostCreateWithName (kCFAllocatorDefault, hostnameRef);
         CFStreamCreatePairWithSocketToCFHost (kCFAllocatorDefault,hostRef, atoi(servTcpPort), &xb->readStream, &xb->writeStream);
      }
      if(xb->readStream != nil && xb->writeStream != nil)
      {
         ret = 0;
         if(!CFWriteStreamOpen (xb->writeStream)) /* true if stream was successfully opened */
            ret = -1;
         if(!CFReadStreamOpen (xb->readStream))
            ret = -1;
         if (ret != -1) {
            if (!isIPhoneSocketConnectionEstablished(xb))
               ret = -1;
         }
      }
      else
      {
         ret = -1;
      }
#else
		{
			/*
            xmlBlasterProps:
              dispatch/connection/useSelect=1
              dispatch/connection/plugin/socket/connectTimeout=3
              Johannes Ahlert:
                  beides ist so laufzeitabhängig, find ich noch besser
                  default für useSelect ist 0
			*/
			int useSelect = 0;
			useSelect = xb->props->getInt(xb->props, "dispatch/connection/plugin/socket/useSelect", useSelect);

			if ( useSelect ) 
			{
				/* Die Variante mit select erfordert, daß der Socket nicht-blockierend
				   gemacht wird. Dies kann (und wird in diesem Beispiel) nach dem Verbinden
				   wieder rückgängig gemacht, sodaß man wie gewohnt mit dem Socket arbeiten kann.
				*/
				int            ret; 
				fd_set         fds;
				int            connectTimeout = 5;
				unsigned long  opt            = 1;
				struct timeval timeout;

				ioctlsocket( xb->socketToXmlBlaster, FIONBIO, &opt );

				/*
					Den Verbindungsaufbau anstossen
				*/
				if ( connect(xb->socketToXmlBlaster, (struct sockaddr *)&xmlBlasterAddr, sizeof(xmlBlasterAddr)) == SOCKET_ERROR )
				{
					/*
					   Das schlägt normalerweise fehl, wobei der Fehler WSAEWOULDBLOCK
					   darauf hinweist, daß der Verbindungsaufbau durchaus noch Erfolgreich
					   sein kann, und der Aufruf nur fehlgeschlagen ist, weil er andernfalls
					   blockieren würde, was ja absichtlich deaktiviert wurde.
					*/
					if ( WSAGetLastError() != WSAEWOULDBLOCK ) 
					{
						/* logging */
						return false;
					}
				}

				/* Deskriptor-Set zurücksetzen und mit dem zu verbindenden Socket belegen */
				FD_ZERO( &fds );
				FD_SET( xb->socketToXmlBlaster, &fds );

				/* Den gewählte timeout-Wert einsetzen */
				connectTimeout  = xb->props->getInt(xb->props, "dispatch/connection/plugin/socket/connectTimeout", connectTimeout);
				timeout.tv_sec  = connectTimeout;
				timeout.tv_usec = 0;

				/* Nun select aufrufen; dieses kehrt entweder nach Ablauf des Timeouts
				   zurück, oder wenn der Socket zum Schreiben bereit ist, was genau dann
				  passiert, wenn er erfolgreich verbunden wurde.
				*/
				ret = select( xb->socketToXmlBlaster + 1, 0, &fds, 0, &timeout );
				if ( ret == SOCKET_ERROR )
				{
					/* logging */
					return false;
				}

				/* Falls select zurückgekehrt ist, aber der zu verbindende Socket nicht
				   im Deskriptor-Set vorhanden ist, war das Verbinden in der gegebenen
				   Zeit nicht erfolgreich.
				*/
				if ( FD_ISSET(xb->socketToXmlBlaster, &fds) == 0 )
				{
					/* logging */
					ret = -1;
				}

				/*
				   Der Socket kann nun wieder blockierend gemacht werden
				*/
				opt = 0;
				ioctlsocket( xb->socketToXmlBlaster, FIONBIO, &opt );
			}
			else
				ret=connect(xb->socketToXmlBlaster, (struct sockaddr *)&xmlBlasterAddr, sizeof(xmlBlasterAddr));
		}
#endif
      if(ret != -1) {
         if (xb->logLevel>=XMLBLASTER_LOG_INFO) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_INFO, __FILE__, "Connected to xmlBlaster");
         xb->useUdpForOneway = xb->props->getBool(xb->props, "plugin/socket/useUdpForOneway", xb->useUdpForOneway);
         xb->useUdpForOneway = xb->props->getBool(xb->props, "dispatch/connection/plugin/socket/useUdpForOneway", xb->useUdpForOneway);

         if (xb->useUdpForOneway) {
            struct sockaddr_in localAddr;
            socklen_t size = (socklen_t)sizeof(localAddr);
            xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_INFO, __FILE__,
               "Using UDP connection for oneway calls, see -dispatch/connection/plugin/socket/useUdpForOneway true");

            xb->socketToXmlBlasterUdp = (int)socket(AF_INET, SOCK_DGRAM, 0);

            if (xb->socketToXmlBlasterUdp != -1) {
               if (getsockname(xb->socketToXmlBlaster, (struct sockaddr *)&localAddr, &size) == -1) {
                  if (xb->logLevel>=XMLBLASTER_LOG_WARN) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__,
                     "Can't determine the local socket host and port (in UDP), errno=%d", errno);
                  return false;
               }

               if (bind(xb->socketToXmlBlasterUdp, (struct sockaddr *)&localAddr, sizeof(localAddr)) < 0) {
                  if (xb->logLevel>=XMLBLASTER_LOG_WARN) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__,
                     "Failed binding local port (in UDP) -dispatch/connection/plugin/socket/localHostname %s -dispatch/connection/plugin/socket/localPort %d",
                     localHostName, localPort);
                  return false;
               }
               if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
                  "Bound local UDP port -dispatch/connection/plugin/socket/localHostname %s -dispatch/connection/plugin/socket/localPort %d",
                  localHostName, localPort);

               if ((ret=connect(xb->socketToXmlBlasterUdp, (struct sockaddr *)&xmlBlasterAddr, sizeof(xmlBlasterAddr))) == -1) {
                  char errnoStr[MAX_ERRNO_LEN];
                  xb_strerror(errnoStr, MAX_ERRNO_LEN, errno);
                  strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
                  SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                           "[%.100s:%d] Connecting to xmlBlaster -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %.10s failed (in UDP), ret=%d, %s",
                           __FILE__, __LINE__, serverHostName, servTcpPort, ret, errnoStr);
                  if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
                  return false;
               }
               if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Connected to xmlBlaster with UDP");
            } /* if (xb->socketToXmlBlasterUdp != -1) */
            else {
               strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
               SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                        "[%.100s:%d] Connecting to xmlBlaster (socket=-1) -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %.10s failed (in UDP) errno=%d",
                        __FILE__, __LINE__, serverHostName, servTcpPort, errno);
               return false;
            }
         } /* if (xb->useUdpForOneway) */

      }
      else { /* connect(...) == -1 */
         char errnoStr[MAX_ERRNO_LEN];
         xb_strerror(errnoStr, MAX_ERRNO_LEN, errno);
         strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                  "[%.100s:%d] Connecting to xmlBlaster -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %.10s failed, ret=%d, %s",
                  __FILE__, __LINE__, serverHostName, servTcpPort, ret, errnoStr);
         if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
         return false;
      }
   }
   else {
      strncpy0(exception->errorCode, "user.configuration", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] Connecting to xmlBlaster (socket=-1) -dispatch/connection/plugin/socket/hostname %s -dispatch/connection/plugin/socket/port %.10s failed errno=%d",
               __FILE__, __LINE__, serverHostName, servTcpPort, errno);
      return false;
   }
   xb->isInitialized = true;
   return true;
}


/**
 * Set the queue properties.
 * Example:
 * <pre>
   QueueProperties queueProperties;
   strncpy0(queueProperties.dbName, "xmlBlasterClient.db", QUEUE_DBNAME_MAX);
   strncpy0(queueProperties.nodeId, "clientJoe1081594557415", QUEUE_ID_MAX);
   strncpy0(queueProperties.queueName, "connection_clientJoe", QUEUE_ID_MAX);
   strncpy0(queueProperties.tablePrefix, "XB_", QUEUE_PREFIX_MAX);
   queueProperties.maxNumOfEntries = 10000000L;
   queueProperties.maxNumOfBytes = 1000000000LL;
 * <pre>
 * @param queueProperties The queue configuration,
 *        if 0 or parts of it are empty it will be initialized by environment settings
 * @return true on success
 * @throws exception if already initialized or if initialization fails
 */
static bool xmlBlasterInitQueue(XmlBlasterConnectionUnparsed *xb, QueueProperties *queueProperties, XmlBlasterException *exception)
{
#ifdef XMLBLASTER_PERSISTENT_QUEUE_TEST
   if (checkArgs(xb, "initQueue", false, exception) == false ) return false;
   if (xb->queueP) {
      char message[XMLBLASTEREXCEPTION_MESSAGE_LEN];
      SNPRINTF(message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
               "[%.100s:%d] The queue is initialized already, call to initQueue() is ignored", __FILE__, __LINE__);
      embedException(exception, "user.illegalArgument", message, exception);
      xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__, exception->message);
      return false;
   }

   {
      QueueProperties tmp;
      memset(&tmp, 0, sizeof(QueueProperties));

      if (queueProperties == 0)
         queueProperties = &tmp;

      if (*queueProperties->dbName == 0) {
         strncpy0(queueProperties->dbName, xb->props->getString(xb->props, "queue/connection/dbName", "xmlBlasterClient.db"), QUEUE_DBNAME_MAX);
      }
      if (*queueProperties->nodeId == 0) {
         strncpy0(queueProperties->nodeId, xb->props->getString(xb->props, "queue/connection/nodeId", "client"), QUEUE_ID_MAX);
      }
      if (*queueProperties->queueName == 0) {
         strncpy0(queueProperties->queueName, xb->props->getString(xb->props, "queue/connection/queueName", "connection_client"), QUEUE_ID_MAX);
      }
      if (*queueProperties->tablePrefix == 0) {
         strncpy0(queueProperties->tablePrefix, xb->props->getString(xb->props, "queue/connection/tablePrefix", "XB_"), QUEUE_PREFIX_MAX);
      }
      if (queueProperties->maxNumOfEntries == 0) {
         queueProperties->maxNumOfEntries = xb->props->getInt(xb->props, "queue/connection/maxEntries", 10000000);
      }
      if (queueProperties->maxNumOfBytes == 0) {
         queueProperties->maxNumOfBytes = xb->props->getInt64(xb->props, "queue/connection/maxBytes", 10000000LL);
      }
      if (queueProperties->logFp == 0) queueProperties->logFp = xb->log;
      if (queueProperties->logLevel == 0) queueProperties->logLevel = xb->logLevel;
      if (queueProperties->userObject == 0) queueProperties->userObject = xb->userObject;

      xb->queueP = createQueue(queueProperties, exception);
      if (*exception->errorCode != 0) {
         xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "Queue initializeation failed: [%s] %s\n", exception->errorCode, exception->message);
         return false;
      }
      xb->queueP->userObject = xb;
   }
   return true;
#else
   if (queueProperties) {} /* To suppress compiler warning that not used */
   strncpy0(exception->errorCode, "user.illegalArgument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
   SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
            "[%.100s:%d] Queue support is not compiled into the library, please recompile with '-DXMLBLASTER_PERSISTENT_QUEUE=1 and -DXMLBLASTER_PERSISTENT_QUEUE_TEST=1", __FILE__, __LINE__);
   xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__, exception->message);
   return false;
#endif /* XMLBLASTER_PERSISTENT_QUEUE_TEST */
}

static bool isConnected(XmlBlasterConnectionUnparsed *xb)
{
   return (xb->socketToXmlBlaster > -1) ? true : false;
}

const char *xmlBlasterConnectionUnparsedUsage()
{
   /* To prevent compiler warning */
   /*   "string length `596' is greater than the length `509' ISO C89 compilers are required to support" */
   /* we have a static variable */
   enum { SIZE=2048 };
   static char usage[SIZE];
   strncpy0(usage,
      "\n   -dispatch/connection/plugin/socket/hostname [localhost]"
      "\n                       Where to find xmlBlaster."
      "\n   -dispatch/connection/plugin/socket/port [7607]"
      "\n                       The port where xmlBlaster listens."
      "\n   -dispatch/connection/plugin/socket/localHostname [NULL]", SIZE/2);
   strncat0(usage,
      "\n                       Force the local IP, useful on multi homed computers."
      "\n   -dispatch/connection/plugin/socket/localPort [0]"
      "\n                       Force the local port, useful to tunnel firewalls."
      "\n   -dispatch/connection/plugin/socket/compress/type []"
#if XMLBLASTER_ZLIB==1
      "\n                       Switch on compression with 'zlib:stream'."
#else
      "\n                       No compression support. Try recompiling with with '-DXMLBLASTER_ZLIB=1'."
#endif
      "\n   -dispatch/connection/plugin/socket/useUdpForOneway [false]"
      "\n                       Use UDP for publishOneway() calls.", SIZE/2);
   return usage;
}

/**
 * Used internally only, does no disconnect, only cleanup of socket
 */
static void xmlBlasterConnectionShutdown(XmlBlasterConnectionUnparsed *xb)
{
   if (xb != 0 && xb->isConnected(xb)) {
#     if defined(_WINDOWS)
      int how = SD_BOTH;   /* SD_BOTH requires Winsock2.h */
#     elif defined(__IPhoneOS__)
#     else
      int how = SHUT_RDWR; /* enum SHUT_RDWR = 2 */
#     endif

#ifdef __IPhoneOS__
      {
         CFReadStreamRef readStream = xb->readStream;
         if (readStream != nil) {
                 xb->readStream = nil;
                 CFReadStreamClose(readStream);
                 CFRelease(readStream);
         }
      }
      {
         CFWriteStreamRef writeStream = xb->writeStream;
         if (writeStream != nil) {
            xb->writeStream = nil;
            CFWriteStreamClose(writeStream);
            CFRelease(writeStream);
            xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_INFO, __FILE__,
                            "shutdown() CFStreams were cosed=%d", writeStream);
         }
      }
#else
      if (xb->socketToXmlBlaster != -1 && xb->socketToXmlBlaster != 0) {
         if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "shutdown() socketToXmlBlaster=%d socketToXmlBlasterUdp=%d", xb->socketToXmlBlaster, xb->socketToXmlBlasterUdp);
         shutdown(xb->socketToXmlBlaster, how);
         closeSocket(xb->socketToXmlBlaster);
         xb->socketToXmlBlaster = -1;
      }
      if (xb->socketToXmlBlasterUdp != -1) {
         shutdown(xb->socketToXmlBlasterUdp, how);
         closeSocket(xb->socketToXmlBlasterUdp);
         xb->socketToXmlBlasterUdp = -1;
      }
#endif
   }


}

/**
 * Send a message over the socket to xmlBlaster.
 * @param xb The this pointer
 * @param methodName The name of the remote method to invoke e.g. "connect"
 * @param msgType The type of message: INVOKE, RESPONSE, EXCEPTION
 * @param data The message payload to send, we take a clone so you can do with it what you want
 * @param dataLen The length of data in bytes
 * @param responseSocketDataHolder The returned data, you need to free it with free(response->data) if we returned true.
 *        Supply NULL for oneway messages.
 * @param exception The exception struct, exception->errorCode is filled on exception.
 *        You need to supply it.
 * @param udp Whether to use UDP or TCP. Supply true for UDP.
 * @return true if OK and response is filled (if not oneway or exception or response itself)<br />
           false on error and exception is filled
 */
static bool sendData(XmlBlasterConnectionUnparsed *xb,
              const char * const methodName,
              enum XMLBLASTER_MSG_TYPE_ENUM msgType,
              const char *data_,
              size_t dataLen_,
              SocketDataHolder *responseSocketDataHolder,
              XmlBlasterException *exception,
              bool udp)
{
   ssize_t numSent;
   size_t rawMsgLen = 0;
   char *rawMsg = (char *)0;
   char *rawMsgStr;
   MsgRequestInfo *requestInfoP;
   MsgRequestInfo requestInfo;
   memset(&requestInfo, 0, sizeof(MsgRequestInfo));
   requestInfo.responseMutexIsValid = false; /* Remember when the client thread has created the mutex */
   if (data_ == 0) {
      data_ = "";
      dataLen_ = 0;
   }

   if (exception == 0) {
      xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "[%s:%d] Please provide valid exception to sendData()", __FILE__, __LINE__);
      return false;
   }
   initializeXmlBlasterException(exception);

   if (responseSocketDataHolder)
      memset(responseSocketDataHolder, 0, sizeof(SocketDataHolder));

   if (!xb->isConnected(xb)) {
      strncpy0(exception->errorCode, "communication.noConnection", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] No connection to xmlBlaster", __FILE__, __LINE__);
      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return false;
   }

   if (strcmp(XMLBLASTER_CONNECT, methodName) && strlen(xb->secretSessionId) < 1) {
      strncpy0(exception->errorCode, "user.notConnected", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please call connect() before invoking '%s'", __FILE__, __LINE__, methodName);
      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return false;
   }

   getTimestampStr(requestInfo.requestIdStr, MAX_REQUESTID_LEN);

   requestInfo.methodName = methodName;
   if (xb->preSendEvent != 0) {
      /* A callback function pointer is registered to be notified just before sending */
      XmlBlasterBlob blob;
      blobcpyAlloc(&blob, data_, dataLen_); /* Take a clone, the preSendEvent() function may manipulate it */
      requestInfo.blob.dataLen = blob.dataLen;
      requestInfo.blob.data = blob.data;
      requestInfo.xa = xb->preSendEvent_userP;
      requestInfoP = xb->preSendEvent(&requestInfo, exception);
      if (*exception->message != 0) {
         if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "Re-throw exception from preSendEvent errorCode=%s message=%s", exception->errorCode, exception->message);
         return false;
      }
      if (requestInfoP == 0) {
         strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] ERROR: returning requestInfo 0 without exception is not supported, please correct your preSendEvent() function.", __FILE__, __LINE__);
         if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
         return false;
      }
      if (blob.data != requestInfoP->blob.data) {
         /* The callback function has changed/manipulated the user data */
         freeBlobHolderContent(&blob);
      }
      rawMsg = encodeSocketMessage(msgType, requestInfo.requestIdStr, requestInfo.methodName, xb->secretSessionId,
                             requestInfoP->blob.data, requestInfoP->blob.dataLen, xb->logLevel >= XMLBLASTER_LOG_DUMP, &rawMsgLen);
      freeBlobHolderContent(&requestInfoP->blob);
   }
   else {
      rawMsg = encodeSocketMessage(msgType, requestInfo.requestIdStr, requestInfo.methodName, xb->secretSessionId,
                             data_, dataLen_, xb->logLevel >= XMLBLASTER_LOG_DUMP, &rawMsgLen);
   }

   /* AWARE:
    * From now on requestInfoP is used by callback thread
    * (was added by successful xb->preSendEvent() above)
    * If we leave sendData() this is destroyed (requestInfoP is on stack!)
    */

   /* send the header ... */
   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Lowlevel writing data to socket ...");
   numSent = xb->writeToSocket.writeToSocketFuncP(xb->writeToSocket.userP, udp ? xb->socketToXmlBlasterUdp : xb->socketToXmlBlaster, rawMsg, (int)rawMsgLen);
   if (numSent == -1) {
      if (xb->logLevel>=XMLBLASTER_LOG_WARN) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__,
                                   "Lost connection to xmlBlaster server");
      xmlBlasterConnectionShutdown(xb);
      strncpy0(exception->errorCode, "communication.noConnection", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Lost connection to xmlBlaster server", __FILE__, __LINE__);
      free(rawMsg);
      if (xb->postSendEvent != 0) {
         requestInfo.rollback = true;
         requestInfoP = xb->postSendEvent(&requestInfo, exception);
      }
      return false;
   }

   if (numSent != (int)rawMsgLen) {
      if (xb->logLevel>=XMLBLASTER_LOG_ERROR) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__,
         "Sent only %d bytes from %u", numSent, rawMsgLen);
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] ERROR Sent only %ld bytes from %lu", __FILE__, __LINE__, (long)numSent, (unsigned long)rawMsgLen);
      free(rawMsg);
      if (xb->postSendEvent != 0) {
         requestInfo.rollback = true;
         requestInfoP = xb->postSendEvent(&requestInfo, exception);
      }
      return false;
   }
   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Lowlevel writing data to socket done.");

   free(rawMsg);
   rawMsg = 0;

   if (xbl_isOneway(msgType, methodName))
      return true; /* Responses and exceptions are oneway */

   if (responseSocketDataHolder) { /* if not oneway read the response message */

      if (xb->postSendEvent != 0) {
         /* A callback function pointer is registered to be notified just after sending */
         requestInfo.responseType = 0;
         requestInfo.blob.dataLen = 0;
         requestInfo.blob.data = 0;

         /* !!! Here the thread blocks until a response from CallbackServer arrives !!! */
         requestInfoP = xb->postSendEvent(&requestInfo, exception);
         if (*exception->message != 0) {
            if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
               "Re-throw exception from preSendEvent errorCode=%s message=%s", exception->errorCode, exception->message);
            return false;
         }
         if (requestInfoP == 0) {
            printf("[XmlBlasterConnectionUnparsed] TODO: returning requestInfo 0 is not implemented");
         }
         /* TODO: Possible race condition */
         responseSocketDataHolder->type = requestInfoP->responseType;
         responseSocketDataHolder->version = XMLBLASTER_SOCKET_VERSION;
         strncpy0(responseSocketDataHolder->requestId, requestInfo.requestIdStr, MAX_REQUESTID_LEN);
         strncpy0(responseSocketDataHolder->methodName, methodName, MAX_METHODNAME_LEN);

         if (requestInfoP->responseType == MSG_TYPE_EXCEPTION) { /* convert XmlBlasterException thrown from remote */
            convertToXmlBlasterException(&requestInfoP->blob, exception, xb->logLevel >= XMLBLASTER_LOG_DUMP);
            freeBlobHolderContent(&requestInfoP->blob);
            return false;
         }
         else {
            responseSocketDataHolder->blob.dataLen = requestInfoP->blob.dataLen;
            responseSocketDataHolder->blob.data = requestInfoP->blob.data;     /* The responseSocketDataHolder is now responsible to free(responseSocketDataHolder->blob.data) */
         }
         if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "requestId '%s' returns dataLen=%d", requestInfo.requestIdStr, requestInfoP->blob.dataLen);
      }
      else {
         /* Wait on the response ourself */
         if (getResponse(xb, responseSocketDataHolder, exception, udp) == false) {  /* false on EOF */
            xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__, "Lost connection to xmlBlaster server");
            xmlBlasterConnectionShutdown(xb);
            strncpy0(exception->errorCode, "communication.noConnection", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
            SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Lost connection to xmlBlaster server", __FILE__, __LINE__);
            return false;
         }
         if (responseSocketDataHolder->type == MSG_TYPE_EXCEPTION) { /* convert XmlBlasterException */
            convertToXmlBlasterException(&responseSocketDataHolder->blob, exception, xb->logLevel >= XMLBLASTER_LOG_DUMP);
            freeBlobHolderContent(&responseSocketDataHolder->blob);
            if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
               "Re-throw exception from response errorCode=%s message=%s", exception->errorCode, exception->message);
            return false;
         }
      }

      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) {
         rawMsgStr = blobDump(&responseSocketDataHolder->blob);
         xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, "Received response msgLen=%u type=%c version=%c requestId=%s methodName=%s dateLen=%u data='%.100s ...'",
                  responseSocketDataHolder->msgLen, responseSocketDataHolder->type, responseSocketDataHolder->version, responseSocketDataHolder->requestId,
                  responseSocketDataHolder->methodName, responseSocketDataHolder->blob.dataLen, rawMsgStr);
         freeBlobDump(rawMsgStr);
      }
   }

   return true;
}

/**
 * Parse the returned message from xmlBlaster.
 * This method blocks until data arrives.
 * <br />
 * The responseSocketDataHolder holds all informations about the returned data from xmlBlaster,
 * on error the exception struct is filled.
 *
 * @param responseSocketDataHolder You need to free(responseSocketDataHolder->data) if return is 'true'.
 * @param exception Contains the exception thrown (on error only *exception->errorCode!=0)
 * @return true if OK or on exception, false on EOF
 */
static bool getResponse(XmlBlasterConnectionUnparsed *xb, SocketDataHolder *responseSocketDataHolder, XmlBlasterException *exception, bool udp)
{
   bool stopListenLoop = false;
   return parseSocketData(xb->socketToXmlBlaster, &xb->readFromSocket, responseSocketDataHolder, exception, &stopListenLoop, udp, xb->logLevel >= XMLBLASTER_LOG_DUMP);
}

/**
 * Connect to the server.
 * @param qos The QoS to connect
 * @param The exception struct, exception->errorCode is filled on exception
 * @return The raw ConnectReturnQos XML string returned from xmlBlaster,
 *         only NULL if an exception is thrown.
 *         You need to free() it
 * @return The ConnectReturnQos raw xml string, you need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.connect.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterConnect(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception)
{
   SocketDataHolder responseSocketDataHolder;
   char *response;
   char *qos2;
   char timestampStr[256];

   if (qos == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please provide valid arguments to xmlBlasterConnect()", __FILE__, __LINE__);
      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return (char *)0;
   }

   if (initConnection(xb, exception) == false) {
      return (char *)0;
   }

   /** Append current client UTC timestamp */
   qos2 = strcpyAlloc(qos);
   trimEnd(qos2);
   if (endsWith(qos2, "</qos>")) {
      getCurrentLocalIsoTimestampStr(timestampStr, 200);
      qos2[strlen(qos2) - 6] = 0;
      strcatAlloc(&qos2, "<clientProperty name='__UTC'>");
      strcatAlloc(&qos2, timestampStr);
      strcatAlloc(&qos2, "</clientProperty></qos>");
   }

   if (sendData(xb, XMLBLASTER_CONNECT, MSG_TYPE_INVOKE, (const char *)qos2,
                (qos2 == (const char *)0) ? 0 : strlen(qos2),
                &responseSocketDataHolder, exception, SOCKET_TCP) == false) {
      free(qos2);
      return (char *)0;
   }
   free(qos2);

   response = strFromBlobAlloc(responseSocketDataHolder.blob.data, responseSocketDataHolder.blob.dataLen);
   freeBlobHolderContent(&responseSocketDataHolder.blob);

   /* Extract secret session ID from ConnectReturnQos */
   *xb->secretSessionId = 0;
   {
      const char *pEnd = (const char *)0;
      const char *pStart = strstr(response, "sessionId='");
      if (pStart) {
         pStart += strlen("sessionId='");
         pEnd = strstr(pStart, "'");
         if (pEnd) {
            int len = (int)(pEnd - pStart + 1);
            if (len >= MAX_SECRETSESSIONID_LEN) {
               strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
               SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] ERROR Received too long secret sessionId with len=%d, please change setting MAX_SECRETSESSIONID_LEN", __FILE__, __LINE__, len);
               if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
            }
            strncpy0(xb->secretSessionId, pStart, len);
         }
      }
   }

   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
      "Got response for connect(secretSessionId=%s)", xb->secretSessionId);

   return response;
}

/**
 * Disconnect from server.
 * @param qos The QoS to disconnect
 * @param The exception struct, exception->errorCode is filled on exception
 * @return false on exception
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.disconnect.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static bool xmlBlasterDisconnect(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception)
{
   SocketDataHolder responseSocketDataHolder;

   if (checkArgs(xb, XMLBLASTER_DISCONNECT, true, exception) == false ) return 0;

   if (sendData(xb, XMLBLASTER_DISCONNECT, MSG_TYPE_INVOKE, (const char *)qos,
                (qos == (const char *)0) ? 0 : strlen(qos),
                &responseSocketDataHolder, exception, SOCKET_TCP) == false) {
      return false;
   }

   freeBlobHolderContent(&responseSocketDataHolder.blob);

   xmlBlasterConnectionShutdown(xb);
   *xb->secretSessionId = 0;
   return true;
}


#ifdef XMLBLASTER_PERSISTENT_QUEUE_TEST
/**
 * Extracts the priority from the given QoS.
 * @return NORM=5 on error
 */
static int parsePriority(const char *qos) {
   char *pPrio, *pPrioEnd;
   /*const int PRIORITY_MAXLEN = 10;*/
   #define PRIORITY_MAXLEN 10 /* To be backward compatible to C90 */
   char prioStr[PRIORITY_MAXLEN];
   int len = 1;
   int prio = 5;
   const int lenPrio=strlen("<priority>");

   if (qos == 0) return prio;

   pPrio = strstr(qos, "<priority>");
   if (pPrio == 0) return prio;

   pPrioEnd = strstr(qos, "</priority>");
   if (pPrioEnd == 0) return prio;

   len = pPrioEnd-pPrio-lenPrio;
   if (len >= PRIORITY_MAXLEN) {
      return prio;
   }
   strncpy(prioStr, pPrio+lenPrio, len);
   *(prioStr+len) = 0;
   sscanf(prioStr, "%d", &prio); /* on error prio remains 5, white spaces are stripped by sscanf */
   return prio;
}

/**
 * Puts an entry into the client side queue.
 * @param exception Can be prefilled with an original exception which will be embedded
 * @return 0 on failure, else an allocated "<qos><state id='OK' info='QUEUED'/></qos>" which the caller needs to free()
 */
static char *xmlBlasterQueuePut(XmlBlasterConnectionUnparsed *xb, int priority, BlobHolder *blob, XmlBlasterException *exception)
{
   QueueEntry queueEntry;
   XmlBlasterException queueException;

   QueueProperties *queuePropertiesP = 0; /* 0: read configuration from environment */
   /*
   QueueProperties queueProperties;
   memset(&queueProperties, 0, sizeof(QueueProperties));
   queuePropertiesP = &queueProperties;
   strncpy0(queueProperties.dbName, "xmlBlasterClient.db", QUEUE_DBNAME_MAX);
   strncpy0(queueProperties.nodeId, "clientJoe1081594557415", QUEUE_ID_MAX);
   strncpy0(queueProperties.queueName, "connection_clientJoe", QUEUE_ID_MAX);
   strncpy0(queueProperties.tablePrefix, "XB_", QUEUE_PREFIX_MAX);
   queueProperties.maxNumOfEntries = 10000000L;
   queueProperties.maxNumOfBytes = 1000000000LL;
   queueProperties.logFp = xb->log;
   queueProperties.logLevel = xb->logLevel;
   queueProperties.userObject = xb->userObject;
   queueP = createQueue(&queueProperties, &queueException);
   */

   if (xb->queueP == 0) {
      if (xb->initQueue(xb, queuePropertiesP, exception) == false)
         return 0;
   }

   queueEntry.priority = priority;
   queueEntry.isPersistent = true;
   queueEntry.uniqueId = getTimestamp();
   strncpy0(queueEntry.embeddedType, "MSG_RAW|publish", QUEUE_ENTRY_EMBEDDEDTYPE_LEN);
   queueEntry.embeddedBlob.data = blob->data;
   queueEntry.embeddedBlob.dataLen = blob->dataLen;

   xb->queueP->put(xb->queueP, &queueEntry, &queueException);
   if (*queueException.errorCode != 0) {
      embedException(exception, queueException.errorCode, queueException.message, exception);
      xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "Put to queue failed: [%s] %s\n", exception->errorCode, exception->message);
      return 0;
   }
   *exception->errorCode = 0; /* Successfully queued: no error */
   return strcpyAlloc("<qos><state id='OK' info='QUEUED'/></qos>");
}
#endif /*XMLBLASTER_PERSISTENT_QUEUE_TEST==1*/

/**
 * Publish a message to the server.
 * @return The raw XML string returned from xmlBlaster, only NULL if an exception is thrown
 *         You need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterPublish(XmlBlasterConnectionUnparsed *xb, MsgUnit *msgUnit, XmlBlasterException *exception)
{
   SocketDataHolder responseSocketDataHolder;
   char *response = 0;

   BlobHolder blob = encodeMsgUnit(msgUnit, xb->logLevel >= XMLBLASTER_LOG_DUMP);
   msgUnit->responseQos = 0; /* In case no initial memset(&msgUnit, 0, sizeof(MsgUnit)); was made */

   if (checkArgs(xb, "publish", true, exception) == false ) return 0;

   msgUnit->responseQos = 0; /* Initialize properly */

   if (sendData(xb, XMLBLASTER_PUBLISH, MSG_TYPE_INVOKE, blob.data, blob.dataLen,
                &responseSocketDataHolder, exception, SOCKET_TCP) == false) {

#     ifdef XMLBLASTER_PERSISTENT_QUEUE_TEST /* TEST CODE */
         if (strstr(exception->errorCode, "user.notConnected") != 0 ||
             strstr(exception->errorCode, "communication.noConnection") != 0) { /* On communication problem queue messages */
            int priority = parsePriority(msgUnit->qos);
            response = xmlBlasterQueuePut(xb, priority, &blob, exception);
            /* NO: msgUnit->responseQos = response; otherwise a free(msgUnit) will free the response as well */
         }
#     endif

      free(blob.data);
      return response;
   }
   free(blob.data);

   response = strFromBlobAlloc(responseSocketDataHolder.blob.data, responseSocketDataHolder.blob.dataLen);
   freeBlobHolderContent(&responseSocketDataHolder.blob);

   return response;
}

/**
 * Publish a message array in a bulk to the server.
 * @return The raw XML string array returned from xmlBlaster, only NULL if an exception is thrown
 *         You need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static QosArr *xmlBlasterPublishArr(XmlBlasterConnectionUnparsed *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception)
{
   size_t i;
   SocketDataHolder responseSocketDataHolder;
   QosArr *response = 0;

   BlobHolder blob = encodeMsgUnitArr(msgUnitArr, xb->logLevel >= XMLBLASTER_LOG_DUMP);

   if (checkArgs(xb, "publishArr", true, exception) == false ) return 0;

   for (i=0; i<msgUnitArr->len; i++)
      msgUnitArr->msgUnitArr[i].responseQos = 0; /* Initialize properly */

   if (sendData(xb, XMLBLASTER_PUBLISH, MSG_TYPE_INVOKE, blob.data, blob.dataLen,
                &responseSocketDataHolder, exception, SOCKET_TCP) == false) {
      free(blob.data);
      return 0;
   }
   free(blob.data);

   response = parseQosArr(responseSocketDataHolder.blob.dataLen, responseSocketDataHolder.blob.data);
   freeBlobHolderContent(&responseSocketDataHolder.blob);

   return response;
}

/**
 * Publish oneway a message array in a bulk to the server without receiving an ACK.
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static void xmlBlasterPublishOneway(XmlBlasterConnectionUnparsed *xb, MsgUnitArr *msgUnitArr, XmlBlasterException *exception)
{
   size_t i;
   SocketDataHolder responseSocketDataHolder;

   BlobHolder blob = encodeMsgUnitArr(msgUnitArr, xb->logLevel >= XMLBLASTER_LOG_DUMP);

   if (checkArgs(xb, "publishOneway", true, exception) == false ) return;

   for (i=0; i<msgUnitArr->len; i++) {
      msgUnitArr->msgUnitArr[i].responseQos = 0; /* Initialize properly */
   }

   /*
   if (!xb->useUdpForOneway) {
      strncpy0(exception->errorCode, "communication.noConnection", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%.100s:%d] UDP not enabled, use -dispatch/connection/plugin/socket/enableUDP true", __FILE__, __LINE__);
      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      free(blob.data);
      return;
   }
   */

   if (sendData(xb, XMLBLASTER_PUBLISH_ONEWAY, MSG_TYPE_INVOKE, blob.data, blob.dataLen,
                &responseSocketDataHolder, exception, xb->useUdpForOneway) == false) {
      free(blob.data);
      return;
   }
   free(blob.data);
   freeBlobHolderContent(&responseSocketDataHolder.blob); /* Could be ommitted for oneway */
}

/**
 * Subscribe a message.
 * @return The raw XML string returned from xmlBlaster, only NULL if an exception is thrown
 *         You need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.subscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterSubscribe(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseSocketDataHolder;
   char *response;

   if (checkArgs(xb, "subscribe", true, exception) == false ) return 0;

   if (key == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please provide valid arguments to xmlBlasterSubscribe()", __FILE__, __LINE__);
      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return (char *)0;
   }

   if (qos == (const char *)0) {
      qos = "";
   }
   qosLen = strlen(qos);
   keyLen = strlen(key);

   totalLen = qosLen + 1 + keyLen + 1;

   data = (char *)malloc(totalLen);

   memcpy(data+currpos, qos, qosLen+1); /* inclusive '\0' */
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); /* inclusive '\0' */
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_SUBSCRIBE, MSG_TYPE_INVOKE, data, totalLen,
                &responseSocketDataHolder, exception, SOCKET_TCP) == false) {
      free(data);
      return (char *)0;
   }
   free(data);

   response = strFromBlobAlloc(responseSocketDataHolder.blob.data, responseSocketDataHolder.blob.dataLen);
   freeBlobHolderContent(&responseSocketDataHolder.blob);

   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
      "Got response for subscribe(): %s", response);

   return response;
}

/**
 * UnSubscribe a message from the server.
 * @return The raw QoS XML strings returned from xmlBlaster, only NULL if an exception is thrown
 *         You need to free it with freeQosArr() after usage
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static QosArr *xmlBlasterUnSubscribe(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseSocketDataHolder;
   QosArr *response;

   if (checkArgs(xb, "unSubscribe", true, exception) == false ) return 0;

   if (key == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please provide valid arguments to xmlBlasterUnSubscribe()", __FILE__, __LINE__);
      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return (QosArr *)0;
   }

   if (qos == (const char *)0) {
      qos = "";
   }
   qosLen = strlen(qos);
   keyLen = strlen(key);

   totalLen = qosLen + 1 + keyLen + 1;

   data = (char *)malloc(totalLen);

   memcpy(data+currpos, qos, qosLen+1); /* inclusive '\0' */
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); /* inclusive '\0' */
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_UNSUBSCRIBE, MSG_TYPE_INVOKE, data, totalLen,
                &responseSocketDataHolder, exception, SOCKET_TCP) == false) {
      free(data);
      return (QosArr *)0;
   }
   free(data);

   response = parseQosArr(responseSocketDataHolder.blob.dataLen, responseSocketDataHolder.blob.data);
   freeBlobHolderContent(&responseSocketDataHolder.blob);

   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) {
      size_t ii;
      for (ii=0; ii<response->len; ii++) {
         xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "Got response for unSubscribe(): %s", response->qosArr[ii]);
      }
   }

   return response;
}

/**
 * Erase a message from the server.
 * @return A struct holding the raw QoS XML strings returned from xmlBlaster,
 *         only NULL if an exception is thrown.
 *         You need to freeQosArr() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.erase.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static QosArr *xmlBlasterErase(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseSocketDataHolder;
   QosArr *response;

   if (checkArgs(xb, "erase", true, exception) == false ) return 0;

   if (key == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please provide valid arguments to xmlBlasterErase()", __FILE__, __LINE__);
      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return (QosArr *)0;
   }

   if (qos == (const char *)0) {
      qos = "";
   }
   qosLen = strlen(qos);
   keyLen = strlen(key);

   totalLen = qosLen + 1 + keyLen + 1;

   data = (char *)malloc(totalLen);

   memcpy(data+currpos, qos, qosLen+1); /* inclusive '\0' */
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); /* inclusive '\0' */
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_ERASE, MSG_TYPE_INVOKE, data, totalLen,
                &responseSocketDataHolder, exception, SOCKET_TCP) == false) {
      free(data);
      return (QosArr *)0;
   }
   free(data);

   response = parseQosArr(responseSocketDataHolder.blob.dataLen, responseSocketDataHolder.blob.data);
   freeBlobHolderContent(&responseSocketDataHolder.blob);

   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) {
      size_t ii;
      for (ii=0; ii<response->len; ii++) {
         xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
            "Got response for erase(): %s", response->qosArr[ii]);
      }
   }

   return response;
}

/**
 * Ping the server.
 * @param qos The QoS or 0
 * @param exception *errorCode!=0 on failure
 * @return The ping return QoS raw xml string, you need to free() it
 *         or 0 on failure (in which case *exception.errorCode!='\0')
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
static char *xmlBlasterPing(XmlBlasterConnectionUnparsed *xb, const char * const qos, XmlBlasterException *exception)
{
   SocketDataHolder responseSocketDataHolder;
   char *response;

   if (checkArgs(xb, "ping", true, exception) == false ) return 0;

   if (sendData(xb, XMLBLASTER_PING, MSG_TYPE_INVOKE, (const char *)qos,
                (qos == (const char *)0) ? 0 : strlen(qos),
                &responseSocketDataHolder, exception, SOCKET_TCP) == false) {
      return (char *)0;
   }

   response = strFromBlobAlloc(responseSocketDataHolder.blob.data, responseSocketDataHolder.blob.dataLen);
   freeBlobHolderContent(&responseSocketDataHolder.blob);
   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
      "Got response for ping '%s'", response);
   return response;
}

/**
 * Get a message.
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.get.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @return NULL on error, please check exception in such a case, you need to
 *         call freeMsgUnitArr(msgUnitArr); after usage.
 */
static MsgUnitArr *xmlBlasterGet(XmlBlasterConnectionUnparsed *xb, const char * const key, const char * qos, XmlBlasterException *exception)
{
   size_t qosLen, keyLen, totalLen;
   char *data;
   size_t currpos = 0;
   SocketDataHolder responseSocketDataHolder;
   MsgUnitArr *msgUnitArr = 0;

   if (key == 0) {
      strncpy0(exception->errorCode, "user.illegalargument", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN, "[%s:%d] Please provide valid arguments to xmlBlasterGet()", __FILE__, __LINE__);
      if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__, exception->message);
      return (MsgUnitArr *)0;
   }

   if (qos == (const char *)0) qos = "";
   qosLen = strlen(qos);
   keyLen = strlen(key);

   totalLen = qosLen + 1 + keyLen + 1;

   data = (char *)malloc(totalLen);

   memcpy(data+currpos, qos, qosLen+1); /* inclusive '\0' */
   currpos += qosLen+1;

   memcpy(data+currpos, key, keyLen+1); /* inclusive '\0' */
   currpos += keyLen+1;

   if (sendData(xb, XMLBLASTER_GET, MSG_TYPE_INVOKE, data, totalLen,
                &responseSocketDataHolder, exception, SOCKET_TCP) == false) {
      free(data);
      return (MsgUnitArr *)0; /* exception is filled with details */
   }
   free(data);

   /* Now process the returned messages */

   msgUnitArr = parseMsgUnitArr(responseSocketDataHolder.blob.dataLen, responseSocketDataHolder.blob.data);
   freeBlobHolderContent(&responseSocketDataHolder.blob);

   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,
      "Returned %u messages for get()", msgUnitArr->len);

   return msgUnitArr;
}

/**
 * Write uncompressed to socket (not thread safe)
 */
static ssize_t writenPlain(void *userP, const int fd, const char *ptr, const size_t nbytes) {
   XmlBlasterConnectionUnparsed *xb = (XmlBlasterConnectionUnparsed *)userP;
   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,  "writenPlain(%u)", nbytes);
   return writen(fd, ptr, nbytes);
}

/**
 * Compress data and send to socket.
 */
static ssize_t writenCompressed(void *userP, const int fd, const char *ptr, const size_t nbytes) {
   XmlBlasterConnectionUnparsed *xb = (XmlBlasterConnectionUnparsed *)userP;
   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,  "writenCompressed(%u)", nbytes);
   return xmlBlaster_writenCompressed(xb->zlibWriteBuf, fd, ptr, nbytes);
}

/**
 * Write uncompressed to socket (not thread safe)
 */
static ssize_t readnPlain(void *userP, const int fd, char *ptr, const size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP2) {
   XmlBlasterConnectionUnparsed *xb = (XmlBlasterConnectionUnparsed *)userP;
   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,  "readnPlain(%u)", nbytes);
   return readn(fd, ptr, nbytes, fpNumRead, userP2);
}

/**
 * Compress data and send to socket.
 */
static ssize_t readnCompressed(void *userP, const int fd, char *ptr, const size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP2) {
   XmlBlasterConnectionUnparsed *xb = (XmlBlasterConnectionUnparsed *)userP;
   if (xb->logLevel>=XMLBLASTER_LOG_TRACE) xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_TRACE, __FILE__,  "readnCompressed(%u)", nbytes);
   return xmlBlaster_readnCompressed(xb->zlibReadBuf, fd, ptr, nbytes, fpNumRead, userP2);
}

/**
 * Checks the given arguments to be valid.
 * @param methodName For logging
 * @param checkIsConnected If true does check the connection state as well
 * @return false if the parameters are not usable,
 *         in this case 'exception' is filled with detail informations
 */
static bool checkArgs(XmlBlasterConnectionUnparsed *xb, const char *methodName, bool checkIsConnected, XmlBlasterException *exception)
{
   if (xb == 0) {
      char *stack = getStackTrace(10);
      printf("[%s:%d] Please provide a valid XmlBlasterAccessUnparsed pointer to %s() %s",
               __FILE__, __LINE__, methodName, stack);
      free(stack);
      return false;
   }

   if (exception == 0) {
      char *stack = getStackTrace(10);
      xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_ERROR, __FILE__, "[%s:%d] Please provide valid exception pointer to %s() %s",
              __FILE__, __LINE__, methodName, stack);
      free(stack);
      return false;
   }

   if (checkIsConnected) {
      if (!xb->isConnected(xb)) {
         char *stack = getStackTrace(10);
         strncpy0(exception->errorCode, "communication.noConnection", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
         SNPRINTF(exception->message, XMLBLASTEREXCEPTION_MESSAGE_LEN,
                  "[%.100s:%d] Not connected to xmlBlaster, %s() failed %s",
                   __FILE__, __LINE__, methodName, stack);
         free(stack);
         xb->log(xb->logUserP, xb->logLevel, XMLBLASTER_LOG_WARN, __FILE__, exception->message);
         return false;
      }
   }

   return true;
}



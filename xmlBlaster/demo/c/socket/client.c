/*----------------------------------------------------------------------------
Name:      client.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   client connects with raw socket to xmlBlaster
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Compile:   gcc -Wall -g -o client client.c callbackServer.c -lpthread
Compile-Win: cl /MT -D_WINDOWS client.c callbackServer.c ws2_32.lib
Invoke:    client -socket.hostCB develop -socket.portCB 7607
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <sys/types.h>
#ifdef _WINDOWS
#else
#  include <pthread.h>
#endif
#include "callbackServer.h"

int socketToXmlBlaster = -1;
static long requestId = 0;
#define MAX_SECRETSESSIONID_LEN 256
static unsigned char secretSessionId[MAX_SECRETSESSIONID_LEN];

bool getResponse(ResponseHolder *responseHolder, XmlBlasterException *exception);
char * xmlBlasterConnect(const char * qos, XmlBlasterException *exception);

int initConnection(int argc, char** argv)
{
   int iarg;
   char *servTcpPort = "7607";

   struct sockaddr_in xmlBlasterAddr;
   struct hostent *hostP = 0;
   struct servent *portP = 0;

   char serverHostName[256];
   strcpy(serverHostName, "localhost");
   gethostname(serverHostName, 125);

   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-socket.host") == 0)
         strcpy(serverHostName, argv[++iarg]);
      else if (strcmp(argv[iarg], "-socket.port") == 0)
         servTcpPort = argv[++iarg];
   }

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Lookup xmlBlaster on -socket.host %s -socket.port %s ...\n", serverHostName, servTcpPort);

   memset(secretSessionId, 0, sizeof(secretSessionId));
   memset((char *)&xmlBlasterAddr, 0, sizeof(xmlBlasterAddr));
   xmlBlasterAddr.sin_family=AF_INET;
   hostP = gethostbyname(serverHostName);
   portP = getservbyname(servTcpPort, "tcp");
   if (hostP != 0) {
      xmlBlasterAddr.sin_addr.s_addr = ((struct in_addr *)(hostP->h_addr))->s_addr; //inet_addr("192.168.1.2");
      if (portP != 0)
         xmlBlasterAddr.sin_port = portP->s_port;
      else
         xmlBlasterAddr.sin_port = htons(atoi(servTcpPort));
      socketToXmlBlaster = socket(AF_INET, SOCK_STREAM, 0);
      if (socketToXmlBlaster != -1) {
         int ret=0;
         if ((ret=connect(socketToXmlBlaster, (struct sockaddr *)&xmlBlasterAddr, sizeof(xmlBlasterAddr))) != -1) {
            if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Connected to xmlBlaster\n");
         }
         else {
            if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Connecting to xmlBlaster -socket.host %s -socket.port %s failed\n", serverHostName, servTcpPort); // errno
         }
      }
   }
   return 0;
}

int isConnected()
{
   return socketToXmlBlaster > -1;
}

void shutdownConnection()
{
   if (isConnected()) {
      shutdown(socketToXmlBlaster, 2);
      socketToXmlBlaster = -1;
   }
}

/**
 * @param methodName The name of the remote method to invoke e.g. "connect"
 * @param data The message payload to send
 * @param response The returned data, you need to free it with free()
 *        You need to supply it.
 * @param exception The exception struct, exception->errorCode is filled on exception.
 *        You need to supply it.
 * @return true if OK and response is filled<br />
           false on error and exception is filled
 */
bool sendData(const char * const methodName,
              const unsigned char *const sessionId, 
              const unsigned char *const data,
              long dataLen,
              ResponseHolder *responseHolder,
              XmlBlasterException *exception)
{
   int numSent;
   long rawMsgLen = 0;
   unsigned char *rawMsg = (unsigned char *)0;
   int currpos = 0;
   unsigned char tmp[256];
   long lenUnzipped = dataLen;
   char lenFormatStr[56]; // = "%10.d";
   char lenStr[MSG_LEN_FIELD_LEN+1];

   if (exception == 0) {
      if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Invalid argument exception=NULL, message not sent\n");
      return false;
   }
   *exception->errorCode = 0;

   requestId++;

   rawMsg = calloc(500 + dataLen, sizeof(unsigned char));

   *(rawMsg+MSG_FLAG_POS_TYPE) = MSG_TYPE_INVOKE;
   *(rawMsg+MSG_FLAG_POS_VERSION) = XMLBLASTER_VERSION;

   currpos = MSG_POS_REQESTID;
   //sprintf(tmp, "%ld%s%ld", requestId, methodName, lenUnzipped);
   sprintf(tmp, "%ld", requestId);
   memcpy(rawMsg+currpos, tmp, strlen(tmp)+1); // inclusive '\0'
   currpos += strlen(tmp)+1;

   memcpy(rawMsg+currpos, methodName, strlen(methodName)+1); // inclusive '\0'
   currpos += strlen(methodName)+1;

   memcpy(rawMsg+currpos, sessionId, strlen(sessionId)+1); // inclusive '\0'
   currpos += strlen(sessionId)+1;
   
   sprintf(tmp, "%ld", lenUnzipped);
   memcpy(rawMsg+currpos, tmp, strlen(tmp)+1); // inclusive '\0'
   currpos += strlen(tmp)+1;

   memcpy(rawMsg+currpos, data, dataLen);
   rawMsgLen = currpos+dataLen;

   sprintf(lenFormatStr, "%%%d.d", MSG_LEN_FIELD_LEN);
   sprintf(lenStr, lenFormatStr, rawMsgLen);
   memcpy(rawMsg, lenStr, MSG_LEN_FIELD_LEN);
   
   if (XMLBLASTER_DEBUG) {
      char rawMsgStr[rawMsgLen+1];
      toReadableDump(rawMsg, rawMsgLen, rawMsgStr);
      printf("xmlBlasterClient: Sending now %ld bytes '%s' -> '%s'\n", rawMsgLen, lenStr, rawMsgStr);
   }

   // send the header ...
   numSent = send(socketToXmlBlaster, rawMsg, rawMsgLen, 0);
   if (numSent !=  rawMsgLen) {
      if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Sent only %d bytes from %ld\n", numSent, rawMsgLen);
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: ERROR Sent only %d bytes from %ld\n", numSent, rawMsgLen);
      return false;
   }

   free(rawMsg);

   getResponse(responseHolder, exception);

   if (XMLBLASTER_DEBUG) {
      char rawMsgStr[responseHolder->dataLen+1];
      toReadableDump(responseHolder->data, responseHolder->dataLen, rawMsgStr);
      printf("Received response msgLen=%ld type=%c version=%c requestId=%ld methodName=%s dateLen=%ld data='%s'\n",
             responseHolder->msgLen, responseHolder->type, responseHolder->version, responseHolder->requestId,
             responseHolder->methodName, responseHolder->dataLen, rawMsgStr);
   }

   return true;
}

/**
 * Parse the returned message from xmlBlaster
 * @return true if OK and false if exception is filled
 */
bool getResponse(ResponseHolder *responseHolder, XmlBlasterException *exception) 
{
   char msgLenPtr[MSG_LEN_FIELD_LEN+1];
   char ptr[MSG_LEN_FIELD_LEN+1];
   unsigned char *rawMsg;
   const int MAX_TMPPTR=256;
   char tmpPtr[MAX_TMPPTR];

   // initialize
   memset(msgLenPtr, 0, MSG_LEN_FIELD_LEN+1);
   memset(ptr, 0, MSG_LEN_FIELD_LEN+1);
   memset(tmpPtr, 0, MSG_LEN_FIELD_LEN+1);
   memset(responseHolder, 0, sizeof(ResponseHolder));

   // read the first 10 bytes to determine the length
   int numRead = readn(socketToXmlBlaster, msgLenPtr, MSG_LEN_FIELD_LEN);
   if (numRead != MSG_LEN_FIELD_LEN) {
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: ERROR Received numRead=%d header bytes but expected %d", numRead, MSG_LEN_FIELD_LEN);
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      return false;
   }
   strcpy(ptr, msgLenPtr);
   trim(ptr);
   sscanf(ptr, "%ld", &responseHolder->msgLen);

   // read the complete message
   rawMsg = calloc(responseHolder->msgLen, sizeof(unsigned char));
   memcpy(rawMsg, msgLenPtr, MSG_LEN_FIELD_LEN);
   numRead = readn(socketToXmlBlaster, rawMsg+MSG_LEN_FIELD_LEN, responseHolder->msgLen-MSG_LEN_FIELD_LEN);
   if (numRead != (responseHolder->msgLen-MSG_LEN_FIELD_LEN)) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: ERROR Received numRead=%d message bytes but expected %ld", numRead, (responseHolder->msgLen-MSG_LEN_FIELD_LEN));
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      return false;
   }

   responseHolder->type = *(rawMsg+MSG_FLAG_POS_TYPE);
   if (responseHolder->type != MSG_TYPE_RESPONSE && responseHolder->type != MSG_TYPE_EXCEPTION) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: ERROR Received response message of type=%c", responseHolder->type);
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      return false;
   }

   responseHolder->version = *(rawMsg+MSG_FLAG_POS_VERSION);
   if (responseHolder->version != XMLBLASTER_VERSION) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "xmlBlasterClient: ERROR Received response message of unsupported version=%c", responseHolder->version);
      if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
      return false;
   }


   int currPos = MSG_POS_REQESTID;

   strncpy0(tmpPtr, rawMsg+currPos, MAX_TMPPTR);
   currPos += strlen(tmpPtr)+1;
   trim(tmpPtr);
   sscanf(tmpPtr, "%ld", &responseHolder->requestId);

   strncpy0(responseHolder->methodName, rawMsg+currPos, MAX_METHODNAME_LEN);
   currPos += strlen(responseHolder->methodName)+1;

   strncpy0(responseHolder->secretSessionId, rawMsg+currPos, MAX_SESSIONID_LEN);
   currPos += strlen(responseHolder->secretSessionId)+1;

   strncpy0(tmpPtr, rawMsg+currPos, MAX_TMPPTR);
   currPos += strlen(tmpPtr)+1;
   trim(tmpPtr);
   sscanf(tmpPtr, "%ld", &responseHolder->dataLenUncompressed);

   // Read the payload
   responseHolder->dataLen = responseHolder->msgLen - currPos;
   responseHolder->data = malloc(responseHolder->dataLen * sizeof(unsigned char));
   memcpy(responseHolder->data, rawMsg+currPos, responseHolder->dataLen);

   free(rawMsg);
   return true;
}

/**
* @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
* @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
*/
//void xmlBlasterPublish(char *key, char *content, int contentLen, char *qos)
char *xmlBlasterPublish(MsgUnit *msgUnit, XmlBlasterException *exception)
{
   int qosLen, keyLen, totalLen;
   unsigned char *data;
   char contentLenStr[126];

   if (msgUnit == 0 || msgUnit->key == 0 || msgUnit->content == 0) {
      if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Invalid argument=NULL in publish(), message not sent\n");
      return (char *)0;
   }
   if (msgUnit->qos == 0) msgUnit->qos = "";

   qosLen = strlen(msgUnit->qos);
   keyLen = strlen(msgUnit->key);
   sprintf(contentLenStr, "%d", msgUnit->contentLen);

   totalLen = qosLen + 1 + keyLen + 1 + strlen(contentLenStr) + 1 + msgUnit->contentLen;

   data = (unsigned char *)malloc(totalLen);

   int currpos = 0;
   memcpy(data+currpos, msgUnit->qos, qosLen+1); // inclusive '\0'
   currpos += qosLen+1;

   memcpy(data+currpos, msgUnit->key, keyLen+1); // inclusive '\0'
   currpos += keyLen+1;

   memcpy(data+currpos, contentLenStr, strlen(contentLenStr)+1); // inclusive '\0'
   currpos += strlen(contentLenStr)+1;

   memcpy(data+currpos, msgUnit->content, msgUnit->contentLen);
   //currpos += msgUnit->contentLen;

   ResponseHolder responseHolder;
   if (sendData(XMLBLASTER_PUBLISH, secretSessionId, data, totalLen,
                &responseHolder, exception) == false) {
      free(data);
      return 0;
   }
   free(data);
   char *response = blobcpy_alloc(responseHolder.data, responseHolder.dataLen);
   free(responseHolder.data);
   return response;
}

/**
 * @param qos The QoS to connect
 * @param The exception struct, exception->errorCode is filled on exception
 * @return The ConnectReturnQos raw xml string, you need to free() it
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 */
char *xmlBlasterConnect(const char * const qos, XmlBlasterException *exception)
{
   if (qos == 0 || exception == 0) {
      if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: ERROR Invalid argument=NULL in connect(), message not sent\n");
      return (char *)0;
   }

   ResponseHolder responseHolder;
   if (sendData(XMLBLASTER_CONNECT, secretSessionId, (const unsigned char * const)qos, strlen(qos),
                &responseHolder, exception) == false) {
      return 0;
   }

   char *response = blobcpy_alloc(responseHolder.data, responseHolder.dataLen);
   free(responseHolder.data);

   // Extract secret session ID from ConnectReturnQos
   *secretSessionId = 0;
   const char *pStart = strstr(response, "sessionId='");
   if (pStart) {
      pStart += strlen("sessionId='");
      const char *pEnd = strstr(pStart, "'");
      if (pEnd) {
         int len = pEnd - pStart + 1;
         if (len >= MAX_SECRETSESSIONID_LEN) {
            strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
            sprintf(exception->message, "xmlBlasterClient: ERROR Received too long secret sessionId with len=%d, please change setting MAX_SECRETSESSIONID_LEN", len);
            if (XMLBLASTER_DEBUG) { printf(exception->message); printf("\n"); }
         }
         strncpy0(secretSessionId, pStart, len);
      }
   }

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: Got response for connect(secretSessionId=%s)", secretSessionId);

   return response;
}

/**
 * Here we asynchronous receive the callback from xmlBlaster
 * msg = char *key, char *content, int contentLen, char *qos
 *
 * NOTE: After this call the memory of msg is freed immediately by callbackServer.c
 *       So you need to take a copy of all msg members if needed.
 */
void update(MsgUnit *msg)
{
   char *xml = messageUnitToXml(msg);
   if (XMLBLASTER_DEBUG) printf("client.update(): Asynchronous message update arrived:\n%s\n", xml);
   free(xml);
   /*
   char content[msg->contentLen+1];
   contentToString(content, msg);
   if (XMLBLASTER_DEBUG)
      printf("client.update(): Asynchronous message update arrived:\nkey=%s\ncontent=%s\nqos=%s\n",
             msg->xmlKey, content, msg->qos);
   */
}


int main(int argc, char** argv)
{
#ifndef _WINDOWS
   pthread_t tid;
#endif
   int ret, iarg;
   char *data = (char *)0;
   char *response = (char *)0;
   XmlBlasterException xmlBlasterException;

   callbackData cbArgs;

   cbArgs.hostCB = 0;
   cbArgs.portCB = 7611;
   cbArgs.update = update;

   for (iarg=0; iarg < argc-1; iarg++) {
      if (strcmp(argv[iarg], "-socket.hostCB") == 0)
         cbArgs.hostCB = argv[++iarg];
      else if (strcmp(argv[iarg], "-socket.portCB") == 0)
         cbArgs.portCB = atoi(argv[++iarg]);
   }

#  ifndef _WINDOWS
   ret = pthread_create(&tid, 0, (cbFp)initCallbackServer, &cbArgs);
#  endif

   initConnection(argc, argv);
   
   data = "<qos>"
          " <securityService type='htpasswd' version='1.0'>"
          "  <![CDATA["
          "   <user>michele</user>"
          "   <passwd>secret</passwd>"
          "  ]]>"
          " </securityService>"
          "</qos>";

   response = xmlBlasterConnect(data, &xmlBlasterException);
   free(response);
   if (strlen(xmlBlasterException.errorCode) > 0) {
      printf("Caught exception during connect, errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
      exit(1);
   }

   printf("Connected to xmlBlaster, publishing a message ...\n");

   MsgUnit msgUnit;
   msgUnit.key = "<key oid='HelloWorld'/>";
   msgUnit.content = "Some message payload";
   msgUnit.contentLen = strlen("Some message payload");
   msgUnit.qos = "<qos><persistent/></qos>";
   response = xmlBlasterPublish(&msgUnit, &xmlBlasterException);

   if (response) {
      printf("Publish success, returned status is '%s'\n", response);
      free(response);
   }
   else {
      printf("Caught exception in publish, errorCode=%s, message=%s", xmlBlasterException.errorCode, xmlBlasterException.message);
      exit(1);
   }

   //publish("<key oid='cpuinfo'/>", data, strlen(data), "<qos/>");

   if (XMLBLASTER_DEBUG) printf("xmlBlasterClient: going to sleep 100 sec ...\n");
#  ifndef _WINDOWS
   sleep(10000);
#  endif
   exit(0);
}

                       

/**
 * Allocates the string with malloc for you. 
 * You need to free it with free()
 * @return 1 if OK
 */
char *strcpyAlloc(const char *src)
{
   if (src == 0) return (char *)0;
   char *dest;
   dest = (char *)malloc((strlen(src)+1)*sizeof(char));
   strcpy(dest, src);
   return dest;
}

/**
 * Allocates the string with malloc for you. 
 * You need to free it with free()
 * @return 1 if OK
 */
int strcpy_alloc(char **dest, const char *src)
{
   if (src == 0) {(*dest)=(char *)0; return -1;}  // error
   (*dest) = (char *)malloc((strlen(src)+1)*sizeof(char));
   strcpy((*dest), src);
   if ((*dest) != (char *)0) return 1;       // OK
   return 0;    // nothing done
}

/**
 * Allocates the string with malloc for you. 
 * @return The string or NULL on error
 *         You need to free it with free()
 */
char *blobcpy_alloc(const unsigned char *blob, const int len)
{
   char *dest;
   int i;
   if (blob == 0 || len < 1) {
      return (char *)0;
   }

   dest = (char *)malloc((len+1)*sizeof(char));
   for (i=0; i<len; i++) {
      dest[i] = (char)blob[i];
   }
   dest[len] = 0;
   return dest;
}

/**
 * Same as strcat but reallocs the 'dest' string
 */
int strcat_alloc(char **dest, const char *src)
{
   if (src == 0) return -1;  // error
   (*dest) = (char *)realloc(*dest, (strlen(src)+strlen(*dest)+1)*sizeof(char));
   strcat((*dest), src);
   if ((*dest) != 0) return 1;       // OK
   return 0;    // error
}

/**
 * Guarantees a '\0' terminated string
 * @param maxLen will be filled with a '\0'
 * @return The destination string 'to'
 */
char *strncpy0(char * const to, const char * const from, const int maxLen)
{
   char *ret=strncpy(to, from, maxLen);
   *(to+maxLen-1) = '\0';
   return ret;
}

void trim(unsigned char *s)
{
   int first=0;
   int len;
   
   if (s == (unsigned char *)0) return;

   len = strlen((char *) s);

   {  // find beginning of text
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

   int i;
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
 * @param len The length of the binary data
 * @param readable is returned, it must be allocated one byte longer than data/len to hold the terminating '\0'
 */
void toReadableDump(unsigned char *data, int len, unsigned char *readable)
{
   int i;
   for (i=0; i<len; i++) {
      if (data[i] == 0)
         readable[i] = '*';
      else
         readable[i] = data[i];
   }
   readable[len] = 0;
}


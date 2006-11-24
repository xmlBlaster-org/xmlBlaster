/*----------------------------------------------------------------------------
Name:      xmlBlasterSocket.c
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains some socket specific helper methods
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
-----------------------------------------------------------------------------*/
#include <stdio.h>
#include <string.h>
#include <socket/xmlBlasterSocket.h>

void closeSocket(int fd) {
#ifdef _WINDOWS
   closesocket(fd);
#else
   (void)close(fd);
#endif
}

/**
 * Write the given amount of bytes to socket. 
 * This method blocks until data all data is sent, we loop
 * as the low level write() can return when the socket
 * buffer is full but not all data expected are sent.
 *
 * This code is not thread safe, you need to add a mutex to 
 * your calling code if two threads simultaneously want to read
 * from the same socket 'fd'.
 *
 * @return number of bytes written, -1 is EOF
 * @author W. Richard Stevens
 */
ssize_t writen(const int fd, const char * ptr, const size_t nbytes)
{
   ssize_t nleft, nwritten;
   int flag = 0; /* MSG_WAITALL; */

   nleft = (ssize_t)nbytes;
   while(nleft > 0) {
      nwritten = send(fd, ptr, (int)nleft, flag); /* write() is deprecated on Win */
      if (nwritten <= 0) {
         return nwritten; /* error */
      }
      nleft -= nwritten;
      ptr += nwritten;
   }
   return (ssize_t)nbytes - nleft;
}

/**
 * Read the given amount of bytes from socket. 
 * This method blocks until data arrives, we loop
 * as the low level recv() can return when the socket
 * buffer is empty but not all data expected arrived.
 *
 * This code is not thread safe, you need to add a mutex to 
 * your calling code if two threads simultaneously want to read
 * from the same socket 'fd'.
 *
 * @param fd The socket descriptor
 * @param ptr A buffer which is big enough to hold nbytes
 * @param nbytes The number of bytes to read
 * @param fpNumRead Function pointer, if not null we make a callback about the progress
 * @param userP Is bounced back to fpNumRead
 * @return number of bytes read, -1 is EOF
 * @author W. Richard Stevens
 */
ssize_t readn(const int fd, char *ptr, const size_t nbytes, XmlBlasterNumReadFunc fpNumRead, void *userP)
{
   ssize_t nread;
   ssize_t nleft;
   int flag = 0; /* MSG_WAITALL; */
   nleft = (ssize_t)nbytes;

   if (fpNumRead != 0 && nbytes > 10) { /* Ignore to report the msgLength read (first 10 bytes of a message) */
      fpNumRead(userP, (ssize_t)0, nbytes); /* Callback with startup status */
   }

   while(nleft > 0) {
      nread = recv(fd, ptr, (int)nleft, flag); /* read() is deprecated on Win */
      if (nread <= 0)  /* -1 is error, 0 is no more data to read which should not happen as we are blocking */
         break;        /* EOF is -1 */
      nleft -= nread;

      if (fpNumRead != 0 && nbytes > 10) { /* Ignore to report the msgLength read (first 10 bytes of a message) */
         fpNumRead(userP, (ssize_t)nbytes-nleft, nbytes); /* Callback with current status */
      }

      ptr += nread;
   }
   return (ssize_t)nbytes-nleft;
}

/**
 * Creates a raw blob to push over a socket as described in protocol.socket
 * @param msgUnit The message which we need to send
 * @param debug Pass true for debugging output to stdout
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @return The raw 'serialized' MsgUnit as a char* in BlobHolder, the caller needs to free() it.
 */
Dll_Export BlobHolder encodeMsgUnit(MsgUnit *msgUnit, bool debug)
{
   size_t qosLen=0, keyLen=0, contentLenStrLen=0;
   enum { SIZE = 126 };
   char contentLenStr[SIZE];
   size_t currpos = 0;
   BlobHolder blob;
   memset(&blob, 0, sizeof(BlobHolder));

   if (msgUnit == 0) {
      if (debug) printf("[xmlBlasterSocket] ERROR Invalid msgUnit=NULL in encodeMsgUnit()\n");
      return blob;
   }
   if (msgUnit->content == 0)
      msgUnit->contentLen = 0;
   SNPRINTF(contentLenStr, SIZE, "%ld", (long)msgUnit->contentLen);
   contentLenStrLen = strlen(contentLenStr);

   if (msgUnit->qos != 0)
      qosLen = strlen(msgUnit->qos);
   
   if (msgUnit->key != 0)
      keyLen = strlen(msgUnit->key);
   
   blob.dataLen = qosLen + 1 + keyLen + 1 + contentLenStrLen + 1 + msgUnit->contentLen;

   blob.data = (char *)malloc(blob.dataLen);

   if (msgUnit->qos != 0)
      memcpy(blob.data+currpos, msgUnit->qos, qosLen+1); /* inclusive '\0' */
   else
      *(blob.data+currpos) = 0;
   currpos += qosLen+1;

   if (msgUnit->key != 0)
      memcpy(blob.data+currpos, msgUnit->key, keyLen+1); /* inclusive '\0' */
   else
      *(blob.data+currpos) = 0;
   currpos += keyLen+1;

   memcpy(blob.data+currpos, contentLenStr, contentLenStrLen+1); /* inclusive '\0' */
   currpos += contentLenStrLen+1;

   if (msgUnit->content != 0)
      memcpy(blob.data+currpos, msgUnit->content, msgUnit->contentLen);
   /* currpos += msgUnit->contentLen; */

   return blob;
}

/**
 * Creates a raw blob to push over a socket as described in protocol.socket
 * @param msgUnitArr An array of messages
 * @param debug Set to true if you wish debugging output to stdout
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @return The raw 'serialized' MsgUnitArr as a char*, the caller needs to free() it.
 */
Dll_Export BlobHolder encodeMsgUnitArr(MsgUnitArr *msgUnitArr, bool debug)
{
   size_t i;
   size_t currpos = 0;

   BlobHolder blob;
   memset(&blob, 0, sizeof(BlobHolder));

   if (msgUnitArr == 0) {
      if (debug) printf("[xmlBlasterSocket] ERROR Invalid msgUnitArr=NULL in encodeMsgUnitArr()\n");
      return blob;
   }

   /* First calculate total length to allocate */
   for (i=0; i<msgUnitArr->len; i++) {
      MsgUnit* msgUnit = &msgUnitArr->msgUnitArr[i];
      size_t qosLen=0, keyLen=0;
      enum { SIZE = 126 };
      char contentLenStr[SIZE];

      if (msgUnit->content == 0)
         msgUnit->contentLen = 0;
      snprintf0(contentLenStr, SIZE, "%ld", (long)msgUnit->contentLen);

      if (msgUnit->qos != 0)
         qosLen = strlen(msgUnit->qos);
      
      if (msgUnit->key != 0)
         keyLen = strlen(msgUnit->key);
   
      blob.dataLen += qosLen + 1 + keyLen + 1 + strlen(contentLenStr) + 1 + msgUnit->contentLen;
   }

   blob.data = (char *)malloc(blob.dataLen);

   /* Now dump the message ... */
   for (i=0; i<msgUnitArr->len; i++) {
      MsgUnit* msgUnit = &msgUnitArr->msgUnitArr[i];
      size_t qosLen=0, keyLen=0, contentLenStrLen=0;
      enum { SIZE = 126 };
      char contentLenStr[SIZE];

      if (msgUnit->content == 0)
         msgUnit->contentLen = 0;
      snprintf0(contentLenStr, SIZE, "%ld", (long)msgUnit->contentLen);
      contentLenStrLen = strlen(contentLenStr);

      if (msgUnit->qos != 0) {
         qosLen = strlen(msgUnit->qos);
         memcpy(blob.data+currpos, msgUnit->qos, qosLen+1); /* inclusive '\0' */
      }
      else
         *(blob.data+currpos) = 0;
      currpos += qosLen+1;

      if (msgUnit->key != 0) {
         keyLen = strlen(msgUnit->key);
         memcpy(blob.data+currpos, msgUnit->key, keyLen+1); /* inclusive '\0' */
      }
      else
         *(blob.data+currpos) = 0;
      currpos += keyLen+1;

      memcpy(blob.data+currpos, contentLenStr, contentLenStrLen+1); /* inclusive '\0' */
      currpos += contentLenStrLen+1;

      if (msgUnit->content != 0)
         memcpy(blob.data+currpos, msgUnit->content, msgUnit->contentLen);
      currpos += msgUnit->contentLen;
   }
   return blob;
}

char *encodeSocketMessage(
              enum XMLBLASTER_MSG_TYPE_ENUM msgType,
              const char * const requestId, 
              const char * const methodName,
              const char * const secretSessionId,
              const char *data,
              size_t dataLen,
              bool debug,
              size_t *rawMsgLen)
{
   char *rawMsg = (char *)0;
   char *rawMsgStr;
   size_t currpos = 0;
   enum { SIZE = 256 };
   char tmp[SIZE];
   size_t lenUnzipped = dataLen;
   enum { SIZEF = 56 };
   char lenFormatStr[56]; /* = "%10.d"; */
   char lenStr[MSG_LEN_FIELD_LEN+1];

   if (data == 0) {
      data = "";
      dataLen = 0;
      lenUnzipped = 0;
   }

   rawMsg = (char *)calloc(50 + MAX_SESSIONID_LEN + MAX_METHODNAME_LEN + dataLen, sizeof(char));

   *(rawMsg+MSG_FLAG_POS_TYPE) = (char)msgType;   /* e.g. MSG_TYPE_INVOKE */
   *(rawMsg+MSG_FLAG_POS_VERSION) = XMLBLASTER_SOCKET_VERSION;

   currpos = MSG_POS_REQESTID;
   if (requestId == 0) printf("*** assert: xmlBlasterSocket.c requestId is NULL!\n");
   memcpy(rawMsg+currpos, requestId, strlen(requestId)+1); /* inclusive '\0' */
   currpos += strlen(requestId)+1;

   if (methodName == 0) printf("*** assert: xmlBlasterSocket.c methodName is NULL!\n");
   memcpy(rawMsg+currpos, methodName, strlen(methodName)+1); /* inclusive '\0' */
   currpos += strlen(methodName)+1;

   if (secretSessionId == 0) printf("*** assert: xmlBlasterSocket.c secretSessionId is NULL!\n");
   memcpy(rawMsg+currpos, secretSessionId, strlen(secretSessionId)+1); /* inclusive '\0' */
   currpos += strlen(secretSessionId)+1;
   
   snprintf0(tmp, SIZE, "%lu", (unsigned long)lenUnzipped);
   memcpy(rawMsg+currpos, tmp, strlen(tmp)+1); /* inclusive '\0' */
   currpos += strlen(tmp)+1;

   if (data == 0) printf("*** assert: xmlBlasterSocket.c data is NULL!\n");
   memcpy(rawMsg+currpos, data, dataLen);      /* add the msgUnit data */
   *rawMsgLen = currpos+dataLen;

   snprintf0(lenFormatStr, SIZEF, "%%%d.d", MSG_LEN_FIELD_LEN);
   snprintf0(lenStr, MSG_LEN_FIELD_LEN+1, lenFormatStr, *rawMsgLen);
   memcpy(rawMsg, lenStr, MSG_LEN_FIELD_LEN);
   
   if (debug) {
      rawMsgStr = toReadableDump(rawMsg, *rawMsgLen);
      printf("[xmlBlasterSocket] Sending now %lu bytes -> '%s'\n", (unsigned long)*rawMsgLen, rawMsgStr);
      free(rawMsgStr);
   }

   return rawMsg;
}

/**
 * Read a message from the given socket. 
 * This method blocks until data arrives.
 *
 * @param xmlBlasterSocket The socket to read data from (needs to be valid)
 * @param fpHolder Struct containing the function pointer which access the socket to read from (if necessary decompressing on the fly)
 * @param socketDataHolder The struct to put the parsed message into (needs to be allocated by you or on your stack)
 * @param exception The struct to put exceptions into (needs to be allocated by you or to be on your stack)
 * @param stopP The *stopP may change to 'true' during receiv() blocking
 * @param udp true if it is a UDP connection
 * @param debug Set to true to have debugging output on console
 * @return true: A messages is parsed and put into your socketDataHolder,
 *         you need to free(socketDataHolder->data) after working with it.
 *         Please check socketDataHolder->type if it is an exception.
 *         false: The socket is closed (EOF)
 */
bool parseSocketData(int xmlBlasterSocket, const XmlBlasterReadFromSocketFuncHolder *fpHolder,
       SocketDataHolder *socketDataHolder, XmlBlasterException *exception, bool *stopP, bool udp, bool debug)
{
   char msgLenPtr[MSG_LEN_FIELD_LEN+1];
   char *rawMsg = 0;
   char tmpPtr[256];
   ssize_t numRead;
   size_t currPos = 0;
   unsigned long msgLenL; /* to have 64 bit portable sscanf */

   char packet[MAX_PACKET_SIZE];
   /* initialize */
   memset(msgLenPtr, 0, MSG_LEN_FIELD_LEN+1);
   memset(socketDataHolder, 0, sizeof(SocketDataHolder));
   memset(exception, 0, sizeof(XmlBlasterException));
   exception->remote = false;

   if (debug) printf("[xmlBlasterSocket] Blocking now for %s callback messages ...\n", (udp) ? "udp" : "tcp");
   if (udp)
      numRead = recv(xmlBlasterSocket, packet, MAX_PACKET_SIZE, 0);
   else
      /* read the first 10 bytes to determine the length */
      numRead = fpHolder->readFromSocketFuncP(fpHolder->userP, xmlBlasterSocket, msgLenPtr, MSG_LEN_FIELD_LEN, fpHolder->numReadFuncP, fpHolder->numReadUserP);
   if (numRead <= 0 || *stopP == true) {
      return false; /* EOF on socket */
   }
   if ((!udp && numRead != MSG_LEN_FIELD_LEN) ||
       ( udp && numRead < MSG_LEN_FIELD_LEN)) {
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      snprintf0(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN, "[xmlBlasterSocket] ERROR Received numRead=%ld header bytes but expected %d", (long)numRead, MSG_LEN_FIELD_LEN);
      if (debug) { printf(exception->message); printf("\n"); }
      return true;
   }
   if (udp) {
      memcpy(msgLenPtr,  packet, MSG_LEN_FIELD_LEN);
   }
   *(msgLenPtr + MSG_LEN_FIELD_LEN) = 0; 
   trim(msgLenPtr);
   if (strToULong(&msgLenL, msgLenPtr) == false) {
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      snprintf0(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
              "[xmlBlasterSocket] ERROR Received numRead=%ld header bytes with invalid message length='%s'",
              (long)numRead, msgLenPtr);
      if (debug) { printf(exception->message); printf("\n"); }
      return true;
   }
   socketDataHolder->msgLen = (size_t)msgLenL;
   if (debug) printf("[xmlBlasterSocket] Receiving message of size %lu ...\n", (unsigned long)socketDataHolder->msgLen);

   if (socketDataHolder->msgLen <= MSG_LEN_FIELD_LEN || socketDataHolder->msgLen > MAX_MSG_LEN) {
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      snprintf0(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN,
              "[xmlBlasterSocket] ERROR Received numRead=%ld header bytes with invalid message length='%s' parsed to '%ld'",
              (long)numRead, msgLenPtr, (long)socketDataHolder->msgLen);
      if (debug) { printf(exception->message); printf("\n"); }
      return true;
   }

   /* read the complete message */
   rawMsg = (char *)calloc(socketDataHolder->msgLen, sizeof(char));
   memcpy(rawMsg, msgLenPtr, MSG_LEN_FIELD_LEN);
   if (udp) {
      memcpy(rawMsg+MSG_LEN_FIELD_LEN, packet+MSG_LEN_FIELD_LEN, socketDataHolder->msgLen-MSG_LEN_FIELD_LEN);
      numRead -= MSG_LEN_FIELD_LEN;
   }
   else
      numRead = fpHolder->readFromSocketFuncP(fpHolder->userP, xmlBlasterSocket, rawMsg+MSG_LEN_FIELD_LEN,
                          (int)socketDataHolder->msgLen-MSG_LEN_FIELD_LEN, fpHolder->numReadFuncP, fpHolder->numReadUserP);
   if (numRead <= 0 || *stopP == true) {
      free(rawMsg);
      return false; /* EOF on socket */
   }
   if ((size_t)numRead != (socketDataHolder->msgLen-MSG_LEN_FIELD_LEN)) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      snprintf0(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN, "[xmlBlasterSocket] ERROR Received numRead=%ld message bytes but expected %lu", (long)numRead, (unsigned long)(socketDataHolder->msgLen-MSG_LEN_FIELD_LEN));
      if (debug) { printf(exception->message); printf("\n"); }
      free(rawMsg);
      return true;
   }

   if (debug) {
      char *rawMsgStr = toReadableDump(rawMsg, socketDataHolder->msgLen);
      printf("[xmlBlasterSocket] Read %lu bytes from socket -> '%s'\n", (unsigned long)socketDataHolder->msgLen, rawMsgStr);
      free(rawMsgStr);
   }

   /* if (debug) {
      char *tmp = toReadableDump(rawMsg, socketDataHolder->msgLen);
      printf("[xmlBlasterSocket] Read %u bytes from socket\n%s\n", socketDataHolder->msgLen, tmp);
      free(tmp);
   }*/

   socketDataHolder->type = *(rawMsg+MSG_FLAG_POS_TYPE);
   if (socketDataHolder->type != MSG_TYPE_INVOKE &&
       socketDataHolder->type != MSG_TYPE_RESPONSE &&
       socketDataHolder->type != MSG_TYPE_EXCEPTION) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      snprintf0(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN, "[xmlBlasterSocket] ERROR Received response message of type=%c", socketDataHolder->type);
      if (debug) { printf(exception->message); printf("\n"); }
      free(rawMsg);
      return true;
   }

   socketDataHolder->version = *(rawMsg+MSG_FLAG_POS_VERSION);
   if (socketDataHolder->version != XMLBLASTER_SOCKET_VERSION) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      snprintf0(exception->message, EXCEPTIONSTRUCT_MESSAGE_LEN, "[xmlBlasterSocket] ERROR Received response message of unsupported version=%c", socketDataHolder->version);
      if (debug) { printf(exception->message); printf("\n"); }
      free(rawMsg);
      return true;
   }


   currPos = MSG_POS_REQESTID;

   strncpy0(socketDataHolder->requestId, rawMsg+currPos, MAX_REQUESTID_LEN);
   currPos += strlen(socketDataHolder->requestId)+1;

   strncpy0(socketDataHolder->methodName, rawMsg+currPos, MAX_METHODNAME_LEN);
   currPos += strlen(socketDataHolder->methodName)+1;

   strncpy0(socketDataHolder->secretSessionId, rawMsg+currPos, MAX_SESSIONID_LEN);
   currPos += strlen(socketDataHolder->secretSessionId)+1;

   strncpy0(tmpPtr, rawMsg+currPos, 256);
   currPos += strlen(tmpPtr)+1;
   trim(tmpPtr);
   socketDataHolder->dataLenUncompressed = 0;
   msgLenL = 0;
   if (strlen(tmpPtr) > 0 && strToULong(&msgLenL, tmpPtr) != 1) {
      printf("[xmlBlasterSocket] WARN uncompressed data length '%s' is invalid, we continue nevertheless\n", tmpPtr);
   }
   else {
      socketDataHolder->dataLenUncompressed = (size_t)msgLenL;
   }

   /* Read the payload */
   socketDataHolder->blob.dataLen = socketDataHolder->msgLen - currPos;
   if (socketDataHolder->blob.dataLen > 0) {
      socketDataHolder->blob.data = (char *)malloc(socketDataHolder->blob.dataLen * sizeof(char));
      memcpy(socketDataHolder->blob.data, rawMsg+currPos, socketDataHolder->blob.dataLen);
   }
   else {
      /*
      socketDataHolder->blob.dataLen = 6;
      socketDataHolder->blob.data = strcpyAlloc("<qos/>");
      */
      /*
      Allow empty message for example for get() returns with no match
      socketDataHolder->blob.dataLen = 1;
      socketDataHolder->blob.data = (char *)malloc(1);
      *socketDataHolder->blob.data = 0;
      */
   }

   free(rawMsg);
   rawMsg = 0;
   return true;
}

/**
 * The blob data is copied into the given exception object. 
 * Note: exception->remote is always set to true (assuming a remote blob)
 */
void convertToXmlBlasterException(const XmlBlasterBlob *blob, XmlBlasterException *exception, bool debug)
{
   size_t currpos = 0;
   size_t len;
   /* initializeXmlBlasterException(exception); */
   exception->remote = true;
   strncpy0(exception->errorCode, blob->data+currpos, XMLBLASTEREXCEPTION_ERRORCODE_LEN);
   currpos += strlen(exception->errorCode) + 1;
   len = ((blob->dataLen-currpos) > XMLBLASTEREXCEPTION_MESSAGE_LEN) ? XMLBLASTEREXCEPTION_MESSAGE_LEN : (blob->dataLen-currpos);
   strncpy0(exception->message, blob->data+currpos, len);
   trim(exception->message);
   if (debug) printf("[xmlBlasterSocket] Converted to XmlBlasterException\n");
}

/**
 * The given exception is dumped into the blob data. 
 * @param blob The encoded exception, you need to free the blob struct yourself after usage with
 *             freeBlobHolderContent(&blob);
 * @param exception The given exception struct
 * @param debug Print output to stdout
 */
void encodeXmlBlasterException(XmlBlasterBlob *blob, const XmlBlasterException *exception, bool debug)
{
   MsgUnit msgUnit;
   BlobHolder b;

   memset(&msgUnit, 0, sizeof(MsgUnit));
   msgUnit.qos = exception->errorCode;
   msgUnit.key = exception->message;

   b = encodeMsgUnit(&msgUnit, debug);
   blob->data = b.data;
   blob->dataLen = b.dataLen;
   if (debug) printf("[xmlBlasterSocket] Converted XmlBlasterException to SOCKET blob\n");
}

/**
 * Parses the QoS XML string array returned by erase() and unSubscribe()
 * @return The returned status QoS, never null, needs to be freed with freeQosArr() after usage.
 */
QosArr *parseQosArr(size_t dataLen, char *data)
{
   size_t ii;
   MsgUnitArr *msgUnitArr = parseMsgUnitArr(dataLen, data);
   QosArr* qosArr = (QosArr *)calloc(1, sizeof(QosArr));
   qosArr->len = msgUnitArr->len;
   qosArr->qosArr = (const char **)calloc(qosArr->len, sizeof(const char *));
   for (ii=0; ii<msgUnitArr->len; ii++) {
      qosArr->qosArr[ii] = strcpyAlloc(msgUnitArr->msgUnitArr[ii].qos);
   }
   freeMsgUnitArr(msgUnitArr);
   return qosArr;
}

/**
 * Parses the userData part of a raw socket message and fills an array
 * of MsgUnit structs.
 * @return The messages (never NULL), you need to free them after usage with freeMsgUnitArr(MsgUnitArr *)
 */
Dll_Export MsgUnitArr *parseMsgUnitArr(size_t dataLen, char *data)
{
   MsgUnitArr *msgUnitArr = (MsgUnitArr *)calloc(1, sizeof(MsgUnitArr));
   size_t currpos = 0;
   uint32_t currIndex = 0;
   enum { SIZE = 56 };
   msgUnitArr->isOneway = false;
   if (dataLen <= 0) {
      return msgUnitArr; /* Empty messageUnit array, only a first \0 for the qos */
   }
   msgUnitArr->len = 10;
   msgUnitArr->msgUnitArr = (MsgUnit *)calloc(msgUnitArr->len, sizeof(MsgUnit));
   while (currpos < dataLen) {
      char ptr[SIZE];

      if (currIndex >= msgUnitArr->len) {
         msgUnitArr->len += 10;
         msgUnitArr->msgUnitArr = (MsgUnit *)realloc(msgUnitArr->msgUnitArr, msgUnitArr->len * sizeof(MsgUnit));
      }

      {
         unsigned long msgLenL; /* to have 64 bit portable sscanf */
         MsgUnit *msgUnit = &msgUnitArr->msgUnitArr[currIndex++];
         memset(msgUnit, 0, sizeof(MsgUnit));
        
         /* read QoS */
         msgUnit->qos = strcpyAlloc(data+currpos);
         currpos += strlen(msgUnit->qos)+1;
        
         /* read key */
         if (currpos < dataLen) {
            if (strlen(data+currpos) > 0) {
               msgUnit->key = strcpyAlloc(data+currpos);
               currpos += strlen(msgUnit->key)+1;
            }
            else {
               currpos++;
            }
         }
        
         /* read content */
         if (currpos < dataLen) {
            char *tmp;
            strncpy0(ptr, data+currpos, SIZE);
            currpos += strlen(ptr)+1;
            trim(ptr);
            msgLenL = 0;
            if (strToULong(&msgLenL, ptr) != 1) {
               printf("[xmlBlasterSocket] WARN MsgUnit content length '%s' is invalid, we continue nevertheless\n", ptr);
            }
            msgUnit->contentLen = (size_t)msgLenL;
        
            tmp = (char *)malloc(msgUnit->contentLen * sizeof(char));
            memcpy(tmp, data+currpos, msgUnit->contentLen);
            msgUnit->content = tmp;
            currpos += msgUnit->contentLen;
         }
      }
   }

   if (currIndex == 0) {
      free(msgUnitArr->msgUnitArr);
      msgUnitArr->len = 0;
   }
   else if (currIndex < msgUnitArr->len) {
      msgUnitArr->msgUnitArr = (MsgUnit *)realloc(msgUnitArr->msgUnitArr, currIndex * sizeof(MsgUnit));
      msgUnitArr->len = currIndex; 
   }

   return msgUnitArr;
}

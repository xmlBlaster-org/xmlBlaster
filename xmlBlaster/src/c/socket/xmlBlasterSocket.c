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

/**
 * Read the given amount of bytes
 * This method blocks until data arrives.
 *
 * @return number of bytes read
 */
static size_t readn(int fd, char *ptr, size_t nbytes)
{
   ssize_t nread;
   size_t nleft;
   int flag = 0; // MSG_WAITALL;
   nleft = nbytes;

   while(nleft > 0) {
      nread = recv(fd, ptr, (int)nleft, flag);
      //nread = read(fd, ptr, (int)nleft);  // UNIX only
      if (nread < 0)
         return nread; /* error, return < 0 */
      else if (nread == 0 || nread == -1)
         break;        /* EOF is -1 */
      nleft -= nread;
      ptr += nread;
   }
   return (nbytes-nleft);
}

/**
 * Creates a raw blob to push over a socket as described in protocol.socket
 * @param totalLen is returned
 * @param rawMsg is returned
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @return The raw MsgUnit, the caller needs to free() it.
 */
char *encodeMsgUnit(MsgUnit *msgUnit, size_t *totalLen, bool debug)
{
   size_t qosLen, keyLen;
   char *data;
   char contentLenStr[126];
   size_t currpos = 0;
   bool keyWas0 = false; // to avoid that the caller free()'s it
   bool qosWas0 = false;
   bool contentWas0 = false;

   if (msgUnit == 0) {
      if (debug) printf("[XmlBlasterAccessUnparsed] ERROR Invalid msgUnit=NULL in encodeMsgUnit()\n");
      return (char *)0;
   }
   if (msgUnit->content == 0) {
      msgUnit->contentLen = 0;
      msgUnit->content = "";
      contentWas0 = true;
   }
   if (msgUnit->key == 0) {
      msgUnit->key = "";
      keyWas0 = true;
   }
   if (msgUnit->qos == 0) {
      msgUnit->qos = "";
      qosWas0 = true;
   }

   qosLen = strlen(msgUnit->qos);
   keyLen = strlen(msgUnit->key);
   sprintf(contentLenStr, "%d", msgUnit->contentLen);

   *totalLen = qosLen + 1 + keyLen + 1 + strlen(contentLenStr) + 1 + msgUnit->contentLen;

   data = (char *)malloc(*totalLen);

   memcpy(data+currpos, msgUnit->qos, qosLen+1); // inclusive '\0'
   currpos += qosLen+1;

   memcpy(data+currpos, msgUnit->key, keyLen+1); // inclusive '\0'
   currpos += keyLen+1;

   memcpy(data+currpos, contentLenStr, strlen(contentLenStr)+1); // inclusive '\0'
   currpos += strlen(contentLenStr)+1;

   memcpy(data+currpos, msgUnit->content, msgUnit->contentLen);
   //currpos += msgUnit->contentLen;

   if (qosWas0) msgUnit->qos = 0;
   if (qosWas0) msgUnit->qos = 0;
   if (contentWas0) msgUnit->content = 0;

   return data;
}

/**
 * Creates a raw blob to push over a socket as described in protocol.socket
 * @param rawMsgLen is returned
 * @param rawMsg is returned
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html
 * @return The raw message, the caller needs to free() it.
 */
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
   char tmp[256];
   size_t lenUnzipped = dataLen;
   char lenFormatStr[56]; // = "%10.d";
   char lenStr[MSG_LEN_FIELD_LEN+1];

   if (data == 0) {
      data = "";
      dataLen = 0;
      lenUnzipped = 0;
   }

   rawMsg = (char *)calloc(50 + MAX_SESSIONID_LEN + MAX_METHODNAME_LEN + dataLen, sizeof(char));

   *(rawMsg+MSG_FLAG_POS_TYPE) = msgType;   // e.g. MSG_TYPE_INVOKE
   *(rawMsg+MSG_FLAG_POS_VERSION) = XMLBLASTER_VERSION;

   currpos = MSG_POS_REQESTID;
   memcpy(rawMsg+currpos, requestId, strlen(requestId)+1); // inclusive '\0'
   currpos += strlen(requestId)+1;

   memcpy(rawMsg+currpos, methodName, strlen(methodName)+1); // inclusive '\0'
   currpos += strlen(methodName)+1;

   memcpy(rawMsg+currpos, secretSessionId, strlen(secretSessionId)+1); // inclusive '\0'
   currpos += strlen(secretSessionId)+1;
   
   sprintf(tmp, "%u", lenUnzipped);
   memcpy(rawMsg+currpos, tmp, strlen(tmp)+1); // inclusive '\0'
   currpos += strlen(tmp)+1;

   memcpy(rawMsg+currpos, data, dataLen);      // add the msgUnit data
   *rawMsgLen = currpos+dataLen;

   sprintf(lenFormatStr, "%%%d.d", MSG_LEN_FIELD_LEN);
   sprintf(lenStr, lenFormatStr, *rawMsgLen);
   memcpy(rawMsg, lenStr, MSG_LEN_FIELD_LEN);
   
   if (debug) {
      rawMsgStr = toReadableDump(rawMsg, *rawMsgLen);
      printf("[XmlBlasterAccessUnparsed] Sending now %u bytes -> '%s'\n", *rawMsgLen, rawMsgStr);
      free(rawMsgStr);
   }

   return rawMsg;
}

/**
 * Read a message from the given socket. 
 * This method blocks until data arrives.
 *
 * @param xmlBlasterSocket The socket to read data from (needs to be valid)
 * @param socketDataHolder The struct to put the parsed message into (needs to be allocated by you or on your stack)
 * @param exception The struct to put exceptions into (needs to be allocated by you or to be on your stack)
 * @param debug Set to true to have debugging output on console
 * @return true: A messages is parsed and put into your socketDataHolder (you need to free(socketDataHolder->data) after working with it)
 *         or if *exception->errorCode != 0: An error occurred and the exception struct is filled with information (you don't need to free anything)
 *         false: The socket is closed (EOF)
 */
bool parseSocketData(int xmlBlasterSocket, SocketDataHolder *socketDataHolder, XmlBlasterException *exception, bool debug) 
{
   char msgLenPtr[MSG_LEN_FIELD_LEN+1];
   char ptr[MSG_LEN_FIELD_LEN+1];
   char *rawMsg;
   char tmpPtr[256];
   size_t numRead;
   size_t currPos = 0;

   // initialize
   memset(msgLenPtr, 0, MSG_LEN_FIELD_LEN+1);
   memset(ptr, 0, MSG_LEN_FIELD_LEN+1);
   memset(tmpPtr, 0, MSG_LEN_FIELD_LEN+1);
   memset(socketDataHolder, 0, sizeof(SocketDataHolder));
   memset(exception, 0, sizeof(XmlBlasterException));
   exception->remote = false;

   // read the first 10 bytes to determine the length
   //numRead = recv(xmlBlasterSocket, msgLenPtr, MSG_LEN_FIELD_LEN, 0);
   numRead = readn(xmlBlasterSocket, msgLenPtr, MSG_LEN_FIELD_LEN);
   if (numRead <= 0) {
      return false; // EOF on socket
   }
   if (numRead != MSG_LEN_FIELD_LEN) {
      strncpy0(exception->errorCode, "user.connect", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[xmlBlasterSocket] ERROR Received numRead=%d header bytes but expected %d", numRead, MSG_LEN_FIELD_LEN);
      if (debug) { printf(exception->message); printf("\n"); }
      return true;
   }
   strcpy(ptr, msgLenPtr);
   trim(ptr);
   sscanf(ptr, "%u", &socketDataHolder->msgLen);
   if (debug) printf("[xmlBlasterSocket] Receiving message of size %u ...\n", socketDataHolder->msgLen);

   // read the complete message
   rawMsg = (char *)calloc(socketDataHolder->msgLen, sizeof(char));
   memcpy(rawMsg, msgLenPtr, MSG_LEN_FIELD_LEN);
   //numRead = recv(xmlBlasterSocket, rawMsg+MSG_LEN_FIELD_LEN, (int)socketDataHolder->msgLen-MSG_LEN_FIELD_LEN, 0);
   numRead = readn(xmlBlasterSocket, rawMsg+MSG_LEN_FIELD_LEN, (int)socketDataHolder->msgLen-MSG_LEN_FIELD_LEN);
   if (numRead <= 0) {
      return false; // EOF on socket
   }
   if (numRead != (socketDataHolder->msgLen-MSG_LEN_FIELD_LEN)) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[xmlBlasterSocket] ERROR Received numRead=%d message bytes but expected %u", numRead, (socketDataHolder->msgLen-MSG_LEN_FIELD_LEN));
      if (debug) { printf(exception->message); printf("\n"); }
      free(rawMsg);
      return true;
   }
   if (debug) printf("[xmlBlasterSocket] Read %u bytes from socket\n", socketDataHolder->msgLen);
   //if (debug) {
   //   char *tmp = toReadableDump(rawMsg, socketDataHolder->msgLen);
   //   printf("[xmlBlasterSocket] Read %u bytes from socket\n%s\n", socketDataHolder->msgLen, tmp);
   //   free(tmp);
   //}

   socketDataHolder->type = *(rawMsg+MSG_FLAG_POS_TYPE);
   if (socketDataHolder->type != MSG_TYPE_INVOKE &&
       socketDataHolder->type != MSG_TYPE_RESPONSE &&
       socketDataHolder->type != MSG_TYPE_EXCEPTION) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[xmlBlasterSocket] ERROR Received response message of type=%c", socketDataHolder->type);
      if (debug) { printf(exception->message); printf("\n"); }
      free(rawMsg);
      return true;
   }

   socketDataHolder->version = *(rawMsg+MSG_FLAG_POS_VERSION);
   if (socketDataHolder->version != XMLBLASTER_VERSION) {
      strncpy0(exception->errorCode, "user.response", XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      sprintf(exception->message, "[xmlBlasterSocket] ERROR Received response message of unsupported version=%c", socketDataHolder->version);
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
   sscanf(tmpPtr, "%u", &socketDataHolder->dataLenUncompressed);

   // Read the payload
   socketDataHolder->dataLen = socketDataHolder->msgLen - currPos;
   socketDataHolder->data = (char *)malloc(socketDataHolder->dataLen * sizeof(char));
   memcpy(socketDataHolder->data, rawMsg+currPos, socketDataHolder->dataLen);

   free(rawMsg);
   rawMsg = 0;

   if (socketDataHolder->type == MSG_TYPE_EXCEPTION) { // convert XmlBlasterException
      size_t currpos = 0;
      exception->remote = true;
      strncpy0(exception->errorCode, socketDataHolder->data+currpos, XMLBLASTEREXCEPTION_ERRORCODE_LEN);
      currpos += strlen(exception->errorCode) + 1;
      sprintf(exception->message, XMLBLASTEREXCEPTION_MESSAGE_FMT, socketDataHolder->data+currpos);
      trim(exception->message);
      
      free(socketDataHolder->data);
      socketDataHolder->data = 0;
      socketDataHolder->dataLen = 0;
      return true;
   }

   return true;
}

/**
 * Parses the userData part of a raw socket message and fills an array
 * of MsgUnit structs.
 * @return The messages (never NULL), you need to free them after usage with freeMsgUnitArr(MsgUnitArr *)
 */
MsgUnitArr *parseMsgUnitArr(size_t dataLen, char *data)
{
   MsgUnitArr *msgUnitArr = (MsgUnitArr *)calloc(1, sizeof(MsgUnitArr));
   size_t currpos = 0;
   size_t currIndex = 0;
   msgUnitArr->len = 10;
   msgUnitArr->msgUnitArr = (MsgUnit *)calloc(msgUnitArr->len, sizeof(MsgUnit));
   while (currpos < dataLen) {
      char ptr[56];

      if (currIndex >= msgUnitArr->len) {
         msgUnitArr->len += 10;
         msgUnitArr->msgUnitArr = (MsgUnit *)realloc(msgUnitArr->msgUnitArr, msgUnitArr->len * sizeof(MsgUnit));
      }

      {
         MsgUnit *msgUnit = &msgUnitArr->msgUnitArr[currIndex++];
        
         // read QoS
         msgUnit->qos = strcpyAlloc(data+currpos);
         currpos += strlen(msgUnit->qos)+1;
        
         // read key
         if (currpos < dataLen) {
            if (strlen(data+currpos) > 0) {
               msgUnit->key = strcpyAlloc(data+currpos);
               currpos += strlen(msgUnit->key)+1;
            }
            else {
               currpos++;
            }
         }
        
         // read content
         if (currpos < dataLen) {
            strcpy(ptr, data+currpos);
            currpos += strlen(ptr)+1;
            trim(ptr);
            sscanf(ptr, "%u", &msgUnit->contentLen);
        
            msgUnit->content = (char *)malloc(msgUnit->contentLen * sizeof(char));
            memcpy(msgUnit->content, data+currpos, msgUnit->contentLen);
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

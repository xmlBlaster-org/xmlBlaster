/*------------------------------------------------------------------------------
Name:      Parser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Parser class for raw socket messages
Version:   $Id: Parser.java,v 1.21 2002/03/31 13:21:26 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

import java.util.Vector;

/**
 * Parser class for raw socket messages. 
 * <br />
 * This class creates and parses raw byte[] messages which can be used
 * to transfer over a socket connection.
 * <br />
 * Parser instances may be reused, but are NOT reentrant (there are many 'global' variables)
 * <br />
 * Please read the requirement specification
 * <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirement/protocol.socket.html">protocol.socket</a>
 *
 * <pre>
 *  msgLen[10] flag[6] requestId methodName sessionId  lenUnzipped  userData  checkSum[10]
 *  +---------+-------+------ -*----------*-----------*-----------*-----------+----------+
 *
 *
 *  The 'userData' consists of 0-n of these:
 *
 *  qos      key    len   content
 *  +-----*---------*-----*----------+
 *
 *
 *  Examples, '*' marks a null byte and '|' is just to show the boundary (is not part of the message):
 *
 *  Testing qos/key/content
 *  |        83**I**17711*publish*oxf6hZs**<qos></qos>*<key oid='hello'/>*11*Hello world|
 *
 *  Testing qos/key
 *  |        70**I**17711*get*oxf6hZs**<qos></qos>*<key oid='ooo'></key>*0*|
 *
 *  Testing publish return with qos
 *  |        48**R**17711*publish*oxf6hZs**<qos/>**0*|
 *
 *  Testing nothing
 *  |        38**I**17711*get*oxf6hZs****0*|
 *
 *  Testing ping:
 *  |        29**I**11*ping*****0*|
 *
 *  Testing XmlBlasterException
 *  |        76**E**17711*get*oxf6hZs**Parser*An XmlBlasterException test only*0*|
 *
 *  Testing qos/key/content return value
 *  |        85**R**17711*publish***<qos></qos>*<key oid='hello'/>*20*Hello world response|
 *
 *  Testing a QoS return value
 *  |        59**R**17711*get***<qos><state>OK</state></qos>**0*|
 *
 *  Testing two qos/key/content
 *  |       100**I**17711*publish*oxf6hZs**<qos/>*<key oid='x1'/>*6*Hello1<qos/>*<key oid='x2'/>*6*Hello2|
 * </pre>
 *
 * @author ruff@swand.lake.de
 */
public class Parser
{
   private static final String ME = "Parser";
   
   public static final int NUM_FIELD_LEN = 10;
   public static final int FLAG_FIELD_LEN = 6;
   public static final int MAX_STRING_LEN = Integer.MAX_VALUE;
   public static final String EMPTY_STRING = "";

   public static final byte CHECKSUM_ADLER_BYTE = (byte)65; // 'A'
   public static final byte COMPRESSED_GZIP_BYTE = (byte)90; // 'Z'
   public static final byte INVOKE_BYTE = (byte)73; // INVOKE_TYPE = "I";
   public static final byte RESPONSE_BYTE = (byte)82; // RESPONSE_TYPE = "R";
   public static final byte EXCEPTION_BYTE = (byte)69; // EXCEPTION_TYPE = "E";
   public static final byte VERSION_1_BYTE = (byte)49;  // '1'
   private static final byte[] EMPTY10 = new String("          ").getBytes();
   private static final byte NULL_BYTE = (byte)0;

   //private int msgLength;

   /** flag field number one */
   private boolean checksum;
   /** flag field 2 */
   private boolean compressed;
   /** flag field 3 */
   private byte type;
   /** flag field 4 */
   private byte byte4;
   /** flag field 5 */
   private byte byte5;
   /** flag field 6 */
   private int version;

   private String requestId;
   private String methodName;
   private String sessionId;
   private long lenUnzipped;
   private long checkSumResult;

   /** Unique counter */
   private static long counter = 0L;

   /** Holding MessageUnit objects which acts as a holder for the method arguments */
   private Vector msgVec;

   /** Set debug level */
   public int SOCKET_DEBUG=0;

   protected long index = 0L;

   // create only once, for low level parsing
   //private ByteArray byteArray = new ByteArray(256);
   private Buf buf = new Buf();
   private byte[] first10 = new byte[NUM_FIELD_LEN];


   /**
    * The same parser object may be reused. 
    */
   public Parser() {
      msgVec = new Vector();
      initialize();
   }


   public Parser(byte type, String methodName, String sessionId) {
      msgVec = new Vector();
      initialize();
      setType(type);
      setMethodName(methodName);
      setSessionId(sessionId);
   }


   public Parser(byte type, String requestId, String methodName, String sessionId) {
      msgVec = new Vector();
      initialize();
      setType(type);
      setRequestId(requestId);
      setMethodName(methodName);
      setSessionId(sessionId);
   }


   /**
    * This method allows to reuse a Parser instance. 
    */
   public void initialize() {
      index = 0L;
      //msgLength = -1;
      checksum = false;
      compressed = false;
      type = INVOKE_BYTE; // request
      byte4 = 0;
      byte5 = 0;
      version = 1;
      requestId = null;
      methodName = null;
      sessionId = "";
      lenUnzipped = -1L;
      checkSumResult = -1L;
      msgVec.clear();
   }

   public int getNumMessages()
   {
      return msgVec.size();
   }

   /**
    * @param type The method type, e.g. Parser.INVOKE_BYTE
    */
   public final void setType(byte type) {
      this.type = type;
   }

   /**
    * @return The method type, e.g. Parser.INVOKE_BYTE
    */
   public final byte getType() {
      return this.type;
   }

   public final boolean isInvoke() {
      return (INVOKE_BYTE == type);
   }
   public final boolean isResponse() {
      return (RESPONSE_BYTE == type);
   }
   public final boolean isException() {
      return (EXCEPTION_BYTE == type);
   }

   /**
    * Set a unique ID (unique for this client), it
    * will be bounced back with the return value or with an
    * exception occurred during this request. 
    * <br />
    * Note that you usually shouldn't set this value, this class generates
    * a unique requestId which you can access with getRequestId()
    */
   public final void setRequestId(String requestId) {
      this.requestId = requestId;
   }

   /**
    * Use this when sending a message.
    * <p>
    * Get a unique ID (unique for this client), it
    * will be bounced back with the return value or with an
    * exception occurred during this request
    * </p>
    * @param praefix If desired you can specify a prefix for the request ID, e.g. "joe:"
    * @return An ID (unique in this JVM scope), e.g. "joe:3400" or "3400" if praefix is null
    */
   public final String createRequestId(String praefix) {
      if (this.requestId == null || this.requestId.length() < 1) {
         if (praefix == null) praefix = "";
         synchronized(Parser.class) {
            if (this.counter >= (Long.MAX_VALUE-1L)) this.counter = 0L;
            this.counter++;
            this.requestId = praefix+this.counter;
         }
      }
      return this.requestId;
   }

   /**
    * Use this when receiving a message.
    * @return The received request ID
    */
   public final String getRequestId() {
      if (requestId == null) throw new IllegalArgumentException(ME + ": getRequestId returns null");
      return this.requestId;
   }

   /** For example Constants.PUBLISH */
   public final void setMethodName(String methodName) {
      this.methodName = methodName;
   }

   /**
    * For example Constants.PUBLISH
    * @return unchecked
    */
   public final String getMethodName() {
      return this.methodName;
   }
   
   /** The authentication sessionId */
   public final void setSessionId(String sessionId) {
      this.sessionId = sessionId;
   }
   
   /** The authentication sessionId */
   public final String getSessionId() {
      if (sessionId == null) return "";
      return this.sessionId;
   }


   /** Enable checksum? */
   public final void setChecksum(boolean checksum) {
      if (checksum == true) {
         Log.warn(ME, "Checksum for raw socket message is not supported");
         return;
      }
      this.checksum = checksum;
   }
   
   /** Compress message? */
   public final void setCompressed(boolean compressed) {
      if (compressed == true) {
         Log.warn(ME, "Compression for raw socket message is not supported");
         return;
      }
      this.compressed = compressed;
   }

   /**
    * Use for methods get, subscribe, unSubscribe, erase
    * @exception IllegalArgumentException if invoked multiple times
    */
   public final void addKeyAndQos(String key, String qos) {
      if (!msgVec.isEmpty())
         throw new IllegalArgumentException(ME+".addKeyAndQos() may only be invoked once");
      MessageUnit msg = new MessageUnit(key, (byte[])null, qos);
      msgVec.add(msg);
   }

   /**
    * Use for exception message
    * <br />
    * NOTE: Exceptions don't return
    * @exception IllegalArgumentException if invoked multiple times
    */
   public final void addException(XmlBlasterException e) {
      if (!msgVec.isEmpty())
         throw new IllegalArgumentException(ME+".addException() may only be invoked once");
      MessageUnit msg = new MessageUnit(e.reason, (byte[])null, e.id);
      msgVec.add(msg);
   }

   /**
    * Use for methods update, publish. 
    * <br />
    * Use for return value of method get.
    * <br />
    * Multiple adds are OK
    */
   public final void addMessage(MessageUnit msg) {
      msgVec.add(msg);
   }

   /**
    * Use for methods update, publish. 
    * <br />
    * Use for return value of method get.
    * <br />
    * Multiple adds are OK
    */
   public final void addMessage(MessageUnit[] arr) {
      for (int ii=0; ii<arr.length; ii++)
         msgVec.add(arr[ii]);
   }

   /**
    * Add a QoS value, use for methods connect, disconnect, ping.
    * <br />
    * Use for return value of methods connect, disconnect, ping, update, publish, subscribe, unSubscribe and erase
    * @exception IllegalArgumentException if invoked multiple times
    */
   public final void addMessage(String qos) {
      if (!msgVec.isEmpty())
         throw new IllegalArgumentException(ME+".addQos() may only be invoked once");
      MessageUnit msg = new MessageUnit(null, (byte[])null, qos);
      msgVec.add(msg);
   }

   /** @see #addMessage(String qos) */
   public final void addQos(String qos) {
      addMessage(qos);
   }

   /**
    * Add a QoS array value. 
    * <br />
    * Use for return value of methods publishArr and erase
    * @exception IllegalArgumentException if invoked multiple times
    */
   public final void addMessage(String[] qos) {
      if (!msgVec.isEmpty())
         throw new IllegalArgumentException(ME+".addQos() may only be invoked once");
      for (int ii=0; ii<qos.length; ii++) {
         MessageUnit msg = new MessageUnit(null, (byte[])null, qos[ii]);
         msgVec.add(msg);
      }
   }

   /** @see #addMessage(String[] qos) */
   public final void addQos(String[] qos) {
      addMessage(qos);
   }

   /**
    * Returns all messages in a Vector
    */
   public final Vector getMessages() {
      return msgVec;
   }

   /**
    * Returns all messages as an array
    */
   public final MessageUnit[] getMessageArr() {
      if (msgVec.isEmpty()) return new MessageUnit[0];
      MessageUnit[] arr = new MessageUnit[msgVec.size()];
      for (int ii=0; ii<msgVec.size(); ii++) {
         arr[ii] = (MessageUnit)msgVec.elementAt(ii); // JDK 1.1 compatible
      }
      return arr;
   }

   /**
    * Response is usually only a QoS
    * @exception IllegalArgumentException if there is no QoS to get
    */
   public final String getQos() {
      if (msgVec.isEmpty()) {
         throw new IllegalArgumentException(ME + ": getQos() is called without having a response");
      }
      MessageUnit msg = (MessageUnit)msgVec.elementAt(0);
      return msg.getQos();
   }

   /**
    * Response is usually only a QoS
    * @exception IllegalArgumentException if there is no QoS to get
    */
   public final String[] getQosArr() {
      if (msgVec.isEmpty()) {
         throw new IllegalArgumentException(ME + ": getQos() is called without having a response");
      }
      Vector msgs = getMessages();
      String[] strArr = new String[msgs.size()];
      for (int ii=0; ii<strArr.length; ii++) {
         strArr[ii] = ((MessageUnit)msgs.elementAt(ii)).getQos();
      }
      return strArr;
   }

   /**
    * On errors. 
    * @exception IllegalArgumentException if there is no exception to get
    */
   public final XmlBlasterException getException() {
      if (msgVec.isEmpty()) {
         throw new IllegalArgumentException(ME + ": getException() is called without having an exception");
      }
      MessageUnit msg = (MessageUnit)msgVec.elementAt(0);
      return new XmlBlasterException(msg.getQos(), msg.getXmlKey());
   }

   /**
    * Blocks on socket until a complete message is read. 
    * @return A complete message in a byte[].
    *         NOTE: The first 10 bytes are not initialized.<br />
    *         null: An empty message which only contains the header 10 bytes
    */
   public final Buf readOneMsg(InputStream in) throws IOException
   {
      if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Entering readOneMsg(), waiting on inputStream");

      // First we extract the first 10 bytes to get the msgLength ...
      int remainLength = NUM_FIELD_LEN;
      int lenRead;
      {
         int off = 0;
         while ((lenRead = in.read(first10, off, remainLength)) != -1) {
            remainLength -= lenRead;
            if (remainLength == 0) break;
            off += lenRead;
            //Log.info(ME, "Receive: lenRead=" + lenRead + " off=" + off + " remainLength=" + remainLength);
         }
      }

      int msgLength = 0;
      if (lenRead == -1)
         throw new IOException("Can't read message header (first 10 bytes) from socket, message is corrupted");

      try {
         msgLength = Integer.parseInt((new String(first10, 0, NUM_FIELD_LEN)).trim());
      }
      catch (NumberFormatException e) {
         throw new IOException("Format of message header is corrupted '" + new String(first10) + "', expected integral value");
      }

      if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Got first 10 bytes of total length=" + msgLength);
      if (msgLength < NUM_FIELD_LEN)
         throw new IOException("Message format is corrupted, the given message length=" + msgLength + " is invalid");
      else if (msgLength == NUM_FIELD_LEN)
         return null; // An empty message only contains the header 10 bytes


      // Now we know the msgLength, lets extract the complete message ...
      if (buf.buf == null || buf.buf.length != msgLength) {
         buf.buf = null;
         buf.buf = new byte[msgLength];
         buf.offset = 0;
      }
      buf.offset = NUM_FIELD_LEN;
      remainLength = msgLength - buf.offset;
      while ((lenRead = in.read(buf.buf, buf.offset, remainLength)) != -1) {
         remainLength -= lenRead;
         if (remainLength == 0) break;
         buf.offset += lenRead;
         //Log.info(ME, "Receive: lenRead=" + lenRead + " buf.offset=" + buf.offset + " remainLength=" + remainLength);
      }

      if (lenRead == -1)
         throw new IOException("Can't read complete message (" + msgLength + " bytes) from socket, only " + remainLength + " received, message is corrupted");

      if (remainLength != 0) // assert
         throw new IOException("Internal error, can't read complete message (" + msgLength + " bytes) from socket, only " + remainLength + " received, message is corrupted");

      return buf;
   }

   /**
    * This parses the raw message from an InputStream (typically from a socket).
    * Use the get...() methods to access the data.
    */
   public final void parse(InputStream in) throws IOException {

      initialize();

      Buf buf = readOneMsg(in); // blocks until one message is read

      if (buf == null) {
         setMethodName(Constants.PING);
         return; // The shortest ping ever
      }

      checksum = (buf.buf[NUM_FIELD_LEN] > 0);
      if (checksum) {
         Log.warn(ME, "Ignoring checksum flag");
      }
      compressed = (buf.buf[NUM_FIELD_LEN+1] > 0);
      if (compressed) {
         Log.warn(ME, "Ignoring compress flag");
      }
      type = buf.buf[NUM_FIELD_LEN+2];
      byte4 = buf.buf[NUM_FIELD_LEN+3];
      byte5 = buf.buf[NUM_FIELD_LEN+4];
      version = (int)buf.buf[NUM_FIELD_LEN+5] - 48;
      if (version != 1) {
         Log.warn(ME, "Ignoring version=" + version + " on 1 is supported");
      }

      buf.offset = NUM_FIELD_LEN+FLAG_FIELD_LEN;

      requestId = toString(buf);
      methodName = toString(buf);
      sessionId = toString(buf);

      lenUnzipped = toInt0(buf, -1);
      if (lenUnzipped != -1) {
         Log.warn(ME, "Ignoring given unzipped message length");
      }

      String qos = null;
      String xmlKey = null;
      byte[] content = null;
      for (int ii=0; ii<Integer.MAX_VALUE; ii++) {
         qos = toString(buf);
         MessageUnit msgUnit = new MessageUnit(null, (byte[])null, qos);
         addMessage(msgUnit);
         if (buf.offset >= buf.buf.length) break;

         msgUnit.setKey(toString(buf));
         if (buf.offset >= buf.buf.length) break;

         if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Getting messageUnit content index=" + index);
         msgUnit.setContent(toByte(buf));
         if (buf.offset >= buf.buf.length) break;
      }

      if (checksum)
         checkSumResult = toLong0(buf, -1);

      if (buf.offset != buf.buf.length) {
         String str = "Format mismatch, read index=" + buf.offset + " expected message length=" + buf.buf.length + " we need to disconnect the client, can't recover.";
         throw new IOException(str);
      }

      if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "messageUnit OK index=" + index);
   }

   /**
    * Calculates the length of user data including null bytes and len field
    * @exception IllegalArgumentException Message size is limited to Integer.MAX_VALUE bytes
    */
   private long getUserDataLen() {
      long len=0L;
      for (int ii=0; ii<msgVec.size(); ii++) {
         MessageUnit unit = (MessageUnit)msgVec.elementAt(ii);
         len += unit.size() + 3;   // three null bytes
         String tmp = ""+unit.getContent().length;
         len += tmp.length();
      }
      if (len > Integer.MAX_VALUE)
         throw new IllegalArgumentException("Message size is limited to " + Integer.MAX_VALUE + " bytes");
      return len;
   }


   /**
    * <pre>
    *  msgLen[10] flag[6] requestId methodName sessionId  lenUnzipped  userData  checkSum[10]
    *  +---------+-------+------ -*----------*-----------*-----------*-----------+----------+
    *
    *
    *  The 'userData' consists of 0-n of these:
    *
    *  qos      key    len   content
    *  +-----*---------*-----*----------+
    *
    *  An example is ('*' marks a null byte):
    *
    *  "        83**I**17711*publish*oxf6hZs**<qos></qos>*<key oid='hello'/>*11*Hello world"
    *
    * </pre>
    */
   public final byte[] createRawMsg() throws XmlBlasterException {

      if (Constants.checkMethodName(methodName) == false) {
         String str = "Can't send message, method '" + methodName + " is unknown";
         //Log.error(ME, str);
         throw new IllegalArgumentException(ME + ": " + str);
      }

      try {
         long len = getUserDataLen() + 500;
         ByteArray out = new ByteArray((int)len);

            /*
         int lenProxyHeader = 0;
         if (proxyHost != null) {
             telnet proxy 3128
             
             GET http://192.121.221.46:8080 HTTP/1.0


             POST /path/script.cgi HTTP/1.0
             From: frog@jmarshall.com
             User-Agent: HTTPxmlBlaster/1.0
             Content-Type: application/x-www-form-urlencoded
             Content-Length: 32

             home=Cosby&favorite+flavor=flies

            final byte[] CRLF = {13, 10};
            final String CRLFstr = new String(CRLF);
            StringBuffer buf = new StringBuffer(256);
            buf.append("POST http://").append(hostname).append(":").append(port).append(" HTTP/1.0").append(CRLFstr);
         }
            */

         out.write(EMPTY10, 0, EMPTY10.length); // Reserve 10 bytes at the beginning ...

         // Write the 6 byte fields ...
         out.write((checksum)?CHECKSUM_ADLER_BYTE:NULL_BYTE);    // 'A'
         out.write((compressed)?COMPRESSED_GZIP_BYTE:NULL_BYTE); // 'Z'
         out.write(type); // 'I' or 'R' or 'E'
         out.write(NULL_BYTE);       // byte4
         out.write(NULL_BYTE);       // byte5
         out.write(VERSION_1_BYTE);  // '1'

         out.write(createRequestId(null).getBytes());
         out.write(NULL_BYTE);

         out.write(getMethodName().getBytes());
         out.write(NULL_BYTE);

         out.write(getSessionId().getBytes());
         out.write(NULL_BYTE);

         if (lenUnzipped > 0)
            out.write(new String(""+lenUnzipped).getBytes());
         out.write(NULL_BYTE);

         if (msgVec.isEmpty()) {
            out.write(NULL_BYTE);
            out.write(NULL_BYTE);
            out.write("0".getBytes());
            out.write(NULL_BYTE);
         }
         else {
            for (int ii=0; ii<msgVec.size(); ii++) {
               MessageUnit unit = (MessageUnit)msgVec.elementAt(ii);
               out.write(unit.qos.getBytes());
               out.write(NULL_BYTE);
               out.write(unit.xmlKey.getBytes());
               out.write(NULL_BYTE);
               out.write((""+unit.content.length).getBytes());
               out.write(NULL_BYTE);
               out.write(unit.content);
            }
         }

         if (checksum == true) {
            int pos = out.size();
            out.write(EMPTY10, 0, EMPTY10.length);
            byte[] checkSumResultB = new String(""+checkSumResult).getBytes();
            out.insert(pos+EMPTY10.length-checkSumResultB.length, checkSumResultB);
         }

         // Finally we know the overall length, write it to the header:
         byte[] msgLengthB = new String(""+out.size()).getBytes();
         out.insert(EMPTY10.length - msgLengthB.length, msgLengthB);
         return out.toByteArray();
      }
      catch(IOException e) {
         String text = "Creation of message failed.";
         Log.warn(ME, text + " " + e.toString());
         throw new XmlBlasterException(ME, text);
      }
   }

   /**
    * Reads the binary content of a message. First we parse the long value which
    * holds the content length, than we retrieve the binary content. 
    */
   public final byte[] toByte(Buf buf) throws IOException {
      int len = toInt0(buf, 0);
      byte[] b = new byte[(int)len];
      if (len == 0L)
         return b;
      
      System.arraycopy(buf.buf, buf.offset, b, 0, len);
      buf.offset += len;
      return b;
   }

   /**
    * Converts bytes from byte[] until \0 to a long
    */
   public final long toLong0(Buf buf, long defaultVal) throws IOException {
      String tmp = toString(buf).trim();
      if (tmp == null || tmp.length() < 1)
         return defaultVal;
      try {
         return Long.parseLong(tmp);
      }
      catch (NumberFormatException e) {
         e.printStackTrace();
         Log.error(ME, "toLong0(" + niceAndShort(tmp) + ") " + buf.toLiteral());
         throw new IOException("Format is corrupted '" + dump() + "', expected long integral value");
      }
   }

   /**
    * Converts bytes from byte[] until \0 to an int
    */
   private final int toInt0(Buf buf, int defaultVal) throws IOException {
      String tmp = toString(buf).trim();
      if (tmp == null || tmp.length() < 1)
         return defaultVal;
      try {
         return Integer.parseInt(tmp.trim());
      }
      catch (NumberFormatException e) {
         e.printStackTrace();
         Log.error(ME, "toInt0(" + niceAndShort(tmp) + ") " + buf.toLiteral());
         throw new IOException("Format is corrupted '" + dump() + "', expected integral value");
      }
   }

   private String niceAndShort(String tmp)
   {
      if (tmp == null)
         return "null";
      if (tmp.length() > 50)
         return tmp.substring(0,50) + " ...";
      return tmp;
   }

   /**
    * Extracts string until next null byte '\0'
    */
   private final String toString(Buf buf) throws IOException {
      int startOffset = buf.offset;
      for (; buf.offset<buf.buf.length; buf.offset++) {
         if (buf.buf[buf.offset] == 0) {
            if (startOffset == buf.offset) {
               buf.offset++;  // overread the 0
               return EMPTY_STRING;
            }
            buf.offset++;  // overread the 0
            return new String(buf.buf, startOffset, buf.offset-startOffset-1);
         }
      }
      if (buf.offset == buf.buf.length)
         return EMPTY_STRING;

      return new String(buf.buf, startOffset, buf.offset-startOffset);
   }

   private String dump() {
      StringBuffer buffer = new StringBuffer(256);
      buffer.append("msgLength=" + buf.buf.length);
      buffer.append(", checksum=" + checksum);
      buffer.append(", compressed=" + compressed);
      buffer.append(", type=" + type);
      buffer.append(", byte4=" + byte4);
      buffer.append(", byte5=" + byte5);
      buffer.append(", version=" + version);
      buffer.append(", requestId=" + requestId);
      buffer.append(", methodName=" + methodName);
      buffer.append(", sessionId=" + sessionId);
      buffer.append(", lenUnzipped=" + lenUnzipped);
      buffer.append(", checkSumResult=" + checkSumResult);
      buffer.append(", index=" + index);
      return buffer.toString();
   }

   /**
    * Get the raw messages as a string, for tests and for dumping only
    * @return The stringified message, null bytes are replaced by '*'
    */
   public static String toLiteral(byte[] arr) {
      StringBuffer buffer = new StringBuffer(arr.length+10);
      byte[] dummy = new byte[1];
      for (int ii=0; ii<arr.length; ii++) {
         if (arr[ii] == 0)
            buffer.append("*");
         else {
            dummy[0] = arr[ii];
            buffer.append(new String(dummy));
         }
      }
      return buffer.toString();
   }

   private class Buf {
      byte[] buf; // Holding one message
      int offset; // Current position of reading

      public String toString() {
         if (buf == null) return "null";
         byte[] tmp = new byte[buf.length];
         for (int ii=0; ii<buf.length; ii++) {
            if (buf[ii] == 0)
               tmp [ii] = (byte)'*';
            else
               tmp[ii] = buf[ii];
         }
         return new String(tmp);
      }

      /**
       * Get the current section of buf as a string -20 to + 100 bytes
       * @return The stringified message, null bytes are replaced by '*'
       */
      public String toLiteral() {
         StringBuffer buffer = new StringBuffer(200);
         int start = 0;
         if (offset > 20)
            start = offset-20;
         buffer.append("Dumping from offset=" + start + ", problemOffset=" + offset + " msgLen=" + buf.length + ": '");
         byte[] dummy = new byte[1];
         int ii=start;
         for (; ii<offset+100 && ii<buf.length; ii++) {
            if (buf[ii] == 0)
               buffer.append("*");
            else {
               dummy[0] = buf[ii];
               buffer.append(new String(dummy));
            }
         }
         buffer.append("'");
         if (ii < buf.length)
            buffer.append(" ...");
         return buffer.toString();
      }
   }


   /** java org.xmlBlaster.protocol.socket.Parser */
   public static void main( String[] args ) {
      try {
         byte[] rawMsg = null;
         String testName;

         testName = "Testing qos/key/content";
         System.out.println("\n----------------------\n"+testName);
         {
            Parser parser = new Parser();
            parser.setType(Parser.INVOKE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(Constants.PUBLISH);
            parser.setSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            MessageUnit msg = new MessageUnit("<key oid='hello'/>", "Hello world".getBytes(), "<qos></qos>");
            parser.addMessage(msg);

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            Parser receiver = new Parser();
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            //System.out.println("\nReceived: \n" + receiver.dump());
            String receive = toLiteral(receiver.createRawMsg());
            System.out.println("Received: \n|" + receive + "|");
            if (toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }

         testName = "Testing many qos/key/content";
         System.out.println("\n----------------------\n"+testName);
         {
            Parser parser = new Parser();
            parser.setType(Parser.INVOKE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(Constants.PUBLISH);
            parser.setSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            parser.addMessage(new MessageUnit("<key oid='x1'/>", "Hello1".getBytes(), "<qos/>"));
            parser.addMessage(new MessageUnit("<key oid='x2'/>", "Hello2".getBytes(), "<qos/>"));
            //parser.addMessage(new MessageUnit("<key oid='x3'/>", "Hello3".getBytes(), "<qos/>"));
            //parser.addMessage(new MessageUnit("<key oid='x4'/>", "Hello4".getBytes(), "<qos/>"));

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            Parser receiver = new Parser();
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            //System.out.println("\nReceived: \n" + receiver.dump());
            String receive = toLiteral(receiver.createRawMsg());
            System.out.println("Received: \n|" + receive + "|");
            if (toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }

         testName = "Testing qos/key";
         System.out.println("\n----------------------\n"+testName);
         {
            Parser parser = new Parser();
            parser.setType(Parser.INVOKE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(Constants.GET);
            parser.setSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            parser.addKeyAndQos("<key oid='ooo'></key>", "<qos></qos>");

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            Parser receiver = new Parser();
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            //System.out.println("\nReceived: \n" + receiver.dump());
            String receive = toLiteral(receiver.createRawMsg());
            System.out.println("Received: \n|" + receive + "|");
            if (toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }


         testName = "Testing qos return";
         System.out.println("\n----------------------\n"+testName);
         {
            Parser parser = new Parser();
            parser.setType(Parser.RESPONSE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(Constants.PUBLISH);
            parser.setSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            parser.addQos("<qos/>");

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            Parser receiver = new Parser();
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            //System.out.println("\nReceived: \n" + receiver.dump());
            String receive = toLiteral(receiver.createRawMsg());
            System.out.println("Received: \n|" + receive + "|");
            if (toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }

         testName = "Testing nothing";
         System.out.println("\n----------------------\n"+testName);
         {
            Parser parser = new Parser();
            parser.setType(Parser.INVOKE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(Constants.GET);
            parser.setSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            Parser receiver = new Parser();
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            //System.out.println("\nReceived: \n" + receiver.dump());
            String receive = toLiteral(receiver.createRawMsg());
            System.out.println("Received: \n|" + receive + "|");
            if (toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }

         testName = "Testing really nothing";
         System.out.println("\n----------------------\n"+testName);
         {
            rawMsg = "        10".getBytes();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            Parser receiver = new Parser();
            receiver.setSessionId(null);
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            //System.out.println("\nReceived: \n" + receiver.dump());
            String receive = toLiteral(receiver.createRawMsg());
            System.out.println("Received: \n|" + receive + "|");
            if ("        29**I**11*ping*****0*".equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }



         testName = "Testing XmlBlasterException";
         System.out.println("\n----------------------\n"+testName);
         {
            Parser parser = new Parser();
            parser.setType(Parser.EXCEPTION_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(Constants.PUBLISH);
            parser.setSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            XmlBlasterException ex = new XmlBlasterException("QueueOverflow", "The destination queue is full");
            parser.addException(ex);

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            Parser receiver = new Parser();
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            //System.out.println("\nReceived: \n" + receiver.dump());
            String receive = toLiteral(receiver.createRawMsg());
            System.out.println("Received: \n|" + receive + "|");
            if (toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }
      

         testName = "Testing qos/key/content return value";
         System.out.println("\n----------------------\n"+testName);
         {
            Parser parser = new Parser();
            parser.setType(Parser.RESPONSE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(Constants.GET);
            //parser.setSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            MessageUnit msg = new MessageUnit("<key oid='hello'/>", "Hello world response".getBytes(), "<qos></qos>");
            parser.addMessage(msg);

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            Parser receiver = new Parser();
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            //System.out.println("\nReceived: \n" + receiver.dump());
            String receive = toLiteral(receiver.createRawMsg());
            System.out.println("Received: \n|" + receive + "|");
            if (toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }

         testName = "Testing a QoS return value";
         System.out.println("\n----------------------\n"+testName);
         {
            Parser parser = new Parser();
            parser.setType(Parser.RESPONSE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(Constants.ERASE);
            //parser.setSessionId("");
            parser.setChecksum(false);
            parser.setCompressed(false);
            parser.addQos("<qos><state>OK</state></qos>");

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            Parser receiver = new Parser();
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            //System.out.println("\nReceived: \n" + receiver.dump());
            String receive = toLiteral(receiver.createRawMsg());
            System.out.println("Received: \n|" + receive + "|");
            if (toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }


      }
      catch(Throwable e) {
         e.printStackTrace();
         Log.error("", e.toString());
      }
   }
}
/*
[19.03.2002 22:31:34 INFO  Tim] Success: Update #31000 received: 'Yeahh, i'm the new content number 30801', average messages/second = 462
java.lang.NumberFormatException: R
        at java.lang.Integer.parseInt(Integer.java:414)
        at java.lang.Integer.parseInt(Integer.java:463)
        at org.xmlBlaster.protocol.socket.Parser.toInt0(Parser.java:745)
        at org.xmlBlaster.protocol.socket.Parser.toByte(Parser.java:710)
        at org.xmlBlaster.protocol.socket.Parser.parse(Parser.java:559)
        at org.xmlBlaster.client.protocol.socket.SocketCallbackImpl.run(SocketCallbackImpl.java:102)
        at java.lang.Thread.run(Thread.java:484)
Exception found location=Parser.java:749
[19.03.2002 22:31:36 ERROR Parser.java:749-Parser] toInt0(R) Dumping from offset=196602, problemOffset=196622 msgLen=270530: 'riptio      3083**R**1162*publish*sessionId:192.168.1.2-null-1016573425420--608318242-3**LoadTestSub**0*LoadTestSub**0*L' ...
Exception found location=SocketCallbackImpl.java:112
[19.03.2002 22:31:36 ERROR SocketCallbackImpl.java:112-SocketCallbackImpl-] Closing connection to server: java.io.IOException: Format is corrupted 'msgLength=270530, checksum=false, compressed=false, type=73, byte4=0, byte5=0, version=1, requestId=Tim:137, methodName=update, sessionId=unknown, lenUnzipped=-1, checkSumResult=-1, index=0', expected integral value
*/

/*------------------------------------------------------------------------------
Name:      Parser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Parser class for raw socket messages
Version:   $Id: Parser.java,v 1.17 2002/03/13 19:46:18 ruff Exp $
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
 * Parser instances may be reused, but are not reentrant (Convert::index is currently a 'global' variable)
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

   public static final byte CHECKSUM_ADLER_BYTE = (byte)65; // 'A'
   public static final byte COMPRESSED_GZIP_BYTE = (byte)90; // 'Z'
   public static final byte INVOKE_BYTE = (byte)73; // INVOKE_TYPE = "I";
   public static final byte RESPONSE_BYTE = (byte)82; // RESPONSE_TYPE = "R";
   public static final byte EXCEPTION_BYTE = (byte)69; // EXCEPTION_TYPE = "E";
   public static final byte VERSION_1_BYTE = (byte)49;  // '1'
   private static final byte[] EMPTY10 = new String("          ").getBytes();;
   private static final byte NULL_BYTE = (byte)0;

   private long msgLength;

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
   private ByteArray byteArray = new ByteArray(126);


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
      msgLength = -1L;
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
      MessageUnit msg = new MessageUnit(key, null, qos);
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
      MessageUnit msg = new MessageUnit(e.reason, null, e.id);
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
      MessageUnit msg = new MessageUnit(null, null, qos);
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
         MessageUnit msg = new MessageUnit(null, null, qos[ii]);
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
    * This parses the raw message from an InputStream (typically from a socket).
    * Use the get...() methods to access the data.
    */
   public final void parse(InputStream in) throws IOException {

      initialize();

      if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Entering wait on inputStream");
      msgLength = toLong(in);
      if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Got first 10 bytes of total length=" + msgLength);

      if (msgLength == 10) {
         setMethodName(Constants.PING);
         return; // The shortest ping ever
      }

      index++;
      checksum = (in.read() > 0);

      index++;
      compressed = (in.read() > 0);

      index++;
      type = (byte)in.read();

      index++;
      byte4 = (byte)in.read();

      index++;
      byte5 = (byte)in.read();

      index++;
      version = in.read() - 48;

      // !!!! TODO Performance:
      // byte[] buf = new byte[msgLen];
      // while ((len=read(buf, msgLen) != -1) ...
      
      requestId = toString(in);
      methodName = toString(in);
      sessionId = toString(in);

      lenUnzipped = toLong0(in, -1);

      String qos = null;
      String xmlKey = null;
      byte[] content = null;
      for (int ii=0; ii<Integer.MAX_VALUE; ii++) {
         qos = toString(in);
         MessageUnit msgUnit = new MessageUnit(null, null, qos);
         addMessage(msgUnit);
         if (index >= msgLength) break;

         msgUnit.setKey(toString(in));
         if (index >= msgLength) break;

         if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Getting messageUnit content index=" + index);
         msgUnit.setContent(toByte(in));
         if (index >= msgLength) break;
      }

      if (checksum)
         checkSumResult = toLong0(in, -1);

      if (index != msgLength) {
         String str = "Format mismatch, read index=" + index + " expected message length=" + msgLength + " we need to disconnect the client, can't recover.";
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
         msgLength = out.size();
         byte[] msgLengthB = new String(""+msgLength).getBytes();
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
    * Converts max. 10 bytes from InputStream to a long. 
    * <br />
    * If a null is read before
    * 10 bytes are read, parsing is stopped there.
    */
   public final long toLong(InputStream in) throws IOException {
      byteArray.reset();
      for (int ii=0; ii<NUM_FIELD_LEN; ii++) {
         index++;
         int val = in.read();
         if (val == 0)
            break; // Field is terminated by null
         if (val == -1)
            throw new IOException("Can't read expected " + NUM_FIELD_LEN + " bytes from socket, only " + ii + " received");
         byteArray.write(val);
      }
      try {
         return Long.parseLong(byteArray.toString().trim());
      }
      catch (NumberFormatException e) {
         throw new IOException("Format is corrupted '" + byteArray.toString() + "', expected integral value");
      }
   }

   /**
    * Reads the binary content of a message. First we parse the long value which
    * holds the content length, than we retrieve the binary content. 
    */
   public final byte[] toByte(InputStream in) throws IOException {
      byteArray.reset();
      long len = toLong0(in, 0L);
      if (len >= Integer.MAX_VALUE) throw new IllegalArgumentException("Length of data is bigger " + Integer.MAX_VALUE + " bytes");
      byte[] b = new byte[(int)len];
      if (len == 0L)
         return b;
      {
         in.read(b, 0, (int)len);
         index += len;
      }
      return b;
   }

   /**
    * Converts bytes from InputStream until \0 to a long
    */
   public final long toLong0(InputStream in, long defaultVal) throws IOException {
      String tmp = toString(in);
      if (tmp == null || tmp.length() < 1)
         return defaultVal;
      try {
         return Long.parseLong(tmp.trim());
      }
      catch (NumberFormatException e) {
         throw new IOException("Format is corrupted '" + byteArray.toString() + "', expected integral value");
      }
   }

   /**
    * Extracts string until next null byte '\0'
    */
   public final String toString(InputStream in) throws IOException {
      byteArray.reset();
      for (int ii=0; ii<MAX_STRING_LEN; ii++) {
         index++;
         int val = in.read();
         if (val == 0)
            break; // end of string
         if (val == -1)
            throw new IOException("Can't read expected string '" + byteArray.toString()+ "' to its end");
         byteArray.write(val);
      }
      return byteArray.toString();
   }

   private String dump() {
      StringBuffer buffer = new StringBuffer(256);
      buffer.append("msgLength=" + msgLength);
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

/*------------------------------------------------------------------------------
Name:      Parser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Parser class for raw socket messages
Version:   $Id: Parser.java,v 1.8 2002/02/14 19:04:26 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

import java.util.Vector;

/**
 * Parser class for raw socket messages. 
 * <br />
 * This class creates and parses raw byte[] messages which can be used
 * to transfer over a socket connection.
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
 * @author ruff@swand.lake.de
 */
public class Parser extends Converter
{
   private static final String ME = "Parser";
   
   public static final String INVOKE_TYPE = "I";
   public static final String RESPONSE_TYPE = "R";
   public static final String EXCEPTION_TYPE = "E";

   public static final byte CHECKSUM_ADLER_BYTE = (byte)65; // 'A'
   public static final byte COMPRESSED_GZIP_BYTE = (byte)90; // 'Z'
   public static final byte INVOKE_BYTE = (byte)73; // INVOKE_TYPE = "I";
   public static final byte RESPONSE_BYTE = (byte)82; // RESPONSE_TYPE = "R";
   public static final byte EXCEPTION_BYTE = (byte)69; // EXCEPTION_TYPE = "E";
   public static final byte VERSION_1_BYTE = (byte)49;  // '1'
   private static final byte[] EMPTY10 = new String("          ").getBytes();;
   private static final byte NULL_BYTE = (byte)0;

   private long msgLength;

   /** flag fields 1 */
   private boolean checksum;
   /** flag fields 2 */
   private boolean compressed;
   /** flag fields 3 */
   private String type;
   /** flag fields 4 */
   private byte byte4;
   /** flag fields 5 */
   private byte byte5;
   /** flag fields 6 */
   private int version;

   private String requestId;
   private String methodName;
   private String sessionId;
   private long lenUnzipped;
   private long checkSumResult;

   /** Unique counter */
   private static long counter = 0L;

   private Vector msgVec;


   /**
    * The same parser object may be reused. 
    */
   public Parser() {
      msgVec = new Vector();
      initialize();
   }


   public Parser(String type, String requestId, String methodName, String sessionId) {
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
      super.index = 0L;
      msgLength = -1L;
      checksum = false;
      compressed = false;
      type = INVOKE_TYPE; // request
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

   /** Parser.INVOKE_TYPE */
   public void setType(String type) {
      this.type = type;
   }

   public boolean isInvoke() {
      return (INVOKE_TYPE.equals(type));
   }
   public boolean isResponse() {
      return (RESPONSE_TYPE.equals(type));
   }
   public boolean isException() {
      return (EXCEPTION_TYPE.equals(type));
   }

   /**
    * Set a unique ID (unique for this client), it
    * will be bounced back with the return value or with an
    * exception occurred during this request. 
    * <br />
    * Note that you usually shouldn't set this value, this class generates
    * a unique requestId which you can access with getRequestId()
    */
   public void setRequestId(String requestId) {
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
   public String createRequestId(String praefix) {
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
   public String getRequestId() {
      if (requestId == null) Log.error(ME, "getRequestId returns null");
      return this.requestId;
   }

   /** For example Constants.PUBLISH */
   public void setMethodName(String methodName) {
      this.methodName = methodName;
   }

   /**
    * For example Constants.PUBLISH
    * @return unchecked
    */
   public String getMethodName() {
      return this.methodName;
   }
   
   /** The authentication sessionId */
   public void setSessionId(String sessionId) {
      this.sessionId = sessionId;
   }
   
   /** The authentication sessionId */
   public String getSessionId() {
      return this.sessionId;
   }


   /** Enable checksum? */
   public void setChecksum(boolean checksum) {
      if (checksum == true) {
         Log.warn(ME, "Checksum for raw socket message is not supported");
         return;
      }
      this.checksum = checksum;
   }
   
   /** Compress message? */
   public void setCompressed(boolean compressed) {
      if (compressed == true) {
         Log.warn(ME, "Compression for raw socket message is not supported");
         return;
      }
      this.compressed = compressed;
   }

   /**
    * Use for methods connect, disconnect, ping.
    * <br />
    * Use for return value of methods connect, disconnect, ping, update, publish, subscribe, unSubscribe and erase
    * @exception IllegalArgumentException if invoked multiple times
    */
   public void addQos(String qos) {
      if (!msgVec.isEmpty())
         throw new IllegalArgumentException(ME+".addQos() may only be invoked once");
      MessageUnit msg = new MessageUnit(null, null, qos);
      msgVec.add(msg);
   }

   /**
    * Use for methods get, subscribe, unSubscribe, erase
    * @exception IllegalArgumentException if invoked multiple times
    */
   public void addKeyAndQos(String key, String qos) {
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
   public void addException(XmlBlasterException e) {
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
   public void addMessage(MessageUnit msg) {
      msgVec.add(msg);
   }

   /**
    * Returns all messages in a Vector
    */
   public Vector getMessages() {
      return msgVec;
   }

   /**
    * Returns all messages as an array
    */
   public MessageUnit[] getMessageArr() {
      if (msgVec.isEmpty()) return new MessageUnit[0];
      MessageUnit[] arr = new MessageUnit[msgVec.size()];
      for (int ii=0; ii<msgVec.size(); ii++) {
         arr[ii] = (MessageUnit)msgVec.elementAt(ii);
      }
      return arr;
   }

   /**
    * Response is usually only a QoS
    */
   public String getQos() {
      if (msgVec.isEmpty()) {
         Log.warn(ME, "getQos() is called without having a response");
         return "<qos></qos>";
      }
      MessageUnit msg = (MessageUnit)msgVec.elementAt(0);
      return msg.getQos();
   }

   /**
    * Response is usually only a QoS
    */
   public XmlBlasterException getException() {
      if (msgVec.isEmpty()) {
         Log.warn(ME, "getException() is called without having an exception");
         return new XmlBlasterException(ME, "Invalid exception");
      }
      MessageUnit msg = (MessageUnit)msgVec.elementAt(0);
      return new XmlBlasterException(msg.getQos(), msg.getXmlKey());
   }

   /**
    * This parses the raw message from an InputStream (typically from a socket).
    * Use the get...() methods to access the data.
    */
   public void parse(InputStream inputStream) throws XmlBlasterException {

      initialize();
      try {
         for (int ii=0; ii<20 && (inputStream.available() <= 0); ii++) {
            Log.warn(ME, "Client sends empty data, trying again after sleeping 10 milli ...");
            org.jutils.runtime.Sleeper.sleep(10); // On heavy logins, sometimes available() returns 0, but after sleeping it is OK
         }
         BufferedInputStream in = new BufferedInputStream(inputStream);

         msgLength = toLong(in);

         if (msgLength == 10) {
            setMethodName(Constants.PING);
            return; // The shortest ping ever
         }

         checksum = (readNext(in) > 0);
         compressed = (readNext(in) > 0);

         byte[] dummy = new byte[1];
         dummy[0] = (byte)readNext(in);
         type = new String(dummy);

         //type = Integer.toString(readNext(in)); Gives mit "82" instead of "R"

         byte4 = (byte)readNext(in);
         byte5 = (byte)readNext(in);

         version = readNext(in) - 48;
         
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

            msgUnit.setContent(toByte(in));
            if (index >= msgLength) break;
         }

         if (checksum)
            checkSumResult = toLong0(in, -1);

         if (index != msgLength) {
            String str = "Format mismatch, read index=" + index + " expected message length=" + msgLength;
            Log.error(ME, str + " we need to disconnect the client, can't recover.");
            throw new XmlBlasterException(ME, str);
         }
         in.close();
      }
      catch(IOException e) {
         e.printStackTrace();
         String text = "Received message corrupted and lost.";
         Log.warn(ME, text + " " + e.toString());
         throw new XmlBlasterException(ME, text);
      }
   }


   /**
    * Calculates the length of user data including null bytes and len field
    */
   private long getUserDataLen() {
      long len=0L;
      for (int ii=0; ii<msgVec.size(); ii++) {
         MessageUnit unit = (MessageUnit)msgVec.elementAt(ii);
         len += unit.size() + 3;   // three null bytes
         String tmp = ""+unit.getContent().length;
         len += tmp.length();
      }
      return len;
   }

   /**
    * TODO:
    * Move to Constants.java as soon as branch is merged to main trunc
    * @param method E.g. "publish", this is checked if a known method
    * @return true if method is known
    */
   public static boolean checkMethodName(String method) {
      if (Constants.GET.equals(method) ||
          Constants.ERASE.equals(method) ||
          Constants.PUBLISH.equals(method) ||
          Constants.SUBSCRIBE.equals(method) ||
          Constants.UNSUBSCRIBE.equals(method) ||
          Constants.UPDATE.equals(method) ||
          Constants.PING.equals(method) ||
          Constants.CONNECT.equals(method) ||
          Constants.DISCONNECT.equals(method))
          return true;
      return false;
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
    *  An example is ('*' mark a null byte):
    *
    *  "        83**I**17711*publish*oxf6hZs**<qos></qos>*<key oid='hello'/>*11*Hello world"
    *
    * </pre>
    */
   public byte[] createRawMsg() throws XmlBlasterException {

      if (checkMethodName(methodName) == false) {
         String str = "Can't send message, method '" + methodName + " is unknown";
         Log.error(ME, str);
         throw new IllegalArgumentException(ME + ": " + str);
      }

      try {
         long len = getUserDataLen() + 100;
         if (len > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Message size is limited to " + Integer.MAX_VALUE + " bytes");
         ByteArray out = new ByteArray((int)len);

         out.write(EMPTY10, 0, EMPTY10.length); // Reserve 10 bytes at the beginning ...

         out.write((checksum)?CHECKSUM_ADLER_BYTE:NULL_BYTE);    // 'A'
         out.write((compressed)?COMPRESSED_GZIP_BYTE:NULL_BYTE); // 'Z'
         if (isInvoke())
            out.write(INVOKE_BYTE);
         else if (isResponse())
            out.write(RESPONSE_BYTE);
         else if (isException())
            out.write(EXCEPTION_BYTE);
         else {
            if (Log.TRACE) Log.trace(ME, "Unknown type '" + type + "', setting to invoke.");
            out.write(INVOKE_BYTE); // INVOKE_TYPE = "I";
         }
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
         String text = "Sending message failed.";
         Log.warn(ME, text + " " + e.toString());
         throw new XmlBlasterException(ME, text);
      }
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
            parser.setType(Parser.INVOKE_TYPE);
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
            parser.setType(Parser.INVOKE_TYPE);
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
            parser.setType(Parser.INVOKE_TYPE);
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
            parser.setType(Parser.RESPONSE_TYPE);
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
            parser.setType(Parser.INVOKE_TYPE);
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
            parser.setType(Parser.EXCEPTION_TYPE);
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
            parser.setType(Parser.RESPONSE_TYPE);
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
            parser.setType(Parser.RESPONSE_TYPE);
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

/*------------------------------------------------------------------------------
Name:      Parser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Parser class for raw socket messages
Version:   $Id: Parser.java,v 1.3 2002/02/13 15:38:22 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.XmlBlasterImpl;
import org.xmlBlaster.engine.helper.MessageUnit;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

import java.util.Vector;

/**
 * Parser class for raw socket messages
 * @author ruff@swand.lake.de
 */
public class Parser extends Converter
{
   private static final String ME = "Parser";
   
   public static final String INVOKE_TYPE = "I";
   public static final String RESPONSE_TYPE = "R";
   public static final String EXCEPTION_TYPE = "E";
   
   private static final byte[] EMPTY10 = new String("          ").getBytes();;
   private static final byte NULL_BYTE = (byte)0;

   private long msgLength = -1L;

   // Read 6 flag fields
   private boolean checksum = false;
   private boolean compressed = false;
   private String type = new String(""); // request
   private byte byte4 = 0;
   private byte byte5 = 0;
   private int version = 1;

   private String requestId;
   private String methodName;
   private String sessionId;
   private long lenUnzipped = -1L;
   private long checkSumResult = -1L;

   private static long counter = 0L;

   private Vector msgVec = null;

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
    * Set a unique ID (unique for this client), it
    * will be bounced back with the return value or with an
    *   exception occurred during this request
    */
   public String getRequestId() {
      if (this.requestId == null || this.requestId.length() < 1) {
         synchronized(Parser.class) {
            if (this.counter >= (Long.MAX_VALUE-1L)) this.counter = 0L;
            this.counter++;
            this.requestId = ""+this.counter;
         }
      }
      return this.requestId;
   }

   /** For example XmlBlasterImpl.PUBLISH */
   public void setMethodName(String methodName) {
      this.methodName = methodName;
   }

   /**
    * For example XmlBlasterImpl.PUBLISH
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
      this.checksum = checksum;
   }
   
   /** Compress message? */
   public void setCompressed(boolean compressed) {
      this.compressed = compressed;
   }

   public void addMessage(MessageUnit msg) {
      if (msgVec == null) msgVec = new Vector();
      msgVec.add(msg);
   }

   /**
    */
   public void parse(InputStream inputStream) throws XmlBlasterException {

      index = 0L;
      try {
         for (int ii=0; ii<20 && (inputStream.available() <= 0); ii++) {
            Log.warn(ME, "Client sends empty data, trying again after sleeping 10 milli ...");
            org.jutils.runtime.Sleeper.sleep(10); // On heavy logins, sometimes available() returns 0, but after sleeping it is OK
         }
         BufferedInputStream in = new BufferedInputStream(inputStream);

         msgLength = toLong(in);

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
      if (msgVec == null) return len;
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
      if (XmlBlasterImpl.GET.equals(method) ||
          XmlBlasterImpl.ERASE.equals(method) ||
          XmlBlasterImpl.PUBLISH.equals(method) ||
          XmlBlasterImpl.SUBSCRIBE.equals(method) ||
          XmlBlasterImpl.UNSUBSCRIBE.equals(method) ||
          XmlBlasterImpl.UPDATE.equals(method) ||
          XmlBlasterImpl.PING.equals(method) ||
          XmlBlasterImpl.CONNECT.equals(method) ||
          XmlBlasterImpl.DISCONNECT.equals(method))
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
   public ByteArray createStream() throws XmlBlasterException {

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

         out.write((checksum)?(byte)65:0); // 'A'
         out.write((compressed)?(byte)90:0); // 'Z'
         if (isInvoke()) // INVOKE_TYPE = "I";
            out.write((byte)73);
         else if (isResponse()) // RESPONSE_TYPE = "R";
            out.write((byte)82);
         else if (isException()) // EXCEPTION_TYPE = "E";
            out.write((byte)69);
         else {
            Log.error(ME, "Unknown type '" + type + "', setting to invoke.");
            out.write((byte)73); // INVOKE_TYPE = "I";
         }
         out.write(NULL_BYTE); // byte4
         out.write(NULL_BYTE); // byte5
         out.write((byte)49);  // '1'

         out.write(getRequestId().getBytes());
         out.write(NULL_BYTE);

         out.write(getMethodName().getBytes());
         out.write(NULL_BYTE);

         out.write(getSessionId().getBytes());
         out.write(NULL_BYTE);

         if (lenUnzipped > 0)
            out.write(new String(""+lenUnzipped).getBytes());
         out.write(NULL_BYTE);

         if (XmlBlasterImpl.PUBLISH.equals(methodName) || XmlBlasterImpl.UPDATE.equals(methodName)) {
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
         return out;
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
         {
            Parser parser = new Parser();
            parser.setType(Parser.INVOKE_TYPE);
            parser.setRequestId("7711");
            parser.setMethodName(XmlBlasterImpl.PUBLISH);
            parser.setSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            MessageUnit msg = new MessageUnit("<key oid='hello'/>", "Hello world".getBytes(), "<qos></qos>");
            parser.addMessage(msg);

            ByteArray out = parser.createStream();
            rawMsg = out.toByteArray();
            String send = toLiteral(rawMsg);
            System.out.println("Created and ready to send: \n>" + send + "<");
         }

         {
            Parser receiver = new Parser();
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            //System.out.println("\nReceived: \n" + receiver.dump());
            ByteArray outReceive = receiver.createStream();
            String receive = toLiteral(outReceive.toByteArray());
            System.out.println("Received: \n>" + receive + "<");
            if (toLiteral(rawMsg).equals(receive))
               System.out.println("SUCCESS");
            else
               System.out.println("FAILURE");
         }
      }
      catch(Throwable e) {
         e.printStackTrace();
         Log.error("", e.toString());
      }
   }
}

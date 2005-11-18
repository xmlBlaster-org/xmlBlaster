/*------------------------------------------------------------------------------
Name:      XbfParser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XbfParser class for raw socket messages
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.xbformat;

import org.jutils.log.LogChannel;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.MsgUnitRaw;

import java.io.IOException;
import java.io.InputStream;

/**
 * XbfParser class for raw socket/email messages. 
 * <br />
 * This class creates and parses raw byte[] messages which can be used
 * to transfer over a socket connection.
 * <br />
 * XbfParser instances may be reused, but are NOT reentrant (there are many 'global' variables)
 * <br />
 * Please read the requirement specification
 * <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">protocol.socket</a>
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
 *  |        76**E**17711*get*oxf6hZs**XbfParser*An XmlBlasterException test only*0*|
 *
 *  Testing qos/key/content return value
 *  |        85**R**17711*publish***<qos></qos>*<key oid='hello'/>*20*Hello world response|
 *
 *  Testing a QoS return value
 *  |        58**R**17711*get***<qos><state id='OK'/></qos>**0*|
 *
 *  Testing two qos/key/content
 *  |       100**I**17711*publish*oxf6hZs**<qos/>*<key oid='x1'/>*6*Hello1<qos/>*<key oid='x2'/>*6*Hello2|
 * </pre>
 *
 * @author xmlBlaster@marcelruff.info
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 */
public class XbfParser implements I_MsgInfoParser
{
   /* TODO: Try performance with
         b[i*2] = (byte)(c & 0xff);
         b[i*2 + 1] = (byte)((c >> 8) & 0xff);
     to cast char[] into byte[]
   */

   private static final String ME = "xbformat.XbfParser";
   private Global glob;

   private LogChannel log;

   public static final int NUM_FIELD_LEN = 10;
   public static final int FLAG_FIELD_LEN = 6;
   public static final int MAX_STRING_LEN = Integer.MAX_VALUE;
   public static final String EMPTY_STRING = "";

   public static final byte CHECKSUM_ADLER_BYTE = (byte)65; // 'A'
   public static final byte COMPRESSED_GZIP_BYTE = (byte)90; // 'Z'
   public static final byte VERSION_1_BYTE = (byte)49;  // '1'
   private static final byte[] EMPTY10 = new String("          ").getBytes();
   private static final byte NULL_BYTE = (byte)0;
   
   public static final String XBFORMAT_EXTENSION = ".xbf";
   public static final String XBFORMAT_ZLIB_EXTENSION = ".xbfz";
   public static final String XBFORMAT_MIMETYPE = "application/xmlBlaster-xbf";
   public static final String XBFORMAT_ZLIB_MIMETYPE = "application/xmlBlaster-xbfz";


   // create only once, for low level parsing
   //private ByteArray byteArray = new ByteArray(256);
   private Buf buf;
   private byte[] first10;
   private long lenUnzipped;
   private long checkSumResult;
   
   private int maxMsgLength = Integer.MAX_VALUE; // TODO: Set by environment or calulate by LowMemoryDetector.java physical memory (expects JDK 1.5)

   /** If not null somebody wants to be notified about the current bytes send over socket */
   private I_ProgressListener progressListener;
   
   static {
      MsgInfoParserFactory.instance().register(XBFORMAT_EXTENSION, XbfParser.class.getName());
      MsgInfoParserFactory.instance().register(XBFORMAT_ZLIB_EXTENSION, XbfParser.class.getName());
      MsgInfoParserFactory.instance().register(XBFORMAT_MIMETYPE, XbfParser.class.getName());
      MsgInfoParserFactory.instance().register(XBFORMAT_ZLIB_MIMETYPE, XbfParser.class.getName());
   }

   public XbfParser() {
      //initialize();
   }

   public void init(Global glob, I_ProgressListener progressListener,
         I_PluginConfig pluginConfig) {
      this.glob = glob;
      this.log = glob.getLog("xfb");
      this.progressListener = progressListener;
      //this.someConfig = glob.get("someConfig", (String)null, null, pluginConfig);
   }
   
   private void initialize() {
      this.buf = new Buf();
      this.first10 = new byte[NUM_FIELD_LEN];
   }

   /**
    * @param isCompressed true/false
    * @return XBFORMAT_MIMETYPE = "application/xmlBlaster-xbf";
    *         XBFORMAT_ZLIB_MIMETYPE = "application/xmlBlaster-xbfz";
    */
   public final String getMimetype(boolean isCompressed) {
      return (isCompressed) ? XBFORMAT_ZLIB_MIMETYPE : XBFORMAT_MIMETYPE; 
   }

   /**
    * @param isCompressed true/false
    * @return XBFORMAT_EXTENSION = ".xbf";
    *         XBFORMAT_ZLIB_EXTENSION = ".xbfz";
    */
   public final String getExtension(boolean isCompressed) {
      return (isCompressed) ? XBFORMAT_ZLIB_EXTENSION : XBFORMAT_EXTENSION; 
   }

   /**
    * Blocks on socket until a complete message is read.
    * @return A complete message in a byte[].
    *         NOTE: The first 10 bytes are not initialized.<br />
    *         null: An empty message which only contains the header 10 bytes
    */
   private final Buf readOneMsg(MsgInfo msgInfo, InputStream in) throws IOException
   {
      if (log.TRACE) log.trace(ME, "Entering readOneMsg(), waiting on inputStream");

      // First we extract the first 10 bytes to get the msgLength ...
      int remainLength = NUM_FIELD_LEN;
      int lenRead;
      int msgLength = 0;
      I_ProgressListener listener = null;
      synchronized (in) {
         {
            int off = 0;
            while ((lenRead = in.read(first10, off, remainLength)) != -1) {
               remainLength -= lenRead;
               if (remainLength == 0) break;
               off += lenRead;
               //log.info(ME, "Receive: lenRead=" + lenRead + " off=" + off + " remainLength=" + remainLength);
            }
         }

         if (lenRead == -1)
            // if (sock.isClosed()) // since JDK 1.4
            // throw new IOException("Can't read message header (first 10 bytes) from socket, message is corrupted");
            throw new IOException(ME + ": Got EOF, lost socket connection");

         try {
            msgLength = Integer.parseInt((new String(first10, 0, NUM_FIELD_LEN)).trim());
         }
         catch (NumberFormatException e) {
            throw new IOException(ME + ": Format of xbf-message header is corrupted '" + new String(first10) + "', expected integral value");
         }

         listener = this.progressListener;
         if (listener != null) {
            listener.progressRead("", 10, msgLength);
         }

         if (log.TRACE) log.trace(ME, "Got first 10 bytes of total length=" + msgLength);
         if (msgLength == NUM_FIELD_LEN)
            return null; // An empty message only contains the header 10 bytes
         else if (msgLength < (NUM_FIELD_LEN+FLAG_FIELD_LEN))
            throw new IOException(ME + ": Message format is corrupted, the given message length=" + msgLength + " is invalid");

         if (msgLength > maxMsgLength) {
            throw new IOException(ME + ": Message format is corrupted, the given message length=" + msgLength + " would produce an OutOfMemory");
         }

         // Now we know the msgLength, lets extract the complete message ...
         if (buf.buf == null || buf.buf.length != msgLength) {
            buf.buf = null;
            try {
               buf.buf = new byte[msgLength];
            }
            catch (OutOfMemoryError e) {
               throw new IOException(ME + ": Message format is corrupted, the given message length=" + msgLength + " produces:" + e.toString());
            }
            buf.offset = 0;
         }
         buf.offset = NUM_FIELD_LEN;
         remainLength = msgLength - buf.offset;
         while ((lenRead = in.read(buf.buf, buf.offset, remainLength)) != -1) {
            remainLength -= lenRead;

            listener = this.progressListener;
            if (listener != null) {
               listener.progressRead("", 10, msgLength);
            }
            
            if (remainLength == 0) break;
            buf.offset += lenRead;
            //log.info(ME, "Receive: lenRead=" + lenRead + " buf.offset=" + buf.offset + " remainLength=" + remainLength);
         }
      }

      if (lenRead == -1)
         throw new IOException(ME + ": Can't read complete message (" + msgLength + " bytes) from socket, only " + remainLength + " received, message is corrupted");

      if (remainLength != 0) // assert
         throw new IOException(ME + ": Internal error, can't read complete message (" + msgLength + " bytes) from socket, only " + remainLength + " received, message is corrupted");

      listener = this.progressListener;
      if (listener != null) {
         listener.progressRead("", msgLength, msgLength);
      }
      return buf;
   }

   /**
    * This parses the raw message from an InputStream (typically from a socket).
    * Use the get...() methods to access the data.
    * <p />
    * This method blocks until a message arrives
    */
   public final MsgInfo parse(InputStream in) throws  IOException, IllegalArgumentException {
      if (log.CALL) log.call(ME, "Entering parse()");
      MsgInfo msgInfo = new MsgInfo(this.glob);
      msgInfo.setMsgInfoParser(this);
      initialize();

      Buf buf = readOneMsg(msgInfo, in); // blocks until one message is read

      if (buf == null) {
         msgInfo.setMethodName(MethodName.PING);
         return msgInfo; // The shortest ping ever
      }

      if (log.DUMP) log.dump(ME, "Raw message of length " + buf.buf.length + " received >" + toLiteral(buf.buf) + "<");

      msgInfo.setChecksum(buf.buf[NUM_FIELD_LEN] > 0);
      if (msgInfo.isChecksum()) {
         log.warn(ME, "Ignoring checksum flag");
      }
      msgInfo.setCompressed(buf.buf[NUM_FIELD_LEN+1] > 0);
      if (msgInfo.isCompressed()) {
         log.warn(ME, "Ignoring compress flag");
      }
      msgInfo.setType(buf.buf[NUM_FIELD_LEN+2]);
      msgInfo.setByte4(buf.buf[NUM_FIELD_LEN+3]);
      msgInfo.setByte5(buf.buf[NUM_FIELD_LEN+4]);
      msgInfo.setVersion(buf.buf[NUM_FIELD_LEN+5] - 48);
      if (msgInfo.getVersion() != 1) {
         log.warn(ME, "Ignoring version=" + msgInfo.getVersion() + " on 1 is supported");
      }

      buf.offset = NUM_FIELD_LEN+FLAG_FIELD_LEN;

      msgInfo.setRequestId(toString(buf));
      msgInfo.setMethodName(MethodName.toMethodName(toString(buf)));
      msgInfo.setSecretSessionId(toString(buf));

      lenUnzipped = toInt0(buf, -1);
      if (lenUnzipped != -1) {
         if (log.TRACE) log.trace(ME, "Ignoring given unzipped message length of size " + lenUnzipped);
      }

      String qos = null;
      for (int ii=0; ii<Integer.MAX_VALUE; ii++) {
         qos = toString(buf);
         if (buf.offset >= buf.buf.length) {
            if (qos.length() > 0) {
               MsgUnitRaw msgUnit = new MsgUnitRaw(null, (byte[])null, qos);
               msgInfo.addMessage(msgUnit);
            }
            break;
         }

         String key = toString(buf);
         if (buf.offset >= buf.buf.length) {
            MsgUnitRaw msgUnit = new MsgUnitRaw(key, (byte[])null, qos);
            msgInfo.addMessage(msgUnit);
            break;
         }

         if (log.TRACE) log.trace(ME, "Getting messageUnit #" + ii);
         MsgUnitRaw msgUnit = new MsgUnitRaw(key, toByte(buf), qos);
         msgInfo.addMessage(msgUnit);

         if (buf.offset >= buf.buf.length) break;
      }

      if (msgInfo.isChecksum())
         checkSumResult = toLong0(buf, -1);

      if (buf.offset != buf.buf.length) {
         String str = "Format mismatch, read index=" + buf.offset + " expected message length=" + buf.buf.length + " we need to disconnect the client, can't recover.";
         throw new IOException(ME + ": " + str);
      }

      if (log.TRACE) log.trace(ME, "Leaving parse(), message successfully parsed");
      return msgInfo;
   }

   /**
    * Returns a raw data string.
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
   public final byte[] createRawMsg(MsgInfo msgInfo) throws XmlBlasterException {

      try {
         long len = msgInfo.getUserDataLen() + 500;
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
         out.write((msgInfo.isChecksum())?CHECKSUM_ADLER_BYTE:NULL_BYTE);    // 'A'
         out.write((msgInfo.isCompressed())?COMPRESSED_GZIP_BYTE:NULL_BYTE); // 'Z'
         out.write(msgInfo.getType()); // 'I' or 'R' or 'E'
         out.write(NULL_BYTE);       // byte4
         out.write(NULL_BYTE);       // byte5
         out.write(VERSION_1_BYTE);  // '1'

         out.write(msgInfo.createRequestId(null).getBytes());
         out.write(NULL_BYTE);

         out.write(msgInfo.getMethodName().getMethodNameBytes());
         out.write(NULL_BYTE);

         out.write(msgInfo.getSecretSessionId().getBytes());
         out.write(NULL_BYTE);

         if (lenUnzipped > 0)
            out.write(new String(""+lenUnzipped).getBytes());
         out.write(NULL_BYTE);

         for (int ii=0; ii<msgInfo.getMessages().size(); ii++) {
            MsgUnitRaw unit = (MsgUnitRaw)msgInfo.getMessages().elementAt(ii);
            out.write(unit.getQos().getBytes());
            out.write(NULL_BYTE);
            out.write(unit.getKey().getBytes());
            out.write(NULL_BYTE);
            out.write((""+unit.getContent().length).getBytes());
            out.write(NULL_BYTE);
            out.write(unit.getContent());
         }

         if (msgInfo.isChecksum() == true) {
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
         log.warn(ME, text + " " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, text, e);
      }
   }

   /**
    * Reads the binary content of a message. First we parse the long value which
    * holds the content length, than we retrieve the binary content.
    */
   public final byte[] toByte(Buf buf) throws IOException {
      int len = toInt0(buf, 0);
      byte[] b = new byte[len];
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
         log.error(ME, "toLong0(" + niceAndShort(tmp) + ") " + buf.toLiteral());
         throw new IOException(ME + ": Format is corrupted '" + toString() + "', expected long integral value");
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
         log.error(ME, "toInt0(" + niceAndShort(tmp) + ") " + buf.toLiteral());
         throw new IOException(ME + ": Format is corrupted '" + toString() + "', expected integral value");
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
   private final String toString(Buf buf) throws IOException  {
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
      //if (buf.offset == buf.buf.length)
      //   return EMPTY_STRING;

      return new String(buf.buf, startOffset, buf.offset-startOffset);
   }

   /**
    * Get the raw messages as a string, for tests and for dumping only
    * @return The stringified message, null bytes are replaced by '*'
    */
   public final String toLiteral(MsgInfo msgInfo) throws XmlBlasterException {
      return createLiteral(createRawMsg(msgInfo));
   }

   public final String toLiteral(byte[] arr) {
      return createLiteral(arr);
   }
   
   /**
    * Get the raw messages as a string, for tests and for dumping only
    * @return The stringified message, null bytes are replaced by '*'
    */
   public static final String createLiteral(byte[] arr) {
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

   /**
    * java org.xmlBlaster.util.xbformat.XbfParser
    * See: java org.xmlBlaster.util.xbformat.MsgInfo
    */
   /*
   public static void main( String[] args ) {
      try {
         Global glob = new Global(args);
         byte[] rawMsg = null;
         String testName;

         testName = "Testing qos/key/content";
         System.out.println("\n----------------------\n"+testName);
         try {
            XbfParser parser = new XbfParser(glob);
            parser.setType(XbfParser.INVOKE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(MethodName.PUBLISH);
            parser.setSecretSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            MsgUnitRaw msg = new MsgUnitRaw("<key oid='hello'/>", "Hello world".getBytes(), "<qos></qos>");
            parser.addMessage(msg);

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         catch (XmlBlasterException e) {
            System.out.println(e.getMessage());
         }
         {
            XbfParser receiver = new XbfParser(glob);
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
         try {
            XbfParser parser = new XbfParser(glob);
            parser.setType(XbfParser.INVOKE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(MethodName.PUBLISH);
            parser.setSecretSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            parser.addMessage(new MsgUnitRaw("<key oid='x1'/>", "Hello1".getBytes(), "<qos/>"));
            parser.addMessage(new MsgUnitRaw("<key oid='x2'/>", "Hello2".getBytes(), "<qos/>"));
            //parser.addMessage(new MsgUnitRaw("<key oid='x3'/>", "Hello3".getBytes(), "<qos/>"));
            //parser.addMessage(new MsgUnitRaw("<key oid='x4'/>", "Hello4".getBytes(), "<qos/>"));

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         catch (XmlBlasterException e) {
            System.out.println(e.getMessage());
         }
         {
            XbfParser receiver = new XbfParser(glob);
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
            XbfParser parser = new XbfParser(glob);
            parser.setType(XbfParser.INVOKE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(MethodName.GET);
            parser.setSecretSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            parser.addKeyAndQos("<key oid='ooo'></key>", "<qos></qos>");

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            XbfParser receiver = new XbfParser(glob);
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
            XbfParser parser = new XbfParser(glob);
            parser.setType(XbfParser.RESPONSE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(MethodName.PUBLISH);
            parser.setSecretSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            parser.addQos("<qos/>");

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            XbfParser receiver = new XbfParser(glob);
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            if (receiver.getMessageArr().length != 1) {
               System.out.println(testName + ": FAILURE numMsg=" + receiver.getMessageArr().length);
            }
            //System.out.println("\nReceived: \n" + receiver.dump());
            String receive = toLiteral(receiver.createRawMsg());
            System.out.println("Received: \n|" + receive + "|");
            if (toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE rawMsg sent=" + toLiteral(rawMsg));
         }

         testName = "Testing nothing";
         System.out.println("\n----------------------\n"+testName);
         {
            XbfParser parser = new XbfParser(glob);
            parser.setType(XbfParser.INVOKE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(MethodName.GET);
            parser.setSecretSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            XbfParser receiver = new XbfParser(glob);
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            if (receiver.getMessageArr().length > 0) {
               System.out.println(testName + ": FAILURE msgLength=" + receiver.getMessageArr().length + " dump=" + receiver.getMessageArr()[0].toXml());
            }
            else {
               //System.out.println("\nReceived: \n" + receiver.dump());
               String receive = toLiteral(receiver.createRawMsg());
               System.out.println("Received: \n|" + receive + "|");
               if (toLiteral(rawMsg).equals(receive))
                  System.out.println(testName + ": SUCCESS");
               else
                  System.out.println(testName + ": FAILURE");
            }
         }

         testName = "Testing really nothing";
         System.out.println("\n----------------------\n"+testName);
         {
            rawMsg = "        10".getBytes();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            XbfParser receiver = new XbfParser(glob);
            receiver.setSecretSessionId(null);
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            receiver.parse(in);
            if (receiver.getMessageArr().length > 0) {
               System.out.println(testName + ": FAILURE");
            }
            else {
               //System.out.println("\nReceived: \n" + receiver.dump());
               String receive = toLiteral(receiver.createRawMsg());
               System.out.println("Received: \n|" + receive + "|");
               if ("        25**I**11*ping***".equals(receive))
                  System.out.println(testName + ": SUCCESS");
               else
                  System.out.println(testName + ": FAILURE");
            }
         }



         testName = "Testing XmlBlasterException";
         System.out.println("\n----------------------\n"+testName);
         {
            XbfParser parser = new XbfParser(glob);
            parser.setType(XbfParser.EXCEPTION_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(MethodName.PUBLISH);
            parser.setSecretSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            XmlBlasterException ex = new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, "QueueOverflow", "The destination queue is full");
            parser.addException(ex);

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            XbfParser receiver = new XbfParser(glob);
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
         try {
            XbfParser parser = new XbfParser(glob);
            parser.setType(XbfParser.RESPONSE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(MethodName.GET);
            //parser.setSecretSessionId("oxf6hZs");
            parser.setChecksum(false);
            parser.setCompressed(false);
            MsgUnitRaw msg = new MsgUnitRaw("<key oid='hello'/>", "Hello world response".getBytes(), "<qos></qos>");
            parser.addMessage(msg);

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         catch (XmlBlasterException e) {
            System.out.println(e.getMessage());
         }
         {
            XbfParser receiver = new XbfParser(glob);
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
            XbfParser parser = new XbfParser(glob);
            parser.setType(XbfParser.RESPONSE_BYTE);
            parser.setRequestId("7711");
            parser.setMethodName(MethodName.ERASE);
            //parser.setSecretSessionId("");
            parser.setChecksum(false);
            parser.setCompressed(false);
            parser.addQos("<qos><state id='OK'/></qos>");

            rawMsg = parser.createRawMsg();
            String send = toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            XbfParser receiver = new XbfParser(glob);
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
         System.err.println(e.toString());
      }
   }
   */
}

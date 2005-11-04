/*------------------------------------------------------------------------------
Name:      MsgInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   MsgInfo class for raw socket messages
Version:   $Id: MsgInfo.java 13866 2005-10-25 08:39:33Z ruff $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.xbformat;

import org.jutils.log.LogChannel;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnitRaw;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

import java.util.Vector;

/**
 * Holds MsgUnits from socket or email protocol drivers with additional tranport attributes. 
 * <br />
 * This class creates and parses raw byte[] messages which can be used
 * to transfer over a socket connection or as email attachment.
 * <br />
 * MsgInfo instances may be reused, but are NOT reentrant (there are many 'global' variables)
 * <br />
 * @author xmlBlaster@marcelruff.info
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 */
public class MsgInfo
{
   private static final String ME = "MsgInfo";
   private final Global glob;
   private final LogChannel log;

   public static final byte INVOKE_BYTE = (byte)73; // INVOKE_TYPE = "I";
   public static final byte RESPONSE_BYTE = (byte)82; // RESPONSE_TYPE = "R";
   public static final byte EXCEPTION_BYTE = (byte)69; // EXCEPTION_TYPE = "E";

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
   private MethodName methodName;
   private String sessionId;
   
   private I_ProgressListener progressListener;

   /** Unique counter */
   private static long counter = 0L;

   /** Holding MsgUnitRaw objects which acts as a holder for the method arguments */
   private Vector msgVec;
   
   private I_MsgInfoParser msgInfoParser;

   /**
    * The same instance object may be reused.
    * Ctor to parse messages using I_MsgInfoParser implementations.
    */
   public MsgInfo(Global glob) {
      this(glob, (byte)0, (String)null, (MethodName)null, (String)null, null);
   }

   /**
    * Ctor to parse messages with msgInfo.parse(iStream);
    */
   public MsgInfo(Global glob, I_ProgressListener progressListener) {
      this(glob, (byte)0, (String)null, (MethodName)null, (String)null, progressListener);
   }

   /**
    * Create a raw message. 
    * msgInfo = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.UPDATE, cbSessionId, progressListener);
    * msgInfo.addMessage(msgArr);
    * byte[] rawMsg = msgInfo.createRawMsg();
    * @param glob
    * @param type
    * @param methodName
    * @param sessionId
    */
   public MsgInfo(Global glob, byte type, MethodName methodName, String sessionId) {
      this(glob, type, (String)null, methodName, sessionId, null);
   }

   public MsgInfo(Global glob, byte type, MethodName methodName, String sessionId, I_ProgressListener progressListener) {
      this(glob, type, (String)null, methodName, sessionId, progressListener);
   }

   public MsgInfo(Global glob, byte type, String requestId, MethodName methodName,
                 String sessionId, I_ProgressListener progressListener) {
      this.glob = glob;
      this.log = glob.getLog("core");
      this.progressListener = progressListener;
      this.msgVec = new Vector();
      initialize();
      setType(type);
      setRequestId(requestId);
      setMethodName(methodName);
      setSecretSessionId(sessionId);
   }


   /**
    * This method allows to reuse a MsgInfo instance.
    */
   public void initialize() {
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
      msgVec.clear();
   }

   public int getNumMessages()
   {
      return msgVec.size();
   }

   /**
    * @param type The method type, e.g. MsgInfo.INVOKE_BYTE
    */
   public final void setType(byte type) {
      this.type = type;
   }

   /**
    * @return The method type, e.g. MsgInfo.INVOKE_BYTE
    */
   public final byte getType() {
      return this.type;
   }

   /**
    * Similar to getType() but returns a nice human readable string for logging output
    */
   public final String getTypeStr() {
      if (isInvoke())
         return "INVOKE";
      else if (isResponse())
         return "RESPONSE";
      else if (isException())
         return "EXCEPTION";
      return "UNKNOWN_TYPE";
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
    * @param prefix If desired you can specify a prefix for the request ID, e.g. "joe:"
    * @return An ID (unique in this JVM scope), e.g. "joe:3400" or "3400" if prefix is null
    */
   public final String createRequestId(String prefix) {
      if (this.requestId == null || this.requestId.length() < 1) {
         if (prefix == null) prefix = "";
         synchronized(MsgInfo.class) {
            if (counter >= (Long.MAX_VALUE-1L)) counter = 0L;
            counter++;
            this.requestId = prefix+counter;
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

   /** For example MethodName.PUBLISH */
   public final void setMethodName(MethodName methodName) {
      this.methodName = methodName;
   }

   /**
    * For example MethodName.PUBLISH
    * @return unchecked
    */
   public final MethodName getMethodName() {
      return this.methodName;
   }

   /** The authentication sessionId */
   public final void setSecretSessionId(String sessionId) {
      this.sessionId = sessionId;
   }

   /** The authentication sessionId */
   public final String getSecretSessionId() {
      if (sessionId == null) return "";
      return this.sessionId;
   }


   /** Enable checksum? */
   public final void setChecksum(boolean checksum) {
      if (checksum == true) {
         log.warn(ME, "Checksum for raw socket message is not supported");
         return;
      }
      this.checksum = checksum;
   }

   /**
    * Compress message?
    * NOTE: This compressed flag is set if the SOCKET header is plain text
    * and the MsgUnit[] is compressed. This mode is not implemented,
    * as we have "zlib:stream" compression which compresses the whole socket input/output
    * stream (there is no need to set this flag as it is compressed as well).
    */
   public final void setCompressed(boolean compressed) {
      if (compressed == true) {
         log.warn(ME, "Compression for raw socket message is not supported");
         return;
      }
      this.compressed = compressed;
   }

   /**
    * Use for methods get, subscribe, unSubscribe, erase
    * @exception IllegalArgumentException if invoked multiple times
    */
   public final void addKeyAndQos(String key, String qos) throws XmlBlasterException {
      if (!msgVec.isEmpty())
         throw new IllegalArgumentException(ME+".addKeyAndQos() may only be invoked once");
      MsgUnitRaw msg = new MsgUnitRaw(key, (byte[])null, qos);
      msgVec.add(msg);
   }

   /**
    * Use for exception message
    * <br />
    * NOTE: Exceptions don't return
    * @exception IllegalArgumentException if invoked multiple times
    */
   public final void addException(XmlBlasterException e) throws XmlBlasterException {
      if (!msgVec.isEmpty())
         throw new IllegalArgumentException(ME+".addException() may only be invoked once");
      MsgUnitRaw msg = new MsgUnitRaw(e.getMessage(), e.toByteArr(), e.getErrorCodeStr());
      msgVec.add(msg);
   }

   /**
    * Use for methods update, publish.
    * <br />
    * Use for return value of method get.
    * <br />
    * Multiple adds are OK
    */
   public final void addMessage(MsgUnitRaw msg) {
      msgVec.add(msg);
   }

   public final void removeMessage(MsgUnitRaw msg) {
      msgVec.remove(msg);
   }

   /**
    * Use for methods update, publish.
    * <br />
    * Use for return value of method get.
    * <br />
    * Multiple adds are OK
    */
   public final void addMessage(MsgUnitRaw[] arr) {
      for (int ii=0; ii<arr.length; ii++)
         msgVec.add(arr[ii]);
   }

   /**
    * Add a QoS value, use for methods connect, disconnect, ping.
    * <br />
    * Use for return value of methods connect, disconnect, ping, update, publish, subscribe, unSubscribe and erase
    * @exception IllegalArgumentException if invoked multiple times
    */
   public final void addMessage(String qos) throws XmlBlasterException {
      if (!msgVec.isEmpty())
         throw new IllegalArgumentException(ME+".addQos() may only be invoked once");
      MsgUnitRaw msg = new MsgUnitRaw(null, (byte[])null, qos);
      msgVec.add(msg);
   }

   /** @see #addMessage(String qos) */
   public final void addQos(String qos) throws XmlBlasterException {
      addMessage(qos);
   }

   /**
    * Add a QoS array value.
    * <br />
    * Use for return value of methods publishArr and erase
    * @exception IllegalArgumentException if invoked multiple times
    */
   public final void addMessage(String[] qos) throws XmlBlasterException {
      if (!msgVec.isEmpty())
         throw new IllegalArgumentException(ME+".addQos() may only be invoked once");
      for (int ii=0; ii<qos.length; ii++) {
         MsgUnitRaw msg = new MsgUnitRaw(null, (byte[])null, qos[ii]);
         msgVec.add(msg);
      }
   }

   /** @see #addMessage(String[] qos) */
   public final void addQos(String[] qos) throws XmlBlasterException {
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
   public final MsgUnitRaw[] getMessageArr() {
      if (msgVec.isEmpty()) return new MsgUnitRaw[0];
      MsgUnitRaw[] arr = new MsgUnitRaw[msgVec.size()];
      for (int ii=0; ii<msgVec.size(); ii++) {
         arr[ii] = (MsgUnitRaw)msgVec.elementAt(ii); // JDK 1.1 compatible
      }
      return arr;
   }

   /**
    * Response is usually only a QoS
    * @exception IllegalArgumentException if there is no QoS to get
    */
   public final String getQos() {
      /* OK for empty get() return
      if (msgVec.isEmpty()) {
         throw new IllegalArgumentException(ME + ": getQos() is called without having a response");
      }
      */
      if (msgVec.isEmpty()) {
         return null;
      }
      MsgUnitRaw msg = (MsgUnitRaw)msgVec.elementAt(0);
      return msg.getQos();
   }

   /**
    * Response is usually only a QoS
    * @exception IllegalArgumentException if there is no QoS to get
    */
   public final String[] getQosArr() {
      Vector msgs = getMessages();
      String[] strArr = new String[msgs.size()];
      for (int ii=0; ii<strArr.length; ii++) {
         strArr[ii] = ((MsgUnitRaw)msgs.elementAt(ii)).getQos();
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
      MsgUnitRaw msg = (MsgUnitRaw)msgVec.elementAt(0);
      return XmlBlasterException.parseByteArr(glob, msg.getContent());
   }

   /**
    * Calculates the length of user data including null bytes and len field
    * @exception IllegalArgumentException Message size is limited to Integer.MAX_VALUE bytes
    */
   long getUserDataLen() {
      long len=0L;
      for (int ii=0; ii<msgVec.size(); ii++) {
         MsgUnitRaw unit = (MsgUnitRaw)msgVec.elementAt(ii);
         len += unit.size() + 3;   // three null bytes
         String tmp = ""+unit.getContent().length;
         len += tmp.length();
      }
      if (len > Integer.MAX_VALUE)
         throw new IllegalArgumentException(ME + ": Message size is limited to " + Integer.MAX_VALUE + " bytes");
      return len;
   }


   /**
    * @param byte4 The byte4 to set.
    */
   public void setByte4(byte byte4) {
      this.byte4 = byte4;
   }

   /**
    * @param byte5 The byte5 to set.
    */
   public void setByte5(byte byte5) {
      this.byte5 = byte5;
   }

   /**
    * @return Returns the version.
    */
   public int getVersion() {
      return this.version;
   }

   /**
    * @param version The version to set.
    */
   public void setVersion(int version) {
      this.version = version;
   }

   /**
    * @return Returns the sessionId.
    */
   public String getSessionId() {
      return this.sessionId;
   }

   /**
    * @param sessionId The sessionId to set.
    */
   public void setSessionId(String sessionId) {
      this.sessionId = sessionId;
   }

   /**
    * @return Returns the checksum.
    */
   public boolean isChecksum() {
      return this.checksum;
   }

   /**
    * @return Returns the compressed.
    */
   public boolean isCompressed() {
      return this.compressed;
   }

   /**
    * @return Returns the msgInfoParser.
    */
   public I_MsgInfoParser getMsgInfoParser() {
      if (this.msgInfoParser == null) {
         this.msgInfoParser = new XbfParser();
         this.msgInfoParser.init(this.glob, this.progressListener);
      }
      return this.msgInfoParser;
   }

   /**
    * @param msgInfoParser The msgInfoParser to set.
    */
   public void setMsgInfoParser(I_MsgInfoParser msgInfoParser) {
      this.msgInfoParser = msgInfoParser;
   }

   public final String dump() {
      StringBuffer buffer = new StringBuffer(256);
      //buffer.append("msgLength=" + buf.buf.length);
      buffer.append(", checksum=" + checksum);
      buffer.append(", compressed=" + compressed);
      buffer.append(", type=" + type);
      buffer.append(", byte4=" + byte4);
      buffer.append(", byte5=" + byte5);
      buffer.append(", version=" + version);
      buffer.append(", requestId=" + requestId);
      buffer.append(", methodName=" + methodName);
      buffer.append(", sessionId=" + sessionId);
      //buffer.append(", lenUnzipped=" + lenUnzipped);
      //buffer.append(", checkSumResult=" + checkSumResult);
      return buffer.toString();
   }
   
   /**
    * Get the raw messages as a string, for tests and for dumping only
    * @return The stringified message, null bytes are replaced by '*'
    */
   public final String toLiteral() throws XmlBlasterException {
      return getMsgInfoParser().toLiteral(this);
   }
   

   public static String toLiteral(byte[] rawMsg) {
      I_MsgInfoParser parser = MsgInfoParserFactory.instance().getMsgInfoParser(Global.instance(), null);
      return parser.toLiteral(rawMsg);
   }

   /**
    * Access the serialized message, ready to send over the wire. 
    * @return
    * @throws XmlBlasterException
    */
   public final byte[] createRawMsg() throws XmlBlasterException {
      return getMsgInfoParser().createRawMsg(this);
   }
   
   /**
    * Convenience method. 
    * @param in
    * @return
    */
   public static MsgInfo parse(Global glob, I_ProgressListener progressListener, InputStream in) throws IOException {
      I_MsgInfoParser parser = MsgInfoParserFactory.instance().getMsgInfoParser(glob, progressListener);
      return parser.parse(in);
   }

   /**
    * Convenience method. 
    * @param in
    * @return
    */
   public static MsgInfo parse(Global glob, I_ProgressListener progressListener, byte[] rawMsg) throws IOException {
      return parse(glob, progressListener, new ByteArrayInputStream(rawMsg));
   }

   /** java org.xmlBlaster.util.xbformat.MsgInfo */
   public static void main( String[] args ) {
      try {
         Global glob = new Global(args);
         I_ProgressListener progressListener = null;
         byte[] rawMsg = null;
         String testName;

         testName = "Testing qos/key/content";
         System.out.println("\n----------------------\n"+testName);
         try {
            MsgInfo msgInfo = new MsgInfo(glob);
            msgInfo.setType(MsgInfo.INVOKE_BYTE);
            msgInfo.setRequestId("7711");
            msgInfo.setMethodName(MethodName.PUBLISH);
            msgInfo.setSecretSessionId("oxf6hZs");
            msgInfo.setChecksum(false);
            msgInfo.setCompressed(false);
            MsgUnitRaw msg = new MsgUnitRaw("<key oid='hello'/>", "Hello world".getBytes(), "<qos></qos>");
            msgInfo.addMessage(msg);

            rawMsg = msgInfo.createRawMsg();
            String send = msgInfo.toLiteral();
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         catch (XmlBlasterException e) {
            System.out.println(e.getMessage());
         }
         {
            ByteArrayInputStream in = new ByteArrayInputStream(rawMsg);
            I_MsgInfoParser parser = MsgInfoParserFactory.instance().getMsgInfoParser(glob, null);
            MsgInfo msgInfo = parser.parse(in);
            //System.out.println("\nReceived: \n" + msgInfo.dump());
            String literal = msgInfo.toLiteral();
            System.out.println("Received: \n|" + literal + "|");
            if (parser.toLiteral(rawMsg).equals(literal))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }

         testName = "Testing many qos/key/content";
         System.out.println("\n----------------------\n"+testName);
         try {
            MsgInfo msgInfo = new MsgInfo(glob);
            msgInfo.setType(MsgInfo.INVOKE_BYTE);
            msgInfo.setRequestId("7711");
            msgInfo.setMethodName(MethodName.PUBLISH);
            msgInfo.setSecretSessionId("oxf6hZs");
            msgInfo.setChecksum(false);
            msgInfo.setCompressed(false);
            msgInfo.addMessage(new MsgUnitRaw("<key oid='x1'/>", "Hello1".getBytes(), "<qos/>"));
            msgInfo.addMessage(new MsgUnitRaw("<key oid='x2'/>", "Hello2".getBytes(), "<qos/>"));
            //msgInfo.addMessage(new MsgUnitRaw("<key oid='x3'/>", "Hello3".getBytes(), "<qos/>"));
            //msgInfo.addMessage(new MsgUnitRaw("<key oid='x4'/>", "Hello4".getBytes(), "<qos/>"));

            rawMsg = msgInfo.createRawMsg();
            String send = msgInfo.toLiteral();
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         catch (XmlBlasterException e) {
            System.out.println(e.getMessage());
         }
         {
            MsgInfo msgInfo = MsgInfo.parse(glob, progressListener, new ByteArrayInputStream(rawMsg));
            //System.out.println("\nReceived: \n" + msgInfo.dump());
            String receive = msgInfo.toLiteral();
            System.out.println("Received: \n|" + receive + "|");
            if (msgInfo.getMsgInfoParser().toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }

         testName = "Testing qos/key";
         System.out.println("\n----------------------\n"+testName);
         {
            MsgInfo msgInfo = new MsgInfo(glob);
            msgInfo.setType(MsgInfo.INVOKE_BYTE);
            msgInfo.setRequestId("7711");
            msgInfo.setMethodName(MethodName.GET);
            msgInfo.setSecretSessionId("oxf6hZs");
            msgInfo.setChecksum(false);
            msgInfo.setCompressed(false);
            msgInfo.addKeyAndQos("<key oid='ooo'></key>", "<qos></qos>");

            rawMsg = msgInfo.createRawMsg();
            String send = msgInfo.toLiteral();
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            MsgInfo msgInfo = MsgInfo.parse(glob, progressListener, rawMsg);
            //System.out.println("\nReceived: \n" + msgInfo.dump());
            String receive = msgInfo.toLiteral();
            System.out.println("Received: \n|" + receive + "|");
            if (msgInfo.getMsgInfoParser().toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }


         testName = "Testing qos return";
         System.out.println("\n----------------------\n"+testName);
         {
            MsgInfo msgInfo = new MsgInfo(glob);
            msgInfo.setType(MsgInfo.RESPONSE_BYTE);
            msgInfo.setRequestId("7711");
            msgInfo.setMethodName(MethodName.PUBLISH);
            msgInfo.setSecretSessionId("oxf6hZs");
            msgInfo.setChecksum(false);
            msgInfo.setCompressed(false);
            msgInfo.addQos("<qos/>");

            rawMsg = msgInfo.createRawMsg();
            String send = msgInfo.toLiteral();
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            MsgInfo msgInfo = MsgInfo.parse(glob, progressListener, rawMsg);
            if (msgInfo.getMessageArr().length != 1) {
               System.out.println(testName + ": FAILURE numMsg=" + msgInfo.getMessageArr().length);
            }
            //System.out.println("\nReceived: \n" + msgInfo.dump());
            String receive = msgInfo.toLiteral();
            System.out.println("Received: \n|" + receive + "|");
            if (msgInfo.getMsgInfoParser().toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE rawMsg sent=" + msgInfo.getMsgInfoParser().toLiteral(rawMsg));
         }

         testName = "Testing nothing";
         System.out.println("\n----------------------\n"+testName);
         {
            MsgInfo msgInfo = new MsgInfo(glob);
            msgInfo.setType(MsgInfo.INVOKE_BYTE);
            msgInfo.setRequestId("7711");
            msgInfo.setMethodName(MethodName.GET);
            msgInfo.setSecretSessionId("oxf6hZs");
            msgInfo.setChecksum(false);
            msgInfo.setCompressed(false);

            rawMsg = msgInfo.createRawMsg();
            String send = msgInfo.getMsgInfoParser().toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            MsgInfo msgInfo = MsgInfo.parse(glob, progressListener, rawMsg);
            if (msgInfo.getMessageArr().length > 0) {
               System.out.println(testName + ": FAILURE msgLength=" + msgInfo.getMessageArr().length + " dump=" + msgInfo.getMessageArr()[0].toXml());
            }
            else {
               //System.out.println("\nReceived: \n" + msgInfo.dump());
               String receive = msgInfo.toLiteral();
               System.out.println("Received: \n|" + receive + "|");
               if (msgInfo.getMsgInfoParser().toLiteral(rawMsg).equals(receive))
                  System.out.println(testName + ": SUCCESS");
               else
                  System.out.println(testName + ": FAILURE");
            }
         }

         testName = "Testing really nothing";
         System.out.println("\n----------------------\n"+testName);
         {
            rawMsg = "        10".getBytes();
            String send = MsgInfoParserFactory.instance().getMsgInfoParser(glob, progressListener).toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            MsgInfo msgInfo = MsgInfo.parse(glob, progressListener, rawMsg);
            msgInfo.setSecretSessionId(null);
            if (msgInfo.getMessageArr().length > 0) {
               System.out.println(testName + ": FAILURE");
            }
            else {
               //System.out.println("\nReceived: \n" + msgInfo.dump());
               String receive = msgInfo.getMsgInfoParser().toLiteral(msgInfo.createRawMsg());
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
            MsgInfo msgInfo = new MsgInfo(glob);
            msgInfo.setType(MsgInfo.EXCEPTION_BYTE);
            msgInfo.setRequestId("7711");
            msgInfo.setMethodName(MethodName.PUBLISH);
            msgInfo.setSecretSessionId("oxf6hZs");
            msgInfo.setChecksum(false);
            msgInfo.setCompressed(false);
            XmlBlasterException ex = new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, "QueueOverflow", "The destination queue is full");
            msgInfo.addException(ex);

            rawMsg = msgInfo.createRawMsg();
            String send = msgInfo.getMsgInfoParser().toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            MsgInfo msgInfo = MsgInfo.parse(glob, progressListener, rawMsg);
            //System.out.println("\nReceived: \n" + msgInfo.dump());
            String receive = msgInfo.toLiteral();
            System.out.println("Received: \n|" + receive + "|");
            if (msgInfo.getMsgInfoParser().toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }


         testName = "Testing qos/key/content return value";
         System.out.println("\n----------------------\n"+testName);
         try {
            MsgInfo msgInfo = new MsgInfo(glob);
            msgInfo.setType(MsgInfo.RESPONSE_BYTE);
            msgInfo.setRequestId("7711");
            msgInfo.setMethodName(MethodName.GET);
            //msgInfo.setSecretSessionId("oxf6hZs");
            msgInfo.setChecksum(false);
            msgInfo.setCompressed(false);
            MsgUnitRaw msg = new MsgUnitRaw("<key oid='hello'/>", "Hello world response".getBytes(), "<qos></qos>");
            msgInfo.addMessage(msg);

            rawMsg = msgInfo.createRawMsg();
            String send = msgInfo.getMsgInfoParser().toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         catch (XmlBlasterException e) {
            System.out.println(e.getMessage());
         }
         {
            MsgInfo msgInfo = MsgInfo.parse(glob, progressListener, rawMsg);
            //System.out.println("\nReceived: \n" + msgInfo.dump());
            String receive = msgInfo.toLiteral();
            System.out.println("Received: \n|" + receive + "|");
            if (msgInfo.getMsgInfoParser().toLiteral(rawMsg).equals(receive))
               System.out.println(testName + ": SUCCESS");
            else
               System.out.println(testName + ": FAILURE");
         }

         testName = "Testing a QoS return value";
         System.out.println("\n----------------------\n"+testName);
         {
            MsgInfo msgInfo = new MsgInfo(glob);
            msgInfo.setType(MsgInfo.RESPONSE_BYTE);
            msgInfo.setRequestId("7711");
            msgInfo.setMethodName(MethodName.ERASE);
            //msgInfo.setSecretSessionId("");
            msgInfo.setChecksum(false);
            msgInfo.setCompressed(false);
            msgInfo.addQos("<qos><state id='OK'/></qos>");

            rawMsg = msgInfo.createRawMsg();
            String send = msgInfo.getMsgInfoParser().toLiteral(rawMsg);
            System.out.println(testName + ": Created and ready to send: \n|" + send + "|");
         }
         {
            MsgInfo msgInfo = MsgInfo.parse(glob, progressListener, rawMsg);
            //System.out.println("\nReceived: \n" + msgInfo.dump());
            String receive = msgInfo.toLiteral();
            System.out.println("Received: \n|" + receive + "|");
            if (msgInfo.getMsgInfoParser().toLiteral(rawMsg).equals(receive))
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
}

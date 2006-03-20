/*------------------------------------------------------------------------------
Name:      MethodName.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.def;

import java.util.Hashtable;

/**
 * This class holds all method names to access xmlBlaster. 
 * @author xmlBlaster@marcelruff.info
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html">The interface requirement</a>
 */
public final class MethodName implements java.io.Serializable
{
   private static final long serialVersionUID = -6644144030401574462L;
   private final static Hashtable hash = new Hashtable(); // The key is the 'methodName' String and the value is an 'MethodName' instance
   private final String methodName;
   private final int argType;
   private final int returnType;

   private transient byte[] methodNameBytes; // for better performance in SOCKET protocol

   // The possible method return types, useful for SOCKET protocol (see requirement 'protocol.socket')
   private static final int RETURN_VOID = 0;
   private static final int RETURN_STRING = 1;
   private static final int RETURN_STRINGARR = 2;
   private static final int RETURN_MSGARR = 3;

   // The possible method argument types, useful for SOCKET protocol and persistence
   private static final int ARG_QOS = 0;
   private static final int ARG_KEYQOS = 1;
   private static final int ARG_MSGARR = 2;
   private static final int ARG_STR_MSGARR = 3;
   //private static final int ARG_MSG = 4;

   public static final MethodName CONNECT = new MethodName("connect", ARG_QOS, RETURN_STRING);
   public static final MethodName DISCONNECT = new MethodName("disconnect", ARG_QOS, RETURN_STRING);
   public static final MethodName GET = new MethodName("get", ARG_KEYQOS, RETURN_MSGARR);
   public static final MethodName ERASE = new MethodName("erase", ARG_KEYQOS, RETURN_STRINGARR);
   public static final MethodName PUBLISH = new MethodName("publish", ARG_MSGARR, RETURN_STRINGARR);
   public static final MethodName PUBLISH_ARR = new MethodName("publishArr", ARG_MSGARR, RETURN_STRINGARR);
   public static final MethodName PUBLISH_ONEWAY = new MethodName("publishOneway", ARG_MSGARR, RETURN_VOID);
   public static final MethodName SUBSCRIBE = new MethodName("subscribe", ARG_KEYQOS, RETURN_STRING);
   public static final MethodName UNSUBSCRIBE = new MethodName("unSubscribe", ARG_KEYQOS, RETURN_STRINGARR);
   public static final MethodName UPDATE = new MethodName("update", ARG_STR_MSGARR, RETURN_STRINGARR);
   public static final MethodName UPDATE_ONEWAY = new MethodName("updateOneway", ARG_STR_MSGARR, RETURN_VOID);
   public static final MethodName PING = new MethodName("ping", ARG_QOS, RETURN_STRING);
   public static final MethodName DUMMY = new MethodName("dummy", ARG_QOS, RETURN_STRING);
   public static final MethodName UNKNOWN = new MethodName("unknown", ARG_QOS, RETURN_STRING);
   public static final MethodName EXCEPTION = new MethodName("exception", ARG_QOS, RETURN_VOID);
   // for testsuite only

   /**
    * @exception IllegalArgumentException if the given methodName is null
    */
   private MethodName(String methodName, int argType, int returnType) {
      if (methodName == null)
         throw new IllegalArgumentException("Your given methodName is null");
      this.methodName = methodName;
      this.argType = argType;
      this.returnType = returnType;
      hash.put(methodName, this);
   }

   /**
    * Return the methodName. 
    */
   public String toString() {
      return this.methodName;
   }

   /**
    * Returns the methodName. 
    */
   public String getMethodName() {
      return this.methodName;
   }

   /**
    * When you compare two methodName with == and they are
    * loaded by different Classloaders it will fail (return false even
    * if they are the same method), using
    * this equals() method is safe under such circumstances
    */
   public boolean equals(MethodName other) {
      return getMethodName().equals(other.getMethodName());
   }

   /**
    * When you compare two methodName with == and they are
    * loaded by different Classloaders it will fail (return false even
    * if they are the same method), using
    * this equals() method is safe under such circumstances
    */
   public boolean equals(String other) {
      return getMethodName().equals(other);
   }

   /**
    * For better performance in SOCKET protocol. 
    * @return methodName dumped to a byte[]
    */
   public byte[] getMethodNameBytes() {
      if (this.methodNameBytes == null) {
         this.methodNameBytes = this.methodName.getBytes();
      }
      return this.methodNameBytes;
   }

   public boolean wantsQosArg() {
      return this.argType == ARG_QOS;
   }

   public boolean wantsKeyQosArg() {
      return this.argType == ARG_KEYQOS;
   }

   public boolean wantsMsgArrArg() {
      return this.argType == ARG_MSGARR;
   }

   public boolean wantsStrMsgArrArg() {
      return this.argType == ARG_STR_MSGARR;
   }

   public boolean returnsVoid() {
      return this.returnType == RETURN_VOID;
   }

   public boolean returnsString() {
      return this.returnType == RETURN_STRING;
   }

   public boolean returnsStringArr() {
      return this.returnType == RETURN_STRINGARR;
   }

   public boolean returnsMsgArr() {
      return this.returnType == RETURN_MSGARR;
   }

   /**
    * Returns the MethodName object for the given String. 
    * @param methodName The String code to lookup
    * @return The enumeration object for this methodName
    * @exception IllegalArgumentException if the given methodName is invalid
    */
   public static final MethodName toMethodName(String methodName) throws IllegalArgumentException {
      if (methodName == null)
         throw new IllegalArgumentException("MethodName: A 'null' methodName is invalid");
      Object entry = hash.get(methodName);
      if (entry != null)
         return (MethodName)entry;

      // 2. try case insensitive: Buggy as it does not work for "unSubscribe" with big letter 'S'
      methodName = methodName.toLowerCase();
      entry = hash.get(methodName);
      if (entry == null)
         throw new IllegalArgumentException("MethodName: The given methodName=" + methodName + " is unknown");
      return (MethodName)entry;
   }

   public static final MethodName toMethodName(byte[] methodNameBytes) throws IllegalArgumentException {
      return toMethodName(new String(methodNameBytes)); // tuning possible by doing it ourself?
   }

   ///////////////
   // This code is a helper for serialization so that after
   // deserial the check
   //   MethodName.PUBLISH == instance
   // is still usable (the singleton is assured when deserializing)
   public Object writeReplace() throws java.io.ObjectStreamException {
      return new SerializedForm(this.getMethodName());
   }
   private static class SerializedForm implements java.io.Serializable {
      private static final long serialVersionUID = -4747332683196055305L;
      String methodName;
      SerializedForm(String methodName) { this.methodName = methodName; }
      Object readResolve() throws java.io.ObjectStreamException {
         return MethodName.toMethodName(methodName);
      }
   }
   ///////////////END

   /**
    * <pre>
    *  java org.xmlBlaster.util.def.MethodName
    * </pre>
    */
   public static void main (String [] args) {
      try {
         MethodName.toMethodName((String)null);
         System.out.println("null should not return");
      }
      catch (Throwable e) {
         System.out.println("ERROR: " + e.toString());
      }
      MethodName methodName = MethodName.toMethodName("UpDaTe");
      System.out.println("OK: " + methodName);
   }
}

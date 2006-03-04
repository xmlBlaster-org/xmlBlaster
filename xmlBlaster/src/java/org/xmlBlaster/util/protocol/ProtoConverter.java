/*------------------------------------------------------------------------------
Name:      ProtoConverter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Converter used to convert native data to protocol-specific data.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol;

import java.util.Vector;
import java.util.Enumeration;
import java.util.logging.Logger;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnitRaw;


/**
 * This is a utility class used to convert general xmlBlaster data to data
 * is protocol-specific. This is necessary in cases where the protocol does not
 * support a particular data type. This class is used extensively in the
 * xml-rpc protocol where user-defined classes are not supported.
 * <p />
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class ProtoConverter {

   private static Logger log = Logger.getLogger(ProtoConverter.class.getName());
   private final static String ME = "ProtoConverter";

   /**
    * Converts a Vector to a MsgUnitRaw. The first element in vec must be a
    * String object corresponding to the literal for MsgUnitRaw.xmlKey, the
    * second element is a byte[] object corresponding to MsgUnitRaw.content.
    * The third (and last) element is a String corresponding to the literal of
    * MsgUnitRaw.qos.
    *
    * @param vec the vector to convert
    * @return the MsgUnitRaw to which the vector has been converted to.
    * @throws XmlBlasterException if the size of vec is different from 3, or
    *                             if the type of the vector elements does not
    *                             correspond to the MsgUnitRaw equivalent.
    *
    */
   public static MsgUnitRaw vector2MsgUnitRaw(Vector vec)
      throws XmlBlasterException
   {
      MsgUnitRaw ret = null;
      int size = vec.size();
      if (size != 3) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_WRONG_API_USAGE, ME, "Can't create a MessageUnit from " + size + " tokens");
      }

      try {
         Enumeration enumeration = vec.elements();
         String xmlKey = (String)enumeration.nextElement();
         Object obj = enumeration.nextElement();
         byte[] content = null;
         if (obj instanceof byte[]) {
            content = (byte[])obj;
         }
         else {
            String str = (String)obj;
            content = str.getBytes();
         }
         String qos = (String)enumeration.nextElement();
         ret = new MsgUnitRaw(xmlKey, content, qos);

      }

      catch (ClassCastException e) {
         for (int i=0; i<vec.size(); i++) {
            System.out.println(ME + ": Vector #" + i + " = " + vec.elementAt(i).getClass().getName());
         }
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_WRONG_API_USAGE, ME, "Can't create a MessageUnit", e);
      }

      return ret;
   }



   /**
    * Converts a single MsgUnitRaw to a Vector.
    *
    * @param msg the MsgUnitRaw to convert to a Vector.
    * @return the Vector object containing three elements [String, byte[],
    *         String] representing the MsgUnitRaw.
    */
   public static Vector messageUnit2Vector (MsgUnitRaw msg)
   {
      Vector ret = new Vector();
      ret.addElement(msg.getKey());
      ret.addElement(msg.getContent());
      ret.addElement(msg.getQos());
      return ret;
   }


   /**
    * Converts a Vector to a MsgUnitRaw[]. The input Vector must contain
    * elements of the type Vector. Each of such (sub)Vectors must have three
    * elements fullfilling the requirements for the Vector objects in
    * vector2MsgUnitRaw.
    * @param vec the input Vector (of Vector).
    * @return the MsgUnitRaw[].
    * @throws XmlBlasterException if the types in the input vector are not
    *                             consistent with the required types.
    */
   public static MsgUnitRaw[] vector2MsgUnitRawArray(Vector vec)
      throws XmlBlasterException
   {
      int size = vec.size();
      MsgUnitRaw[] msgUnitArr = new MsgUnitRaw[size];

      try {
         Enumeration enumeration = vec.elements();
         for (int i=0; i < size; i++) {
            msgUnitArr[i] = vector2MsgUnitRaw((Vector)enumeration.nextElement());
         }
      }

      catch (ClassCastException e) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_WRONG_API_USAGE, ME, "Can't create a MessageUnit[]", e);
      }


      return msgUnitArr;
   }


   /**
    * Converts a MsgUnitRaw[] object into a Vector.
    * @param msgs The array of MsgUnitRaw objects to convert to a Vector object.
    * @return the Vector containing all information about the msgs parameter.
    */
   public static Vector messageUnitArray2Vector (MsgUnitRaw[] msgs)
   {
      int size = msgs.length;
      Vector ret = new Vector();
      for (int i=0; i < size; i++) {
         ret.addElement(messageUnit2Vector(msgs[i]));
      }
      return ret;
   }


   /**
    * Converts a Vector to a String[] object. All elements of the Vector must
    * be valid String objects.
    * @param vec vector containing the Strings to be converted.
    * @return an array of String objects.
    * @throws XmlBlasterException if the elements in the Vector where not valid
    *             String objects.
    */
   public static String[] vector2StringArray(Vector vec) throws XmlBlasterException
   {
      int size = vec.size();
      String[] ret = new String[size];

      try {
         Enumeration enumeration = vec.elements();
         for (int i=0; i < size; i++) {
            ret[i] = (String)enumeration.nextElement();
         }
      }

      catch (ClassCastException e) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_WRONG_API_USAGE, ME, "Can't create a String[]", e);
      }

      return ret;
   }


   /**
    * Converts one element of a Vector to a String object.
    * @param vec vector containing the String to be converted.
    * @return The String object.
    * @throws XmlBlasterException if the element in the Vector is not a valid
    *             String object.
    */
   public static String vector2String(Vector vec) throws XmlBlasterException
   {
      int size = vec.size();
      if (size == 0)
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_WRONG_API_USAGE, ME, "Can't create a String from the given Vector");
      if (size > 1)
         log.severe("There are too many strings in the vector");

      try {
         return (String)vec.elementAt(0);
      }
      catch (ClassCastException e) {
         throw new XmlBlasterException(Global.instance(), ErrorCode.USER_WRONG_API_USAGE, ME, "Can't create a String", e);
      }
   }


   /**
    * Converts a String[] into a Vector object.
    * @param strings array of String objects to convert to a Vector.
    * @return a Vector object containing all the elements of the input array.
    */
   public static Vector stringArray2Vector(String[] strings)
   {
      int size = strings.length;
      Vector ret = new Vector();

      for (int i=0; i< size; i++) {
         ret.addElement(strings[i]);
      }

      return ret;
   }


   /**
    * For testing ...
    */

   public static void main (String args[])
   {

      byte[] content = new byte[100];
      MsgUnitRaw[] msgs = new MsgUnitRaw[5];
      String[] strings = new String[5];

      System.out.println("The Original Messages & strings: \n\n");

      try {
         for (int i=0; i < 5; i++) {
            msgs[i] = new MsgUnitRaw("<key uid='" + i + "'></key>", content, "<qos></qos>");
            strings[i] = new String("string nr. " + (i+1) );
            System.out.println(msgs[i].getKey() + " " + strings[i]);
         }


         Vector vec = ProtoConverter.messageUnitArray2Vector(msgs);
         Vector strVector = ProtoConverter.stringArray2Vector(strings);

         MsgUnitRaw[] msgs2 = ProtoConverter.vector2MsgUnitRawArray(vec);
         String[] strings2 = ProtoConverter.vector2StringArray(strVector);

         System.out.println("\n\nReconverted Messages: ");

         for (int i=0; i < msgs2.length; i++) {
            System.out.println(msgs2[i].getKey() + " " + strings2[i]);
         }

      }

      catch (XmlBlasterException ex) {
         System.err.println("Exception: " + ex.toString());
      }


   }

}


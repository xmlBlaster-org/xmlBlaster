/*------------------------------------------------------------------------------
Name:      ProtoConverter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Converter used to convert native data to protocol-specific data.
Version:   $Id: ProtoConverter.java,v 1.2 2000/09/15 17:16:21 ruff Exp $
Author:    "Michele Laghi" <michele.laghi@attglobal.net>
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol;

import java.util.Vector;
import java.util.Enumeration;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.Log;


/**
 * This is a utility class used to convert general xmlBlaster data to data
 * is protocol-specific. This is necessary in cases where the protocol does not
 * support a particular data type. This class is used extensively in the
 * xml-rpc protocol where user-defined classes are not supported.
 * <p />
 * @author michele.laghi@attglobal.net
 */
public class ProtoConverter {

   private final static String ME = "ProtoConverter";

   /**
    * Converts a Vector to a MessageUnit. The first element in vec must be a
    * String object corresponding to the literal for MessageUnit.xmlKey, the
    * second element is a byte[] object corresponding to MessageUnit.content.
    * The third (and last) element is a String corresponding to the literal of
    * MessageUnit.qos.
    *
    * @param vec the vector to convert
    * @return the MessageUnit to which the vector has been converted to.
    * @throws XmlBlasterException if the size of vec is different from 3, or
    *                             if the type of the vector elements does not
    *                             correspond to the MessageUnit equivalent.
    *
    */
   public static MessageUnit vector2MessageUnit (Vector vec)
      throws XmlBlasterException
   {
      MessageUnit ret = null;
      int size = vec.size();
      if (size != 3) {
         Log.error(ME + ".vector2MessageUnit", "not a valid MessageUnit");
         throw new XmlBlasterException("Not a valid Message Unit", "Wrong size");
      }

      try {
         Enumeration enumeration = vec.elements();
         String xmlKey = (String)enumeration.nextElement();
         byte[] content = (byte[])enumeration.nextElement();
         String qos = (String)enumeration.nextElement();
         ret = new MessageUnit(xmlKey, content, qos);

      }

      catch (ClassCastException e) {
         Log.error(ME + ".vector2MessageUnit", "not a valid MessageUnit: "
                          + e.toString());
         throw new XmlBlasterException("Not a valid Message Unit", "Class Cast Exception");
      }

      return ret;
   }



   /**
    * Converts a single MessageUnit to a Vector.
    *
    * @param msg the MessageUnit to convert to a Vector.
    * @return the Vector object containing three elements [String, byte[],
    *         String] representing the MessageUnit.
    */
   public static Vector messageUnit2Vector (MessageUnit msg)
   {
      Vector ret = new Vector();
      ret.addElement(msg.getXmlKey());
      ret.addElement(msg.getContent());
      ret.addElement(msg.getQos());
      return ret;
   }


   /**
    * Converts a Vector to a MessageUnit[]. The input Vector must contain
    * elements of the type Vector. Each of such (sub)Vectors must have three
    * elements fullfilling the requirements for the Vector objects in
    * vector2MessageUnit.
    * @param vec the input Vector (of Vector).
    * @return the MessageUnit[].
    * @throws XmlBlasterException if the types in the input vector are not
    *                             consistent with the required types.
    */
   public static MessageUnit[] vector2MessageUnitArray (Vector vec)
      throws XmlBlasterException
   {
      int size = vec.size();
      MessageUnit[] msgUnitArr = new MessageUnit[size];

      try {
         Enumeration enumeration = vec.elements();
         for (int i=0; i < size; i++) {
            msgUnitArr[i] = vector2MessageUnit((Vector)enumeration.nextElement());
         }
      }

      catch (ClassCastException e) {
         Log.error(ME + ".vector2MessageUnitArray", "not a valid MessageUnit[]: "
                          + e.toString());
         throw new XmlBlasterException("Not a valid MessageUnit[]", "Class Cast Exception");
      }


      return msgUnitArr;
   }


   /**
    * Converts a MessageUnit[] object into a Vector.
    * @param msgs The array of MessageUnit objects to convert to a Vector object.
    * @return the Vector containing all information about the msgs parameter.
    */
   public static Vector messageUnitArray2Vector (MessageUnit[] msgs)
   {
      int size = msgs.length;
      Vector ret = new Vector();
      for (int i=0; i < size; i++) {
         ret.addElement(messageUnit2Vector(msgs[i]));
      }
      return ret;
   }


   /**
    * Convets a Vector to a String[] object. All elements of the Vector must
    * be valid String objects.
    * @param vec vector containing the Strings to be converted.
    * @return an array of String objects.
    * @throws XmlBlasterException if the elements in the Vector where not valid
    *             String objects.
    */
   public static String[] vector2StringArray (Vector vec) throws XmlBlasterException
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
         Log.error(ME + ".vector2StringArray", "not a valid String: "
                          + e.toString());
         throw new XmlBlasterException("Not a valid String", "Class Cast Exception");
      }

      return ret;
   }


   /**
    * Converts a String[] into a Vector object.
    * @param strings array of String objects to convert to a Vector.
    * @return a Vector object containing all the elements of the input array.
    */
   public static Vector stringArray2Vector (String[] strings)
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
      MessageUnit[] msgs = new MessageUnit[5];
      String[] strings = new String[5];

      System.out.println("The Original Messages & strings: \n\n");

      for (int i=0; i < 5; i++) {
         msgs[i] = new MessageUnit("<key uid='" + i + "'></key>", content, "<qos></qos>");
         strings[i] = new String("string nr. " + (i+1) );
         System.out.println(msgs[i].getXmlKey() + " " + strings[i]);
      }


      Vector vec = ProtoConverter.messageUnitArray2Vector(msgs);
      Vector strVector = ProtoConverter.stringArray2Vector(strings);

      try {
         MessageUnit[] msgs2 = ProtoConverter.vector2MessageUnitArray(vec);
         String[] strings2 = ProtoConverter.vector2StringArray(strVector);

         System.out.println("\n\nReconverted Messages: ");

         for (int i=0; i < msgs2.length; i++) {
            System.out.println(msgs2[i].getXmlKey() + " " + strings2[i]);
         }

      }

      catch (XmlBlasterException ex) {
         System.err.println("Exception: " + ex.toString());
      }


   }

}


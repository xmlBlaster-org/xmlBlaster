/*------------------------------------------------------------------------------
Name:      KeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: KeyWrapper.java,v 1.4 2000/06/14 13:54:04 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * This base class encapsulates XmlKey which you send to xmlBlaster.
 * <p />
 * A typical minimal key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711'>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that tags inside of key are application know how, which you have to supply.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * see xmlBlaster/src/dtd/XmlKey.xml
 */
public class KeyWrapper
{
   private static final String ME = "KeyWrapper";

   /** The default oid value is an empty string, in which case xmlBlaster generates an oid for you */
   protected String oid = "";


   /**
    * Constructor with unknown oid
    */
   public KeyWrapper()
   {
   }


   /**
    * Constructor with given oid
    */
   public KeyWrapper(String oid)
   {
      if (oid != null)
         this.oid = oid;
   }


   /**
    * Converts the data in XML ASCII string.
    * <p />
    * This is the minimal key representation.<br />
    * You should provide your own toString() method.
    * @return An XML ASCII string
    */
   public String toString()
   {
      StringBuffer sb = new StringBuffer();
      sb.append("<key oid='").append(oid).append("'>\n");
      sb.append("</key>");
      return sb.toString();
   }
}

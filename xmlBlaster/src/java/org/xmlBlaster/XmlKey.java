/*------------------------------------------------------------------------------
Name:      XmlKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling one xmlKey, knows how to parse it with SAX
           $Revision: 1.1 $  $Date: 1999/11/08 22:38:39 $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;


/**
 * XmlKey
 */
public class XmlKey
{
   private String ME = "XmlKey";

   /**
    * The original key in XML syntax, for example:
    * <key id="This is the unique attribute"></key>
    */
   private String xmlKey_literal;

   /**
    * The extracted unique identifier <key id="This is the unique attribute"></key>
    * -> uniqueKey = "This is the unique attribute"
    */
   private String uniqueKey;

   public XmlKey(String xmlKey_literal)
   {
      if (Log.CALLS) Log.trace(ME, "Creating new XmlKey " + xmlKey_literal);

      this.xmlKey_literal = xmlKey_literal;

      // !!! hack: use SAX parser or generate one !!!
      this.uniqueKey = xmlKey_literal;
   }


   public String toString()
   {
      return xmlKey_literal;
   }


   public String getUniqueKey()
   {
      return uniqueKey;
   }


   public String getMimeType()
   {
      return "xml";  // hack !!!! needs to be parsed with SAX
   }

}

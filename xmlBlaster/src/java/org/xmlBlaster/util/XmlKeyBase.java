/*------------------------------------------------------------------------------
Name:      XmlKeyBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with SAX
Version:   $Id: XmlKeyBase.java,v 1.2 1999/11/16 18:44:50 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * XmlKeyBase
 */
public class XmlKeyBase
{
   private String ME = "XmlKeyBase";

   /**
    * The original key in XML syntax, for example:
    * <key id="This is the unique attribute"></key>
    */
   protected String xmlKey_literal;


   public XmlKeyBase(String xmlKey_literal)
   {
      if (Log.CALLS) Log.trace(ME, "Creating new XmlKey " + xmlKey_literal);

      this.xmlKey_literal = xmlKey_literal;
   }


   public String toString()
   {
      return xmlKey_literal;
   }
}

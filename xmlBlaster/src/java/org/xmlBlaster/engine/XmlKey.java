/*------------------------------------------------------------------------------
Name:      XmlKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with SAX
Version:   $Id: XmlKey.java,v 1.2 1999/11/16 18:44:49 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;


/**
 * XmlKey
 */
public class XmlKey extends org.xmlBlaster.util.XmlKeyBase
{
   private String ME = "XmlKey";

   /**
    * The extracted unique identifier <key id="This is the unique attribute"></key>
    * -> uniqueKey = "This is the unique attribute"
    */
   private String uniqueKey;

   private String mimeType = "xml";  // hack !!!! needs to be parsed with SAX


   public XmlKey(String xmlKey_literal)
   {
      super(xmlKey_literal);

      // !!! hack: use SAX parser or generate one !!!
      this.uniqueKey = xmlKey_literal;
   }


   public String getUniqueKey()
   {
      return uniqueKey;
   }


   public String getMimeType()
   {
      return mimeType;
   }

}

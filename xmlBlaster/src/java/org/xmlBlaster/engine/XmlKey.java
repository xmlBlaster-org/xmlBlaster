/*------------------------------------------------------------------------------
Name:      XmlKey.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with SAX
Version:   $Id: XmlKey.java,v 1.4 1999/11/18 16:59:56 ruff Exp $
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

   public XmlKey(String xmlKey_literal) throws XmlBlasterException
   {
      super(xmlKey_literal);
   }
   public XmlKey(String xmlKey_literal, boolean isPublish) throws XmlBlasterException
   {
      super(xmlKey_literal, isPublish);
   }
}

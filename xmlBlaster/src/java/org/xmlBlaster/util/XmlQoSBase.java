/*------------------------------------------------------------------------------
Name:      XmlQoSBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: XmlQoSBase.java,v 1.2 1999/11/16 18:44:50 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * XmlQoSBase
 * In good old C days this would have been named a 'flag' (with bitwise setting)
 * but: this nows some more stuff, namely:
 *
 *  - The stringified IOR of the ClientCallback
 */
public class XmlQoSBase
{
   private String ME = "XmlQoSBase";

   /**
    * The original key in XML syntax, for example:
    *    "<qos></qos>"
    */
   protected String xmlQoS_literal;


   public XmlQoSBase(String xmlQoS_literal)
   {
      if (Log.CALLS) Log.trace(ME, "Creating new XmlQoSBase");

      this.xmlQoS_literal = xmlQoS_literal;
   }


   public String toString()
   {
      return xmlQoS_literal;
   }
}

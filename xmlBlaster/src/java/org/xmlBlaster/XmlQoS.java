/*------------------------------------------------------------------------------
Name:      XmlQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
           $Revision: 1.1 $  $Date: 1999/11/08 22:39:13 $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;


/**
 * XmlQoS
 * In good old C days this would have been named a 'flag' (with bitwise setting)
 * but: this nows some more stuff, namely:
 *
 *  - The stringified IOR of the ClientCallback
 */
public class XmlQoS
{
   private String ME = "XmlQoS";

   /**
    * The original key in XML syntax, for example:
    * <key id="This is the unique attribute"></key>
    */
   private String xmlQoS_literal;

   public XmlQoS(String xmlQoS_literal)
   {
      if (Log.CALLS) Log.trace(ME, "Creating new XmlQoS");

      this.xmlQoS_literal = xmlQoS_literal;

   }

   public String getCallbackIOR() throws XmlBlasterException
   {
      if (!xmlQoS_literal.startsWith("IOR:"))
         throw new XmlBlasterException("XmlQoS-MissingCallbackIOR", "Please specify the Callback String IOR as qos argument (its a hack in the moment :-)");
      return xmlQoS_literal; // !!! hack: SAX parsing is missing !!! 
   }
}

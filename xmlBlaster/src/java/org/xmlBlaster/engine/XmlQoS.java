/*------------------------------------------------------------------------------
Name:      XmlQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: XmlQoS.java,v 1.2 1999/11/16 18:44:49 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;


/**
 * XmlQoS
 * In good old C days this would have been named a 'flag' (with bitwise setting)
 * but: this nows some more stuff, namely:
 *
 *  - The stringified IOR of the ClientCallback
 */
public class XmlQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private String ME = "XmlQoS";

   public XmlQoS(String xmlQoS_literal)
   {
      super(xmlQoS_literal);
   }


   public String getCallbackIOR() throws XmlBlasterException
   {
      if (!xmlQoS_literal.startsWith("IOR:")) {
         Log.error(ME + ".MissingCallbackIOR", "Please specify the Callback String IOR as qos argument (its a hack in the moment :-)");
         throw new XmlBlasterException(ME + ".MissingCallbackIOR", "Please specify the Callback String IOR as qos argument (its a hack in the moment :-)");
      }

      return xmlQoS_literal; // !!! hack: SAX parsing is missing !!! 
   }
}

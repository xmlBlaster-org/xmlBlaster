/*------------------------------------------------------------------------------
Name:      XmlQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: XmlQoS.java,v 1.3 1999/12/02 16:48:06 ruff Exp $
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

   public XmlQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      super(xmlQoS_literal);
   }
}

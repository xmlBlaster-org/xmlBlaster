/*------------------------------------------------------------------------------
Name:      XmlQoSClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: XmlQoSClient.java,v 1.3 1999/12/02 16:48:06 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;


/**
 * XmlQoSClient
 * Informations sent to server about client preferences and wishes
 */
public class XmlQoSClient extends org.xmlBlaster.util.XmlQoSBase
{
   private String ME = "XmlQoSClient";

   public XmlQoSClient(String xmlQoS_literal) throws XmlBlasterException
   {
      super(xmlQoS_literal);
   }
}

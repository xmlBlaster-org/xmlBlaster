/*------------------------------------------------------------------------------
Name:      XmlQoSClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: XmlQoSClient.java,v 1.2 1999/11/16 18:44:49 ruff Exp $
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

   public XmlQoSClient(String xmlQoS_literal)
   {
      super(xmlQoS_literal);
   }


   public XmlQoSClient()
   {
      super("");
   }
}

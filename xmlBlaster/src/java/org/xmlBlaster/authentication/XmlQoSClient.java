/*------------------------------------------------------------------------------
Name:      XmlQoSClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
           $Revision: 1.1 $  $Date: 1999/11/13 17:16:05 $
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

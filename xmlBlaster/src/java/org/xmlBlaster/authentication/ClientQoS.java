/*------------------------------------------------------------------------------
Name:      ClientQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: ClientQoS.java,v 1.2 2000/02/20 17:38:50 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;


/**
 * Informations sent to server about client preferences and wishes
 */
public class ClientQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private String ME = "ClientQoS";

   public ClientQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      init(xmlQoS_literal);
   }
}

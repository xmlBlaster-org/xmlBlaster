/*------------------------------------------------------------------------------
Name:      XmlQoSUpdate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: XmlQoSUpdate.java,v 1.3 1999/11/16 18:44:49 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;


/**
 * XmlQoSUpdate
 * Informations sent from server to client via the update() Method
 */
public class XmlQoSUpdate extends org.xmlBlaster.util.XmlQoSBase
{
   private String ME = "XmlQoSUpdate";

   public XmlQoSUpdate(String xmlQoS_literal)
   {
      super(xmlQoS_literal);
   }


   public XmlQoSUpdate()
   {
      super("");
   }
}

/*------------------------------------------------------------------------------
Name:      XmlQoSUpdate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
           $Revision: 1.1 $  $Date: 1999/11/12 13:07:06 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;


/**
 * XmlQoSUpdate
 * Informations sent from server to client via the update() Method
 */
public class XmlQoSUpdate extends XmlQoS
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

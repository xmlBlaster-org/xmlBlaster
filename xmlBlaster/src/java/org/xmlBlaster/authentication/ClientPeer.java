/*------------------------------------------------------------------------------
Name:      ClientPeer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
           $Revision: 1.1 $  $Date: 1999/11/14 21:53:18 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;


/**
 * ClientPeer
 * Every client has in the server exactly on ClientPeer object
 */
public class ClientPeer
{
   private String ME = "ClientPeer";

   public ClientPeer(String niceName)
   {
      if (Log.HACK_POA) {
         Log.warning(ME, "Entering constructor: " + niceName);
      }
      
      ME += "-";
      ME += niceName;
   }


   public ClientPeer()
   {
   }
}

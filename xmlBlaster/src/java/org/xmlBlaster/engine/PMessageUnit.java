/*------------------------------------------------------------------------------
Name:      PMessageUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Ccapsulate a MessageUnit and QoS for persistence and engine 
Version:   $Id: PMessageUnit.java,v 1.3 2000/06/13 16:39:37 kron Exp $
Author:    manuel.kron@gmx.net 
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine;
  
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.engine.xml2java.PublishQoS;

public class PMessageUnit
{
   public final MessageUnit msgUnit; 
   public final PublishQoS  pubQos;
   public final String      sender;
   public final long        timeStamp;

   public PMessageUnit(MessageUnit mu,PublishQoS qos, String send)
   {
      timeStamp = System.currentTimeMillis();
      msgUnit   = mu;
      pubQos    = qos;
      sender    = send;
   }
}

/*------------------------------------------------------------------------------
Name:      PMessageUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Ccapsulate a MessageUnit and QoS for persistence and engine 
Version:   $Id: PMessageUnit.java,v 1.4 2000/06/13 17:28:56 kron Exp $
Author:    manuel.kron@gmx.net 
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine;
  
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.engine.xml2java.PublishQoS;

public class PMessageUnit
{
   private static final String ME = "PMessageUnit";
   public final MessageUnit msgUnit; 
   public final PublishQoS  pubQos;
   public final String      sender;
   public final long        timeStamp;
   public long        size = 0L;

   public PMessageUnit(MessageUnit mu,PublishQoS qos, String send)
   {
      timeStamp = System.currentTimeMillis();
      msgUnit   = mu;
      pubQos    = qos;
      sender    = send;
     
      size = mu.size + qos.size + 2200;
   }

}

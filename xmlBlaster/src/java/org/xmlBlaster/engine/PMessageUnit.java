/*------------------------------------------------------------------------------
Name:      PMessageUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Ccapsulate a MessageUnit and QoS for persistence and engine 
Version:   $Id: PMessageUnit.java,v 1.2 2000/06/13 16:10:47 kron Exp $
Author:    manuel.kron@gmx.net 
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine;
  
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.engine.xml2java.PublishQoS;

public class PMessageUnit
{
   public final MessageUnit _msgUnit; 
   public final PublishQoS  _pubQos;
   public final String      _sender;
   public final long        _timeStamp;

   public PMessageUnit(MessageUnit mu,PublishQoS qos, String sender)
   {
      _timeStamp = System.currentTimeMillis();
      _msgUnit   = mu;
      _pubQos    = qos;
      _sender    = sender;
   }
}

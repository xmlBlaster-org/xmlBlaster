/*------------------------------------------------------------------------------
Name:      PMessageUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Ccapsulate a MessageUnit and QoS for persistence and engine
Version:   $Id: PMessageUnit.java,v 1.2 2000/12/26 14:56:40 ruff Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.persistence;
import java.io.Serializable;

import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Log;


public class PMessageUnit implements Serializable
{
   private static final String ME = "PMessageUnit";

   public final MessageUnit msgUnit;
   public final long        timeStamp;
   public final boolean     isDurable;
   public long              size = 0L;
   public String            oid = "";

   public PMessageUnit(MessageUnit mu,boolean durable, String myOid)
   {
      oid = myOid;
      isDurable = durable;
      timeStamp = System.currentTimeMillis();
      msgUnit   = mu;

      // PublishQoS is now in the MessageUnit
      size = mu.xmlKey.length() + mu.content.length  + mu.qos.length() + 2200;
   }

}

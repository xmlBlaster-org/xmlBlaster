/*------------------------------------------------------------------------------
Name:      PMessageUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Ccapsulate a MessageUnit and QoS for persistence and engine
Version:   $Id: PMessageUnit.java,v 1.3 2002/03/13 16:41:17 ruff Exp $
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

      // PublishQos is now in the MessageUnit
      size = mu.xmlKey.length() + mu.content.length  + mu.qos.length() + 2200;
   }

}

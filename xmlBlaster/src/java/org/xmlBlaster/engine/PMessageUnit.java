/*------------------------------------------------------------------------------
Name:      PMessageUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Ccapsulate a MessageUnit and QoS for persistence and engine
Version:   $Id: PMessageUnit.java,v 1.10 2000/09/15 17:16:15 ruff Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine;
import java.io.*;

import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Log;

import gnu.regexp.*;

public class PMessageUnit implements Serializable
{
   private static final String ME = "PMessageUnit";

   public final MessageUnit msgUnit;
   public final long        timeStamp;
   public final boolean     isDurable;
   public long              size = 0L;
   public String            oid = "";


   public PMessageUnit(MessageUnit mu,boolean durable)
   {
      isDurable = durable;
      timeStamp = System.currentTimeMillis();
      msgUnit   = mu;
      // PublishQoS is now in the MessageUnit
      size = mu.xmlKey.length() + mu.content.length  + mu.qos.length() + 2200;
      oid = getOid();
   }


   private String getOid()
   {
     RE expression = null;
     String oid = null;
     try{
        expression = new RE("oid=(\'|\"|\\s)(.*)(\'|\")");
        REMatch match = expression.getMatch(msgUnit.xmlKey);
        if(match != null)
        {
            /** matches OID pure */
            RE re = new RE("[^oid=\'\"]");
            REMatch[] matches = re.getAllMatches(match.toString());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < matches.length; i++) {
              sb.append(matches[i]);
            }
            oid = sb.toString();

         }else{
            Log.error(ME,"Invalid xmlKey.");
         }

      }catch(REException e){
        Log.error(ME,"Can't create RE."+e.toString());
      }
     return oid;
   }

}

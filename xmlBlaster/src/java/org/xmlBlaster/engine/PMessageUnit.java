/*------------------------------------------------------------------------------
Name:      PMessageUnit.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Ccapsulate a MessageUnit and QoS for persistence and engine
Version:   $Id: PMessageUnit.java,v 1.7 2000/06/18 15:21:59 ruff Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine;
import java.io.*;

import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.jutils.log.Log;

import gnu.regexp.*;

public class PMessageUnit implements Serializable
{
   private static final String ME = "PMessageUnit";
   public final MessageUnit msgUnit;
   public final PublishQoS  pubQos;
   public final String      sender;
   public final long        timeStamp;
   public long        size = 0L;
   public String oid = "";

   public PMessageUnit(MessageUnit mu,PublishQoS qos, String send)
   {
      timeStamp = System.currentTimeMillis();
      msgUnit   = mu;
      pubQos    = qos;
      sender    = send;

      size = mu.xmlKey.length() + mu.content.length  + qos.size + 2200;
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

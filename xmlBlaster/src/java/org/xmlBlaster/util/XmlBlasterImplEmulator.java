/*------------------------------------------------------------------------------
Name:      XmlBlasterImplEmulator.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Used for testing purposes to see what the plugins would do
Version:   $Id: XmlBlasterImplEmulator.java,v 1.1 2002/05/22 17:20:11 laghi Exp $
Author:    laghi@swissinfo.org
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.util.Log;

/**
 * @author laghi@swissinfo.org
 */
public class XmlBlasterImplEmulator implements I_XmlBlaster
{
   private long sessionIdNum = 0L;
   private final static String ME = "XmlBlasterImplEmulator";

   public XmlBlasterImplEmulator ()
   {
   }

   public String subscribe(String sessionId, String xmlKey_literal,
      String subscribeQoS_literal) throws XmlBlasterException
   {
      if (sessionId == null) {
         sessionId = "" + this.sessionIdNum++;
      }
      if (Log.TRACE) {
         Log.trace(ME, "subscribe sessionId: " + sessionId);
         Log.trace(ME, "subscribe (cont) key: " + xmlKey_literal);
         Log.trace(ME, "subscribe (cont) qos: " + subscribeQoS_literal);
      }
      return sessionId;
   }

   public void unSubscribe(String sessionId, String xmlKey_literal,
      String unSubscribeQoS_literal) throws XmlBlasterException
   {
      if (Log.TRACE) {
         Log.trace(ME, "unSubscribe sessionId: " + sessionId);
         Log.trace(ME, "unSubscribe (cont) key: " + xmlKey_literal);
         Log.trace(ME, "unSubscribe (cont) qos: " + unSubscribeQoS_literal);
      }
   }

   public String publish(String sessionId, MessageUnit msgUnit)
      throws XmlBlasterException
   {
      if (sessionId == null) {
         sessionId = "" + this.sessionIdNum++;
      }
      if (Log.TRACE) {
         Log.trace(ME, "publish sessionId: " + sessionId);
         Log.trace(ME, "publish (cont) key: " + msgUnit.getXmlKey());
         Log.trace(ME, "publish (cont) content: " + new String(msgUnit.content));
         Log.trace(ME, "publish (cont) qos: " + msgUnit.getQos());
      }
      return sessionId;
   }


   public String[] publishArr(String sessionId, MessageUnit[] msgUnitArr)
      throws XmlBlasterException
   {
      String[] ret = new String[msgUnitArr.length];
      for (int i=0; i < msgUnitArr.length; i++)
         ret[i] = publish(sessionId, msgUnitArr[i]);
      return ret;
   }


   public String[] erase(String sessionId, String xmlKey_literal, String eraseQoS_literal)
      throws XmlBlasterException
   {
      if (Log.TRACE) {
         Log.trace(ME, "erase sessionId: " + sessionId);
         Log.trace(ME, "erase (cont) key: " + xmlKey_literal);
         Log.trace(ME, "erase (cont) qos: " + eraseQoS_literal);
      }
      String[] ret = new String[1];
      ret[0] = sessionId;
      return ret;
   }

   public MessageUnit[] get(String sessionId, String xmlKey_literal, String getQoS_literal)
      throws XmlBlasterException
   {
      if (Log.TRACE) {
         Log.trace(ME, "get sessionId: " + sessionId);
         Log.trace(ME, "get (cont) key: " + xmlKey_literal);
         Log.trace(ME, "get (cont) qos: " + getQoS_literal);
      }
      return null;
   }


   public String toXml() throws XmlBlasterException
   {
      return "<xmlBlaster></xmlBlaster>";
   }

   public String toXml(String extraOffset) throws XmlBlasterException
   {
      return toXml();
   }
}


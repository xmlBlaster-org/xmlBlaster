/*------------------------------------------------------------------------------
Name:      BlasterHttpProxy.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This class contains some useful, static helper methods.
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import java.rmi.RemoteException;
import java.io.*;
import java.util.*;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.ConnectQos;

/**
 * Contains some useful, static helper methods. It is
 * a singleton.
 * <p />
 * It holds a hashtable with all current browser-xmlBlaster connections.
 * <p />
 * You can also use this class to handle shared attributes for all servlets.
 * @author Konrad Krafft
 * @author Marcel Ruff
 */
public class BlasterHttpProxy
{
   private static final String ME = "BlasterHttpProxy";

   /** Mapping the sessionId to a HttpPushHandler instance.
    * <p />
    * This HttpPushHandlers knows how to access xmlBlaster (with CORBA/SOCKET etc)
    * and the Browser (with http).
    */
   private static Hashtable httpPushHandlers = new Hashtable();

   /** Stores global Attributes for other Servlets */
   private static Hashtable attributes       = new Hashtable();


   /**
    * returns a Object by a given name
    *
    * @param name key name
    * @return Object
    */
   public static Object getAttribute(String name) {
      return attributes.get(name);
   }

   /**
    * writes a Object by a given name
    *
    * @param name key name
    * @param obj Object
    */
   public static void setAttribute(String name, Object obj) {
      attributes.put(name,obj);
   }

   /**
    * Remove the attribute.
    *
    * @param name key name
    */
   public static void removeAttribute(String name) {
      attributes.remove(name);
   }

   public static I_XmlBlasterAccess getXmlBlasterAccess(String sessionId) throws XmlBlasterException {
      return getHttpPushHandler(sessionId).getXmlBlasterAccess();
   }

   /**
    * Gives a proxy connection by a given sessionId.
    * <p />
    * @param sessionId HTTP sessionId
    * @return valid httpPushHandler for valid HTTP sessionId (never null)
    * @exception If sessionId not found.
    */
   public static void addHttpPushHandler(String sessionId, HttpPushHandler httpPushHandler) throws XmlBlasterException {
      if (sessionId == null) {
         throw new XmlBlasterException(ME+".NoSessionId", "Sorry, no valid session ID, are cookies disabled?");
      }
      synchronized(httpPushHandlers) {
         httpPushHandlers.put(sessionId, httpPushHandler);
      }
   }

   /**
    * Gives a proxy connection by a given sessionId.
    * <p />
    * @param sessionId HTTP sessionId
    * @return valid httpPushHandler for valid HTTP sessionId (never null)
    * @exception If sessionId not found.
    */
   public static HttpPushHandler getHttpPushHandler(String sessionId) throws XmlBlasterException {
      if (sessionId == null) {
         throw new XmlBlasterException(ME+".NoSessionId", "Sorry, no valid session ID, are cookies disabled?");
      }
      synchronized(httpPushHandlers) {
         HttpPushHandler pc = (HttpPushHandler)httpPushHandlers.get(sessionId);
         if (pc != null)
            return pc;
      }
      throw new XmlBlasterException(ME, "Session not registered yet (sessionId="+sessionId+")");
   }

   /**
    * Cleanup Hashtable etc.
    */
   public static void cleanup(String sessionId) {
      synchronized(httpPushHandlers) {
         httpPushHandlers.remove(sessionId);
      }
   }
}


/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: RequestBroker.java,v 1.11 1999/11/17 16:00:53 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.ServerImpl;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.clientIdl.BlasterCallback;
import java.util.*;
import java.io.*;

/**
 * RequestBroker
 */
public class RequestBroker
{
   final private static String ME = "RequestBroker";

   private static RequestBroker requestBroker = null; // Singleton pattern

   final private Map messageContainerMap = Collections.synchronizedMap(new HashMap());

   final private ServerImpl serverImpl;

   private com.jclark.xsl.dom.XMLProcessorImpl xmlProc;  // One global instance to save instantiation time

   private com.sun.xml.tree.XmlDocument xmlKeyDoc = null;// Sun's DOM extensions, no portable
   //private org.w3c.dom.Document xmlKeyDoc = null;     // Document with the root node
   private org.w3c.dom.Node xmlKeyRootNode = null;    // Root node <xmlBlaster></xmlBlaster>

   /**
    * Access to RequestBroker singleton
    */
   public static RequestBroker getInstance(ServerImpl serverImpl) throws XmlBlasterException
   {
      synchronized (RequestBroker.class)
      {
         if (requestBroker == null)
            requestBroker = new RequestBroker(serverImpl);
      }
      return requestBroker;
   }


   /**
    * Access to RequestBroker singleton
    */
   public static RequestBroker getInstance()
   {
      synchronized (RequestBroker.class)
      {
         if (requestBroker == null) {
            Log.panic(ME, "Use other getInstance first");
         }
      }
      return requestBroker;
   }


   /**
    * private Constructor for Singleton Pattern
    */
   private RequestBroker(ServerImpl serverImpl) throws XmlBlasterException
   {
      this.serverImpl = serverImpl;
      this.xmlProc = new com.jclark.xsl.dom.SunXMLProcessorImpl();    // [ 75 millis ]

      /*
      // Instantiate the xmlBlaster DOM tree with <xmlBlaster> root node (DOM portable)
      String xml = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                   "<xmlBlaster></xmlBlaster>";
      java.io.StringReader reader = new java.io.StringReader(xml);
      org.xml.sax.InputSource input = new org.xml.sax.InputSource(reader);

      try {
         xmlKeyDoc = xmlProc.load(input);
      } catch (java.io.IOException e) {
         Log.error(ME+".IO", "Problems when building DOM tree from your XmlKey: " + e.toString());
         throw new XmlBlasterException(ME+".IO", "Problems when building DOM tree from your XmlKey: " + e.toString());
      } catch (org.xml.sax.SAXException e) {
         Log.error(ME+".SAX", "Problems when building DOM tree from your XmlKey: " + e.toString());
         throw new XmlBlasterException(ME+".SAX", "Problems when building DOM tree from your XmlKey: " + e.toString());
      }
      */
      
      // Using Sun's approach to be able to use  com.sun.xml.tree.XmlDocument::changeNodeOwner(node) later
      xmlKeyDoc = new com.sun.xml.tree.XmlDocument ();
      com.sun.xml.tree.ElementNode root = (com.sun.xml.tree.ElementNode) xmlKeyDoc.createElement ("xmlBlaster");
      xmlKeyDoc.appendChild(root);
      xmlKeyRootNode = xmlKeyDoc.getDocumentElement(); 
   }


   /**
    * Accessing the  XML to DOM parser
    */
   public com.jclark.xsl.dom.XMLProcessorImpl getXMLProcessorImpl()
   {
      return this.xmlProc;
   }


   /**
    * Adding a new node to the xmlBlaster xmlKey tree
    * @return the node added
    */
   public org.w3c.dom.Node addKeyNode(org.w3c.dom.Node node) throws XmlBlasterException
   {
      try {
         Log.info(ME, "addKeyNode=" + node.toString());
         xmlKeyDoc.changeNodeOwner(node);  // com.sun.xml.tree.XmlDocument::changeNodeOwner(node) // not DOM portable
         // !!!!!!! xmlKeyDoc.appendChild(node);
         Log.info(ME, "New tree=" + xmlKeyRootNode.toString());
         Writer          out = new OutputStreamWriter (System.out);
         xmlKeyDoc.write(out);
         return node;
      } catch (Exception e) {
         Log.error(ME+".addKeyNode", "Problems adding new key tree: " + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(ME+".addKeyNode", "Problems adding new key tree: " + e.toString());
      }
   }


   /**
    * Removing a node from the xmlBlaster xmlKey tree
    * @param The node removed
    */
   public org.w3c.dom.Node removeKeyNode(org.w3c.dom.Node node)
   {
      return xmlKeyRootNode.removeChild(node); 
   }


   /**
    */
   public void subscribe(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS subscribeQoS) throws XmlBlasterException
   {
      String uniqueKey = xmlKey.getUniqueKey();
      SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKey, subscribeQoS);
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(uniqueKey);
         MessageUnitHandler msg;
         if (obj == null) {
            msg = new MessageUnitHandler(this, subs);
            messageContainerMap.put(uniqueKey, msg);
         }
         else {
            msg = (MessageUnitHandler)obj;
         }
         msg.addSubscriber(subs);
      }
   }


   /**
    */
   public void unSubscribe(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS unSubscribeQoS) throws XmlBlasterException
   {
      String uniqueKey = xmlKey.getUniqueKey();

      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.remove(uniqueKey);
         if (obj == null) {
            Log.warning(ME + ".DoesntExist", "Sorry, can't unsubscribe, message unit doesn't exist: " + uniqueKey);
            throw new XmlBlasterException(ME + ".DoesntExist", "Sorry, can't unsubscribe, message unit doesn't exist: " + uniqueKey);
         }
         MessageUnitHandler msg = (MessageUnitHandler)obj;
         SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKey, unSubscribeQoS);
         int numRemoved = msg.removeSubscriber(subs);
         if (numRemoved < 1) {
            Log.warning(ME + ".NotSubscribed", "Sorry, can't unsubscribe, you never subscribed to " + uniqueKey);
            throw new XmlBlasterException(ME + ".NotSubscribed", "Sorry, can't unsubscribe, you never subscribed to " + uniqueKey);
         }
      }
   }


   /**
    */
   public int publish(MessageUnit[] messageUnitArr, String[] qos_literal_Arr) throws XmlBlasterException
   {
      // !!! TODO: handling of qos

      int retVal = 0;

      for (int ii=0; ii<messageUnitArr.length; ii++) {

         MessageUnit messageUnit = messageUnitArr[ii];
         XmlKey xmlKey = new XmlKey(messageUnit.xmlKey);
         MessageUnitHandler msgHandler;

         synchronized(messageContainerMap) {
            Object obj = messageContainerMap.get(xmlKey.getUniqueKey());
            if (obj == null) {
               msgHandler = new MessageUnitHandler(requestBroker, messageUnit);
               messageContainerMap.put(msgHandler.getUniqueKey(), msgHandler);
            }
            else {
               msgHandler = (MessageUnitHandler)obj;
               msgHandler.setContent(messageUnit.content);
            }
         }

         Map subscriberMap = msgHandler.getSubscriberMap();
         if (Log.TRACE) Log.trace(ME, "subscriberMap.size() = " + subscriberMap.size());

         // DANGER: The whole update blocks if one client blocks - needs a redesign !!!
         // PREFORMANCE: All updates for each client should be collected !!!
         synchronized(subscriberMap) {
            Iterator iterator = subscriberMap.values().iterator();

            while (iterator.hasNext())
            {
               SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
               if (Log.TRACE) Log.trace(ME, "xmlBlaster.update(" + xmlKey.getUniqueKey() + ") to " + sub.getClientInfo().toString());
               BlasterCallback cb = sub.getClientInfo().getCB();
               XmlQoSUpdate xmlQoS = new XmlQoSUpdate();
               MessageUnit[] marr = new MessageUnit[1];
               marr[0] = messageUnit;
               String[] qarr = new String[1];
               qarr[0] = xmlQoS.toString();
               cb.update(marr, qarr);
            }
         }

         retVal++;
      }

      return retVal;
   }


   /**
    */
   public int erase(XmlKey xmlKey, XmlQoS unSubscribeQoS) throws XmlBlasterException
   {
      String uniqueKey = xmlKey.getUniqueKey();
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.remove(uniqueKey);
         if (obj == null) {
            Log.warning(ME + ".NotRemoved", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
            return 0;
            // throw new XmlBlasterException(ME + ".NOT_REMOVED", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
         }
         else {
            obj = null;
            return 1;
         }
      }
   }
}

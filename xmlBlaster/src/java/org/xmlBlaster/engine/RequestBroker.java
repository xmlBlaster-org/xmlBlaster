/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: RequestBroker.java,v 1.13 1999/11/17 23:38:53 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.ServerImpl;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.clientIdl.BlasterCallback;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.ClientListener;
import org.xmlBlaster.authentication.ClientEvent;
import java.util.*;
import java.io.*;

/**
 * RequestBroker
 *
 * The interface ClientListener informs about Client login/logout
 */
public class RequestBroker implements ClientListener
{
   final private static String ME = "RequestBroker";

   private static RequestBroker requestBroker = null; // Singleton pattern

   /**
    * All MessageUnitHandler objects are stored in this map. 
    * <p>
    * key   = xmlKey.getUniqueKey()
    * value = MessageUnitHandler object
    */
   final private Map messageContainerMap = Collections.synchronizedMap(new HashMap());

   /**
    * All Subscriptions of a Client are in this map
    * A multimap would be appropriate, but since this is not supported
    * by the Collections API, a map with a set as value is used. 
    * <br>
    * Used for performant logout.
    * <p>
    * key   = client.getUniqueKey()
    * value = clientSubscriptionSet with SubscriptionInfo objects
    */
   final private Set clientSubscriptionSet = Collections.synchronizedSet(new HashSet());
   final private Map clientSubscriptionMap = Collections.synchronizedMap(new HashMap());

   /**
    * All generic subscriptions are collected here. 
    * Generic are all subscriptions who don't subscribe a precise key-oid,
    * but rather subscribe all MessageUnits matching a XPath query match.
    * <br>
    * If new MessageUnits are published, this set is consulted to check
    * if some older subscriptions would match as well
    * <p>
    * value = SubscriptionInfo objects with generic subscriptions, but not
    *         those, who subscribed a MessageUnit exactly by a known oid
    */
   final private Set subscribeRequestSet = Collections.synchronizedSet(new HashSet());

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

      serverImpl.getAuthenticate().addClientListener(this);
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
         xmlKeyRootNode.appendChild(node);
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

      /*
      if (xmlKey.isGeneratedOid()) { // subscription without a given oid
         subscriptionMultiMap.put();
      }
      */

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

      Object obj;
      synchronized(messageContainerMap) {
         obj = messageContainerMap.remove(uniqueKey);
      }
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


   /**
    * Invoked on successfull client login (interface ClientListener)
    */
   public void clientAdded(ClientEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Login event for client " + clientInfo.toString());
   }


   /**
    * Invoked when client does a logout (interface ClientListener)
    */    
   public void clientRemove(ClientEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      String name = clientInfo.toString();
      if (Log.TRACE) Log.trace(ME, "Logout event for client " + name);

      String uniqueKey = clientInfo.getUniqueKey();

      {
         Object obj;
         synchronized(clientSubscriptionMap) {
            obj = clientSubscriptionMap.remove(uniqueKey);
         }
         if (obj == null) {
            Log.warning(ME + ".ClientDoesntExist", "Sorry, can't logout, client " + name + " doesn't exist");
            throw new XmlBlasterException(ME + ".ClientDoesntExist", "Sorry, can't logout, client " + name + " doesn't exist");
         }
         Set subscriptionSet = (Set)obj;
         synchronized (subscriptionSet) {
            Iterator iterator = subscriptionSet.iterator();
            while (iterator.hasNext()) {
               SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
               sub.removeSubscribe(); // removes me from MessageUnitHandler::subscriberMap
            }
         }
      }

      {  // Slow linear search!!!!
         synchronized(subscribeRequestSet) {
            Iterator iterator = subscribeRequestSet.iterator();
            while (iterator.hasNext()) {
               SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
               if (sub.getClientInfo().getUniqueKey().equals(uniqueKey)) {
                  // !!! is this allowed?? or is it killing the iterator?????????
                  subscribeRequestSet.remove(sub);
               }
            }
         }
      }
   }

}

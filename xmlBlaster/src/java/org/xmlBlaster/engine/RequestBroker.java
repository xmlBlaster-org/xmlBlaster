/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: RequestBroker.java,v 1.26 1999/11/29 18:39:21 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlToDom;
import org.xmlBlaster.util.I_MergeDomNode;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.serverIdl.XmlBlasterException;
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
public class RequestBroker implements ClientListener, MessageEraseListener, I_MergeDomNode
{
   final private static String ME = "RequestBroker";

   private static RequestBroker requestBroker = null; // Singleton pattern

   /**
    * All MessageUnitHandler objects are stored in this map.
    * <p>
    * key   = messageUnithandler.getUniqueKey() == xmlKey.getUniqueKey() == oid value from <key oid="...">
    * value = MessageUnitHandler object
    */
   final private Map messageContainerMap = Collections.synchronizedMap(new HashMap());

   final private ClientSubscriptions clientSubscriptions;

   /**
    * For listeners who want to be informed about subscribe/unsubscribe events
    */
   final private Set subscriptionListenerSet = Collections.synchronizedSet(new HashSet());

   /**
    * For listeners who want to be informed about erase() of messages.
    */
   final private Set messageEraseListenerSet = Collections.synchronizedSet(new HashSet());

   private com.jclark.xsl.dom.XMLProcessorImpl xmlProc;  // One global instance to save instantiation time
   private com.fujitsu.xml.omquery.DomQueryMgr queryMgr;

   private com.sun.xml.tree.XmlDocument xmlKeyDoc = null;// Sun's DOM extensions, no portable
   //private org.w3c.dom.Document xmlKeyDoc = null;     // Document with the root node
   private org.w3c.dom.Node xmlKeyRootNode = null;    // Root node <xmlBlaster></xmlBlaster>


   /**
    * Access to RequestBroker singleton
    */
   public static RequestBroker getInstance(Authenticate authenticate) throws XmlBlasterException
   {
      synchronized (RequestBroker.class) {
         if (requestBroker == null) {
            requestBroker = new RequestBroker(authenticate);
         }
      }
      return requestBroker;
   }


   /**
    * Access to RequestBroker singleton
    */
   public static RequestBroker getInstance()
   {
      synchronized (RequestBroker.class) {
         if (requestBroker == null) {
            Log.panic(ME, "Use other getInstance first");
         }
      }
      return requestBroker;
   }


   /**
    * private Constructor for Singleton Pattern
    */
   private RequestBroker(Authenticate authenticate) throws XmlBlasterException
   {
      this.clientSubscriptions = ClientSubscriptions.getInstance(this, authenticate);

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

      authenticate.addClientListener(this);
      addMessageEraseListener(this);
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
    * @param the node to merge into the DOM tree
    * @return the node added
    */
   public org.w3c.dom.Node mergeNode(org.w3c.dom.Node node) throws XmlBlasterException
   {
      try {     // !!! synchronize is missing !!!
         Log.info(ME, "mergeNode=" + node.toString());

         xmlKeyDoc.changeNodeOwner(node);  // com.sun.xml.tree.XmlDocument::changeNodeOwner(node) // not DOM portable

         // !!! PENDING: If same key oid exists, remove the old and replace with new

         xmlKeyRootNode.appendChild(node);

         if (Log.TRACE) Log.trace(ME, "Successfully merged tree");

         if (Log.DUMP) {  // dump the whole tree
            Writer out = new OutputStreamWriter (System.out);
            xmlKeyDoc.write(out);
         }

         // !!! not performaning, should be instantiate only just before needed
         //     with stale check
         queryMgr = new com.fujitsu.xml.omquery.DomQueryMgr(xmlKeyDoc);

         return node;

      } catch (Exception e) {
         Log.error(ME+".mergeNode", "Problems adding new key tree: " + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(ME+".mergeNode", "Problems adding new key tree: " + e.toString());
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
    * Invoked by a client, to subscribe to one/many MessageUnit
    */
   public void subscribe(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS subscribeQoS) throws XmlBlasterException
   {
      Vector xmlKeyVec = parseKeyOid(xmlKeyDoc, clientInfo, xmlKey, subscribeQoS);
      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
         if (xmlKeyExact == null && xmlKey.getQueryType() == XmlKey.EXACT_QUERY) // subscription on a yet unknown message ...
            xmlKeyExact = xmlKey;
         SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKeyExact, subscribeQoS);
         subscribeToOid(subs);                // fires event for subscription
      }
   }


   /**
    * Invoked by a client, to subscribe to one/many MessageUnit
    */
   public MessageUnit[] get(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS subscribeQoS) throws XmlBlasterException
   {
      Vector xmlKeyVec = parseKeyOid(xmlKeyDoc, clientInfo, xmlKey, subscribeQoS);
      MessageUnit[] messageUnitArr = new MessageUnit[xmlKeyVec.size()];

      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
         if (xmlKeyExact == null && xmlKey.getQueryType() == XmlKey.EXACT_QUERY) // subscription on a yet unknown message ...
            xmlKeyExact = xmlKey;

         MessageUnitHandler messageUnitHandler = getMessageHandlerFromOid(xmlKeyExact.getUniqueKey());
         messageUnitArr[ii] = messageUnitHandler.getMessageUnit();;
      }

      return messageUnitArr;
   }


   /**
    * This method does the XPath query.
    *
    * @param clientName is only needed for nicer logging output
    * @return Array of matching XmlKey objects (may contain null elements)
    *
    * TODO: a query Handler, allowing drivers for REGEX, XPath, SQL, etc. queries
    */
   private Vector parseKeyOid(org.w3c.dom.Document xmlDoc/*, com.sun.xml.tree.XmlDocument xmlDoc*/,
                              ClientInfo clientInfo, XmlKey xmlKey, XmlQoS qos)  throws XmlBlasterException
   {
      Vector xmlKeyVec = new Vector();
      String clientName = clientInfo.toString();

      if (xmlKey.getQueryType() == XmlKey.XPATH_QUERY) { // query: subscription without a given oid

         // fires event for query subscription, this needs to be remembered
         // for a match check of future published messages
         fireSubscriptionEvent(new SubscriptionInfo(clientInfo, xmlKey, qos), true);

         Enumeration nodeIter;
         try {
            if (Log.TRACE) Log.trace(ME, "Goin' to query DOM tree with XPATH = " + xmlKey.getQueryString());
            nodeIter = queryMgr.getNodesByXPath(xmlDoc, xmlKey.getQueryString());
         } catch (Exception e) {
            Log.warning(ME + ".InvalidQuery", "Sorry, can't access, query snytax is wrong");
            throw new XmlBlasterException(ME + ".InvalidQuery", "Sorry, can't access, query snytax is wrong");
         }
         int n = 0;
         while (nodeIter.hasMoreElements()) {
            n++;
            Object obj = nodeIter.nextElement();
            com.sun.xml.tree.ElementNode node = (com.sun.xml.tree.ElementNode)obj;
            try {
               String uniqueKey = getKeyOid(node);
               Log.info(ME, "Client " + clientName + " is accessing message oid=\"" + uniqueKey + "\" after successfull query");
               xmlKeyVec.addElement(getXmlKeyFromOid(uniqueKey));
            } catch (Exception e) {
               e.printStackTrace();
               Log.error(ME, e.toString());
            }
         }
         Log.info(ME, n + " MessageUnits matched to subscription " + xmlKey.literal());
      }

      else if (xmlKey.getQueryType() == XmlKey.EXACT_QUERY) { // subscription with a given oid
         Log.info(ME, "Access Client " + clientName + " with EXACT oid=\"" + xmlKey.getUniqueKey() + "\"");
         XmlKey xmlKeyExact = getXmlKeyFromOid(xmlKey.getUniqueKey());
         xmlKeyVec.addElement(xmlKeyExact);
      }

      else {
         Log.warning(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
         throw new XmlBlasterException(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
      }

      return xmlKeyVec;
   }

   /**
    * @param oid == XmlKey:uniqueKey
    */
   public XmlKey getXmlKeyFromOid(String oid)
   {
      MessageUnitHandler messageUnitHandler = getMessageHandlerFromOid(oid);
      if (messageUnitHandler == null) {
         return null;
      }
      return messageUnitHandler.getXmlKey();
   }


   /**
    * @param oid == XmlKey:uniqueKey
    */
   public MessageUnitHandler getMessageHandlerFromOid(String oid)
   {
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(oid);
         if (obj == null) {
            if (Log.TRACE) Log.trace(ME, "getMessageHandlerFromOid(): key oid " + oid + " is unknown, messageUnitHandler == null");
            return null;
         }
         return (MessageUnitHandler)obj;
      }
   }


   /**
    */
   private String getKeyOid(org.w3c.dom.Node/*com.sun.xml.tree.ElementNode*/ node) throws XmlBlasterException
   {
      if (node == null) {
         Log.warning(ME+".NoParentNode", "no parent node found");
         throw new XmlBlasterException(ME+".NoParentNode", "no parent node found");
      }

      String nodeName = node.getNodeName();      // com.sun.xml.tree.ElementNode: getLocalName();

      if (nodeName.equals("xmlBlaster")) {       // ERROR: the root node, must be specialy handled
         Log.warning(ME+".NodeNotAllowed", "<xmlBlaster> node not allowed");
         throw new XmlBlasterException(ME+".NodeNotAllowed", "<xmlBlaster> node not allowed");
      }

      if (!nodeName.equals("key")) {
         return getKeyOid(node.getParentNode()); // w3c: getParentNode() sun: getParentImpl()
      }

      /* com.sun.xml.tree.ElementNode:
      org.w3c.dom.Attr keyOIDAttr = node.getAttributeNode("oid");
      if (keyOIDAttr != null)
         return keyOIDAttr.getValue();
      */

      // w3c conforming code:
      org.w3c.dom.NamedNodeMap attributes = node.getAttributes();
      if (attributes != null && attributes.getLength() > 0) {
         int attributeCount = attributes.getLength();
         for (int i = 0; i < attributeCount; i++) {
            org.w3c.dom.Attr attribute = (org.w3c.dom.Attr)attributes.item(i);
            if (attribute.getNodeName().equals("oid")) {
               String val = attribute.getNodeValue();
               // Log.trace(ME, "Found key oid=\"" + val + "\"");
               return val;
            }
         }
      }

      Log.warning(ME+".InternalKeyOid", "Internal getKeyOid() error");
      throw new XmlBlasterException(ME+".InternalKeyOid", "Internal getKeyOid() error");
   }


   /**
    * Low level subscribe, is called when the <key oid="..."> to subscribe is exactly known
    * @param uniqueKey from XmlKey - oid
    * @param subs
    */
   private void subscribeToOid(SubscriptionInfo subs) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Entering subscribeToOid() ...");
      String uniqueKey = subs.getXmlKey().getUniqueKey();
      MessageUnitHandler messageUnitHandler;
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(uniqueKey);
         if (obj == null) {
            // This is a new Message, yet unknown ...
            messageUnitHandler = new MessageUnitHandler(this, subs.getXmlKey());
            messageContainerMap.put(uniqueKey, messageUnitHandler);
         }
         else {
            // This message was known before ...
            messageUnitHandler = (MessageUnitHandler)obj;
         }
      }

      // Now the MessageUnit exists, subscribe to it
      messageUnitHandler.addSubscriber(subs);

      fireSubscriptionEvent(subs, true);  // inform all listeners about this new subscription
   }


   /**
    * SUPPORT FOR QUERY unSubscribe is still missing!!!
    */
   public void unSubscribe(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS unSubscribeQoS) throws XmlBlasterException
   {
      Vector xmlKeyVec = parseKeyOid(xmlKeyDoc, clientInfo, xmlKey, unSubscribeQoS);
      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
         if (xmlKeyExact == null) {
            Log.error(ME + ".OidUnknown", "Internal problem, can't access message, key oid is unknown: " + xmlKey.getUniqueKey());
            throw new XmlBlasterException(ME + ".OidUnknown", "Internal problem, can't access message, key oid is unknown: " + xmlKey.getUniqueKey());

         }
         SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKeyExact, unSubscribeQoS);
         fireSubscriptionEvent(subs, false);
      }
   }


   /**
    * if MessageUnit is created from subscribe or MessageUnit is new, we need to add the
    * DOM here once; XmlKeyBase takes care of that
    *
    * @see xmlBlaster.idl for comments
    */
   public String publish(ClientInfo clientInfo, MessageUnit messageUnit, XmlQoS xmlQoS) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering publish() ...");

      if (messageUnit == null || xmlQoS==null) {
         Log.error(ME + ".InvalidArguments", "The arguments of method publish() are invalid (null)");
         throw new XmlBlasterException(ME + ".InvalidArguments", "The arguments of method publish() are invalid (null)");
      }

      // !!! TODO: handling of qos

      XmlKey xmlKey = new XmlKey(messageUnit.xmlKey, true);

      String retVal = xmlKey.getUniqueKey(); // id <key oid=""> was empty, there was a new oid generated

      //----- 1. set new value or create the new message:
      MessageUnitHandler messageUnitHandler = setMessageUnit(xmlKey, messageUnit);

      // this gap is not 100% thread save

      //----- 2. check all known query subscriptions if the new message fits as well
      if (messageUnitHandler.isNewCreated()) {
         messageUnitHandler.setNewCreatedFalse();

         if (Log.TRACE) Log.trace(ME, "Checking existing query subscriptions if they match with this new one");

         Set set = clientSubscriptions.getQuerySubscribeRequestsSet();
         org.w3c.dom.Document newXmlDoc = xmlKey.getXmlDoc();

         Vector matchingSubsVec = new Vector();
         synchronized (set) {
            Iterator iterator = set.iterator();
            while (iterator.hasNext()) {

               SubscriptionInfo existingQuerySubscription = (SubscriptionInfo)iterator.next();
               XmlKey queryXmlKey = existingQuerySubscription.getXmlKey();

               if (queryXmlKey.getQueryType() == XmlKey.XPATH_QUERY) { // query: subscription without a given oid

                  Vector matchVec = parseKeyOid(newXmlDoc, clientInfo, queryXmlKey, xmlQoS);

                  if (matchVec != null && matchVec.size() == 1 && matchVec.elementAt(0) != null) {
                     if (Log.TRACE) Log.trace(ME, "The new xmlKey=" + xmlKey.getUniqueKey() + " is matching the existing query subscription " + queryXmlKey.getUniqueKey());
                     SubscriptionInfo subs = new SubscriptionInfo(existingQuerySubscription.getClientInfo(),
                                                                  xmlKey,
                                                                  existingQuerySubscription.getXmlQoS());
                     matchingSubsVec.addElement(subs);
                  }
                  else {
                     if (Log.TRACE) Log.trace(ME, "The new xmlKey=" + xmlKey.getUniqueKey() + " does NOT match the existing query subscription " + queryXmlKey.getUniqueKey());
                  }
               }
               else {
                  Log.error(ME, "REGEX check for existing query subscriptions is still missing");
               }
            }

            // now after closing the synchronized block, me may fire the events
            // doing it inside the synchronized could cause a deadlock
            for (int ii=0; ii<matchingSubsVec.size(); ii++) {
               subscribeToOid((SubscriptionInfo)matchingSubsVec.elementAt(ii));    // fires event for subscription
            }
         }
      }

      //----- 3. now we can send updates to all interested clients:
      messageUnitHandler.invokeCallback();

      return retVal;
   }


   /**
    * if MessageUnit is created from subscribe or MessageUnit is new, we need to add the
    * DOM here once; XmlKeyBase takes care of that
    *
    * @see xmlBlaster.idl for comments
    */
   public String[] publish(ClientInfo clientInfo, MessageUnit[] messageUnitArr, String[] qos_literal_Arr) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering publish(array) ...");

      if (messageUnitArr == null || qos_literal_Arr==null || messageUnitArr.length != qos_literal_Arr.length) {
         Log.error(ME + ".InvalidArguments", "The arguments of method publishArr() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments", "The arguments of method publishArr() are invalid");
      }

      // !!! TODO: handling of qos

      String[] returnArr = new String[messageUnitArr.length];
      for (int ii=0; ii<messageUnitArr.length; ii++) {
         XmlQoS xmlQoS = new XmlQoS(qos_literal_Arr[ii]);
         returnArr[ii] = publish(clientInfo, messageUnitArr[ii], xmlQoS);
      }

      return returnArr;
   }


   /**
    * Store or update a new arrived message.
    *
    * @param xmlKey       so the messageUnit.xmlKey_literal is not parsed twice
    * @param messageUnit  containing the new, published data
    * @return messageUnitHandler MessageUnitHandler object, holding the new MessageUnit
    */
   private MessageUnitHandler setMessageUnit(XmlKey xmlKey, MessageUnit messageUnit) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Store the new arrived message ...");
      boolean messageExisted = false; // to shorten the synchronize block
      MessageUnitHandler messageUnitHandler=null;

      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(xmlKey.getUniqueKey());
         if (obj == null) {
            messageUnitHandler = new MessageUnitHandler(requestBroker, xmlKey, messageUnit);
            messageContainerMap.put(xmlKey.getUniqueKey(), messageUnitHandler);
         }
         else {
            messageUnitHandler = (MessageUnitHandler)obj;
            messageExisted = true;
         }
      }

      if (messageExisted) {
         messageUnitHandler.setContent(xmlKey, messageUnit.content);
      }
      else {
         try {
            xmlKey.mergeRootNode(this);  // merge the message DOM tree into the big xmlBlaster DOM tree
         } catch (XmlBlasterException e) {
            synchronized(messageContainerMap) {
               messageContainerMap.remove(xmlKey.getUniqueKey()); // it didn't exist before, so we have to clean up
            }
            throw new XmlBlasterException(e.id, e.reason);
         }
      }
      return messageUnitHandler;
   }


   /**
    * Client wants to erase a message
    */
   public String[] erase(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS qoS) throws XmlBlasterException
   {
      Vector xmlKeyVec = parseKeyOid(xmlKeyDoc, clientInfo, xmlKey, qoS);
      String[] oidArr = new String[xmlKeyVec.size()];

      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
         if (xmlKeyExact == null && xmlKey.getQueryType() == XmlKey.EXACT_QUERY) // subscription on a yet unknown message ...
            xmlKeyExact = xmlKey;

         MessageUnitHandler messageUnitHandler = getMessageHandlerFromOid(xmlKeyExact.getUniqueKey());
         oidArr[ii] = messageUnitHandler.getUniqueKey();
         try {
            fireMessageEraseEvent(clientInfo, messageUnitHandler);
         } catch (XmlBlasterException e) {
         }
         messageUnitHandler.erase();
         messageUnitHandler = null;
      }

      return oidArr;

   }


   /**
    * Invoked on message erase() invocation (interface MessageEraseListener)
    */
   public void messageErase(MessageEraseEvent e) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Erase event occured ...");

      MessageUnitHandler messageUnitHandler = e.getMessageUnitHandler();
      String uniqueKey = messageUnitHandler.getUniqueKey();
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.remove(uniqueKey);
         if (obj == null) {
            Log.warning(ME + ".NotRemoved", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
            throw new XmlBlasterException(ME + ".NOT_REMOVED", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
         }
      }
      org.w3c.dom.Node node = removeKeyNode(messageUnitHandler.getRootNode());
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
      if (Log.TRACE) Log.trace(ME, "Logout event for client " + clientInfo.toString());
   }


   /**
    * Adds the specified subscription listener to receive subscribe/unsubscribe events
    */
   public void addSubscriptionListener(SubscriptionListener l) {
      if (l == null) {
         return;
      }
      synchronized (subscriptionListenerSet) {
         subscriptionListenerSet.add(l);
      }
   }


   /**
    * Removes the specified listener
    */
   public void removeSubscriptionListener(SubscriptionListener l) {
      if (l == null) {
         return;
      }
      synchronized (subscriptionListenerSet) {
         subscriptionListenerSet.remove(l);
      }
   }


   /**
    * Is fired on unSubscribe() and several times on erase()
    */
   public final void fireSubscriptionEvent(SubscriptionInfo subscriptionInfo, boolean subscribe) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Going to fire fireSubscriptionEvent() ...");
      synchronized (subscriptionListenerSet) {
         Iterator iterator = subscriptionListenerSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionListener subli = (SubscriptionListener)iterator.next();
            if (subscribe)
               subli.subscriptionAdd(new SubscriptionEvent(subscriptionInfo));
            else
               subli.subscriptionRemove(new SubscriptionEvent(subscriptionInfo));
         }
      }
   }


   /**
    * Adds the specified messageErase listener to receive subscribe/unsubscribe events
    */
   public void addMessageEraseListener(MessageEraseListener l) {
      if (l == null) {
         return;
      }
      synchronized (messageEraseListenerSet) {
         messageEraseListenerSet.add(l);
      }
   }


   /**
    * Removes the specified listener
    */
   public void removeMessageEraseListener(MessageEraseListener l) {
      if (l == null) {
         return;
      }
      synchronized (messageEraseListenerSet) {
         messageEraseListenerSet.remove(l);
      }
   }


   /**
    */
   private final void fireMessageEraseEvent(ClientInfo clientInfo, MessageUnitHandler messageUnitHandler) throws XmlBlasterException
   {
      synchronized (messageEraseListenerSet) {
         Iterator iterator = messageEraseListenerSet.iterator();
         while (iterator.hasNext()) {
            MessageEraseListener erLi = (MessageEraseListener)iterator.next();
            erLi.messageErase(new MessageEraseEvent(clientInfo, messageUnitHandler));
         }
      }
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of RequestBroker
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of RequestBroker
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      Iterator iterator = messageContainerMap.values().iterator();

      sb.append(offset + "<RequestBroker>");
      while (iterator.hasNext()) {
         MessageUnitHandler messageUnitHandler = (MessageUnitHandler)iterator.next();
         sb.append(messageUnitHandler.printOn(extraOffset + "   ").toString());
      }

      try {
         java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
         xmlKeyDoc.write(out);
         StringTokenizer st = new StringTokenizer(out.toString(), "\n");
         while (st.hasMoreTokens()) {
            sb.append(offset + "   " + st.nextToken());
         }
      } catch (Exception e) { }
      sb.append(offset + "</RequestBroker>\n");

      return sb;
   }
}

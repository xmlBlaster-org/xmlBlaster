/*------------------------------------------------------------------------------
Name:      XmlBlasterProxy.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native xmlBlaster Proxy. Can be called by the client in the same VM
Version:   $Id: XmlBlasterProxy.java,v 1.6 2000/10/18 20:45:43 ruff Exp $
Author:    michele.laghi@attglobal.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;

import java.io.IOException;
import java.util.Vector;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.protocol.AbstractCallbackExtended;
import org.xmlBlaster.util.protocol.ProtoConverter;

import org.xmlBlaster.client.PublishKeyWrapper; // just for testing
import org.xmlBlaster.client.SubscribeKeyWrapper; // just for testing

import helma.xmlrpc.XmlRpcClient;
import helma.xmlrpc.WebServer;
import helma.xmlrpc.XmlRpcException;


/**
 * This is an xmlBlaster proxy. It implements the interface I_XmlBlaster
 * through AbstractCallbackExtended. The client can invoke it as if the
 * xmlBlaster would be on the same VM, making this way the xml-rpc protocol
 * totally transparent.
 * <p />
 * @author michele.laghi@attglobal.net
 */
public class XmlBlasterProxy extends AbstractCallbackExtended
   implements org.xmlBlaster.protocol.I_XmlBlaster
{
   private String ME = "XmlBlasterProxy";
   protected String url = "http://localhost:8080"; // address of xmlBlaster server
   protected int callbackPort = 8081; // port on which to listen to callback
   private XmlRpcClient xmlRpcClient = null; // xml-rpc client to send method calls.
   protected WebServer webServer = null;

   /**
    * One instance of this represents one xmlBlaster server.
    * @param url The complete url of the xmlBlaster server. For example
    *            "http://localhost:8080".
    * @param callbackPort The port on which this proxy listens for incoming
    *                   callbacks (for example: 8081).
    */
   public XmlBlasterProxy (String url, int callbackPort)
      throws XmlBlasterException
   {
      try {
         this.url          = url;
         this.callbackPort = callbackPort;
         this.xmlRpcClient = new XmlRpcClient(url);
         Log.info(ME, "Created XmlRpc client to " + url);

         // similar to -Dsax.driver=com.sun.xml.parser.Parser
         System.setProperty("sax.driver", XmlBlasterProperty.get("sax.driver", "com.sun.xml.parser.Parser"));

         // start the WebServer object here (to receive callbacks)
         webServer = new WebServer(callbackPort);
         webServer.addHandler("$default", this); // register update() method in this class
         Log.info(ME, "Created XmlRpc callback web server on port " + callbackPort);
      }

      catch (java.net.MalformedURLException e) {
         Log.error(ME+".constructor", "Maleformed URL: " + e.toString());
         throw new XmlBlasterException("Maleformed URL", "MaleformedURLException");
      }

      catch (IOException e1) {
         Log.error(ME+".constructor", "IO Exception: " + e1.toString());
         throw new XmlBlasterException("IO Exception", e1.toString());
      }

   }


   /**
    * Subscribe to messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String subscribe (String sessionId, XmlKey xmlKey,
                                  SubscribeQoS subscribeQoS) throws XmlBlasterException
   {
      // convert xmlKey & subscribeQoS to strings (xml literals)
      String xmlKey_literal = xmlKey.toXml();
      String subscribeQoS_literal = subscribeQoS.toXml();

      // call the corresponding method with the signature for literals
      return subscribe(sessionId, xmlKey_literal, subscribeQoS_literal);
   }


   /**
    * Subscribe to messages.
    * <p />
    */
   public final String subscribe (String sessionId, String xmlKey_literal,
                                 String qos_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering subscribe(id=" + sessionId + ")");
      try {
         // prepare the argument vector for the xml-rpc method call
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);
         return (String)xmlRpcClient.execute("xmlBlaster.subscribe", args);
      }

      catch (ClassCastException e) {
         Log.error(ME+".subscribe", "return value not a valid String: " + e.toString());
         throw new XmlBlasterException(ME+".subscribe", "return value not a valid String, Class Cast Exception");
      }

      catch (IOException e1) {
         Log.error(ME+".subscribe", "IO exception: " + e1.toString());
         throw new XmlBlasterException(ME+".subscribe", e1.toString());
      }
      catch (XmlRpcException e2) {
         Log.error(ME+".subscribe", "xml-rpc exception: " + e2.toString());
         throw new XmlBlasterException(ME+".subscribe", e2.toString());
      }

   }


   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void unSubscribe (String sessionId, XmlKey xmlKey,
                                  UnSubscribeQoS unSubscribeQoS) throws XmlBlasterException
   {
      // convert XmlKey & UnSubscribeQoS to literals
      String xmlKey_literal = xmlKey.toXml();
      String qos_literal    = unSubscribeQoS.toXml();

      unSubscribe(sessionId, xmlKey_literal, qos_literal);
   }


   /**
    * Unsubscribe from messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final void unSubscribe (String sessionId, String xmlKey_literal,
                                 String qos_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering unsubscribe(): id=" + sessionId);

      try {
         // prepare the argument list:
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         xmlRpcClient.execute("xmlBlaster.unSubscribe", args);
      }

      catch (IOException e1) {
         Log.error(ME+".unSubscribe", "IO exception: " + e1.toString());
         throw new XmlBlasterException("IO exception", e1.toString());
      }
      catch (XmlRpcException e2) {
         Log.error(ME+".unSubscribe", "xml-rpc exception: " + e2.toString());
         throw new XmlBlasterException("xml-rpc exception", e2.toString());
      }

   }



   /**
    * Publish a message.
    */
   public final String publish (String sessionId, XmlKey xmlKey, MessageUnit msgUnit,
                                PublishQoS publishQoS) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering publish(): id=" + sessionId);

      String xmlKey_literal = xmlKey.toXml();
      String publishQoS_literal = publishQoS.toXml();

      try {
         // convert from MessageUnit to Vector
         Vector msgUnitWrap = ProtoConverter.messageUnit2Vector(msgUnit);

         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(msgUnitWrap);
         args.addElement(publishQoS_literal);

         return (String)xmlRpcClient.execute("xmlBlaster.publish", args);
      }

      catch (ClassCastException e) {
         Log.error(ME+".publish", "not a valid MessageUnit: " + e.toString());
         throw new XmlBlasterException("Not a valid Message Unit", "Class Cast Exception");
      }

      catch (IOException e1) {
         Log.error(ME+".publish", "IO exception: " + e1.toString());
         throw new XmlBlasterException("IO exception", e1.toString());
      }
      catch (XmlRpcException e2) {
         Log.error(ME+".publish", "xml-rpc exception: " + e2.toString());
         throw new XmlBlasterException("xml-rpc exception", e2.toString());
      }
   }


   /**
    * Publish a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String publish (String sessionId, MessageUnit msgUnit)
      throws XmlBlasterException
   {
      XmlKey xmlKey = new XmlKey(msgUnit.xmlKey, true);
      PublishQoS publishQoS = new PublishQoS(msgUnit.qos);

      return publish(sessionId, xmlKey, msgUnit, publishQoS);

   }



   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] publishArr (String sessionId, MessageUnit[] msgUnitArr)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering publishArr: id=" + sessionId);

      if (msgUnitArr == null) {
         Log.error(ME + ".InvalidArguments", "The argument of method publishArr() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments",
                                       "The argument of method publishArr() are invalid");
      }

      try {

         Vector msgUnitArrWrap = ProtoConverter.messageUnitArray2Vector(msgUnitArr);
         // prepare the argument list (as a Vector)
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(msgUnitArrWrap);

         Vector returnVectorWrap = (Vector)xmlRpcClient.execute("xmlBlaster.publishArr", args);

      // re-convert the resuts to String[]
         return ProtoConverter.vector2StringArray(returnVectorWrap);
      }

      catch (ClassCastException e) {
         Log.error(ME+".publishArr", "not a valid String[]: " + e.toString());
         throw new XmlBlasterException("Not a valid String[]", "Class Cast Exception");
      }

      catch (IOException e1) {
         Log.error(ME+".publishArr", "IO exception: " + e1.toString());
         throw new XmlBlasterException("IO exception", e1.toString());
      }
      catch (XmlRpcException e2) {
         Log.error(ME+".publishArr", "xml-rpc exception: " + e2.toString());
         throw new XmlBlasterException("xml-rpc exception", e2.toString());
      }

   }



   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase (String sessionId, XmlKey xmlKey, EraseQoS eraseQoS)
      throws XmlBlasterException
   {

      String xmlKey_literal = xmlKey.toXml();
      String eraseQoS_literal = eraseQoS.toXml();

      return erase(sessionId, xmlKey_literal, eraseQoS_literal);
   }



   /**
    * Delete messages.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] erase (String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering erase() id=" + sessionId);

      try {
         // prepare the argument list (as a Vector) for xml-rpc
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         Vector vec = (Vector)xmlRpcClient.execute("xmlBlaster.erase", args);
         return ProtoConverter.vector2StringArray(vec);

      }

      catch (ClassCastException e) {
         Log.error(ME+".erase", "not a valid Vector: " + e.toString());
         throw new XmlBlasterException("Not a valid Vector", "Class Cast Exception");
      }

      catch (IOException e1) {
         Log.error(ME+".erase", "IO exception: " + e1.toString());
         throw new XmlBlasterException("IO exception", e1.toString());
      }

      catch (XmlRpcException e2) {
         Log.error(ME+".erase", "xml-rpc exception: " + e2.toString());
         throw new XmlBlasterException("xml-rpc exception", e2.toString());
      }

   }



   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MessageUnit[] get (String sessionId, XmlKey xmlKey, GetQoS getQoS)
      throws XmlBlasterException
   {
      String xmlKey_literal = xmlKey.toXml();
      String getQoS_literal = getQoS.toXml();

      return get(sessionId, xmlKey_literal, getQoS_literal);

   }


   /**
    * Synchronous access a message.
    * <p />
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final MessageUnit[] get (String sessionId, String xmlKey_literal,
                                  String qos_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering get() xmlKey=\n" + xmlKey_literal + ") ...");

      try {
         Vector args = new Vector();
         args.addElement(sessionId);
         args.addElement(xmlKey_literal);
         args.addElement(qos_literal);

         Vector retVector = (Vector)xmlRpcClient.execute("xmlBlaster.get", args);
         // convert the vector of vectors to a MessageUnit[] type
         return ProtoConverter.vector2MessageUnitArray(retVector);

      }

      catch (ClassCastException e) {
         Log.error(ME+".get", "not a valid Vector: " + e.toString());
         throw new XmlBlasterException("Not a valid Vector", "Class Cast Exception");
      }

      catch (IOException e1) {
         Log.error(ME+".get", "IO exception: " + e1.toString());
         throw new XmlBlasterException("IO exception", e1.toString());
      }

      catch (XmlRpcException e2) {
         Log.error(ME+".get", "xml-rpc exception: " + e2.toString());
         throw new XmlBlasterException("xml-rpc exception", e2.toString());
      }
   }


   public String toXml () throws XmlBlasterException
   {
      return toXml("");
   }


   public String toXml (String extraOffset) throws XmlBlasterException
   {
      try {
         Vector args = new Vector();
         args.addElement(extraOffset);
         return (String)xmlRpcClient.execute("xmlBlaster.toXml", args);
      }

      catch (ClassCastException e) {
         Log.error(ME+".toXml", "not a valid Vector: " + e.toString());
         throw new XmlBlasterException("Not a valid Vector", "Class Cast Exception");
      }

      catch (IOException e1) {
         Log.error(ME+".toXml", "IO exception: " + e1.toString());
         throw new XmlBlasterException("IO exception", e1.toString());
      }

      catch (XmlRpcException e2) {
         Log.error(ME+".toXml", "xml-rpc exception: " + e2.toString());
         throw new XmlBlasterException("xml-rpc exception", e2.toString());
      }

   }



   // The following methods are not defined in AbstractCallbackExtended

   /**
    * Does a login, returns a handle to xmlBlaster interface.
    * <p />
    * @param loginName The unique login name
    * @param password
    * @return sessionId The unique ID for this client
    * @exception XmlBlasterException If user is unknown
    */
   public String login(String loginName, String password, String qos_literal,
                        String sessionId)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering login: name=" + loginName);

      try {
         // prepare the argument vector for the xml-rpc method call
         Vector args = new Vector();
         args.addElement(loginName);
         args.addElement(password);
         args.addElement(qos_literal);
         args.addElement(sessionId);
         return (String)xmlRpcClient.execute("authenticate.login", args);
      }

      catch (ClassCastException e) {
         Log.error(ME+".login", "return value not a valid String: " + e.toString());
         throw new XmlBlasterException("return value not a valid String",
                                       "Class Cast Exception");
      }

      catch (IOException e1) {
         Log.error(ME+".login", "IO exception: " + e1.toString());
         throw new XmlBlasterException("IO exception", e1.toString());
      }
      catch (XmlRpcException e2) {
         Log.error(ME+".login", "xml-rpc exception: " + e2.toString());
         throw new XmlBlasterException("xml-rpc exception", e2.toString());
      }

   }



   /**
    * Does a logout.
    * <p />
    * @param sessionId The client sessionId
    * @exception XmlBlasterException If sessionId is invalid
    */
   public void logout(final String sessionId) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering logout: id=" + sessionId);

      try {
         // prepare the argument vector for the xml-rpc method call
         Vector args = new Vector();
         args.addElement(sessionId);
         xmlRpcClient.execute("authenticate.logout", args);
      }

      catch (IOException e1) {
         Log.error(ME+".logout", "IO exception: " + e1.toString());
         throw new XmlBlasterException("IO exception", e1.toString());
      }
      catch (XmlRpcException e2) {
         Log.error(ME+".logout", "xml-rpc exception: " + e2.toString());
         throw new XmlBlasterException("xml-rpc exception", e2.toString());
      }
   }


   /**
    * The update method. 
    * Gets invoked from xmlBlaster callback via client WebServer.
    */
   public void update (String loginName, UpdateKey updateKey, byte[] content,
                       UpdateQoS updateQoS)
   {
      if (Log.CALL) Log.call(ME, "Entering update(): loginName: " + loginName);
      // insert here what you want to do with the message....
      Log.info(ME, "THE UPDATE HAS BEEN CALLED SUCCESSFULLY !!!! ");
      if (Log.TRACE) Log.trace(ME, "The message sent is: ");
      if (Log.TRACE) Log.trace(ME, new String(content));
   }



   /**
    * For Testing.
    * <pre>
    * java -Dsax.driver=com.sun.xml.parser.Parser org.xmlBlaster.client.protocol.xmlrpc.XmlBlasterProxy
    * </pre>
    */
   public static void main(String args[])
   {
      final String ME = "XmlRpcHttpClient";
      try { XmlBlasterProperty.init(args); } catch(org.jutils.JUtilsException e) { Log.panic(ME, e.toString()); }
      // build the proxy
      try {
         XmlBlasterProxy proxy = new XmlBlasterProxy("http://localhost:8080", 8081);

         String qos = "<qos><callback type='XML-RPC'>http://localhost:8081</callback></qos>";
         String sessionId = "Session1";

         String loginAnswer = proxy.login("LunaMia", "silence", qos, sessionId);
         Log.info(ME, "The answer from the login is: " + loginAnswer);

         String contentString = "This is a simple Test Message for the xml-rpc Protocol";
         byte[] content = contentString.getBytes();

         PublishKeyWrapper xmlKey = new PublishKeyWrapper("", "text/xml", null);

         MessageUnit msgUnit = new MessageUnit(xmlKey.toXml(), content, "<qos></qos>");
         String publishOid = proxy.publish(sessionId, msgUnit);
         Log.info(ME, "Published message with " + publishOid);

         SubscribeKeyWrapper subscribeKey = new SubscribeKeyWrapper(publishOid);

         Log.info(ME, "Subscribe key: " + subscribeKey.toXml());

         proxy.subscribe(sessionId, subscribeKey.toXml(), "");

         // wait some time if necessary ....
         proxy.erase(sessionId, subscribeKey.toXml(), "");

         Log.exit(ME, "Good bye.");

      } catch(XmlBlasterException e) {
         Log.error(ME, "XmlBlasterException: " + e.toString());
      }

      // wait for some time here ....

   }


}


/*------------------------------------------------------------------------------
Name:      ServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Implementing the CORBA xmlBlaster-server interface
           $Revision $
           $Date: 1999/11/11 12:03:06 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.serverIdl;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.XmlKey;
import org.xmlBlaster.engine.XmlQoS;
import java.util.*;


/**
<p>
<ul>
<li> <b>Java Class</b> org.xmlBlaster.ServerImpl
<li> <b>Source File</b> org/xmlBlaster/ServerImpl.java
<li> <b>IDL Source File</b> xmlBlaster.idl
<li> <b>IDL Absolute Name</b> ::org::xmlBlaster::Server
<li> <b>Repository Identifier</b> IDL:org/xmlBlaster/Server:1.0
</ul>
<b>IDL definition:</b>
<pre>
    #pragma prefix "org/xmlBlaster"
    interface Server {
      void initCallback(
        in Object iorCallback
      );
      void subscribe(
        in ::org::xmlBlaster::XmlType xmlKey,
        in ::org::xmlBlaster::XmlType qos
      )
      raises(
        ::org::xmlBlaster::XmlBlasterException
      );
      void unSubscribe(
        in ::org::xmlBlaster::XmlType xmlKey,
        in ::org::xmlBlaster::XmlType qos
      )
      raises(
        ::org::xmlBlaster::XmlBlasterException
      );
      long set(
        in ::org::xmlBlaster::XmlType xmlKey,
        in ::org::xmlBlaster::ContentType content
      )
      raises(
        ::org::xmlBlaster::XmlBlasterException
      );
      long setQos(
        in ::org::xmlBlaster::XmlType xmlKey,
        in ::org::xmlBlaster::ContentType content,
        in ::org::xmlBlaster::XmlType qos
      )
      raises(
        ::org::xmlBlaster::XmlBlasterException
      );
      long erase(
        in ::org::xmlBlaster::XmlType xmlKey,
        in ::org::xmlBlaster::XmlType qos
      )
      raises(
        ::org::xmlBlaster::XmlBlasterException
      );
    };
</pre>
</p>
*/
//public class ServerImpl extends ServerPOA {
public class ServerImpl implements ServerOperations {

  private final String ME = "ServerImpl";
  private org.omg.CORBA.ORB orb;
  private RequestBroker requestBroker;


  /** Construct a persistently named object.
   */
  public ServerImpl(org.omg.CORBA.ORB orb) {
    if (Log.CALLS) Log.trace(ME, "Entering constructor with ORB argument");
    this.orb = orb;
    this.requestBroker = RequestBroker.getInstance(this);
  }


  /** Construct a transient object.
   */
  public ServerImpl() {
    super();
    if (Log.CALLS) Log.trace(ME, "Entering constructor without ORB argument");
    this.requestBroker = RequestBroker.getInstance(this);
  }


  /**
  <p>
  Operation: <b>::org::xmlBlaster::Server::initCallback</b>.
  <pre>
    #pragma prefix "org/xmlBlaster/Server"
    void initCallback(
      in Object iorCallback
    );
  </pre>
  </p>
  */
  public void initCallback(
    org.omg.CORBA.Object iorCallback
  ) {
    // IMPLEMENT: Operation
  }


  /**
  <p>
  Operation: <b>::org::xmlBlaster::Server::subscribe</b>.
  <pre>
    #pragma prefix "org/xmlBlaster/Server"
    void subscribe(
      in ::org::xmlBlaster::XmlType xmlKey,
      in ::org::xmlBlaster::XmlType qos
    )
    raises(
      ::org::xmlBlaster::XmlBlasterException
    );
  </pre>
  </p>
  */
  public void subscribe(String xmlKey, String qos) throws XmlBlasterException {
    if (Log.CALLS) Log.trace(ME, "Got subscribe request: xmlKey=" + xmlKey + ", qos=" + qos);
    XmlKey keyObj = new XmlKey(xmlKey);
    XmlQoS qosObj = new XmlQoS(qos);
    requestBroker.subscribe(keyObj, qosObj);
  }


  /**
  <p>
  Operation: <b>::org::xmlBlaster::Server::unSubscribe</b>.
  <pre>
    #pragma prefix "org/xmlBlaster/Server"
    void unSubscribe(
      in ::org::xmlBlaster::XmlType xmlKey,
      in ::org::xmlBlaster::XmlType qos
    )
    raises(
      ::org::xmlBlaster::XmlBlasterException
    );
  </pre>
  </p>
  */
  public void unSubscribe(
    java.lang.String xmlKey,
    java.lang.String qos
  ) throws
    XmlBlasterException {
    // IMPLEMENT: Operation
  }


  /**
  <p>
  Operation: <b>::org::xmlBlaster::Server::set</b>.
  <pre>
    #pragma prefix "org/xmlBlaster/Server"
    long set(
      in ::org::xmlBlaster::XmlType xmlKey,
      in ::org::xmlBlaster::ContentType content
    )
    raises(
      ::org::xmlBlaster::XmlBlasterException
    );
  </pre>
  </p>
  */
  public int set(String xmlKey_literal, byte[] content) throws XmlBlasterException {

    XmlKey xmlKey = new XmlKey(xmlKey_literal);
    if (Log.CALLS) Log.trace(ME, "Entering xmlBlaster.set(" + xmlKey.getUniqueKey() + ")");
    return requestBroker.set(xmlKey, content);
  }


  /**
  <p>
  Operation: <b>::org::xmlBlaster::Server::setQos</b>.
  <pre>
    #pragma prefix "org/xmlBlaster/Server"
    long setQos(
      in ::org::xmlBlaster::XmlType xmlKey,
      in ::org::xmlBlaster::ContentType content,
      in ::org::xmlBlaster::XmlType qos
    )
    raises(
      ::org::xmlBlaster::XmlBlasterException
    );
  </pre>
  </p>
  */
  public int setQos(
    java.lang.String xmlKey,
    byte[] content,
    java.lang.String qos
  ) throws
    XmlBlasterException {
    // IMPLEMENT: Operation
    return 0;
  }
  /**
  <p>
  Operation: <b>::org::xmlBlaster::Server::erase</b>.
  <pre>
    #pragma prefix "org/xmlBlaster/Server"
    long erase(
      in ::org::xmlBlaster::XmlType xmlKey,
      in ::org::xmlBlaster::XmlType qos
    )
    raises(
      ::org::xmlBlaster::XmlBlasterException
    );
  </pre>
  </p>
  */
  public int erase(
    java.lang.String xmlKey,
    java.lang.String qos
  ) throws
    XmlBlasterException {
    // IMPLEMENT: Operation
    return 0;
  }


   /**
    * !!! This is the wrong place: But where shall i put it?
    */
   public org.xmlBlaster.clientIdl.BlasterCallback getBlasterCallback(String callbackIOR)
   {
      return org.xmlBlaster.clientIdl.BlasterCallbackHelper.narrow(orb.string_to_object(callbackIOR));
   }
}

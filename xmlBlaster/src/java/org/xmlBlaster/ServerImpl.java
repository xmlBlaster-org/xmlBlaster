package org.xmlBlaster;
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
public class ServerImpl extends ServerPOA {
  private String name;

  /** Construct a persistently named object. */
  public ServerImpl(java.lang.String name) {
    this.name = name;
  }
  /** Construct a transient object. */
  public ServerImpl() {
    super();
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
  public void subscribe(
    java.lang.String xmlKey,
    java.lang.String qos
  ) throws
    org.xmlBlaster.XmlBlasterException {
    System.out.println("Got subscribe request: xmlKey=" + xmlKey + ", qos=" + qos);
    // IMPLEMENT: Operation
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
    org.xmlBlaster.XmlBlasterException {
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
  public int set(
    java.lang.String xmlKey,
    byte[] content
  ) throws
    org.xmlBlaster.XmlBlasterException {
    // IMPLEMENT: Operation
    return 0;
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
    org.xmlBlaster.XmlBlasterException {
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
    org.xmlBlaster.XmlBlasterException {
    // IMPLEMENT: Operation
    return 0;
  }
}

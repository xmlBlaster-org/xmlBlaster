package org.xmlBlaster;
/**
<p>
<ul>
<li> <b>Java Class</b> org.xmlBlaster.BlasterCallbackImpl
<li> <b>Source File</b> org/xmlBlaster/BlasterCallbackImpl.java
<li> <b>IDL Source File</b> xmlBlaster.idl
<li> <b>IDL Absolute Name</b> ::org::xmlBlaster::BlasterCallback
<li> <b>Repository Identifier</b> IDL:org/xmlBlaster/BlasterCallback:1.0
</ul>
<b>IDL definition:</b>
<pre>
    #pragma prefix "org/xmlBlaster"
    interface BlasterCallback {
      void update(
        in ::org::xmlBlaster::XmlType xmlKey,
        in ::org::xmlBlaster::ContentType content
      );
    };
</pre>
</p>
*/
public class BlasterCallbackImpl extends BlasterCallbackPOA {
  String name;

  /** Construct a persistently named object. */
  public BlasterCallbackImpl(java.lang.String name) {
    this.name = name;
  }
  /** Construct a transient object. */
  public BlasterCallbackImpl() {
    super();
  }
  /**
  <p>
  Operation: <b>::org::xmlBlaster::BlasterCallback::update</b>.
  <pre>
    #pragma prefix "org/xmlBlaster/BlasterCallback"
    void update(
      in ::org::xmlBlaster::XmlType xmlKey,
      in ::org::xmlBlaster::ContentType content
    );
  </pre>
  </p>
  */
  public void update(
    java.lang.String xmlKey,
    byte[] content
  ) {
    // IMPLEMENT: Operation
  }
}

package org.xmlBlaster;

import org.xmlBlaster.util.*;
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
//public class BlasterCallbackImpl extends BlasterCallbackPOA {         // inheritance approach
public class BlasterCallbackImpl implements BlasterCallbackOperations { // tie approsch
   final String ME;

   /**
    * Construct a persistently named object.
    */
   public BlasterCallbackImpl(java.lang.String name) {
      this.ME = "BlasterCallbackImpl-" + name;
      if (Log.CALLS) Log.trace(ME, "Entering constructor with argument");
   }


   /**
    * Construct a transient object.
    */
   public BlasterCallbackImpl() {
      super();
      this.ME = "BlasterCallbackImpl";
      if (Log.CALLS) Log.trace(ME, "Entering constructor without argument");
   }


   /**
    * <p>
    * Operation: <b>::org::xmlBlaster::BlasterCallback::update</b>.
    * <pre>
    *   #pragma prefix "org/xmlBlaster/BlasterCallback"
    *   void update(
    *     in ::org::xmlBlaster::XmlType xmlKey,
    *     in ::org::xmlBlaster::ContentType content
    *   );
    * </pre>
    * </p>
    */
   public void update(String xmlKey_literal, byte[] content) {
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      Log.info(ME, "Callback invoked for " + xmlKey.getUniqueKey() + " content length = " + content.length);
   }
}

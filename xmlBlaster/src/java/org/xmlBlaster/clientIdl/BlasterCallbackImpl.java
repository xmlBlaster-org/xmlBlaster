/*------------------------------------------------------------------------------
Name:      BlasterCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client callback
           $Revision: 1.2 $
           $Date: 1999/11/12 13:07:06 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.clientIdl;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlKeyBase;


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
public class BlasterCallbackImpl implements BlasterCallbackOperations { // tie approach
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
    */
   public void update(String xmlKey_literal, byte[] content, String qos_literal) {
      XmlKeyBase xmlKey = new XmlKeyBase(xmlKey_literal);
      Log.info(ME, "Callback invoked for " + xmlKey.toString() + " content length = " + content.length);
   }
}

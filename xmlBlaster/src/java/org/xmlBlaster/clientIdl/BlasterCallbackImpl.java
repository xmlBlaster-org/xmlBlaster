/*------------------------------------------------------------------------------
Name:      BlasterCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client callback
Version:   $Id: BlasterCallbackImpl.java,v 1.6 1999/11/18 22:12:12 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.clientIdl;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.serverIdl.XmlBlasterException;


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
   public void update(MessageUnit[] messageUnitArr, String[] qos_literal_Arr) throws XmlBlasterException {
      for (int ii=0; ii<messageUnitArr.length; ii++) {
         MessageUnit messageUnit = messageUnitArr[ii];
         XmlKeyBase xmlKey = new XmlKeyBase(messageUnit.xmlKey);
         Log.info(ME, "================== BlasterCallback update START =============");
         Log.info(ME, "Callback invoked for " + xmlKey.toString() + " content length = " + messageUnit.content.length);
         Log.info(ME, new String(messageUnit.content));
         Log.info(ME, "================== BlasterCallback update END ===============");
      }
   }
}

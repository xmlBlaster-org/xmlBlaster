/*------------------------------------------------------------------------------
Name:      JavascriptCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;

//import org.mozilla.javascript.Context;
//import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import org.mozilla.javascript.JavaScriptException;

/**
 * This is a little helper class wraps the different, protocol specific
 * update() methods, and delivers the client a nicer update() method.
 * <p>
 * You may use this, if you don't want to program with the rawer CORBA BlasterCallback.update()
 * or RMI or XMLRPC.
 *
 * @version $Revision: 1.7 $
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>.
 */
public class JavascriptCallback implements I_Callback
{
   private ScriptableObject script  = null;

   public JavascriptCallback (ScriptableObject script)
   {
      this.script = script;
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    *
    * @param cbSessionId The session ID specified by the client which registered the callback
    * @param updateKey   The arrived key
    * @param content     The arrived message content
    * @param qos         Quality of Service of the MsgUnit
    *
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], org.xmlBlaster.client.qos.UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException
   {
      try {
         Object[] args = new Object[4];
         args[0] = cbSessionId;
         args[1] = updateKey;
         args[2] = new String(content);
         args[3] = updateQos;
         return (String)ScriptableObject.callMethod(this.script, "update", args);
     }
      catch (JavaScriptException ex) {
         throw new XmlBlasterException("JavascriptCallback.update()", ex.toString());
      }
   }

}



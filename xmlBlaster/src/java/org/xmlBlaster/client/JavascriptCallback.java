/*------------------------------------------------------------------------------
Name:      JavascriptCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages to invoke a javascript
Version:   $Id: JavascriptCallback.java,v 1.1 2002/03/28 10:25:20 laghi Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;
//import org.mozilla.javascript.Context;
//import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import org.mozilla.javascript.JavaScriptException;

/**
 * This is a little helper class wraps the different, protocol specific
 * update() methods, and delivers the client a nicer update() method.
 * <p>
 * You may use this, if you don't want to program with the rawer CORBA BlasterCallback.update()
 * or RMI or XML-RPC.
 *
 * @version $Revision: 1.1 $
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
    * This is the callback method invoked from XmlBlasterConnection
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * So you should implement in your client the I_Callback interface -
    * suppling the update() method where you can do with the message whatever you want.
    * <p />
    * If you do multiple logins with the same I_Callback implementation, the loginName
    * which is delivered with this update() method may be used to dispatch the message
    * to the correct client.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS) throws XmlBlasterException
   {
      try {
      Object[] args = new Object[4];
      args[0] = loginName;
      args[1] = updateKey;
      args[2] = new String(content);
      args[3] = updateQoS;

      ScriptableObject.callMethod(this.script, "update", args);
      }
      catch (JavaScriptException ex) {
         throw new Error(ex.toString());
      }
   }

}



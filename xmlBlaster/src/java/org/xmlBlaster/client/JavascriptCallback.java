/*------------------------------------------------------------------------------
Name:      JavascriptCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;

//import org.mozilla.javascript.Context;
//import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Context;

import org.apache.batik.script.rhino.WindowWrapper;
import org.apache.batik.script.rhino.RhinoInterpreter;
import org.apache.batik.script.Interpreter;
import org.apache.batik.script.InterpreterException;
import org.apache.batik.script.Window;
import org.apache.batik.util.RunnableQueue;

/**
 * This is a little helper class wraps the different, protocol specific
 * update() methods, and delivers the client a nicer update() method.
 * <p>
 * You may use this, if you don't want to program with the rawer CORBA BlasterCallback.update()
 * or RMI or XMLRPC.
 *
 * @version $Revision: 1.8 $
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>.
 */
public class JavascriptCallback implements I_Callback
{
   private static WindowWrapper javascriptWindow  = null;
   private Interpreter interpreter;
   private RunnableQueue updateQueue;

   /**
    * This constructor is called from Javascript within a SVG document:
    * <pre>
    * ... // other Javascript code with Rhino live connect
    * xmlBlaster.connect(connectQos, new JavascriptCallback(this));
    * </pre>
    * where this == window (the global object in SVG and HTML browsers)
    * and window is of type WindowWrapper.
    * The problem is that WindowWrapper has no method to retrieve the
    * Javascript context like SVGCanvas or UpdateManager, so we get this
    * via the static method getCurrentContext().
    *
    * @see http://xml.apache.org/batik
    * @author laghi
    * @author ruff
    */
   public JavascriptCallback(WindowWrapper javascriptWindow)
   {
      // http://xml.apache.org/batik/svgcanvas.html
      //this.javascriptWindow = org.xmlBlaster.util.Global.instance().getObjectEntry("SVG/Javascript/Interpreter");

      this.javascriptWindow = javascriptWindow;

      Window window = ((RhinoInterpreter.ExtendedContext)Context.getCurrentContext()).getWindow();
      this.updateQueue = window.getBridgeContext().getUpdateManager().getUpdateRunnableQueue();
      this.interpreter = window.getInterpreter();

      /*
         // Add to svg drawing:
         //   function getWindow() {
         //     return this;
         //   }
         // The window handle is returned but it somehow does not work with window.callMethod()

         updateQueue.invokeLater(new Runnable() {
            public void run() {
               String script = "getWindow();";
               try {
                  System.out.println("Calling interpreter: '" + script + "'");
                  JavascriptCallback.javascriptWindow = (WindowWrapper)interpreter.evaluate(script);
                  System.out.println("****JavascriptCallback: GOT WindowWrapper");
               }
               catch(Throwable ie) { //InterpreterException ie) {
                  System.out.println("!!!!!!! Interpreter exception '" + script + ": " + ie.toString());
               }
            }
         });
      */
      /* blocks forever!?
      try {
         updateQueue.invokeAndWait(new Runnable() {
            public void run() {
               String script = "return window";
               try {
                  System.out.println("Calling interpreter: " + script);
                  ScriptableObject obj = (ScriptableObject)interpreter.evaluate(script);
                  System.out.println("****JavascriptCallback: GOT WindowWrapper");
               }
               catch(InterpreterException ie) {
                  System.out.println("Interpreter exception '" + script + ": " + ie.toString());
               }
            }
         });
      }
      catch(InterruptedException ie) {
         System.out.println("Interpreter exception '" + script + ": " + ie.toString());
      }
      */

      System.out.println("****JavascriptCallback: SUCCESS");
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
   public String update(final String cbSessionId, final UpdateKey updateKey,
                        final byte[] content, final UpdateQos updateQos) throws XmlBlasterException
   {
      System.out.println("*****RECEIVING updateKey=" + updateKey.toXml());
      String key = org.jutils.text.StringHelper.replaceAll(updateKey.toXml(), "\n", " ");
      String con = org.jutils.text.StringHelper.replaceAll(new String(content), "\n", " ");
      String qos = org.jutils.text.StringHelper.replaceAll(updateQos.toXml(), "\n", " ");
      final String script =  "update(\"" + cbSessionId + "\", \"" + key + "\", \"" + con + "\", \"" + qos + "\");";
      //final String script =  "update(\"sdkfjs\", \"<key oid='11A'/>\", \"<chess><id>11A</id><transform>translate(166,210)</transform></chess>\", \"<qos/>\");";

      // Dispatch the received message to the GUI event thread:
      updateQueue.invokeLater(new Runnable() {
         public void run() {
            try {
               System.out.println("Calling interpreter content=" + new String(content));
               /* This code is much cleaner but it fails with
                  org.mozilla.javascript.JavaScriptException: java.lang.ClassCastException
                  The reason is that all global window methods don't work (like alert() or setTimout())
               Object[] args = new Object[4];
               args[0] = cbSessionId;
               args[1] = updateKey;
               args[2] = new String(content);
               args[3] = updateQos;
               javascriptWindow.callMethod(javascriptWindow, "update", args);
               */
               interpreter.evaluate(script);
            }
            catch (Exception e) {
               System.out.println("JavascriptCallback update() failed: content=" + new String(content) + ": " + e.toString());
               //throw new XmlBlasterException(Global.instance(), ErrorCode.USER_UPDATE_ERROR,
               //             "JavascriptCallback update() failed", e.toString());
            }
         }
      });

      return "";
   }

}



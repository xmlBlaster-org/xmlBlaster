/*------------------------------------------------------------------------------
Name:      SignalCatcher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import java.lang.reflect.Method;


/**
 * SignalCatcher catches Ctrl-C and does the desired action. 
 * <p />
 * Works only with JDK 1.3 and above, we check with reflection the availability
 * to still be JDK 1.2 compatible.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class SignalCatcher implements Runnable
{
   private String ME = "SignalCatcher";
   private LogChannel log;
   private Thread thread;
   private I_SignalListener listener;
   private boolean runDummy = false;

   /**
    * You need to call init() after construction. 
    * Example for a hook:
    * <pre>
    *  class Shutdown extends Thread {
    *    public void run() {
    *       System.exit(0);
    *    }
    *  }
    *  SignalCatcher c = new SignalCatcher(Global glob, new Shutdown());
    *  c.catchSignals();
    *  ...
    *  c.removeSignalCatcher();
    * </pre>
    */
   public SignalCatcher(Global glob, I_SignalListener listener) {
      this.log = glob.getLog("core");
      this.listener = listener;
      this.thread = new Thread(this, "XmlBlaster signal catcher thread for controlled shudown");
      this.thread.setDaemon(true);
   }

   /**
    * Add shutdown hook.
    * <p />
    * Catch signals, e.g. Ctrl C to stop xmlBlaster.<br />
    * Uses reflection since only JDK 1.3 supports it.
    * <p />
    * NOTE: On Linux build 1.3.0, J2RE 1.3.0 IBM build cx130-20000815 (JIT enabled: jitc) fails with Ctrl-C
    *
    * @return true: Shutdown hook is established
    */
   public boolean catchSignals() {
      Method method;
      try  {
         Class cls = Runtime.getRuntime().getClass();
         Class[] paramCls = new Class[1];
         paramCls[0] = Class.forName("java.lang.Thread");
         method = cls.getDeclaredMethod("addShutdownHook", paramCls);
      }
      catch (java.lang.ClassNotFoundException e) {
         return false;
      }
      catch (java.lang.NoSuchMethodException e) {
         if (log.TRACE) log.trace(ME, "No shutdown hook established");
         return false;
      }

      try {
         if (method != null) {
            Object[] params = new Object[1];
            params[0] = this.thread;
            method.invoke(Runtime.getRuntime(), params);
         }
      }
      catch (java.lang.reflect.InvocationTargetException e) {
         return false;
      }
      catch (java.lang.IllegalAccessException e) {
         return false;
      }
      return true;
   }

   /**
    * @return true on success
    */
   public boolean removeSignalCatcher() {

      if (this.thread == null) {
         return false;
      }

      //boolean removed = Runtime.getRuntime().removeShutdownHook(this.thread);
      boolean removed = false;
      try {
         Method method;
         try  {
            Class cls = Runtime.getRuntime().getClass();
            Class[] paramCls = new Class[1];
            paramCls[0] = Class.forName("java.lang.Thread");
            method = cls.getDeclaredMethod("removeShutdownHook", paramCls);
         }
         catch (java.lang.ClassNotFoundException e) {
            if (log.TRACE) log.trace(ME, "Shutdown hook not removed: " + e.toString());
            return false;
         }
         catch (java.lang.NoSuchMethodException e) {
            if (log.TRACE) log.trace(ME, "No shutdown hook removed");
            return false;
         }

         try {
            if (method != null) {
               Object[] params = new Object[1];
               params[0] = this.thread;
               method.invoke(Runtime.getRuntime(), params);
               removed = true; // TODO: check the real return value
               return removed;
            }
         }
         catch (java.lang.reflect.InvocationTargetException e) {
            if (log.TRACE) log.trace(ME, "Shutdown hook not removed which is OK when we are in shutdown process already: " + e.toString());
            return false;
         }
         catch (java.lang.IllegalAccessException e) {
            if (log.TRACE) log.trace(ME, "Shutdown hook not removed: " + e.toString());
            return false;
         }
         if (log.TRACE) log.trace(ME, "Shutdown hook removed");
      }
      finally {
         this.listener = null;
         if (log.TRACE) log.trace(ME, "Removed = " + removed + " in removeSignalCatcher()");

         // This is a hack to allow the garbage collector to destroy SignalCatcher
         // (An unrun thread can't be garbage collected)
         this.runDummy = true;
         try {
            this.thread.start(); // Run the Thread to allow the garbage collector to clean it up
         }
         catch (IllegalThreadStateException e) {
            if (log.TRACE) log.trace(ME, "Thread has run already: " + e.toString());
         }
         
         this.log = null;
         this.thread = null;
      }
      return removed;
   }

   /**
    * This is invoked on exit
    */
   public void run() {
      if (runDummy) { // Run the Thread to allow the garbage collector to clean it up
         this.listener = null;
         return;
      }
      if (this.log != null) {
         this.log.info(ME, "Shutdown forced by user or signal (Ctrl-C).");
      }
      if (this.listener != null)
         this.listener.shutdownHook();
      this.listener = null;
   }
}

/*------------------------------------------------------------------------------
Name:      SignalCatcher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.log.LogChannel;
import org.jutils.JUtilsException;
import org.jutils.runtime.ThreadLister;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;

import java.lang.reflect.Method;


/**
 * SignalCatcher catches Ctrl-C and does the desired action. 
 * <p />
 * Works only with JDK 1.3 and above, we check with reflection the availability
 * to still be JDK 1.2 compatible.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class SignalCatcher
{
   private String ME = "SignalCatcher";
   private Global glob = null;
   private LogChannel log;
   private Thread shutdownHook = null;


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
   public SignalCatcher(Global glob, Thread hook) {
      this.glob = glob;
      this.log = glob.getLog("core");
      this.shutdownHook = hook;
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
   public boolean catchSignals()
   {
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
            params[0] = shutdownHook;
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

   public void removeSignalCatcher()
   {
      if (shutdownHook == null)
         return;

      Method method;
      try  {
         Class cls = Runtime.getRuntime().getClass();
         Class[] paramCls = new Class[1];
         paramCls[0] = Class.forName("java.lang.Thread");
         method = cls.getDeclaredMethod("removeShutdownHook", paramCls);
      }
      catch (java.lang.ClassNotFoundException e) {
         return;
      }
      catch (java.lang.NoSuchMethodException e) {
         if (log.TRACE) log.trace(ME, "No shutdown hook removed");
         return;
      }

      try {
         if (method != null) {
            Object[] params = new Object[1];
            params[0] = shutdownHook;
            method.invoke(Runtime.getRuntime(), params);
         }
      }
      catch (java.lang.reflect.InvocationTargetException e) {
         return;
      }
      catch (java.lang.IllegalAccessException e) {
         return;
      }
      if (log.TRACE) log.trace(ME, "Shutdown hook removed");
   }
}

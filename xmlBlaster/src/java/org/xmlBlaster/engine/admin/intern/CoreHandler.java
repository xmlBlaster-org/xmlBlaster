/*------------------------------------------------------------------------------
Name:      CoreHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for administrative property access
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.intern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.engine.admin.I_CommandHandler;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.CommandWrapper;
import org.xmlBlaster.engine.admin.I_AdminNode;
import org.xmlBlaster.engine.admin.I_AdminSubscription;
import org.xmlBlaster.engine.admin.I_AdminSubject;
import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.engine.SubscriptionInfo;

import java.util.Vector;
import java.lang.reflect.*;
import java.beans.PropertyDescriptor;


/**
 * Implementation of administrative access to xmlBlaster internal java objects. 
 * @author xmlBlaster@marcelruff.info 
 * @since 0.79f
 */
final public class CoreHandler implements I_CommandHandler, I_Plugin {

   private String ME = "CoreHandler";
   private Global glob = null;
   private LogChannel log = null;
   private CommandManager commandManager = null;

   /**
    * This is called after creation of the plugin. 
    * @param glob The Global handle of this xmlBlaster server instance.
    * @param commandManager My big brother taking care of me
    */
   public void initialize(Global glob, CommandManager commandManager) {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.commandManager = commandManager;
      this.ME = "CoreHandler" + this.glob.getLogPrefixDashed();
      this.commandManager.register("DEFAULT", this);
      this.commandManager.register("client", this);
      this.commandManager.register("subscription", this);
      log.info(ME, "Core administration plugin is initialized");
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Return plugin type for Plugin loader
    * @return "CoreHandler"
    */
   public String getType() {
      return "CoreHandler";
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return "1.0";
   }

   /**
    * Get a human readable name of this filter implementation
    * @return "CoreHandler"
    */
   public String getName() {
      return "CoreHandler";
   }

   /**
    * @see org.xmlBlaster.engine.admin.I_CommandHandler#get(String,CommandWrapper)
    */
   public synchronized MsgUnitRaw[] get(String sessionId, CommandWrapper cmd) throws XmlBlasterException {
      if (cmd == null)
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which is not null");

      String registerKey = cmd.getThirdLevel();
      if (registerKey == null || registerKey.length() < 2)
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid property added, '" + cmd.getCommand() + "' is invalid, aborted request.");

      if (registerKey.startsWith("?")) {
         // for example "/node/heron/?freeMem"
         String ret = ""+getInvoke(registerKey.substring(1), glob.getRequestBroker(), I_AdminNode.class);
         log.info(ME, "Retrieved " + cmd.getCommand());
         if (log.DUMP) log.dump(ME, "Retrieved " + cmd.getCommand() + "=" + ret);
         MsgUnitRaw[] msgs = new MsgUnitRaw[1];
         msgs[0] = new MsgUnitRaw(cmd.getCommand(), ret.getBytes(), "text/plain");
         return msgs;
      }

      if (registerKey.equals("client") || registerKey.equals("DEFAULT")) {
         String loginName = cmd.getUserNameLevel();
         if (loginName == null || loginName.length() < 1 || loginName.startsWith("?"))
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid client name in '" + cmd.getCommand() + "' with '" + loginName + "' is invalid");

         I_AdminSubject subjectInfo = glob.getAuthenticate().getSubjectInfoByName(new SessionName(glob, loginName));
         if (subjectInfo == null)
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid client name in '" + cmd.getCommand() + "' client '" + loginName + "' is unknown");

         String pubSessionId = cmd.getSessionIdLevel();
         if (pubSessionId == null || pubSessionId.length() < 1)
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid public session ID in '" + cmd.getCommand() + "'.");

         if (pubSessionId.startsWith("?")) {
            // for example "/node/heron/joe/?uptime"
            String ret = ""+getInvoke(pubSessionId.substring(1), subjectInfo, I_AdminSubject.class);
            log.info(ME, "Retrieved " + cmd.getCommand() + "=" + ret);
            MsgUnitRaw[] msgs = new MsgUnitRaw[1];
            msgs[0] = new MsgUnitRaw(cmd.getCommand(), ret.getBytes(), "text/plain");
            return msgs;
         }

         String sessionAttr = cmd.getSessionAttrLevel();
         if (sessionAttr == null || sessionAttr.length() < 1 || sessionAttr.startsWith("?")==false)
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid session attribute in '" + cmd.getCommand() + "'.");

         if (sessionAttr.startsWith("?")) {
            // for example "client/joe/ses17/?queue/callback/maxEntries"
            I_AdminSession sessionInfo = subjectInfo.getSessionByPubSessionId(Long.parseLong(pubSessionId));
            if (sessionInfo == null)
               throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "The public session ID '" + pubSessionId + "' in '" + cmd.getCommand() + "' is unknown.");
            String ret = ""+getInvoke(sessionAttr.substring(1), sessionInfo, I_AdminSession.class);
            log.info(ME, "Retrieved " + cmd.getCommand() + "=" + ret);
            MsgUnitRaw[] msgs = new MsgUnitRaw[1];
            msgs[0] = new MsgUnitRaw(cmd.getCommand(), ret.getBytes(), "text/plain");
            return msgs;
         }
      }
      else if (registerKey.equals("subscription")) {
         String subscriptionId = cmd.getUserNameLevel();
         if (subscriptionId == null || subscriptionId.length() < 1 || subscriptionId.startsWith("?"))
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid subscriptionId in '" + cmd.getCommand() + "' with '" + subscriptionId + "' is invalid");

         SubscriptionInfo subscriptionInfo = glob.getRequestBroker().getClientSubscriptions().getSubscription(subscriptionId);
         if (subscriptionInfo == null)
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid subscriptionId in '" + cmd.getCommand() + "' subscriptionId '" + subscriptionId + "' is unknown");

         String methodName = cmd.getFifthLevel();
         if (methodName == null || methodName.length() < 1)
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid method name in '" + cmd.getCommand() + "'.");

         if (methodName.startsWith("?")) {
            // for example "/node/heron/subscription/__subId:3/?topicId"
            String ret = ""+getInvoke(methodName.substring(1), subscriptionInfo, I_AdminSubscription.class);
            log.info(ME, "Retrieved " + cmd.getCommand() + "=" + ret);
            MsgUnitRaw[] msgs = new MsgUnitRaw[1];
            msgs[0] = new MsgUnitRaw(cmd.getCommand(), ret.getBytes(), "text/plain");
            return msgs;
         }
      }

      log.info(ME, cmd.getCommand() + " not implemented");
      return new MsgUnitRaw[0];
   }

   /**
    * Set a value. 
    */
   public String set(String sessionId, CommandWrapper cmd) throws XmlBlasterException {
      if (cmd == null)
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which is not null");

      String client = cmd.getThirdLevel();
      if (client == null || client.length() < 1)
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid property added, '" + cmd.getCommand() + "' is invalid, aborted request.");

      if (client.startsWith("?")) {
         // for example "/node/heron/?freeMem"
         String ret = ""+setInvoke(cmd.getKey(), glob.getRequestBroker(), I_AdminNode.class, cmd.getValue());
         log.info(ME, "Set " + cmd.getCommandStripAssign() + "=" + cmd.getValue());
         return cmd.getValue();
      }

      String loginName = cmd.getUserNameLevel();
      if (loginName == null || loginName.length() < 1 || loginName.startsWith("?"))
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid client name in '" + cmd.getCommand() + "' with '" + loginName + "' is invalid");

      I_AdminSubject subjectInfo = glob.getAuthenticate().getSubjectInfoByName(new SessionName(glob, loginName));
      if (subjectInfo == null)
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid client name in '" + cmd.getCommand() + "' client '" + loginName + "' is unknown");

      String pubSessionId = cmd.getSessionIdLevel();
      if (pubSessionId == null || pubSessionId.length() < 1)
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid public session ID in '" + cmd.getCommand() + "'.");

      if (pubSessionId.startsWith("?")) {
         // for example "/node/heron/joe/?uptime"
         String ret = ""+setInvoke(cmd.getKey(), subjectInfo, I_AdminSubject.class, cmd.getValue());
         log.info(ME, "Set " + cmd.getCommandStripAssign() + "=" + cmd.getValue());
         return cmd.getValue();
      }

      String sessionAttr = cmd.getSessionAttrLevel();
      if (sessionAttr == null || sessionAttr.length() < 1 || sessionAttr.startsWith("?")==false)
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Please pass a command which has a valid session attribute in '" + cmd.getCommand() + "'.");

      if (sessionAttr.startsWith("?")) {
         // for example "client/joe/ses17/?queue/callback/maxEntries"
         I_AdminSession sessionInfo = subjectInfo.getSessionByPubSessionId(Long.parseLong(pubSessionId));
         if (sessionInfo == null)
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "The public session ID '" + pubSessionId + "' in '" + cmd.getCommand() + "' is unknown.");
         String ret = ""+setInvoke(cmd.getKey(), sessionInfo, I_AdminSession.class, cmd.getValue());
         log.info(ME, "Set " + cmd.getCommandStripAssign() + "=" + cmd.getValue());
         return cmd.getValue();
      }

      log.info(ME, cmd.getCommand() + " not implemented");
      return null;
   }

   /**
    * @param property e.g. "uptime", the method "getUptime()" will be called
    * @param aClass e.g. I_AdminSubject.class
    */
   private Object getInvoke(String property, Object impl, Class aInterface) throws XmlBlasterException {
      String methodName = null;
      if (property == null || property.length() < 2)
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME,
                   "Please pass a vaild command, aborted request.");
      try {
         Vector params = new Vector();
         Invoker invoker = new Invoker(glob, impl, aInterface);
         methodName = "get" + property.substring(0,1).toUpperCase() + property.substring(1);
         Object obj = invoker.execute(methodName, params);
         if (log.TRACE) log.trace(ME, "Return for '" + methodName + "' is '" + obj + "'");
         return obj;
         /* This code worked only when a corresponding setXXX() was specified:
         PropertyDescriptor desc = new PropertyDescriptor(property, aClass);
         Method method = desc.getReadMethod();
         //Object[] argValues = new Object[0];
         Object returnValue = method.invoke (impl, null); //argValues);
         log.info(ME, "Invoke method '" + property + "' return=" + returnValue + " class=" + returnValue.getClass());
         return returnValue;
         */
      }
      catch (Exception e) {
         //e.printStackTrace();
         log.error(ME, "Invoke for get method '" + methodName + "' on class=" + aInterface + " on object=" + impl.getClass() + " failed: " + e.toString());
         throw XmlBlasterException.convert(glob, ME, "Invoke for get method '" + property + "' on class=" + aInterface + " on object=" + impl.getClass() + " failed", e);
      }
   }

   private Object[] convertMethodArguments(Class[] classes, String[] args) 
      throws XmlBlasterException {
      if (classes.length != args.length) {
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".convertMethodArguments", "wrong number of arguments: '" + args.length + "' but should be '" + classes.length + "'");
      }
      Object[] ret = new Object[classes.length];
      for (int i=0; i < classes.length; i++) {

         if (classes[i] == String.class) ret[i] = args[i];

         else if (classes[i] == Boolean.TYPE || classes[i] == Boolean.class) {
            try {
               ret[i] = Boolean.valueOf(args[i]);  
            }
            catch (Throwable ex) {
               throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".convertMethodArguments", "wrong type of argument nr. '" + (i+1) + "' should be of type '" + classes[i].getName() + "' but its value is '" + args[i] + "'");
            }
         }
         
         else if (classes[i] == Short.TYPE || classes[i] == Short.class) {
            try {
               ret[i] = Short.valueOf(args[i]);  
            }
            catch (Throwable ex) {
               throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".convertMethodArguments", "wrong type of argument nr. '" + (i+1) + "' should be of type '" + classes[i].getName() + "' but its value is '" + args[i] + "'");
            }
         }
         
         else if (classes[i] == Integer.TYPE || classes[i] == Integer.class) {
            try {
               ret[i] = Integer.valueOf(args[i]);  
            }
            catch (Throwable ex) {
               throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".convertMethodArguments", "wrong type of argument nr. '" + (i+1) + "' should be of type '" + classes[i].getName() + "' but its value is '" + args[i] + "'");
            }
         }
         
         else if (classes[i] == Long.TYPE || classes[i] == Long.class) {
            try {
               ret[i] = Long.valueOf(args[i]);  
            }
            catch (Throwable ex) {
               throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".convertMethodArguments", "wrong type of argument nr. '" + (i+1) + "' should be of type '" + classes[i].getName() + "' but its value is '" + args[i] + "'");
            }
         }
         
         else if (classes[i] == Float.TYPE || classes[i] == Float.class) {
            try {
               ret[i] = Float.valueOf(args[i]);  
            }
            catch (Throwable ex) {
               throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".convertMethodArguments", "wrong type of argument nr. '" + (i+1) + "' should be of type '" + classes[i].getName() + "' but its value is '" + args[i] + "'");
            }
         }
         
         else if (classes[i] == Double.TYPE || classes[i] == Double.class) {
            try {
               ret[i] = Double.valueOf(args[i]);  
            }
            catch (Throwable ex) {
               throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".convertMethodArguments", "wrong type of argument nr. '" + (i+1) + "' should be of type '" + classes[i].getName() + "' but its value is '" + args[i] + "'");
            }
         }
      }
      return ret;      
   }


   /**
    * @param property e.g. "uptime", the method "setUptime()" will be called
    * @param aClass e.g. I_AdminSubject.class
    * @param argValues = new Object[1]; argValues[0] = "Hi"
    */
   private Object setInvoke(String property, Object impl, Class aClass, String[] argValuesAsStrings) throws XmlBlasterException {
      try {
         PropertyDescriptor desc = new PropertyDescriptor(property, aClass);
         Method method = desc.getWriteMethod();
         Object[] argValues =  convertMethodArguments(method.getParameterTypes(), argValuesAsStrings);

         Object obj = method.invoke (impl, argValues);
         log.info(ME, "Successful invoked set method '" + property + "'");
         if (obj != null) log.warn(ME, "Ignoring returned value of set method '" + property + "'");
         return obj;
      }
      catch (Exception e) {
         if (e instanceof XmlBlasterException) throw (XmlBlasterException)e;
         if (argValuesAsStrings.length > 0) {
            log.error(ME, "Invoke for property '" + property + "' with " + argValuesAsStrings.length + " arguments of type " +
               argValuesAsStrings[0].getClass().toString() +
               " on interface " + aClass.toString() + " failed: " + e.toString());
         }
         else {
            log.error(ME, "Invoke for property '" + property + "' on interface " + aClass.toString() + " failed: " + e.toString());
         }
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Invoke for property '" + property + "' on class=" + aClass + " on object=" + impl.getClass() + " failed: " + e.toString());
      }
   }

   private Object setInvoke(String property, Object impl, Class aClass, String value) throws XmlBlasterException {
      String[] argValuesAsStrings = new String[1];
      argValuesAsStrings[0] = value;
      return setInvoke(property, impl, aClass, argValuesAsStrings);
   }

   public String help() {
      return "Administration of properties from system, xmlBlaster.properties and command line";
   }

   public String help(String cmd) {
      return help();
   }

   public void shutdown() {
      if (log.TRACE) log.trace(ME, "Shutdown ignored, nothing to do");
   }

} // end of class CoreHandler


/**
 * Code borrowed from XmlRpc lib from Apache (thanks).
 * This class uses Java Reflection to call methods
 * We use this because the above code with bean PropertyDescriptor
 * forced us to have to every getXXX() a setXXX() as well,
 * but some properties are read only!
 */
class Invoker
{
   private Global glob;
   private Object invokeTarget;
   private Class targetClass;
   boolean debug = true;

   /**
    * @param targetClass is passed explicitly, as target.getClass() would allow
    *        to access methods in the implementing class (e.g. RequestBroker) instead
    *        of only I_AdminClass
    */
   public Invoker(Global glob, Object target, Class targetClass) {
      this.glob = glob;
      this.invokeTarget = target;
      this.targetClass = targetClass;
   }

   // main method, sucht methode in object, wenn gefunden dann aufrufen.
   public Object execute(String methodName, Vector params) throws XmlBlasterException {
      Class[] argClasses = null;
      Object[] argValues = null;
      if (params != null) {
         argClasses = new Class[params.size()];
         argValues = new Object[params.size()];
         for (int i = 0; i < params.size(); i++) {
               argValues[i] = params.elementAt(i);
               if (argValues[i] instanceof Integer)
                  argClasses[i] = Integer.TYPE;
               else if (argValues[i] instanceof Double)
                  argClasses[i] = Double.TYPE;
               else if (argValues[i] instanceof Boolean)
                  argClasses[i] = Boolean.TYPE;
               else
                  argClasses[i] = argValues[i].getClass();
         }
      }

      Method method = null;

      /*
      if (debug) {
         System.err.println("Searching for method: " + methodName);
         for (int i = 0; i < argClasses.length; i++)
               System.err.println("Parameter " + i + ": " +
                     argClasses[i] + " = " + argValues[i]);
      }
      */

      try {
         method = targetClass.getMethod(methodName, argClasses);
      }
      catch (NoSuchMethodException nsm_e) {
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, "CoreHandler",
                   "Please check your command, aborted request.", nsm_e);
      }
      catch (SecurityException s_e) {
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, "CoreHandler",
                   "Aborted request.", s_e);
      }

      try {
         // our policy is to make all public methods callable except the ones defined in java.lang.Object
         if (method.getDeclaringClass () == Class.forName ("java.lang.Object"))
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, "CoreHandler",
                      "Invoker can't call methods defined in java.lang.Object");
      }
      catch (java.lang.ClassNotFoundException e) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, "CoreHandler",
                     "Invoker can't call methods defined in java.lang.Object", e);
      }

      // invoke
      Object returnValue = null;
      try  {
         returnValue = method.invoke (invokeTarget, argValues);
      }
      catch (IllegalAccessException iacc_e) {
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, "CoreHandler",
                   "Aborted request.", iacc_e);
      }
      catch (IllegalArgumentException iarg_e) {
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, "CoreHandler",
                   "Aborted request.", iarg_e);
      }
      catch (InvocationTargetException it_e) {
         if (debug)
            it_e.getTargetException ().printStackTrace ();
         Throwable t = it_e.getTargetException();
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, "CoreHandler",
                   "Aborted request.", t);
      }

      return returnValue;
   }

} // class Invoker



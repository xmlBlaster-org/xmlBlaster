/*------------------------------------------------------------------------------
Name:      CoreHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for administrative property access
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.intern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.admin.I_CommandHandler;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.CommandWrapper;
import org.xmlBlaster.engine.admin.I_AdminNode;
import org.xmlBlaster.engine.admin.I_AdminSubject;
import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.authentication.SessionInfo;

import java.util.Vector;
import java.lang.reflect.*;
import java.beans.PropertyDescriptor;


/**
 * Implementation of administrative access to xmlBlaster internal java objects. 
 * @author ruff@swand.lake.de 
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
      this.ME = this.ME + "-" + glob.getId();
      this.commandManager.register("DEFAULT", this);
      this.commandManager.register("client", this);
      log.info(ME, "Core administration plugin is initialized");
   }

   /**
    * This method is called by the PluginManager.
    * <p />
    * This xmlBlaster.properties entry example
    * <pre>
    *   LoadBalancerPlugin[CoreHandler][1.0]=org.xmlBlaster.engine.command.simpledomain.CoreHandler,DEFAULT_MAX_LEN=200
    * </pre>
    * passes 
    * <pre>
    *   options[0]="DEFAULT_MAX_LEN"
    *   options[1]="200"
    * </pre>
    * <p/>
    * @param Global   An xmlBlaster instance global object holding logging and property informations
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(org.xmlBlaster.util.Global glob, String[] options) throws XmlBlasterException {
      if (options != null) {
         for (int ii=0; ii<options.length-1; ii++) {
            if (options[ii].equalsIgnoreCase("DUMMY")) {
               //DUMMY = (new Long(options[++ii])).longValue();  // ...
            }
         }
      }
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
    * Your plugin should process the command. 
    * <p />
    * @param cmd "/node/heron/?clientList"
    * @return "key=value" or null if not found, e.g. "/node/heron/sysprop/?user.home=/home/joe"
    * @see org.xmlBlaster.engine.admin.I_CommandHandler#get(CommandWrapper)
    */
   public synchronized MessageUnit[] get(String sessionId, CommandWrapper cmd) throws XmlBlasterException {
      if (cmd == null)
         throw new XmlBlasterException(ME, "Please pass a command which is not null");

      String client = cmd.getThirdLevel();
      if (client == null || client.length() < 1)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid property added, '" + cmd.getCommand() + "' is too short, aborted request.");

      if (client.startsWith("?")) {
         // for example "/node/heron/?freeMem"
         String ret = ""+getInvoke(client.substring(1), glob.getRequestBroker(), I_AdminNode.class);
         log.info(ME, "Retrieved " + cmd.getCommand() + "=" + ret);
         MessageUnit[] msgs = new MessageUnit[1];
         msgs[0] = new MessageUnit(cmd.getCommand(), ret.getBytes(), "text/plain");
         return msgs;
      }

      String loginName = cmd.getUserNameLevel();
      if (loginName == null || loginName.length() < 1 || loginName.startsWith("?"))
         throw new XmlBlasterException(ME, "Please pass a command which has a valid client name in '" + cmd.getCommand() + "' with '" + loginName + "' is invalid");

      SubjectInfo subjectInfo = glob.getAuthenticate().getSubjectInfoByName(loginName);
      if (subjectInfo == null)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid client name in '" + cmd.getCommand() + "' client '" + loginName + "' is unknown");

      String pubSessionId = cmd.getSessionIdLevel();
      if (pubSessionId == null || pubSessionId.length() < 1)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid public session ID in '" + cmd.getCommand() + "'.");

      if (pubSessionId.startsWith("?")) {
         // for example "/node/heron/joe/?uptime"
         String ret = ""+getInvoke(pubSessionId.substring(1), subjectInfo, I_AdminSubject.class);
         log.info(ME, "Retrieved " + cmd.getCommand() + "=" + ret);
         MessageUnit[] msgs = new MessageUnit[1];
         msgs[0] = new MessageUnit(cmd.getCommand(), ret.getBytes(), "text/plain");
         return msgs;
      }

      String sessionAttr = cmd.getSessionAttrLevel();
      if (sessionAttr == null || sessionAttr.length() < 1 || sessionAttr.startsWith("?")==false)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid session attribute in '" + cmd.getCommand() + "'.");

      if (sessionAttr.startsWith("?")) {
         // for example "client/joe/ses17/?cb.queue.maxMsg"
         SessionInfo sessionInfo = subjectInfo.getSessionByPublicId(pubSessionId);
         if (sessionInfo == null)
            throw new XmlBlasterException(ME, "The public session ID '" + pubSessionId + "' in '" + cmd.getCommand() + "' is unknown.");
         String ret = ""+getInvoke(sessionAttr.substring(1), sessionInfo, I_AdminSession.class);
         log.info(ME, "Retrieved " + cmd.getCommand() + "=" + ret);
         MessageUnit[] msgs = new MessageUnit[1];
         msgs[0] = new MessageUnit(cmd.getCommand(), ret.getBytes(), "text/plain");
         return msgs;
      }

      log.info(ME, cmd.getCommand() + " not implemented");
      return new MessageUnit[0];
   }

   /**
    * Set a value. 
    */
   public String set(String sessionId, CommandWrapper cmd) throws XmlBlasterException {
      if (cmd == null)
         throw new XmlBlasterException(ME, "Please pass a command which is not null");

      String client = cmd.getThirdLevel();
      if (client == null || client.length() < 1)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid property added, '" + cmd.getCommand() + "' is too short, aborted request.");

      if (client.startsWith("?")) {
         // for example "/node/heron/?freeMem"
         String ret = ""+setInvoke(cmd.getKey(), glob.getRequestBroker(), I_AdminNode.class, cmd.getValue());
         log.info(ME, "Set " + cmd.getCommandStripAssign() + "=" + cmd.getValue());
         return cmd.getValue();
      }

      String loginName = cmd.getUserNameLevel();
      if (loginName == null || loginName.length() < 1 || loginName.startsWith("?"))
         throw new XmlBlasterException(ME, "Please pass a command which has a valid client name in '" + cmd.getCommand() + "' with '" + loginName + "' is invalid");

      SubjectInfo subjectInfo = glob.getAuthenticate().getSubjectInfoByName(loginName);
      if (subjectInfo == null)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid client name in '" + cmd.getCommand() + "' client '" + loginName + "' is unknown");

      String pubSessionId = cmd.getSessionIdLevel();
      if (pubSessionId == null || pubSessionId.length() < 1)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid public session ID in '" + cmd.getCommand() + "'.");

      if (pubSessionId.startsWith("?")) {
         // for example "/node/heron/joe/?uptime"
         String ret = ""+setInvoke(cmd.getKey(), subjectInfo, I_AdminSubject.class, cmd.getValue());
         log.info(ME, "Set " + cmd.getCommandStripAssign() + "=" + cmd.getValue());
         return cmd.getValue();
      }

      String sessionAttr = cmd.getSessionAttrLevel();
      if (sessionAttr == null || sessionAttr.length() < 1 || sessionAttr.startsWith("?")==false)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid session attribute in '" + cmd.getCommand() + "'.");

      if (sessionAttr.startsWith("?")) {
         // for example "client/joe/ses17/?cb.queue.maxMsg"
         SessionInfo sessionInfo = subjectInfo.getSessionByPublicId(pubSessionId);
         if (sessionInfo == null)
            throw new XmlBlasterException(ME, "The public session ID '" + pubSessionId + "' in '" + cmd.getCommand() + "' is unknown.");
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
      try {
         Vector params = new Vector();
         Invoker invoker = new Invoker(impl, aInterface);
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
      catch (java.lang.reflect.InvocationTargetException e) {
         Throwable t = e.getTargetException();
         if (t instanceof XmlBlasterException)
            throw (XmlBlasterException)t;
         else {
            String text = "Invoke of property '" + property + "' failed: " + t.toString();
            log.error(ME, text);
            t.printStackTrace();
            throw new XmlBlasterException(ME, text);
         }
      }
      catch (Exception e) {
         //e.printStackTrace();
         log.error(ME, "Invoke for get method '" + methodName + "' on class=" + aInterface + " on object=" + impl.getClass() + " failed: " + e.toString());
         throw new XmlBlasterException(ME, "Invoke for get method '" + property + "' on class=" + aInterface + " on object=" + impl.getClass() + " failed: " + e.toString());
      }
   }

   /**
    * @param property e.g. "uptime", the method "setUptime()" will be called
    * @param aClass e.g. I_AdminSubject.class
    * @param argValues = new Object[1]; argValues[0] = "Hi"
    */
   private Object setInvoke(String property, Object impl, Class aClass, Object[] argValues) throws XmlBlasterException {
      try {
         PropertyDescriptor desc = new PropertyDescriptor(property, aClass);
         Method method = desc.getWriteMethod();
         Object obj = method.invoke (impl, argValues);
         log.info(ME, "Successful invoked set method '" + property + "'");
         if (obj != null) log.warn(ME, "Ignoring returned value of set method '" + property + "'");
         return obj;
      }
      catch (java.lang.reflect.InvocationTargetException e) {
         Throwable t = e.getTargetException();
         if (t instanceof XmlBlasterException)
            throw (XmlBlasterException)t;
         else {
            String text = "Invoke property '" + property + "' with " + argValues.length + " arguments failed: " + t.toString();
            log.error(ME, text);
            t.printStackTrace();
            throw new XmlBlasterException(ME, text);
         }
      }
      catch (Exception e) {
         if (argValues.length > 0) {
            log.error(ME, "Invoke for property '" + property + "' with " + argValues.length + " arguments of type " +
               argValues[0].getClass().toString() +
               " on interface " + aClass.toString() + " failed: " + e.toString());
         }
         else {
            log.error(ME, "Invoke for property '" + property + "' on interface " + aClass.toString() + " failed: " + e.toString());
         }
         throw new XmlBlasterException(ME, "Invoke for property '" + property + "' on class=" + aClass + " on object=" + impl.getClass() + " failed: " + e.toString());
      }
   }

   private Object setInvoke(String property, Object impl, Class aClass, String value) throws XmlBlasterException {
      Object[] argValues = new Object[1];
      argValues[0] = value;
      return setInvoke(property, impl, aClass, argValues);
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
   private Object invokeTarget;
   private Class targetClass;
   boolean debug = true;

   /**
    * @param targetClass is passed explicitly, as target.getClass() would allow
    *        to access methods in the implementing class (e.g. RequestBroker) instead
    *        of only I_AdminClass
    */
   public Invoker(Object target, Class targetClass) {
      this.invokeTarget = target;
      this.targetClass = targetClass;
   }


   // main method, sucht methode in object, wenn gefunden dann aufrufen.
   public Object execute (String methodName,
         Vector params) throws Exception
   {


      // Array mit Classtype bilden, ObjectAry mit Values bilden
      Class[] argClasses = null;
      Object[] argValues = null;
      if (params != null)
      {
         argClasses = new Class[params.size()];
         argValues = new Object[params.size()];
         for (int i = 0; i < params.size(); i++)
         {
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

      // Methode da ?
      Method method = null;

      if (debug)
      {
         System.err.println("Searching for method: " + methodName);
         for (int i = 0; i < argClasses.length; i++)
               System.err.println("Parameter " + i + ": " +
                     argClasses[i] + " = " + argValues[i]);
      }

      try
      {
         method = targetClass.getMethod(methodName, argClasses);
      }
      // Wenn nicht da dann entsprechende Exception returnen
      catch (NoSuchMethodException nsm_e)
      {
         throw nsm_e;
      }
      catch (SecurityException s_e)
      {
         throw s_e;
      }

      // our policy is to make all public methods callable except the ones defined in java.lang.Object
      if (method.getDeclaringClass () == Class.forName ("java.lang.Object"))
         throw new Exception ("Invoker can't call methods defined in java.lang.Object");

      // invoke
      Object returnValue = null;
      try
      {
         returnValue = method.invoke (invokeTarget, argValues);
      }
      catch (IllegalAccessException iacc_e)
      {
         throw iacc_e;
      }
      catch (IllegalArgumentException iarg_e)
      {
         throw iarg_e;
      }
      catch (InvocationTargetException it_e)
      {
         if (debug)
               it_e.getTargetException ().printStackTrace ();
         // check whether the thrown exception is XmlRpcException
         Throwable t = it_e.getTargetException();
         /*
         if (t instanceof XmlRpcException)
               throw (XmlRpcException) t;
         // It is some other exception
         */
         throw new Exception (t.toString ());
      }

      return returnValue;
   }

} // class Invoker



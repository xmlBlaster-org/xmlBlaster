/*------------------------------------------------------------------------------
Name:      XBConnectionFactoryBuilder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.util.Enumeration;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.RefAddr;
import javax.naming.spi.ObjectFactory;

public class XBObjectFactory implements ObjectFactory {

   /**
    * Searches for a particular property. If the property exists in both the environment of
    * the context and in the particular hastable passed as the environment, than the 
    * later is taken.
    * 
    * @param key The name (id) of the property
    * @param context the context on which to look
    * @param env the additional environment to look into
    * @return
    */
   private Object getProperty(Object key, Context context, Hashtable env) 
      throws NamingException {
      if (env != null) {
         Object ret = env.get(key);
         if (ret != null) return ret;
      }
      if (context != null) {
         Hashtable env2 = context.getEnvironment();
         // env2 can never be null (according to requirement)
         return env2.get(key);
      }
      return null; // to make the compiler happy
   }
   
   public Object getObjectInstance(Object object, Name name, Context context, Hashtable env) 
      throws Exception {
      if (object instanceof Reference) {
         Reference ref = (Reference)object;
         String className = ref.getClassName();
            
         if (className.equals(XBConnectionFactory.class.getName())) {
            String[] args = new String[ref.size()-2];
            Enumeration iter = ref.getAll();
            RefAddr addr = (RefAddr)iter.nextElement();
            String forQueuesTxt = addr.getType();
            boolean forQueues = false;
            try {
               forQueues = Boolean.getBoolean(forQueuesTxt);
            }
            catch (Throwable ex) {
            }
            addr = (RefAddr)iter.nextElement();
            String qosLitteral = addr.getType(); 
            int i = 0;
            while (iter.hasMoreElements()) {
               addr = (RefAddr)iter.nextElement();
               args[i] = addr.getType();
               i++;
            }
            String connectQos = (String)getProperty(XBPropertyNames.CONNECT_QOS, context, env);
            if (connectQos != null) qosLitteral = connectQos;
            return new XBConnectionFactory(qosLitteral, args, forQueues);
         }

         if (className.equals(XBDestination.class.getName())) {
            RefAddr topicRef = ref.get("topicName");
            RefAddr queueRef = ref.get("queueName");
            RefAddr forceQueuingRef = ref.get("forceQueuing");
            String topicName = (String)topicRef.getContent();
            String queueName = (String)queueRef.getContent();
            String forceQueuingTxt = (String)forceQueuingRef.getContent();
            boolean forceQueuing = (new Boolean(forceQueuingTxt)).booleanValue();
            return new XBDestination(topicName, queueName, forceQueuing);
         }
      }
      return null;
   }
}


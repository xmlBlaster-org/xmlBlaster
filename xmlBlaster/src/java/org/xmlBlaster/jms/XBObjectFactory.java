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
import javax.naming.Reference;
import javax.naming.RefAddr;
import javax.naming.spi.ObjectFactory;

public class XBObjectFactory implements ObjectFactory {

   public Object getObjectInstance(Object object, Name name, Context context,
                                   Hashtable env) throws Exception {
      if (object instanceof Reference) {
         Reference ref = (Reference)object;
         String className = ref.getClassName();
            
         if (className.equals(XBConnectionFactory.class.getName())) {
            String[] args = new String[ref.size()];
            Enumeration iter = ref.getAll();
            int i = 0;
            while (iter.hasMoreElements()) {
               RefAddr addr = (RefAddr)iter.nextElement();
               args[i] = addr.getType();
               i++;
            }
            return new XBConnectionFactory(args);
         }

         if (className.equals(XBTopic.class.getName())) {
            RefAddr topicRef = ref.get("topicName");
            String topicName = (String)topicRef.getContent();
            return new XBTopic(topicName);
         }
      }
      return null;
   }
}


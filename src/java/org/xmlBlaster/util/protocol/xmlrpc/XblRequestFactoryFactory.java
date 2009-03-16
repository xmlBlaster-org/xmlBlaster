/*------------------------------------------------------------------------------
Name:      XblRequestFactoryFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the xmlBlaster interface for xml-rpc.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.xmlrpc;

import java.util.HashMap;
import java.util.Map;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.server.RequestProcessorFactoryFactory;

public class XblRequestFactoryFactory implements RequestProcessorFactoryFactory {

   
   public class XblRequestFactory implements RequestProcessorFactory {

      private Object obj;
      
      public XblRequestFactory(Object obj) {
         this.obj = obj;
      }
      
      public Object getRequestProcessor(XmlRpcRequest req) throws XmlRpcException {
         return obj;
      }
      
   }
   
   Map<String, XblRequestFactory> map;
   
   public XblRequestFactoryFactory() {
      map = new HashMap<String, XblRequestFactory>();
   }

   
   public synchronized boolean add(Object obj) {
      if (obj == null)
         return false;
      String name = obj.getClass().getName();
      if (map.containsKey(name))
         return false;
      XblRequestFactory factory = new XblRequestFactory(obj);
      map.put(name, factory);
      return true;
   }
   
   public synchronized RequestProcessorFactory getRequestProcessorFactory(Class clazz) throws XmlRpcException {
      if (clazz == null)
         return null;
      String name = clazz.getName();
      return map.get(name);
   }
   
   
   
}

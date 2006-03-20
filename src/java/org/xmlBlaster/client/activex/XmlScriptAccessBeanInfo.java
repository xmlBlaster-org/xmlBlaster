/*------------------------------------------------------------------------------
Name:      XmlScriptAccessBeanInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.activex;

import java.beans.SimpleBeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;

/**
 * Provide bean info for XmlScriptAccess to be used by ActiveX bridge callback events. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class XmlScriptAccessBeanInfo extends SimpleBeanInfo {
   private static String ME = "XmlScriptAccessBeanInfo";

   /**
    * Create a new bean info. 
    */
   public XmlScriptAccessBeanInfo() {
      //System.out.println(ME + "Calling ctor");
   }

   /**
    * Setting explicit event set info.
    */
   public EventSetDescriptor[] getEventSetDescriptors() {
      EventSetDescriptor[] arr = new EventSetDescriptor[1];
      Class sourceClass = XmlScriptAccess.class;
      String eventSetName = "update";   // -> UpdateEvent
      Class listenerType = UpdateListener.class; // -> addUpdateListener
      String listenerMethodName = "update";
      try {
         EventSetDescriptor ev = new EventSetDescriptor(sourceClass, eventSetName, listenerType, listenerMethodName);
         arr[0] = ev;
         return arr;
      }
      catch (IntrospectionException e) {
         System.out.println(ME + ": Problems creating EventSetDescriptor: " + e.toString());
         throw new RuntimeException(e.toString());
      }
   }

   /**
    * For testing: java org.xmlBlaster.client.activex.XmlScriptAccessBeanInfo
    */
   public static void main(String args[]) {
      XmlScriptAccessBeanInfo beanInfo = new XmlScriptAccessBeanInfo();
      EventSetDescriptor[] eArr = beanInfo.getEventSetDescriptors();
      for(int i=0; i<eArr.length; i++) {
         java.beans.MethodDescriptor[] mArr = eArr[i].getListenerMethodDescriptors();
         for(int j=0; j<mArr.length; j++)
            System.out.println("Method: " + mArr[j].getMethod().toString());
      }
   }
}


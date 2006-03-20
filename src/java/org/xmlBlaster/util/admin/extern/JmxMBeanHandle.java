/*------------------------------------------------------------------------------
Name:      JmxMBeanHandle.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin.extern;

import javax.management.ObjectInstance;
import org.xmlBlaster.util.context.ContextNode;

/**
 * Container to hold the ObjectInstance,mBean and ContextNode triple. 
 * @author Marcel Ruff
 */
public class JmxMBeanHandle {
   private ObjectInstance objectInstance;
   private ContextNode contextNode;
   private Object mBean;

   public JmxMBeanHandle(ObjectInstance objectInstance, ContextNode contextNode, Object mBean) {
      this.objectInstance = objectInstance;
      this.contextNode = contextNode;
      this.mBean = mBean;
   }

   /**
   * @return Returns the ObjectInstance.
   */
   public ObjectInstance getObjectInstance() {
      return this.objectInstance;
   }

   /**
   * @param objectInstance The ObjectInstance to set.
   */
   public void setObjectInstance(ObjectInstance objectInstance) {
      this.objectInstance = objectInstance;
   }

   /**
   * @return Returns the contextNode.
   */
   public ContextNode getContextNode() {
      return this.contextNode;
   }

   /**
   * @param contextNode The contextNode to set.
   */
   public void setContextNode(ContextNode contextNode) {
      this.contextNode = contextNode;
   }

   /**
   * @return Returns the mBean.
   */
   public Object getMBean() {
      return this.mBean;
   }

   /**
   * @param mBean The mBean to set.
   */
   public void setMBean(Object mBean) {
      this.mBean = mBean;
   }
}

package org.xmlBlaster.engine.cluster;

/**
 * Jmx access to configure a cluster node &lt;master/> setup.  
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public interface NodeMasterInfoMBean {
   void setStratum(int stratum);

   int getStratum();

   void setAcceptDefault(boolean acceptDefault);

   boolean isAcceptDefault();

   void setAcceptOtherDefault(boolean acceptOtherDefault);

   boolean isAcceptOtherDefault();

   boolean isDirtyRead();

   void setDirtyRead(boolean dirtyRead);
   
   String getConfiguration();
   
   String destroy();
   
   //Not easy to type the complete XML string ...
   String setConfiguration(String xml);
}

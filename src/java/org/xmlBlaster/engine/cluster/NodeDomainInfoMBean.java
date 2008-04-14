package org.xmlBlaster.engine.cluster;

/**
 * Jmx access to configure a cluster node &lt;master/> setup.  
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public interface NodeDomainInfoMBean {
   void setStratum(int stratum);

   int getStratum();

   void setAcceptDefault(boolean acceptDefault);

   boolean getAcceptDefault();

   void setAcceptOtherDefault(boolean acceptOtherDefault);

   boolean getAcceptOtherDefault();

   boolean getDirtyRead();

   void setDirtyRead(boolean dirtyRead);
   
   String getConfiguration();
   
   //Not easy to type the complete XML string ...
   //String setConfiguration(String xml);
}

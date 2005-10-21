/*------------------------------------------------------------------------------
Name:      ReplManagerPluginMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;


public interface ReplManagerPluginMBean {
   
   /**
    * Never returns null. It returns a list of keys identifying the ongoing replications.
    * @return
    */
   String[] getReplications();
   
   /**
    * Intiates the replication for the given slave.
    * 
    * @param slaveSessionName
    * @param replicationKey
    * @throws Exception
    */
   void initiateReplication(String slaveSessionName, String replicationKey) throws Exception;
   
}

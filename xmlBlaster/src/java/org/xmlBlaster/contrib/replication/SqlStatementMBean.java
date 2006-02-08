/*------------------------------------------------------------------------------
Name:      SqlStatementMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

/**
 * SqlStatementMBean
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface SqlStatementMBean {

   final static String OK = "ok";
   final static String FAILED = "failed";
   final static String WAITING = "waiting";
   
   /**
    * Gets the status which can either be FAILED, WAITING, OK
    * @return
    */
   String getStatus();
   
   /**
    * @return the number of received results (including failed and ok messages)
    */
   int getReceived();

   /**
    * @return all clients expected
    */
   int getAll();
   
   /** 
    * @return the comma separated list of entries received. The values are the SessionNames of the Slaves already received 
    */
   String getReceivedList();
   
   /**
    * @return the number of entries which have failed.
    */
   int getFailed();

   /**
    * @return the comma separated list of SessionNames of slaves which have failed
    */
   String getFailedList();
   
   /**
    * @return the original Statement
    */
   String getStatement();
   
   /**
    * @return the 'shall' response: the response given by the DbWatcher (or Master)
    */
   String getResponse();
   
   /**
    * Gets the response of the specified slave
    * @param slaveSessionName the session name of the slave for which to retrieve the response.
    * @return
    */
   String getSlaveResponse(String slaveSessionName);
   
   String getReplicationPrefix();
   
   String getRequestId();
   
   String getSlaveList();
   
}


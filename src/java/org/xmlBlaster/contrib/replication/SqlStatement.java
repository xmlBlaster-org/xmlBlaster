/*------------------------------------------------------------------------------
Name:      SqlStatement.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.xmlBlaster.contrib.InfoHelper;

/**
 * SqlStatement
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class SqlStatement implements SqlStatementMBean {

   final String me;
   private String status;
   private String replicationPrefix;
   private String requestId;
   private Map slaves;
   private List received;
   private List failed;
   private String referenceResponse;
   private String statement;
   private Object mbeanHandle;
   
   public SqlStatement(String replicationPrefix, String requestId, String statement, List slaveList) {
      this.status = WAITING;
      this.statement = statement;
      this.replicationPrefix = replicationPrefix;
      this.requestId = requestId;
      this.me = "SqlStatement-" + this.replicationPrefix + "-" + this.requestId;
      this.slaves = new TreeMap();
      for (int i=0; i < slaveList.size(); i++)
         this.slaves.put(slaveList.get(i), "");
      this.referenceResponse = "";
      this.received = new ArrayList();
      this.failed = new ArrayList();
   }
   
   public void setHandle(Object mbeanHandle) {
      this.mbeanHandle = mbeanHandle;
   }
   
   public Object getHandle() {
      return this.mbeanHandle;
   }
   
   public void setResponse(String id, String response, boolean isException) throws Exception {
      if (id == null)
         throw new Exception("setResponse for '" + this.me + "' failed since id of the slave/master is null");
      if (response == null)
         throw new Exception("setResponse for '" + this.me + "' failed since response for id '" + id + "' was null");
      String resp = (String)this.slaves.get(id);
      if (resp != null) {
         if (resp.length() > 0)
            throw new Exception("setResponse for '" + this.me + "' failed since response for id '" + id + "' has already been received before");
         if (this.referenceResponse.length() < 1)
            throw new Exception("setResponse for '" + this.me + "' failed for id '" + id + "' since the response of the master has not come yet");
         this.slaves.put(id, response);
         this.received.add(id);
         if (!response.equalsIgnoreCase(this.referenceResponse)) {
            this.failed.add(id);
            this.status = FAILED;
         }
         else {
            if (this.received.size() == this.slaves.size())
               this.status = OK;
         }
      }
      else {
         if (this.replicationPrefix.equals(id)) {
            this.referenceResponse = response;
         }
         else
            throw new Exception("setResponse for '" + this.me + "' failed for id '" + id + "' since not found among the slaves nor the master");
      }
      if (isException)
         this.status = FAILED;
   }
   
   /**
    * @see org.xmlBlaster.contrib.replication.SqlStatementMBean#getStatus()
    */
   public String getStatus() {
      return this.status;
   }

   /**
    * @see org.xmlBlaster.contrib.replication.SqlStatementMBean#getReceived()
    */
   public int getReceived() {
      return this.received.size();
   }

   public int getAll() {
      return this.slaves.size();
   }

   /**
    * @see org.xmlBlaster.contrib.replication.SqlStatementMBean#getReceivedList()
    */
   public String getReceivedList() {
      return InfoHelper.getIteratorAsString(this.received.iterator());
   }

   /**
    * @see org.xmlBlaster.contrib.replication.SqlStatementMBean#getFailed()
    */
   public int getFailed() {
      return this.failed.size();
   }

   /**
    * @see org.xmlBlaster.contrib.replication.SqlStatementMBean#getFailedList()
    */
   public String getFailedList() {
      return InfoHelper.getIteratorAsString(this.failed.iterator());
   }

   /**
    * @see org.xmlBlaster.contrib.replication.SqlStatementMBean#getStatement()
    */
   public String getStatement() {
      return this.statement;
   }

   /**
    * @see org.xmlBlaster.contrib.replication.SqlStatementMBean#getResponse()
    */
   public String getResponse() {
      return this.referenceResponse;
   }

   /**
    * @see org.xmlBlaster.contrib.replication.SqlStatementMBean#getSlaveResponse(java.lang.String)
    */
   public String getSlaveResponse(String slaveSessionName) {
      return (String)this.slaves.get(slaveSessionName);
   }

   /**
    * @see org.xmlBlaster.contrib.replication.SqlStatementMBean#getReplicationPrefix()
    */
   public String getReplicationPrefix() {
      return this.replicationPrefix;
   }

   /**
    * @see org.xmlBlaster.contrib.replication.SqlStatementMBean#getRequestId()
    */
   public String getRequestId() {
      return this.requestId;
   }

   /**
    * @see org.xmlBlaster.contrib.replication.SqlStatementMBean#getSlaveList()
    */
   public String getSlaveList() {
      return InfoHelper.getIteratorAsString(this.slaves.keySet().iterator());
   }

}

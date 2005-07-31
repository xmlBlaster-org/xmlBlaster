/*------------------------------------------------------------------------------
Name:      I_AdminNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Declares available methods of an xmlBlaster server instance for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by RequestBroker.java, delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 0.79f
 */
public interface I_AdminNode {
   /**
    * Get the number of known cluster nodes. 
    * @return The number of xmlBlaster cluster nodes
    */
   public int getNumNodes();
   /**
    * Get the names of all known xmlBlaster cluster nodes. 
    * @return A comma separated list of cluster node names
    */
   public String getNodeList();

   /**
    * Get my cluster node name. 
    * @return My cluster wide unique name
    */
   public String getNodeId();
   /**
    * Unique id of the xmlBlaster server, changes on each restart. 
    * If 'node/heron' is restarted, the instanceId changes.
    * @return nodeId + timestamp, '/node/heron/instanceId/33470080380'
    */
   public String getInstanceId();
   /**
    * Get the xmlBlaster version number. 
    * @return For example "1.0.4"
    */
   public String getVersion();
   /**
    * Returns the xmlBlaster SVN version control revision number. 
    * @return The subversion revision number of the monitored instance,
    *         for example "13593"
    */
   public String getRevisionNumber();
   /**
    * Returns the date when xmlBlaster was compiled. 
    * @return For example "07/28/2005 03:47 PM"
    */
   public String getBuildTimestamp();
   /**
    * The java vendor of the compiler. 
    * @return For example "Sun Microsystems Inc."
    */
   public String getBuildJavaVendor();
   /**
    * The compiler java version. 
    * @return For example "1.5.0"
    */
   public String getBuildJavaVersion();
   /**
    * Dump the complete internal state of xmlBlaster. 
    * Is an operation to not do it automatically on JMX load
    */
   public String dump() throws XmlBlasterException;
   /**
    * Dump the internal xmlBlaster state to the given file. 
    * @param fn The complete path and file name
    */
   public void setDump(String fn) throws XmlBlasterException;

   /**
    * Access the current run level of xmlBlaster. 
    * @return 0 is halted and 9 is fully operational
    */
   public String getRunlevel();

   /**
    * Change the run level of xmlBlaster. 
    * @param 0 is halted and 9 is fully operational
    */
   public void setRunlevel(String level) throws XmlBlasterException;

   /**
    * Returns the current server time in milliseconds. 
    * For an accuracy discussion please consult {@link System.currentTimeMillis}
    * @return For example 1111400317333
    */
   public long getServerTimestampMillis();
   
   /**
    * Access the current server time as a java.sql.Timestamp string. 
    * @return For example "2005-03-21 11:18:12.622"
    */
   public String getServerTimestamp();
   
   /**
    * Get date when xmlBlaster was started.
    * @return "2005-03-20 11:19:12.322"
    */
   public String getStartupDate();

   /**
    * Get elapsed seconds since xmlBlaster started
    */
   public long getUptime();

   /**
    * Access the last logged error.
    * @return The complete information about the last error logged
    */
   public String getLastError();

   /**
    * Access the last logged warning.
    * @return The complete information about the last warning logged
    */
   public String getLastWarning();

    /**
     * Returns the amount of free memory in the Java Virtual Machine.
     * Calling the 
     * <code>gc</code> method may result in increasing the value returned 
     * by <code>freeMemory.</code>
     *
     * @return  an approximation to the total amount of memory currently
     *          available for future allocated objects, measured in bytes.
     */
   public long getFreeMem();

    /**
     * Returns the total amount of memory in the Java virtual machine.
     * The value returned by this method may vary over time, depending on 
     * the host environment.
     * <p>
     * Note that the amount of memory required to hold an object of any 
     * given type may be implementation-dependent.
     * <p>
     * Increase with for example <tt>java -Xmx512M</tt> on startup.
     * 
     * @return  the total amount of memory currently available for current 
     *          and future objects, measured in bytes.
     */
   public long getTotalMem();

   /**
    * @return getTotalMem() - getFreeMem()
    */ 
   public long getUsedMem();

   /* TODO: rename to gc(); as it is an operation */
   public String getGc();
   public void setGc(String dummy);

   /**
    * Shutdown xmlBlaster, exit value is '0'
    */
   public void exit() throws XmlBlasterException;
   public void setExit(String exitValue) throws XmlBlasterException;

   public String getHostname();
   public int getPort();

   /**
    * How many clients instances are allocated. 
    */
   public int getNumClients();

   /**
    * Get the maximum allowed number of clients. 
    */
   public int getMaxClients();

   /**
    * Get the client names. 
    * @return A comma separated list
    */
   public String getClientList();

   /**
    * Get the number of system properties. 
    */
   public int getNumSysprop();

   /**
    * Get the system properties. 
    * @return A comma separated list
    */
   public String getSyspropList();

   /**
    * Get the number of topics. 
    */
   public int getNumTopics();

   /**
    * Get the topics. 
    * @return A comma separated list
    */
   public String getTopicList();

   /**
    * Get the number of subscriptions. 
    * @return A comma separated list
    */
   public int getNumSubscriptions();

   /**
    * Get the subscriptions. 
    * @return A comma separated list
    */
   public String getSubscriptionList();

   //public void setMaxClients(int maxClients);
}

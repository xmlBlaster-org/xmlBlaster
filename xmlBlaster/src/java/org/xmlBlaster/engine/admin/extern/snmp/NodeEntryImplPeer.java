package org.xmlBlaster.engine.admin.extern.snmp;

public class NodeEntryImplPeer
{

    private String nodeName;
    private long nodeUptime;
    private long totalMem;
    private long usedMem;
    private long freeMem;
    private String hostname;
    private long port;
    private long numClients;
    private long maxClients;
    private long clientThreshold; 
    private String errorLogfile;
    private int logLevel;
    private int runLevel;

    /**
     * NodeEntryImplPeer
     * - initializes mib variables.
     */
    public NodeEntryImplPeer(String nodeName, 
			     String hostname,
			     long port,
			     long maxClients,
			     long clientThreshold,
			     String errorLogfile,
			     int logLevel)
    {
        this.nodeName = nodeName;
        this.hostname = hostname;
        this.port = port;
        this.maxClients = maxClients;
        this.clientThreshold = clientThreshold;
        this.errorLogfile = errorLogfile;
        this.logLevel = logLevel;
    }

    /**
     * get_nodeName
     * - gets nodeName from xmlBlaster application.
     * 
     * @return String nodeName: name of an xmlBlaster node.
     */
    public String get_nodeName()
    {
        return nodeName;
    }

    /**
     * get_nodeUptime
     * - gets nodeUptime from xmlBlaster application.
     * 
     * @return long nodeUptime: uptime of an xmlBlaster node.
     */
    public long get_nodeUptime()
    {
        return nodeUptime;
    }

    /**
     * get_totalMem
     * - gets totalMem from xmlBlaster application.
     * 
     * @return long totalMem: total memory of the java virtual machine,
     *              where the xmlBlaster runs.
     */
    public long get_totalMem()
    {
        return totalMem;
    }

    /**
     * get_usedMem
     * - gets usedMem from xmlBlaster application.
     * 
     * @return long usedMem: used memory of the java virtual machine,
     *              where the xmlBlaster runs.
     */
    public long get_usedMem()
    {
        return usedMem;
    }

    /**
     * get_freeMem
     * - gets freeMem from xmlBlaster application.
     * 
     * @return long freeMem: free memory of the java virtual machine,
     *              where the xmlBlaster runs.
     */
    public long get_freeMem()
    {
        return freeMem;
    }

    /**
     * get_hostname
     * - gets hostname from xmlBlaster application.
     * 
     * @return String hostname: name of the host, where the xmlBlaster runs.
     */
    public String get_hostname()
    {
        return hostname;
    }

    /**
     * get_port
     * - gets port from xmlblaster application.
     * 
     * @return long port: port, on which the xmlBlaster runs.
     */
    public long get_port()
    {
        return port;
    }

    /**
     * get_numClients
     * - gets numClients from xmlBlaster application.
     * 
     * @return long numClients: actual number of clients in the clientTable.
     */
    public long get_numClients()
    {
        return numClients;
    }

    /**
     * get_maxClients
     * - gets maxClients from xmlBlaster application.
     * 
     * @return long maxClients: maximum number of clients in the clientTable.
     */
    public long get_maxClients()
    {
        return maxClients;
    }

    /**
     * get_clientThreshold
     * - gets clientThreshold from xmlBlaster application.
     * 
     * @return long clientThreshold: threshold (%) number of clients in the clientTable.
     */
    public long get_clientThreshold()
    {
        return clientThreshold;
    }

    /**
     * get_errorLogfile
     * - gets errorLogfile from xmlBlaster application.
     * 
     * @return String errorLogfile: name of the error logfile.
     */
    public String get_errorLogfile()
    {
        return errorLogfile;
    }

    /**
     * get_logLevel
     * - gets logLevel from xmlBlaster application.
     * 
     * @return int logLevel: various degrees of log levels.
     *             0 = errors
     *             1 = warnings
     *             2 = infos
     */
    public int get_logLevel()
    {
        return logLevel;
    }

    /**
     * get_runLevel
     * - gets runLevel from xmlBlaster application.
     * 
     * @return int runLevel: various degrees of log levels.
     *             0 = halted
     *             3 = standby
     *             6 = cleanup
     *            10 = running
     */
    public int get_runLevel()
    {
        return runLevel;
    }

}














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
    public NodeEntryImplPeer(String nodeNameVal, 
                    String hostnameVal,
                    long portVal, 
                    long maxClientsVal, 
                    long clientThresholdVal, 
                    String errorLogfileVal, 
                    int logLevelVal)
    {
        nodeName = nodeNameVal;
        hostname = hostnameVal;
        port = portVal;
        maxClients = maxClientsVal;
        clientThreshold = clientThresholdVal;
        errorLogfile = errorLogfileVal;
        logLevel = logLevelVal;
    }

    /**
     * get_nodeName
     * - forwards the call to nodeEntryImplPeer.get_nodeName().
     * 
     * @return String nodeName: name of an xmlBlaster node.
     */
    public String get_nodeName()
    {
        return nodeName;
    }

    /**
     * get_nodeUptime
     * - forwards the call to nodeEntryImplPeer.get_nodeUptime().
     * 
     * @return long nodeUptime: uptime of an xmlBlaster node.
     */
    public long get_nodeUptime()
    {
        return nodeUptime;
    }

    /**
     * get_totalMem
     * - forwards the call to nodeEntryImplPeer.get_totalMem().
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
     * - forwards the call to nodeEntryImplPeer.get_usedMem().
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
     * - forwards the call to nodeEntryImplPeer.get_freeMem().
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
     * - forwards the call to nodeEntryImplPeer.get_hostname().
     * 
     * @return String hostname: name of the host, where the xmlBlaster runs.
     */
    public String get_hostname()
    {
        return hostname;
    }

    /**
     * get_port
     * - forwards the call to nodeEntryImplPeer.get_port().
     * 
     * @return long port: port, on which the xmlBlaster runs.
     */
    public long get_port()
    {
        return port;
    }

    /**
     * get_numClients
     * - forwards the call to nodeEntryImplPeer.get_numClients().
     * 
     * @return long numClients: actual number of clients in the clientTable.
     */
    public long get_numClients()
    {
        return numClients;
    }

    /**
     * get_maxClients
     * - forwards the call to nodeEntryImplPeer.get_maxClients().
     * 
     * @return long maxClients: maximum number of clients in the clientTable.
     */
    public long get_maxClients()
    {
        return maxClients;
    }

    /**
     * get_clientThreshold
     * - forwards the call to nodeEntryImplPeer.get_clientThreshold().
     * 
     * @return long clientThreshold: threshold (%) number of clients in the clientTable.
     */
    public long get_clientThreshold()
    {
        return clientThreshold;
    }

    /**
     * get_errorLogfile
     * - forwards the call to nodeEntryImplPeer.get_errorLogfile().
     * 
     * @return String errorLogfile: name of the error logfile.
     */
    public String get_errorLogfile()
    {
        return errorLogfile;
    }

    /**
     * get_logLevel
     * - forwards the call to nodeEntryImplPeer.get_logLevel().
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
     * - forwards the call to nodeEntryImplPeer.get_runLevel().
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













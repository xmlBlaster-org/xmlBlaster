package org.xmlBlaster.engine.admin.extern.snmp;

/** 
 *  NodeEntryImplPeer is the implementation side of a bridge pattern.
 *  Implements the methods, which are called by NodeEntryImpl.
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
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
     * Initializes mib variables.
     * @param NodeName name of mom instance.
     * @param Hostname name of host.
     * @param Port port number.
     * @param MaxClients maximum number of mom clients.
     * @param ClientThreshold threshold number of mom clients.
     * @param ErrorLogfile name of error logfile.
     * @param LogLevel degree of log level (errors, warnings, infos).
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
     * Gets nodeName from xmlBlaster application.
     * @return NodeName name of an xmlBlaster node.
     */
    public String get_nodeName()
    {
        return nodeName;
    }

    /**
     * Gets nodeUptime from xmlBlaster application.
     * @return NodeUptime uptime of an xmlBlaster node.
     */
    public long get_nodeUptime()
    {
        return nodeUptime;
    }

    /**
     * Gets totalMem from xmlBlaster application.
     * @return TotalMem total memory of the java virtual machine, where the xmlBlaster runs.
     */
    public long get_totalMem()
    {
        return totalMem;
    }

    /**
     * Gets usedMem from xmlBlaster application.
     * @return UsedMem used memory of the java virtual machine, where the xmlBlaster runs.
     */
    public long get_usedMem()
    {
        return usedMem;
    }

    /**
     * Gets freeMem from xmlBlaster application.
     * @return FreeMem: free memory of the java virtual machine, where the xmlBlaster runs.
     */
    public long get_freeMem()
    {
        return freeMem;
    }

    /**
     * Gets hostname from xmlBlaster application.
     * @return Hostname name of the host, where the xmlBlaster runs.
     */
    public String get_hostname()
    {
        return hostname;
    }

    /**
     * Gets port from xmlblaster application.
     * @return Port identifies the xmlBlaster port.
     */
    public long get_port()
    {
        return port;
    }

    /**
     * Gets numClients from xmlBlaster application.
     * @return NumClients actual number of clients in the clientTable.
     */
    public long get_numClients()
    {
        return numClients;
    }

    /**
     * Gets maxClients from xmlBlaster application.
     * @return MaxClients maximum number of clients in the clientTable.
     */
    public long get_maxClients()
    {
        return maxClients;
    }

    /**
     * Gets clientThreshold from xmlBlaster application.
     * @return ClientThreshold threshold (%) number of clients in the clientTable.
     */
    public long get_clientThreshold()
    {
        return clientThreshold;
    }

    /**
     * Gets errorLogfile from xmlBlaster application.
     * @return ErrorLogfile name of the error logfile.
     */
    public String get_errorLogfile()
    {
        return errorLogfile;
    }

    /**
     * Gets logLevel from xmlBlaster application.
     * @return LogLevel various degrees of log levels.
     * 0 = errors, 1 = warnings, 2 = infos.
     */
    public int get_logLevel()
    {
        return logLevel;
    }

    /**
     * Gets runLevel from xmlBlaster application.
     * @return RunLevel various degrees of log levels.
     * 0 = halted, 3 = standby, 6 = cleanup, 10 = running
     */
    public int get_runLevel()
    {
        return runLevel;
    }

}














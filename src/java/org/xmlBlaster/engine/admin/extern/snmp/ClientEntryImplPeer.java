package org.xmlBlaster.engine.admin.extern.snmp;

/** 
 *  ClientEntryImplPeer is the implementation side of a bridge pattern.
 *  Implements the methods, which are called by ClientEntryImpl.
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
public class ClientEntryImplPeer
{
    private String clientName;
    private int peerType;
    private int connectionState;
    private long clientQueueMaxEntries;
    private long clientQueueThreshold;
    private int clearClientQueue;
    private long maxSessions;
    private long sessionThreshold;
    private long clientQueueNumEntries;
    private long numSessions;
    private long clientUptime;
    private long clientDowntime;

    /**
     * Initializes ClientEntry mib variables.
     * @param ClientName name of client.
     * @param PeerType client or mom.
     * @param ConnectionState up or down. 
     * @param ClientQueueMaxEntries maximum number of messages in ptp client queue.
     * @param ClientQueueThreshold threshold number of messages in ptp client queue.
     * @param ClearClientQueue for values > 0, the client queue must be cleared.
     * @param MaxSessions maximum number of client sessions.
     * @param SessionThreshold threshold number of client sessions.
     */
    public ClientEntryImplPeer(String clientName,
                               int peerType,
                               int connectionState,
                               long clientQueueMaxEntries,
                               long clientQueueThreshold,
                               int clearClientQueue,
                               long maxSessions,
                               long sessionThreshold)
    {
        this.clientName = clientName;
        this.peerType = peerType;
        this.connectionState = connectionState;
        this.clientQueueMaxEntries = clientQueueMaxEntries;
        this.clientQueueThreshold = clientQueueThreshold;
        this.clearClientQueue = clearClientQueue;
        this.maxSessions = maxSessions;
        this.sessionThreshold = sessionThreshold;
    }

    /**
     * Gets clientName from xmxlBlaster application.
     * @return ClientName name of an xmlBlaster client.
     */
    public String get_clientName()
    {
        return clientName;
    }

    /**
     * Gets peerType from xmlBlaster application.
     * @return PeerType type of peer entity (0 = client, 1 = mom).
     */
    public int get_peerType()
    {
        return peerType;
    }

    /**
     * Gets connectionState from xmlBlaster application.
     * @return ConnectionState state of the client connection (0 = down, 1 = up).
     */
    public int get_connectionState()
    {
        return connectionState;
    }

    /**
     * Gets clientQueueNumEntries from xmlBlaster application.
     * @return ClientQueueNumEntries actual number of messages in the
     * point to point client queue.
     */
    public long get_clientQueueNumEntries()
    {
        return clientQueueNumEntries;
    }

    /**
     * Gets clientQueueMaxEntries from xmlBlaster application.
     * @return ClientQueueMaxEntries maximum number of messages in the
     * point to point client queue.
     */
    public long get_clientQueueMaxEntries()
    {
        return clientQueueMaxEntries;
    }

    /**
     * Gets clientQueueThreshold from xmlBlaster application.
     * @return ClientQueueThreshold threshold (%) number of messages in the
     * point to point client queue.
     */
    public long get_clientQueueThreshold()
    {
        return clientQueueThreshold;
    }

    /**
     * Gets clearClientQueue from xmlBlaster application.
     * @return ClearClientQueue for values > 0, the point to point client queue is emptied.
     */
    public int get_clearClientQueue()
    {
        return clearClientQueue;
    }

    /**
     * Gets numSessions from xmlBlaster application.
     * @return NumSessions actual number of client sessions in the session table.
     */
    public long get_numSessions()
    {
        return numSessions;
    }

    /**
     * Gets maxSessions from xmlBlaster application.
     * @return MaxSessions maximum number of client sessions in the session table.
     */
    public long get_maxSessions()
    {
        return maxSessions;
    }

    /**
     * Gets sessionThreshold from xmlBlaster application.
     * @return SessionThreshold threshold (%) number of client sessions in the session table.
     */
    public long get_sessionThreshold()
    {
        return sessionThreshold;
    }

    /**
     * Gets clientUptime from xmlBlaster application.
     * @return ClientUptime client connection uptime.
     */
    public long get_clientUptime()
    {
        return clientUptime;
    }

    /**
     * gets clientDowntime from xmlBlaster application.
     * @return ClientDowntime client connection downtime.
     */
    public long get_clientDowntime()
    {
        return clientDowntime;
    }

}
















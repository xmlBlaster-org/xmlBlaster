package org.xmlBlaster.engine.admin.extern.snmp;

public class ClientEntryImplPeer
{

    private String clientName;
    private int peerType;
    private int connectionState;
    private long clientQueueMaxMsgs;
    private long clientQueueThreshold;
    private int clearClientQueue;
    private long maxSessions;
    private long sessionThreshold;
    private long clientQueueNumMsgs;
    private long numSessions;
    private long clientUptime;
    private long clientDowntime;

    public ClientEntryImplPeer(String clientNameVal,
			       int peerTypeVal,
			       int connectionStateVal,
			       long clientQueueMaxMsgsVal,
			       long clientQueueThresholdVal,
			       int clearClientQueueVal,
			       long maxSessionsVal,
                               long sessionThreshold)
    {
        clientName = clientNameVal;
        peerType = peerTypeVal;
        connectionState = connectionStateVal;
        clientQueueMaxMsgs = clientQueueMaxMsgsVal;
        clientQueueThreshold = clientQueueThresholdVal;
        clearClientQueue = clearClientQueueVal;
        maxSessions = maxSessionsVal;
        sessionThreshold = sessionThreshold;
    }

    /**
     * get_clientName
     * - gets clientName from xmxlBlaster application.
     * 
     * @return String clientName: name of an xmlBlaster client.
     */
    public String get_clientName()
    {
        return clientName;
    }

    /**
     * get_peerType
     * - gets peerType from xmlBlaster application.
     * 
     * @return int peerType: type of peer entity.
     *             0 = client type
     *             1 = mom type
     */
    public int get_peerType()
    {
        return peerType;
    }

    /**
     * get_connectionState
     * - gets connectionState from xmlBlaster application.
     * 
     * @return int connectionState: state of the client connection.
     *             0 = down
     *             1 = up
     */
    public int get_connectionState()
    {
        return connectionState;
    }

    /**
     * get_clientQueueNumMsgs
     * - gets clientQueueNumMsgs from xmlBlaster application.
     * 
     * @return long clientQueueNumMsgs: actual number of messages in the
     * point to point client queue.
     */
    public long get_clientQueueNumMsgs()
    {
        return clientQueueNumMsgs;
    }

    /**
     * get_clientQueueMaxMsgs
     * - gets clientQueueMaxMsgs from xmlBlaster application.
     * 
     * @return long clientQueueMaxMsgs: maximum number of messages in the
     * point to point client queue.
     */
    public long get_clientQueueMaxMsgs()
    {
        return clientQueueMaxMsgs;
    }

    /**
     * get_clientQueueThreshold
     * - gets clientQueueThreshold from xmlBlaster application.
     * 
     * @return long clientQueueThreshold: threshold (%) number of messages in the
     * point to point client queue.
     */
    public long get_clientQueueThreshold()
    {
        return clientQueueThreshold;
    }

    /**
     * get_clearClientQueue
     * - gets clearClientQueue from xmlBlaster application.
     * 
     * @return int clearClientQueue: if set to true (= 1),
     * the point to point client queue is emptied.
     */
    public int get_clearClientQueue()
    {
        return clearClientQueue;
    }

    /**
     * get_numSessions
     * - gets numSessions from xmlBlaster application.
     * 
     * @return long numSessions: actual number of client sessions in the session table.
     */
    public long get_numSessions()
    {
        return numSessions;
    }

    /**
     * get_maxSessions
     * - gets maxSessions from xmlBlaster application.
     * 
     * @return long maxSessions: maximum number of client sessions in the session table.
     */
    public long get_maxSessions()
    {
        return maxSessions;
    }

    /**
     * get_sessionThreshold
     * - gets sessionThreshold from xmlBlaster application.
     * 
     * @return long sessionThreshold: threshold (%) number of client sessions in the session table.
     */
    public long get_sessionThreshold()
    {
        return sessionThreshold;
    }

    /**
     * get_clientUptime
     * - gets clientUptime from xmlBlaster application.
     * 
     * @return long clientUptime: client connection uptime.
     */
    public long get_clientUptime()
    {
        return clientUptime;
    }

    /**
     * get_clientDowntime
     * - gets clientDowntime from xmlBlaster application.
     * 
     * @return long clientDowntime: client connection downtime.
     */
    public long get_clientDowntime()
    {
        return clientDowntime;
    }

}
















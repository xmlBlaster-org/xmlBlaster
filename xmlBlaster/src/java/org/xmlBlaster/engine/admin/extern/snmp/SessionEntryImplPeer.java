package org.xmlBlaster.engine.admin.extern.snmp;

public class SessionEntryImplPeer
{

    private String sessionName;
    private long cbQueueMaxMsgs;
    private long cbQueueThreshold;
    private int clearCbQueue;
    private int closeSession;
    private long cbQueueNumMsgs;

    public SessionEntryImplPeer(String sessionName,
				long cbQueueMaxMsgs,
				long cbQueueThreshold,
				int clearCbQueue,
                                int closeSession)
    {
        this.sessionName = sessionName;
        this.cbQueueMaxMsgs = cbQueueMaxMsgs;
        this.cbQueueThreshold = cbQueueThreshold;
	this.clearCbQueue = clearCbQueue;
	this.closeSession = closeSession;
    }

    /**
     * get_sessionName
     * - gets sessionName from xmlBlaster application.
     * 
     * @return String sessionName: name of a client session.
     */
    public String get_sessionName()
    {
        return sessionName;
    }

    /**
     * get_cbQueueNumMsgs
     * - gets cbQueueNumMsgs from xmlBlaster application.
     * 
     * @return long cbQueueNumMsgs: actual number of messages in the callback queue.
     */
    public long get_cbQueueNumMsgs()
    {
        return cbQueueNumMsgs;
    }

    /**
     * get_cbQueueMaxMsgs
     * - gets cbQueueMaxMsgs from xmlBlaster application.
     * 
     * @return long cbQueueMaxMsgs: maximum number of messages in the callback queue.
     */
    public long get_cbQueueMaxMsgs()
    {
        return cbQueueMaxMsgs;
    }

    /**
     * get_cbQueueThreshold
     * - gets cbQueueThreshold from xmlBlaster application.
     * 
     * @return long cbQueueThreshold: threshold (%) number of messages in the callback queue.
     */
    public long get_cbQueueThreshold()
    {
        return cbQueueThreshold;
    }

    /**
     * get_clearCbQueue
     * - gets clearCbQueue from xmlBlaster application.
     * 
     * @return int clearCbQueue: if set to true (= 1), the callback queue is emptied.
     */
    public int get_clearCbQueue()
    {
        return clearCbQueue;
    }

    /**
     * get_closeSession
     * - gets closeSession from xmlBlaster application.
     * 
     * @return int closeSession: if set to true (= 1), the session is closed.
     */
    public int get_closeSession()
    {
        return closeSession;
    }
}





















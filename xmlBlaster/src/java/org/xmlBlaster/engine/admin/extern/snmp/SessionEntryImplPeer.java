package org.xmlBlaster.engine.admin.extern.snmp;

/** 
 *  SessionEntryImplPeer is the implementation side of a bridge pattern.
 *  Implements the methods, which are called by SessionEntryImpl.
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
public class SessionEntryImplPeer
{

    private String sessionName;
    private long cbQueueMaxMsgs;
    private long cbQueueThreshold;
    private int clearCbQueue;
    private int closeSession;
    private long cbQueueNumMsgs;

    /**
     * Initializes SessionEntry mib variables.
     * @param SessionName name of a session.
     * @param CbQueueMaxMsgs maximum number of messages in cbQueue.
     * @param CbQueueThreshold threshold number of messages in callback queue.
     * @param ClearCbQueue if > 0, the callback queue must be cleared.
     * @param CloseSession if > 0, the session is closed.
     */
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
     * Gets sessionName from xmlBlaster application.
     * @return SessionName name of a client session.
     */
    public String get_sessionName()
    {
        return sessionName;
    }

    /**
     * Gets cbQueueNumMsgs from xmlBlaster application.
     * @return CbQueueNumMsgs actual number of messages in the callback queue.
     */
    public long get_cbQueueNumMsgs()
    {
        return cbQueueNumMsgs;
    }

    /**
     * Gets cbQueueMaxMsgs from xmlBlaster application.
     * @return CbQueueMaxMsgs maximum number of messages in the callback queue.
     */
    public long get_cbQueueMaxMsgs()
    {
        return cbQueueMaxMsgs;
    }

    /**
     * Gets cbQueueThreshold from xmlBlaster application.
     * @return CbQueueThreshold threshold (%) number of messages in the callback queue.
     */
    public long get_cbQueueThreshold()
    {
        return cbQueueThreshold;
    }

    /**
     * Gets closeSession from xmlBlaster application.
     * Indicates the session status.
     * = 0: the session is open.
     * > 0: the session is closed.
     * @return CloseSession indicates whether the session is open (= 0) or closed (> 1).
     */
    public int get_closeSession()
    {
        return closeSession;
    }
}





















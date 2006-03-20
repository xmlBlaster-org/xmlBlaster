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
    private long cbQueueMaxEntries;
    private long cbQueueThreshold;
    private int clearCbQueue;
    private int closeSession;
    private long cbQueueNumEntries;

    /**
     * Initializes SessionEntry mib variables.
     * @param SessionName name of a session.
     * @param CbQueueMaxEntries maximum number of messages in cbQueue.
     * @param CbQueueThreshold threshold number of messages in callback queue.
     * @param ClearCbQueue if > 0, the callback queue must be cleared.
     * @param CloseSession if > 0, the session is closed.
     */
    public SessionEntryImplPeer(String sessionName,
                                long cbQueueMaxEntries,
                                long cbQueueThreshold,
                                int clearCbQueue,
                                int closeSession)
    {
        this.sessionName = sessionName;
        this.cbQueueMaxEntries = cbQueueMaxEntries;
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
     * Gets cbQueueNumEntries from xmlBlaster application.
     * @return CbQueueNumEntries actual number of messages in the callback queue.
     */
    public long get_cbQueueNumEntries()
    {
        return cbQueueNumEntries;
    }

    /**
     * Gets cbQueueMaxEntries from xmlBlaster application.
     * @return CbQueueMaxEntries maximum number of messages in the callback queue.
     */
    public long get_cbQueueMaxEntries()
    {
        return cbQueueMaxEntries;
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





















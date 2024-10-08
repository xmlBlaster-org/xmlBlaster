/*
 * This Java file has been generated by smidump 0.3.1. It
 * is intended to be edited by the application programmer and
 * to be used within a Java AgentX sub-agent environment.
 *
 * $Id$
 */
package org.xmlBlaster.engine.admin.extern.snmp;

/**
 *  This class extends the Java AgentX (JAX) implementation of
 *  the table row nodeEntry defined in XMLBLASTER-MIB.
 *  NodeEntryImpl is the interface side of a bridge pattern.
 *  Contains a reference to the implementation side of the bridge pattern (= NodeEntryImplPeer).
 *  Implements its methods by forwarding its calls to NodeEntryImplPeer.
 *  
 *  @version @VERSION@
 *  @author Udo Thalmann
 */

import jax.AgentXOID;
import jax.AgentXSetPhase;
import jax.AgentXResponsePDU;
import jax.AgentXEntry;

public class NodeEntryImpl extends NodeEntry
{

    public NodeEntryImplPeer nodeEntryImplPeer;

    /**
     * NodeEntryImpl initializes mib variables.
     * Builds a reference to NodeEntryImplPeer, which implements NodeEntryImpl methods.
     * @param NodeIndex identifies a node in nodeTable.
     * @param NodeEntryImplPeer implements NodeEntryImpl methods.
     */
    public NodeEntryImpl(long nodeIndex, 
                         NodeEntryImplPeer nodeEntryImplPeer)
    {
        super(nodeIndex);
        nodeName = nodeEntryImplPeer.get_nodeName().getBytes();
        hostname = nodeEntryImplPeer.get_hostname().getBytes();
        port = nodeEntryImplPeer.get_port();
        maxClients = nodeEntryImplPeer.get_maxClients();
        clientThreshold = nodeEntryImplPeer.get_clientThreshold();
        errorLogfile = nodeEntryImplPeer.get_errorLogfile().getBytes();
        logLevel = nodeEntryImplPeer.get_logLevel();
        this.nodeEntryImplPeer = nodeEntryImplPeer;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_nodeName().
     * @return NodeName name of an xmlBlaster node.
     */
    public byte[] get_nodeName()
    {
        // nodeName = nodeEntryImplPeer.get_nodeName();
        return nodeName;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_nodeUptime().
     * @return NodeUptime uptime of an xmlBlaster node.
     */
    public long get_nodeUptime()
    {
        // nodeUptime = nodeEntryImplPeer.get_nodeUptime();
        return nodeUptime;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_totalMem().
     * @return TotalMem total memory of the java virtual machine, where the xmlBlaster runs.
     */
    public long get_totalMem()
    {
        // totalMem = nodeEntryImplPeer.get_totalMem();
        return totalMem;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_usedMem().
     * @return UsedMem used memory of the java virtual machine, where the xmlBlaster runs.
     */
    public long get_usedMem()
    {
        // usedMem = nodeEntryImplPeer.get_usedMem();
        return usedMem;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_freeMem().
     * @return FreeMem free memory of the java virtual machine, where the xmlBlaster runs.
     */
    public long get_freeMem()
    {
        // freeMem = nodeEntryImplPeer.get_freeMem();
        return freeMem;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_hostname().
     * @return Hostname name of the host, where the xmlBlaster runs.
     */
    public byte[] get_hostname()
    {
        // hostname = nodeEntryImplPeer.get_hostname();
        return hostname;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_port().
     * @return Port identifies the xmlBlaster port.
     */
    public long get_port()
    {
        // port = nodeEntryImplPeer.get_port();
        return port;
    }

    /**
     * Implements the snmp set command for the mib object port.
     * @param AgentXSetPhase 
     * @param Value to be set
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
    public int set_port(AgentXSetPhase phase, long value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_port = port;
            port = value;
            break;
        case AgentXSetPhase.UNDO:
            port = undo_port;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_numClients().
     * @return NumClients actual number of clients in the clientTable.
     */
    public long get_numClients()
    {
        // numClients = nodeEntryImplPeer.get_numClients();
        return numClients;
    }

    /**
     * orwards the call to nodeEntryImplPeer.get_maxClients().
     * @return MaxClients maximum number of clients in the clientTable.
     */
    public long get_maxClients()
    {
        // maxClients = nodeEntryImplPeer.get_maxClients();
        return maxClients;
    }

    /**
     * Implements the snmp set command for the mib object maxClients.
     * @param AgentXSetPhase
     * @param Value to be set.
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
    public int set_maxClients(AgentXSetPhase phase, long value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_maxClients = maxClients;
            maxClients = value;
            break;
        case AgentXSetPhase.UNDO:
            maxClients = undo_maxClients;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_clientThreshold().
     * @return ClientThreshold threshold (%) number of clients in the clientTable.
     */
    public long get_clientThreshold()
    {
        // clientThreshold = nodeEntryImplPeer.get_clientThreshold();
        return clientThreshold;
    }

    /**
     * Implements the snmp set command for the mib object clientThreshold.
     * @param AgentXSetPhase
     * @param Value to be set.
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
    public int set_clientThreshold(AgentXSetPhase phase, long value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_clientThreshold = clientThreshold;
            clientThreshold = value;
            break;
        case AgentXSetPhase.UNDO:
            clientThreshold = undo_clientThreshold;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_errorLogfile().
     * @return ErrorLogfile name of the error logfile.
     */
    public byte[] get_errorLogfile()
    {
        // errorLogfile = nodeEntryImplPeer.get_errorLogfile();
        return errorLogfile;
    }

    /**
     * Implements the snmp set command for the mib object errorLogfile.
     * @param AgentXSetPhase
     * @param Value to be set
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
    public int set_errorLogfile(AgentXSetPhase phase, byte[] value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_errorLogfile = errorLogfile;
            errorLogfile = new byte[value.length];
            for(int i = 0; i < value.length; i++)
                errorLogfile[i] = value[i];
            break;
        case AgentXSetPhase.UNDO:
            errorLogfile = undo_errorLogfile;
            break;
        case AgentXSetPhase.CLEANUP:
            undo_errorLogfile = null;
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_logLevel().
     * @return LogLevel various degrees of log levels (0 = errors, 1 = warnings, 2 = infos).
     */
    public int get_logLevel()
    {
        return logLevel;
    }

    /**
     * Implements the snmp set command for the mib object logLevel.
     * @param AgentXSetPhase
     * @param Value to be set.
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
    public int set_logLevel(AgentXSetPhase phase, int value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_logLevel = logLevel;
            logLevel = value;
            break;
        case AgentXSetPhase.UNDO:
            logLevel = undo_logLevel;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }

    /**
     * Forwards the call to nodeEntryImplPeer.get_runLevel().
     * @return int runLevel various degrees of log levels.
     * 0 = halted, 3 = standby, 6 = cleanup, 10 = running.
     */
    public int get_runLevel()
    {
        return runLevel;
    }

    /**
     * Implements the snmp set command for the mib object runLevel.
     * @param AgentXSetPhase
     * @param Value to be set.
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
    public int set_runLevel(AgentXSetPhase phase, int value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_runLevel = runLevel;
            runLevel = value;
            break;
        case AgentXSetPhase.UNDO:
            runLevel = undo_runLevel;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }
}



















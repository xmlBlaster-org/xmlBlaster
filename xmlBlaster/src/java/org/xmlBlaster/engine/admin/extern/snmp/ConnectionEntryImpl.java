/*
 * This Java file has been generated by smidump 0.3.1. It
 * is intended to be edited by the application programmer and
 * to be used within a Java AgentX sub-agent environment.
 *
 * $Id: ConnectionEntryImpl.java,v 1.3 2002/07/19 11:08:57 udo Exp $
 */
package org.xmlBlaster.engine.admin.extern.snmp;


import jax.AgentXOID;
import jax.AgentXSetPhase;
import jax.AgentXResponsePDU;
import jax.AgentXEntry;

/**
 *  This class extends the Java AgentX (JAX) implementation of
 *  the table row connectionEntry defined in XMLBLASTER-MIB.
 *  ConnectionEntryImpl is the interface side of a bridge pattern.
 *  Ccontains a reference to the implementation side of the bridge pattern (= ConnectionEntryImplPeer).
 *  Implements its methods by forwarding its calls to ConnectionEntryImplPeer.
 *  
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
public class ConnectionEntryImpl extends ConnectionEntry
{

    public ConnectionEntryImplPeer connectionEntryImplPeer;

    /**
     * Initializes mib variables.
     * Builds a reference to ConnectionEntryImplPeer, which implements ConnectionEntryImpl methods.
     * @param NodeIndex identifies a node in nodeTable.
     * @param ConnectionIndex identifies a connection in connectionTable together with nodeIndex.
     * @param ConnectionEntryImplPeer implements ConnectionEntryImpl methods.
     */
    public ConnectionEntryImpl(long nodeIndex,
			       long connectionIndex,
			       ConnectionEntryImplPeer connectionEntryImplPeer)
    {
        super(nodeIndex, connectionIndex);
        connectionHost = connectionEntryImplPeer.get_connectionHost().getBytes();
        connectionPort = connectionEntryImplPeer.get_connectionPort();
        connectionAddress = connectionEntryImplPeer.get_connectionAddress().getBytes();
        connectionProtocol = connectionEntryImplPeer.get_connectionProtocol();
        this.connectionEntryImplPeer = connectionEntryImplPeer;
    }

    /**
     * Forwards the call to connectionEntryImplPeer.get_connectionHost().
     * @return ConnectionHost name of the connected host.
     */
    public byte[] get_connectionHost()
    {
        // connectionHost = connectionEntryImplPeer.get_connectionHost();
        return connectionHost;
    }

    /**
     * Forwards the call to connectionEntryImplPeer.get_connectionHost().
     * @return ConnectionPort port of the connected host.
     */
    public long get_connectionPort()
    {
        // connectionPort = connectionEntryImplPeer.get_connectionPort();
        return connectionPort;
    }

    /**
     * Forwards the call to connectionEntryImplPeer.get_connectionAddress().
     * @return ConnectionAddress address of the connected host.
     */
    public byte[] get_connectionAddress()
    {
        // connectionAddress = connectionEntryImplPeer.get_connectionAddress();
        return connectionAddress;
    }

    /**
     * Forwards the call to connectionEntryImplPeer.get_connectionProtocol().
     * @return ConnectionProtocol protocol used for connection, 
     * i.e. bootstrap, ior, rmi, xmlrpc, socket, etc.
     */
    public int get_connectionProtocol()
    {
        // connectionProtocol = connectionEntryImplPeer.get_connectionProtocol();
        return connectionProtocol;
    }

}











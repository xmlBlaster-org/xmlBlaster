package org.xmlBlaster.engine.admin.extern.snmp;

/** 
 *  ConnectionEntryImplPeer is the implementation side of a bridge pattern.
 *  Implements the methods, which are called by ConnectionEntryImpl.
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
public class ConnectionEntryImplPeer
{

    private String connectionHost;
    private long connectionPort;
    private String connectionAddress;
    private int connectionProtocol;

    /**
     * Initializes ConnectionEntry mib variables.
     * @param ConnectionHost name of connected host.
     * @param ConnectionPort number of connected port.
     * @param ConnectionAddress connection protocol specific address, e.g. http:://www.netscape.de/.
     * @param ConnectionProtocol e.g. bootstrap, ior, rmi, xmlrpc, socket, etc.
     */
    public ConnectionEntryImplPeer(String connectionHost,
				   long connectionPort,
				   String connectionAddress,
				   int connectionProtocol)
    {
        this.connectionHost = connectionHost;
        this.connectionPort = connectionPort;
        this.connectionAddress = connectionAddress;
        this.connectionProtocol = connectionProtocol;
    }

    /**
     * Gets connectionHost from xmlBlaster application.
     * @return ConnectionHost name of the connected host.
     */
    public String get_connectionHost()
    {
        return connectionHost;
    }

    /**
     * Gets connectionPort from xmlBlaster application.
     * @return ConnectionPort port of the connected host.
     */
    public long get_connectionPort()
    {
        return connectionPort;
    }

    /**
     * Gets connectionAddress from xmlBlaster application.
     * @return ConnectionAddress address of the connected host.
     */
    public String get_connectionAddress()
    {
        return connectionAddress;
    }

    /**
     * Gets connectionProtocol from xmlBlaster application.
     * @return ConnectionProtocol protocol used for connection, 
     * i.e. bootstrap, ior, rmi, xmlrpc, socket, etc.
     */
    public int get_connectionProtocol()
    {
        return connectionProtocol;
    }

}









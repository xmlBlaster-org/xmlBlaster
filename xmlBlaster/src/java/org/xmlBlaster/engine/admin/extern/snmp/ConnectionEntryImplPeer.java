package org.xmlBlaster.engine.admin.extern.snmp;

public class ConnectionEntryImplPeer
{

    private String connectionHost;
    private long connectionPort;
    private String connectionAddress;
    private int connectionProtocol;

    public ConnectionEntryImplPeer(String connectionHostVal,
                          long connectionPortVal,
                          String connectionAddressVal,
                          int connectionProtocolVal)
    {
        connectionHost = connectionHostVal;
        connectionPort = connectionPortVal;
        connectionAddress = connectionAddressVal;
        connectionProtocol = connectionProtocolVal;
    }

    /**
     * get_connectionHost
     * - forwards the call to connectionEntryImplPeer.get_connectionHost().
     * 
     * @return String connectionHost: name of the connected host.
     */
    public String get_connectionHost()
    {
        // connectionHost = connectionEntryImplPeer.get_connectionHost();
        return connectionHost;
    }

    /**
     * get_connectionPort
     * - forwards the call to connectionEntryImplPeer.get_connectionHost().
     * 
     * @return long connectionPort: port of the connected host.
     */
    public long get_connectionPort()
    {
        // connectionPort = connectionEntryImplPeer.get_connectionPort();
        return connectionPort;
    }

    /**
     * get_connectionAddress
     * - forwards the call to connectionEntryImplPeer.get_connectionAddress().
     * 
     * @return String connectionAddress: address of the connected host.
     */
    public String get_connectionAddress()
    {
        // connectionAddress = connectionEntryImplPeer.get_connectionAddress();
        return connectionAddress;
    }

    /**
     * get_connectionProtocol
     * - forwards the call to connectionEntryImplPeer.get_connectionProtocol().
     * 
     * @return int connectionProtocol: protocol used for connection, 
     * i.e. bootstrap, ior, rmi, xmlrpc, socket, etc.
     */
    public int get_connectionProtocol()
    {
        // connectionProtocol = connectionEntryImplPeer.get_connectionProtocol();
        return connectionProtocol;
    }

}








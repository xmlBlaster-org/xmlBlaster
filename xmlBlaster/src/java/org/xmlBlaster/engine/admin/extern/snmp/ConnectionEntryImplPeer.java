package org.xmlBlaster.engine.admin.extern.snmp;

public class ConnectionEntryImplPeer
{

    private String connectionHost;
    private long connectionPort;
    private String connectionAddress;
    private int connectionProtocol;

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
     * get_connectionHost
     * - gets connectionHost from xmlBlaster application.
     * 
     * @return String connectionHost: name of the connected host.
     */
    public String get_connectionHost()
    {
        return connectionHost;
    }

    /**
     * get_connectionPort
     * - gets connectionPort from xmlBlaster application.
     * 
     * @return long connectionPort: port of the connected host.
     */
    public long get_connectionPort()
    {
        return connectionPort;
    }

    /**
     * get_connectionAddress
     * - gets connectionAddress from xmlBlaster application.
     * 
     * @return String connectionAddress: address of the connected host.
     */
    public String get_connectionAddress()
    {
        return connectionAddress;
    }

    /**
     * get_connectionProtocol
     * - gets connectionProtocol from xmlBlaster application.
     * 
     * @return int connectionProtocol: protocol used for connection, 
     * i.e. bootstrap, ior, rmi, xmlrpc, socket, etc.
     */
    public int get_connectionProtocol()
    {
        return connectionProtocol;
    }

}








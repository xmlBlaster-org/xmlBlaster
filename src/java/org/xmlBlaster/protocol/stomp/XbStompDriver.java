package org.xmlBlaster.protocol.stomp;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import javax.net.ServerSocketFactory;

import org.codehaus.stomp.StompHandler;
import org.codehaus.stomp.StompHandlerFactory;
import org.codehaus.stomp.tcp.TcpTransportServer;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * XbStomp driver class to invoke the xmlBlaster server over STOMP protocol.
 * <p />
 * @see <a href="http://stomp.codehaus.org/">Website</a>
 * @see <a href="http://stomp.codehaus.org/Protocol">Protocol describtion</a>
 * @author Dieter Saken
 */




public class XbStompDriver implements I_Driver, StompHandlerFactory {
	private static Logger log = Logger.getLogger(XbStompDriver.class.getName());
	  private String ME = "XbStompDriver";
	   /** The global handle */
	   private Global glob;
	   private String uri = "tcp://localhost:11111";
	   private URI location;
	   private ServerSocketFactory serverSocketFactory;
	   private TcpTransportServer tcpServer;
	 
	public void activate() throws XmlBlasterException {
	     log.info("activate");
         try {
			doStart();
		} catch (Exception e) {
			throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION, "activate", e.getMessage(),e);
		}

	}

	public void deActivate() throws XmlBlasterException {
		try {
		     log.info("deActivate");
		     			doStop();
		} catch (Throwable ex) {
	         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "init. Could'nt shutdown the driver.", ex);
		}

	}

	public String getName() {
		return XbStompDriver.class.getName();
	}

	public String getProtocolId() {
		return XbStompInOutBridge.PROTOCOL_NAME;
	}

	public String getRawAddress() {
		try {
			return getTcpServer().getBindLocation().toString();
		} catch (Throwable e) {
		   ;
		}
		return null;
	}

	public String usage() {
		return "Usage info";
	}

	public String getType() {
		return XbStompInOutBridge.PROTOCOL_NAME;
	}

	public String getVersion() {
		return "1.0";
	}

	public void init(Global glob, PluginInfo pluginInfo)
			throws XmlBlasterException {
		this.glob = glob;
	    try{
	        activate();
		}
		catch (XmlBlasterException ex) {
		         throw ex;
	    }
		catch (Throwable ex) {
		         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "init. Could'nt initialize the driver.", ex);
		}

	}

	public void shutdown() throws XmlBlasterException {
		try {
			doStop();
			this.tcpServer = null;
		} catch (Throwable ex) {
	         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "init. Could'nt shutdown the driver.", ex);
		}
	}
	
 /////////// STOMP
    
    public StompHandler createStompHandler(StompHandler outputHandler)  {
         return new XbStompInOutBridge(this.glob, this, outputHandler);
    }

    /**
     * Joins with the background thread until the transport is stopped
     */
    public void join() throws IOException, URISyntaxException, InterruptedException {
        getTcpServer().join();
    }



    public String getUri() {
        return uri;
    }

    /**
     * Sets the URI string for the hostname/IP address and port to listen on for STOMP frames
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    public URI getLocation() throws URISyntaxException {
        if (location == null) {
            location = new URI(uri);
        }
        return location;
    }

    /**
     * Sets the URI for the hostname/IP address and port to listen on for STOMP frames
     */
    public void setLocation(URI location) {
        this.location = location;
    }

    public ServerSocketFactory getServerSocketFactory() {
        if (serverSocketFactory == null) {
            serverSocketFactory = ServerSocketFactory.getDefault();
        }
        return serverSocketFactory;
    }

    /**
     * Sets the {@link ServerSocketFactory} to use to listen for STOMP frames
     */
    public void setServerSocketFactory(ServerSocketFactory serverSocketFactory) {
        this.serverSocketFactory = serverSocketFactory;
    }

    public TcpTransportServer getTcpServer() throws IOException, URISyntaxException {
        if (tcpServer == null) {
            tcpServer = createTcpServer();
        }
        return tcpServer;
    }

    public void setTcpServer(TcpTransportServer tcpServer) {
        this.tcpServer = tcpServer;
    }



    // Implementation methods
    //-------------------------------------------------------------------------
    protected void doStart() throws Exception {
          getTcpServer().start();
    }

    protected void doStop() throws Exception {
        log.info("stop tcpserver");
     	getTcpServer().stop();
    }

    protected TcpTransportServer createTcpServer() throws IOException, URISyntaxException {
        return new TcpTransportServer(this, getLocation(), getServerSocketFactory());
    }

}

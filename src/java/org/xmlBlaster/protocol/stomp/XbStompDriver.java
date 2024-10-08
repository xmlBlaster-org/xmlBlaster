package org.xmlBlaster.protocol.stomp;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ServerSocketFactory;

import org.codehaus.stomp.StompHandler;
import org.codehaus.stomp.StompHandlerFactory;
import org.codehaus.stomp.tcp.TcpTransportServer;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * XbStomp driver class to invoke the xmlBlaster server over STOMP protocol.
 * <p />
 * 
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.stomp.html">XmlBlaster protocol integration</a>
 * @see <a href="http://stomp.codehaus.org/">Stomp website</a>
 * @see <a href="http://stomp.codehaus.org/Protocol">Stomp protocol describtion</a>
 * @author Dieter Saken
 */

public class XbStompDriver implements I_Driver, StompHandlerFactory, XbStompDriverMBean {
	private static Logger log = Logger.getLogger(XbStompDriver.class.getName());
	private String ME = "XbStompDriver";
	/** The global handle */
	private Global glob;
	/**
	 * The socket address info object holding hostname (useful for multi homed
	 * hosts) and port
	 */
	private SocketUrl socketUrl;
	private URI location;
	private ServerSocketFactory serverSocketFactory;
	private TcpTransportServer tcpServer;
	private AddressServer addressServer;
	/** The singleton handle for this authentication server */
	private I_Authenticate authenticate;
	/* The singleton handle for this xmlBlaster server */
	//private I_XmlBlaster xmlBlasterImpl;
	/** My JMX registration */
	protected Object mbeanHandle;
	protected ContextNode contextNode;
	private boolean isShutdown;
	
	/** Remember all client connections */
	private Set<XbStompInOutBridge> clientSet = new HashSet<XbStompInOutBridge>();
	private int connectCounter;
	
	public void activate() throws XmlBlasterException {
		log.info("activate, starting stomp socket listener");
		this.isShutdown = false;
		try {
			doStart();
		} catch (Exception e) {
			throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION,
					"activate", e.getMessage(), e);
		}

	}

	public void deActivate() { // throws XmlBlasterException {
		try {
			log.info("deActivate, stopping stomp socket listener");
			doStop();
		} catch (Throwable ex) {
			ex.printStackTrace();
			//throw new XmlBlasterException(this.glob,
			//		ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
			//		"init. Could'nt shutdown the driver.", ex);
		}

		XbStompInOutBridge[] arr = getClients();
		log.info("deActivate, throwing out " + arr.length + " clients");
		// shutdown all clients connected
		for (XbStompInOutBridge client: arr) {
			client.shutdown();
			removeClient(client);
		}
		clearClients();
	}

	//@Override //JMX
	public String freeBlockingCallbackThread(String loginName) {
		try {
			log.info("freeBlockingCallbackThread for " + loginName);
			XbStompInOutBridge bridge = findClient(loginName);
			if (bridge != null) {
				int count = bridge.notifyAllFrameAcks();
				return "Notified " + count + " waiting callback threads";
			}
			return "Your given loginName '" + loginName + "' is not known";
		} catch (Throwable e) {
			e.printStackTrace();
			log.severe("freeBlockingCallbackThread for " + loginName + ": " + e.toString());
			return "freeBlockingCallbackThread for " + loginName + " failed: " + e.toString();
		}
	}
	
	//@Override //JMX
	public String[] showBlockingCallbackTreads(String loginName) {
		try {
			log.info("showBlockingCallbackTreads for " + loginName);
			XbStompInOutBridge bridge = findClient(loginName);
			if (bridge != null) {
				RequestHolder[] holders = bridge.getFramesToAck();
				if (holders.length == 0) {
					return new String[] {"No waiting threads for " + loginName};
				}
				String[] ret = new String[holders.length];
				for (int i=0; i<holders.length; i++) {
					log.info("showBlockingCallbackTreads for " + loginName + " found " + holders[i].toString());
					ret[i] = holders[i].toString();
				}
				return ret;
			}
			return new String[] {"Your given loginName '" + loginName + "' is not known"};
		} catch (Throwable e) {
			e.printStackTrace();
			log.severe("showBlockingCallbackTreads for " + loginName + ": " + e.toString());
			return new String[] {"showBlockingCallbackTreads for " + loginName + " failed: " + e.toString()};
		}
	}

	public String getName() {
		return XbStompDriver.class.getName();
	}

	public String getProtocolId() {
		return XbStompInOutBridge.PROTOCOL_NAME;
	}

	public String getRawAddress() {
		// return this.socketUrl.getUrl(); // this.socketUrl.getHostname() + ":"
		// + this.socketUrl.getPort();
		try {
			return getTcpServer().getBindLocation().toString();
		} catch (Throwable e) {
			;
		}
		return null;
	}

	/**
	 * The command line key prefix
	 * 
	 * @return The configured type in xmlBlasterPlugins.xml, defaults to
	 *         "plugin/stomp"
	 */
	public String getEnvPrefix() {
		return (addressServer != null) ? addressServer.getEnvPrefix()
				: "plugin/" + getType().toLowerCase();
	}

	/**
	 * Command line usage.
	 * <p />
	 * <ul>
	 * <li><i>-plugin/STOMP/port</i> The STOMP server port [61613]</li>
	 * <li><i>-plugin/STOMP/hostname</i> Specify a hostname where the STOMP
	 * server runs. Default is the localhost.</li>
	 * </ul>
	 * <p />
	 * Enforced by interface I_Driver.
	 */
	public String usage() {
		String text = "\n";
		text += "StompDriver options:\n";
		text += "   -" + getEnvPrefix() + "port\n";
		text += "                       The server port [61613].\n";
		text += "   -" + getEnvPrefix() + "hostname\n";
		text += "                       Specify a hostname where the stomp server runs.\n";
		text += "                       Default is the localhost.\n";
		text += "   -" + getEnvPrefix() + "updateResponseTimeout\n";
		text += "                       Max wait for the method return value/exception in msec.\n";
		text += "                       Defaults to 'forever', the value to pass is milli seconds.\n";
		text += "   -" + getEnvPrefix() + "pingResponseTimeout\n";
		text += "                       Max wait for the method return value/exception in msec.\n";
		text += "                       Defaults to '60000', the value to pass is milli seconds.\n";
		text += "   "
				+ Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
		text += "\n";
		return text;
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
        this.ME = "StompDriver" + this.glob.getLogPrefixDashed() + "-" + getType();
		try {

			org.xmlBlaster.engine.ServerScope engineGlob = (org.xmlBlaster.engine.ServerScope) glob
					.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
			if (engineGlob == null)
				throw new XmlBlasterException(this.glob,
						ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
						"could not retreive the ServerNodeScope. Am I really on the server side ?");

			// For JMX instanceName may not contain ","
			String vers = ("1.0".equals(getVersion())) ? "" : getVersion();
			this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
					"StompDriver[" + getType() + vers + "]", glob
							.getContextNode());
			this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);

			this.authenticate = engineGlob.getAuthenticate();
			if (this.authenticate == null) {
				throw new XmlBlasterException(this.glob,
						ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
						"authenticate object is null");
			}
			I_XmlBlaster xmlBlasterImpl = this.authenticate.getXmlBlaster();
			if (xmlBlasterImpl == null) {
				throw new XmlBlasterException(this.glob,
						ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
						"xmlBlasterImpl object is null");
			}

			this.addressServer = new AddressServer(glob, getType(), glob
					.getId(), pluginInfo.getParameters());

			this.socketUrl = new SocketUrl(glob, this.addressServer);

			if (this.socketUrl.getPort() < 1) {
				log.info(ME + "Option protocol/socket/port set to "
						+ this.socketUrl.getPort()
						+ ", stomp server not started");
				return;
			}

			activate();
		} catch (XmlBlasterException ex) {
			throw ex;
		} catch (Throwable ex) {
			throw new XmlBlasterException(this.glob,
					ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
					"init. Could'nt initialize the driver.", ex);
		}

	}

	public void shutdown() throws XmlBlasterException {
		if (log.isLoggable(Level.FINER))
			log.finer("Entering shutdown");
		try {
			doStop();
			this.tcpServer = null;
		} catch (Throwable ex) {
			throw new XmlBlasterException(this.glob,
					ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
					"init. Could'nt shutdown the driver.", ex);
		}

		try {
			deActivate();
		} catch (Throwable e) {
			log.severe(e.toString());
		}

		this.glob.unregisterMBean(this.mbeanHandle);

		this.isShutdown = true;

		log.info("Stomp driver '" + getType()
				+ "' stopped, all resources released.");
	}

	public boolean isShutdown() {
		return this.isShutdown;
	}

	public XbStompInOutBridge[] getClients() {
		synchronized (clientSet) {
			return clientSet.toArray(new XbStompInOutBridge[clientSet.size()]);
		}
	}

	public XbStompInOutBridge findClient(String loginName) {
		if (loginName == null)
			return null;
		XbStompInOutBridge[] arr = getClients();
		for (XbStompInOutBridge bridge : arr) {
			if (loginName.equals(bridge.getLoginName()))
				return bridge;
		}
		return null;
	}

	public boolean addClient(XbStompInOutBridge client) {
		synchronized (clientSet) {
			return clientSet.add(client);
		}
	}

	public boolean removeClient(XbStompInOutBridge client) {
		synchronized (clientSet) {
			return clientSet.remove(client);
		}
	}

	public int clearClients() {
		synchronized (clientSet) {
			int num = clientSet.size();
			clientSet.clear();
			return num;
		}
	}

	//@Override
	public int countClients() {
		synchronized (clientSet) {
			return clientSet.size();
		}
	}

	//@Override
	public String[] getClientDump() {
		XbStompInOutBridge[] arr = getClients();
		Set<String>set = new TreeSet<String>();
		for (XbStompInOutBridge bridge : arr) {
			set.add(bridge.getExtendedLogId());
		}
		return set.toArray(new String[set.size()]);
	}

	// ///////// STOMP

	/**
	 * Callback StompHandlerFactory on new client connection
	 */
	//@Override
	public StompHandler createStompHandler(StompHandler outputHandler) {
		XbStompInOutBridge client = new XbStompInOutBridge(this.glob, this, outputHandler);
		addClient(client);
		connectCounter++;
		// The server listener thread
		log.info("New stomp client " + outputHandler.toString() + " arrives, current number of clients=" + countClients() + ", overall connects=" + connectCounter);
		return client;
	}

	/**
	 * Joins with the background thread until the transport is stopped
	 */
	public void join() throws IOException, URISyntaxException,
			InterruptedException {
		getTcpServer().join();
	}

	/**
	 * Stomp specific url syntax
	 * @return "tcp://localhost:61613"
	 * @throws URISyntaxException
	 */
	public URI getLocation() throws URISyntaxException {
		if (location == null) {
			// "tcp://localhost:11111";
			String uri = "tcp://" + this.socketUrl.getHostname() + ":"
					+ this.socketUrl.getPort();
			location = new URI(uri);
		}
		return location;
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

	public synchronized TcpTransportServer getTcpServer() throws IOException,
			URISyntaxException {
		if (tcpServer == null) {
			tcpServer = createTcpServer();
		}
		return tcpServer;
	}

	protected TcpTransportServer createTcpServer() throws IOException,
	URISyntaxException {
		TcpTransportServer server = new TcpTransportServer(this, getLocation(),
				getServerSocketFactory());
		server.setDaemon(true);
		return server;
	}

	// Implementation methods
	// -------------------------------------------------------------------------
	protected void doStart() throws Exception {
		getTcpServer().start();
        log.info("Started successfully " + getType() + " driver on '" + this.socketUrl.getUrl() + "'");
	}

	protected void doStop() throws Exception {
		log.info("stop tcpserver");
		getTcpServer().stop();
		tcpServer = null;
	}

	//@Override
	public boolean isActive() {
		try {
			return getTcpServer().isStarted();
		} catch (Throwable e) {
			e.printStackTrace();
			return false;
		}
	}

	//@Override
	public String getUsageUrl() {
		return "http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.stomp.html";
	}

	//@Override
	public void setUsageUrl(String url) {
	}
	
}

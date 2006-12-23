package org.xmlBlaster.protocol.http.simple;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * This servlet supports requests from a browser, it queries the topic given by
 * "gpsTopicId" configuration which needs to contain GPS coordinates (published
 * for example by a PDA).
 * 
 * <p>
 * Use xmlBlaster/demo/http/gpsmap.html to display the coordinates in a map.
 * </p>
 * 
 * @author Marcel Ruff xmlBlaster@marcelruff.info 2006
 */
public class PullServlet extends HttpServlet {
	private static final long serialVersionUID = -8094289301696678030L;

	private Properties props = new Properties();

	private I_XmlBlasterAccess xmlBlasterAccess;

	private String loginName = "pullServlet";

	private String passwd = "something";

	public void init(ServletConfig conf) throws ServletException {
		super.init(conf);
		// Add the web.xml parameters to our environment settings:
		Enumeration enumer = conf.getInitParameterNames();
		while (enumer.hasMoreElements()) {
			String name = (String) enumer.nextElement();
			if (name != null && name.length() > 0)
				props.setProperty(name, conf.getInitParameter(name));
		}
	}

	public void doGet(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		String host = req.getRemoteAddr();
		String ME = "PullServlet.doGet(" + host + "): ";
		boolean forceLoad = req.getParameter("forceLoad") != null;
		if (forceLoad)
			System.out.println(ME + "forceLoad=" + forceLoad);
		boolean newBrowser = false;
		if (req.getSession(false) == null) {
			req.getSession(true);
			newBrowser = true;
			System.out.println(ME + "new browser arrived");
		}

		// set header field first
		res.setContentType("text/plain");
		PrintWriter out = res.getWriter();

		try {
			Position pos = getCurrentPosition(req);
			if (pos == null) {
				// System.out.println(ME+"no data found ...");
				if (newBrowser) {
					out.write(getStartupUrl());
					System.out.println(ME + getStartupUrl());
				}
				return;
			}

			if (forceLoad || positionHasChanged(req, pos)) {
				String url = getUrl(pos);
				out.print(url);
				System.out.println(ME + "Send new url=" + url);
			}
			// System.out.println(ME+"Ignoring identical");

		} catch (XmlBlasterException e) {
			System.err.println("newBrowser=" + newBrowser + " forceLoad="
					+ forceLoad + ": " + e.toString());
			if (newBrowser || forceLoad)
				out.write(getStartupUrl());
		} finally {
			out.close();

		}
	}

	private Position getCurrentPosition(HttpServletRequest req)
			throws XmlBlasterException {
		I_XmlBlasterAccess xmlBlaster = getXmlBlaster(req);
		String topicId = props.getProperty("gpsTopicId", "vehicle.location");
		GetKey gk = new GetKey(xmlBlaster.getGlobal(), topicId);
		GetQos gq = new GetQos(xmlBlaster.getGlobal());
		MsgUnit[] msgs = xmlBlaster.getCached(gk, gq);
		if (msgs.length > 0) {
			// System.out.println(ME+ msgs.length + " msgs found ...");
			byte[] content = msgs[msgs.length - 1].getContent();
			return parsePosition(content);
		}
		return null;
	}

	// Only call once for each request
	private boolean positionHasChanged(HttpServletRequest req, Position pos) {
		HttpSession session = req.getSession(true);
		Position lastPos = (Position) session.getAttribute("lastPosition");
		if (lastPos != null && lastPos.equals(pos)) {
			return false;
		}
		session.setAttribute("lastPosition", pos);
		return true;
	}

	private synchronized I_XmlBlasterAccess getXmlBlaster(HttpServletRequest req)
			throws XmlBlasterException {
		// HttpSession session = req.getSession(true);
		// I_XmlBlasterAccess xmlBlaster = (I_XmlBlasterAccess) session
		// .getAttribute("xmlBlaster");
		if (this.xmlBlasterAccess == null) {
			if (!props.contains("dispatch/callback/retries"))
				props.put("dispatch/callback/retries", "-1");
			String sessionName = props.getProperty("sessionName", loginName
					+ "/1");
			String password = props.getProperty("password", passwd);
			Global glob = new Global(props);
			this.xmlBlasterAccess = glob.getXmlBlasterAccess();
			ConnectQos qos = new ConnectQos(glob, sessionName, password);
			this.xmlBlasterAccess.connect(qos, new I_Callback() {
				public String update(String cbSessionId, UpdateKey updateKey,
						byte[] content, UpdateQos updateQos) {
					System.out.println("PullServler.update(): Ignoring update "
							+ updateKey.getOid());
					return "";
				}
			});
			this.xmlBlasterAccess.createSynchronousCache(20);
			// session.setAttribute("xmlBlaster", xmlBlaster);
		}
		return this.xmlBlasterAccess;
	}

	public String getUrl(Position pos) {
		StringBuffer url = new StringBuffer();
		url.append("http://maps.google.com/maps?q=");
		url.append(pos.latitude).append(",+").append(pos.longitude);
		url.append("+(").append(pos.label).append(")&iwloc=A&hl=en");
		// String encoded = Global.encode(url.toString(), null);
		return url.toString();
	}

	public String getStartupUrl() {
		// http://maps.google.com/maps?q=047%C2%B042'20.41%22,+009%C2%B012'06.80%22+(Gran'ma%20on%20tour)&iwloc=A&hl=en
		// "047°42'20.41\"", "009°12'06.80\""
		Position pos = new Position();
		pos.label = "Gran'ma is at home";
		pos.latitude = "047%C2%B042'20.41%22";
		pos.longitude = "009%C2%B012'06.80%22";
		return getUrl(pos);
	}

	// Expecting a PDA sending me Gran'mas position
	// with simplified NMEA:
	// >Gran'mas place,047°46'55.86",N,009°10'00.95",E,0<
	public Position parsePosition(byte[] data_) {

		String data;
		try {
			data = new String(data_, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			throw new IllegalArgumentException(e.toString());
		}
		Position pos = new Position();
		String[] tokens = StringPairTokenizer.toArray(data, ",");
		if (tokens.length < 6)
			throw new IllegalArgumentException("Illegal data: " + data);
		pos.label = tokens[0];
		pos.latitude = tokens[1];
		pos.latitudeL = tokens[2];
		pos.longitude = tokens[3];
		pos.longitudeL = tokens[4];
		pos.heading = tokens[5];
		return pos;
	}

	class Position {
		String label = ""; // "Gran'ma on tour

		String latitude = "";

		String latitudeL = ""; // N S

		String longitude = "";

		String longitudeL = ""; // E W

		String heading = ""; // direction degree

		public String toString() {
			return "label=" + label + " latitude=" + latitude + " longitude="
					+ longitude;
		}

		public boolean equals(Position other) {
			if (label.equals(other.label) && latitude.equals(other.latitude)
					&& longitude.equals(other.longitude))
				return true;
			return false;
		}
	}

	public String getServletInfo() {
		return "Unprotected access to xmlBlaster";
	}

	public synchronized void destroy() {
		if (xmlBlasterAccess != null) {
			try {
				xmlBlasterAccess.disconnect(null);
			} catch (Throwable e) {
				System.out.println("Ignoring disconnect problem "
						+ e.toString());
			} finally {
				xmlBlasterAccess = null;
			}
		}
	}
}

package org.xmlBlaster.protocol.http.simple;

import java.io.IOException;
import java.io.PrintWriter;

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
 * This servlet supports requests from a browser,
 * it queries the topic "vehicle.location" which
 * needs to contain GPS coordinates (published for example
 * by a PDA).
 * 
 * <p>Use xmlBlaster/demo/http/gpsmap.html to display
 * the coordinates in a map.</p>
 * 
 * @author Marcel Ruff xmlBlaster@marcelruff.info 2006
 */
public class PullServlet extends HttpServlet {
	private static final long serialVersionUID = -8094289301696678030L;

	private I_XmlBlasterAccess xmlBlasterAccess;

	private String loginName = "pullServlet";

	private String passwd = "something";
	
    public void init(ServletConfig conf) throws ServletException {
	      super.init(conf);
    }

	public void doGet(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {

		System.out.println("Entering doGet() ...");
		try {
			I_XmlBlasterAccess xmlBlaster = getXmlBlaster(req);

			GetKey gk = new GetKey(xmlBlaster.getGlobal(), "vehicle.location");
			GetQos gq = new GetQos(xmlBlaster.getGlobal());
			MsgUnit[] msgs = xmlBlaster.getCached(gk, gq);
			String data = "";
			if (msgs.length > 0) {
				data = msgs[msgs.length - 1].getContentStr();
			}
			else {
				PrintWriter out = res.getWriter();
				out.close();
				return;
			}
			
			// Expecting a PDA sending me Gran'mas position
			// with simplified NMEA:
			// >Gran'mas place,047°46'55.86",N,009°10'00.95",E,0<
			String[] tokens = StringPairTokenizer.toArray(data, ",");
			String label = tokens[0];
			String latitude = tokens[1];
			//String latitudeL = tokens[2]; // N S
			String longitude = tokens[3];
			//String longitudeL = tokens[4]; // E W
			
			//System.out.println("label=" + label + " latitude=" + latitude + " longitude=" + longitude);
			String url = "http://maps.google.com/maps?q=";
			url += latitude + ",+" + longitude;
			url += "+(" + label + ")&iwloc=A&hl=en";
			
			HttpSession session = req.getSession(true);
			String lastUrl = (String)session.getAttribute("lastUrl");
			if (lastUrl != null && lastUrl.equals(url)) {
				PrintWriter out = res.getWriter();
				out.close();
				System.out.println("Ignoring identical");
				return;
			}
			session.setAttribute("lastUrl", url);
			
			String encoded = url;
			//String encoded = Global.encode(url, null);

			// set header field first
			res.setContentType("text/plain");

			// then get the writer and write the response data
			PrintWriter out = res.getWriter();
			out.print(encoded);
			out.close();
			System.out.println("Send new url=" + encoded);
		} catch (XmlBlasterException e) {
			System.err.println(e.toString());
			// set header field first
			res.setContentType("text/html");
			PrintWriter out = res.getWriter();
			out.close();
		}
	}

	private synchronized I_XmlBlasterAccess getXmlBlaster(HttpServletRequest req)
			throws XmlBlasterException {
		//HttpSession session = req.getSession(true);
		//I_XmlBlasterAccess xmlBlaster = (I_XmlBlasterAccess) session
		//		.getAttribute("xmlBlaster");
		if (xmlBlasterAccess == null) {
			Global glob = new Global();
			xmlBlasterAccess = glob.getXmlBlasterAccess();
			ConnectQos qos = new ConnectQos(glob, loginName+"/1", passwd);
			xmlBlasterAccess.connect(qos, new I_Callback() {
				public String update(String cbSessionId, UpdateKey updateKey,
						byte[] content, UpdateQos updateQos) {
					System.out.println("Ignoring update " + updateKey.getOid());
					return "";
				}
			});
			xmlBlasterAccess.createSynchronousCache(20);
			//session.setAttribute("xmlBlaster", xmlBlaster);
		}
		return xmlBlasterAccess;
	}

	public String getServletInfo() {
		return "Unprotected access to xmlBlaster";
	}
	
	public synchronized void destroy() {
		if (xmlBlasterAccess != null)
			xmlBlasterAccess.disconnect(null);
		xmlBlasterAccess = null;
	}
}

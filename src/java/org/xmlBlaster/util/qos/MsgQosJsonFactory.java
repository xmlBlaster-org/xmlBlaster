package org.xmlBlaster.util.qos;

import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;

public class MsgQosJsonFactory implements I_MsgQosFactory {
	private final Global glob;
	private static Logger log = Logger.getLogger(MsgQosJsonFactory.class.getName());

	/**
	 * Can be used as singleton.
	 */
	public MsgQosJsonFactory(Global glob) {
		this.glob = glob;

	}

	@Override
	public MsgQosData readObject(String xmlQos) throws XmlBlasterException {
		// parseJson
		MsgQosData msgQosData = new MsgQosData(glob, this, xmlQos, MethodName.UNKNOWN);
		return null;
	}

	@Override
	public String writeObject(MsgQosData msgQosData, String extraOffset, Properties props) {
		// toJson
		return null;
	}

	@Override
	public String getName() {
		return "MsgQosJsonFactory";
	}

}

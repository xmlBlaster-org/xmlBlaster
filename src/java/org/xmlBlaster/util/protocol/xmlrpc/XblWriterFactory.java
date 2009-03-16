package org.xmlBlaster.util.protocol.xmlrpc;

import org.apache.ws.commons.serialize.XMLWriter;
import org.apache.xmlrpc.serializer.BaseXmlWriterFactory;


/** An implementation of {@link org.apache.xmlrpc.serializer.XmlWriterFactory},
 * which creates instances of
 * {@link org.apache.ws.commons.serialize.CharSetXMLWriter}.
 */
public class XblWriterFactory extends BaseXmlWriterFactory {
   
   private boolean useCDATA;
   
   public XblWriterFactory(boolean useCDATA) {
      this.useCDATA = useCDATA;
   }
   
   protected XMLWriter newXmlWriter() {
      XblWriterImpl ret = new XblWriterImpl();
      ret.setUseCData(useCDATA);
      return ret;
	}
}

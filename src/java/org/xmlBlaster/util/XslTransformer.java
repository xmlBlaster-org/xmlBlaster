package org.xmlBlaster.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.xmlBlaster.util.def.ErrorCode;

/**
 * Holds Transformers cached.
 * 
 * @author Michele Laghi / Marcel Ruff
 */
public class XslTransformer {
   private Global glob;
   private Transformer transformer;
   
   /**
    * Constructs a transformer instance. It is stateful since it holds an own transformer, i.e. it is dedicated to a
    * single xsl (with parameters).
    * 
    * @param xslFilename The mandatory relative name of the xsl file. It is searched in the Classpath. Can not be null. 
    * @param systemId The systemId to associate to the given xsl file. You can pass null here.
    * @param uriResolver A custom uri resolver to be used to find included xsl stylesheets. If you pass null, the default
    * uriResolver will be used.
    * @param props A map containing parameters (or variables) to be passed to the stylesheet. These can be used inside
    * the stylesheet.
    * 
    * @throws XmlBlasterException If an exception occurs when creating the transformer object.
    */
   public XslTransformer(Global glob, String xslFilename, String systemId, URIResolver uriResolver, Map props) throws XmlBlasterException {
      this.glob = glob;
      try {
         FileLocator locator = new FileLocator(glob);
         URL url = locator.findFileInXmlBlasterSearchPath("dummydummy", xslFilename);
         String xslLiteral = locator.read(url);
         this.transformer = newTransformer(systemId, xslLiteral, uriResolver, props);
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_CONFIGURATION,
               "XslTransformer", "constructor.", e);
      }
      
   }

   /**
    * @param systemId
    * @param xslString
    * @param uriResolver
    * @param props Map<String, String>
    * @return
    * @throws Exception
    */
   private static Transformer newTransformer(String systemId, String xslString, URIResolver uriResolver, Map props) throws Exception {
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      if (uriResolver != null)
         transformerFactory.setURIResolver(uriResolver);
      StreamSource xslStreamSource = null;
      if(systemId != null)
          xslStreamSource = new StreamSource(new StringReader(xslString), systemId);
      else
          xslStreamSource = new StreamSource(new StringReader(xslString));

      Transformer transformer = transformerFactory.newTransformer(xslStreamSource);
      if(props != null) {
          Iterator iter = props.entrySet().iterator();
          while(iter.hasNext()) {
              Entry entry = (Entry)iter.next();
              transformer.setParameter((String)entry.getKey(), (String)entry.getValue());
          }
      }
      return transformer;
   }
       
   public byte[] doXSLTransformation(byte[] xmlLiteral) throws XmlBlasterException {
      if (this.transformer == null) {
         return xmlLiteral;
      }
      else {
         try {
            StreamSource xmlStreamSource = new StreamSource(new ByteArrayInputStream(xmlLiteral));
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            StreamResult resultStream = new StreamResult(os);
            this.transformer.transform(xmlStreamSource, resultStream);
            return os.toByteArray();
         }
         catch (TransformerException ex) {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, "XslTransformer", "An exception occured when transforming '" + xmlLiteral + "'", ex);
         }
      }
   }
   
}
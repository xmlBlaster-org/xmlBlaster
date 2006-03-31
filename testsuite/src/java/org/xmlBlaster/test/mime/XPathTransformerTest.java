package org.xmlBlaster.test.mime;

import java.io.File;

import org.custommonkey.xmlunit.XMLTestCase;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.engine.mime.xpath.XPathFilter;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * Test the MIME XPath plugin <tt>org.xmlBlaster.engine.mime.xpath.XPathFilter</tt>. 
 * @author Marcel Ruff
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/mime.plugin.access.xpath.html">The mime.plugin.access.xpath requirement</a>
 */
public class XPathTransformerTest extends XMLTestCase {

   public void testXpathQos() throws Exception {
      ServerScope scope = new ServerScope();
      Global glob = scope;
      XPathFilter filter = new XPathFilter();
      filter.initialize(scope);
      String content = "SomethingFancy";
      String queryStr = "/qos";
      String qos = "<qos/>";
      
      MsgUnit msgUnit = new MsgUnit("<key oid='Hello'/>", content, qos);
      msgUnit.getQosData().setRcvTimestamp(new Timestamp());
      SessionInfo sessionInfo = null;

      PluginInfo info = new PluginInfo(glob, null, "XPathFilter", "1.0");
      info.getParameters().put(XPathFilter.MATCH_AGAINST_QOS, ""+true);
      filter.init(glob, info);
      
      {
         Query query = new Query(glob, queryStr);
         boolean ret = filter.match(sessionInfo, msgUnit, query);
         System.out.println("Match: " + ret + "\nResult: " + msgUnit.getQos());
         assertTrue(queryStr + " should match", ret);
      }

      {
         queryStr = "/a";
         Query query = new Query(glob, queryStr);
         boolean ret = filter.match(sessionInfo, msgUnit, query);
         System.out.println("Match: " + ret + "\nResult: " + msgUnit.getQos());
         assertFalse(queryStr + " shouldn't match", ret);
      }
   }

   public void testXsltTransformation() throws Exception {
      ServerScope scope = new ServerScope();
      Global glob = scope;
      XPathFilter filter = new XPathFilter();
      filter.initialize(scope);
      String content = "<a><b/></a>";
      String xslFile = "test.xsl";
      String queryStr = "/a";
      String qos = "<qos/>";
      
      FileLocator.writeFile(xslFile,
         "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>" +
         "  <xsl:template match ='/'>" +
         "    <c/>" +
         "  </xsl:template>" +
         "</xsl:stylesheet>");
      
      try {
         MsgUnit msgUnit = new MsgUnit("<key oid='Hello'/>", content, qos);
         msgUnit.getQosData().setRcvTimestamp(new Timestamp());
         SessionInfo sessionInfo = null;
   
         PluginInfo info = new PluginInfo(glob, null, "XPathFilter", "1.0");
         info.getParameters().put(XPathFilter.XSL_CONTENT_TRANSFORMER_FILE_NAME, xslFile);
         filter.init(glob, info);
         
         Query query = new Query(glob, queryStr);
         boolean ret = filter.match(sessionInfo, msgUnit, query);
         System.out.println("Match: " + ret + "\nResult: " + msgUnit.getContentStr());
         assertTrue(ret);
         assertXMLEqual("<c/>", msgUnit.getContentStr());
      }
      finally {
         File f = new File(xslFile);
         f.delete();
      }
   }
}

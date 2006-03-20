<?xml version='1.0'?>
<!--
Name:      embedSource.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   used to embedd source into requirements
Version:   $Id$
Author:    laghi@swissinfo.org
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                xmlns:html="http://www.w3.org/1999/xhtml"
                exclude-result-prefixes="#default">

<xsl:output method="xml"/>
 
  <xsl:param name="offset" select="''"/>
   
  <xsl:template match="@*|*|text()|processing-instruction()|comment()">
    <xsl:copy>
      <xsl:apply-templates select="@*|*|text()|processing-instruction()|comment()"/>
    </xsl:copy>
  </xsl:template>

   <xsl:template match="html:a[@class = 'embed'] | a[@class = 'embed']">
      <xsl:message terminate="no">EMBED <xsl:value-of select="@href"/></xsl:message>
      <img class="embed" src="embedded.gif"></img><br/>
      <xsl:apply-templates select="document(concat($offset, @href))/html:html//html:pre"/>
   </xsl:template>
   
   <!-- this is to remove the links inside the 'pre' tag since they are wrong anyway -->      
   <xsl:template match="html:pre//html:a[@class != 'embed']">
      <xsl:apply-templates />
   </xsl:template>   
         
</xsl:stylesheet>




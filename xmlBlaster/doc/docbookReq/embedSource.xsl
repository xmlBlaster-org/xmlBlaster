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

  <xsl:template match="@*|*|text()|processing-instruction()|comment()">
    <xsl:copy>
      <xsl:apply-templates select="@*|*|text()|processing-instruction()|comment()"/>
    </xsl:copy>
  </xsl:template>

   <xsl:template match="html:a[@class='source'] | a[@class='source']">
      <img class="embed" src="embedded.gif"></img><br/>
      <xsl:apply-templates select="document(@href)/html:html//html:pre"/>
   </xsl:template>

   <!-- this is to remove the links inside the 'pre' tag since they are wrong anyway -->      
   <xsl:template match="html:pre//html:a">
      <xsl:apply-templates />
   </xsl:template>   
      
</xsl:stylesheet>




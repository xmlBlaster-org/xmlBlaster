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
 
  <!--
      If you put this stylesheet in the directory where the requirements reside, you don't need
      to pass this paramterer.
      For some (to me unknown) reason forrest takes everything relative to the forrest:context
      directory, so this has to be set to:
      
      <map:parameter name="offset" value="../src/documentation/xdocs/xmlBlaster/doc/requirements"/>
      
   -->
  <xsl:param name="offset" select="''"/>
   
  <xsl:template match="@*|*|text()|processing-instruction()|comment()">
    <xsl:copy>
      <xsl:apply-templates select="@*|*|text()|processing-instruction()|comment()"/>
    </xsl:copy>
  </xsl:template>

   <xsl:template match="html:a[@class='source'] | a[@class='source']">
      <img class="embed" src="embedded.gif"></img><br/>
      <xsl:apply-templates select="document(concat($offset, @href))/html:html//html:pre"/>
   </xsl:template>

   <!-- this is to remove the links inside the 'pre' tag since they are wrong anyway -->      
   <xsl:template match="html:pre//html:a">
      <xsl:apply-templates />
   </xsl:template>   
      
</xsl:stylesheet>




<?xml version='1.0'?>
<!--
Name:      createMenu.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generating a part of the site.xml to build the navigation bar
Version:   $Id$
Author:    laghi@swissinfo.org
-->


<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                xmlns:html="http://www.w3.org/TR/xhtml1/transitional"
                exclude-result-prefixes="#default">


<xsl:output method="xml"/>

    <!-- 
       article/title/text() = requirement/@id
       article/articleinfo/@userlevel = requirement/@status
      -->

   <!-- Create the HTML output for one requirement -->


<!-- loop over all requirement files -->

<xsl:template match="/">
   <xsl:call-template name="rec"/>
</xsl:template>

<xsl:template name="rec">
   <req>
      
      <xsl:variable name="req" select="html:html/html:body/html:table/html:tr/html:td[@class='reqId' and position() = 1]"/>
      <xsl:message terminate="no">REQ <xsl:value-of select="$req"/></xsl:message>
      
      <xsl:choose>
         <xsl:when test="contains($req, '.')">
            <xsl:variable name="prefix" select="substring-before($req, '.')"/>
            <xsl:message terminate="no">PREFIX <xsl:value-of select="$prefix"/></xsl:message>
            <xsl:message terminate="no">COUNT <xsl:value-of select="count(html:html/html:table/html:tr/html:td[@class='reqId' and starts-with(., concat($prefix, '.'))])"/></xsl:message>
            
         </xsl:when>
         <xsl:otherwise>
            <xsl:message terminate="no">SINGLE NAME <xsl:value-of select="$req"/></xsl:message>
            <xsl:message terminate="no">COUNT <xsl:value-of select="count(html:html/html:table/html:tr/html:td[@class='reqId'])"/></xsl:message>
         </xsl:otherwise>
      </xsl:choose>
   </req>
</xsl:template>


<!-- Error handling -->
   <xsl:template match="*">
  <center><p class="xmlerror"><blink>XSL parsing error</blink><br />
  Can't handle your supplied XML file</p>
  <p><xsl:value-of select="name()" /></p></center>
  <xsl:apply-templates />
</xsl:template>

</xsl:stylesheet>




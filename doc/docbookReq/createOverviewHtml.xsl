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
                xmlns="http://www.w3.org/TR/xhtml1/transitional"
                exclude-result-prefixes="#default">


<xsl:output method="xml"/>

    <!-- 
       article/title/text() = requirement/@id
       article/articleinfo/@userlevel = requirement/@status
      -->

   <!-- Create the HTML output for one requirement -->


<!-- loop over all requirement files -->
<xsl:template match="/">
   <html>
   <head>
      <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
      <meta http-equiv="Pragma" content="no-cache" />
      <meta http-equiv="Expires" content="Tue, 31 Dec 1997 23:59:59 GMT" />
      <link REL="stylesheet" type="text/css" href="../howto/xmlBlaster.css" />
      <title>xmlBlaster - Requirements</title>
   </head>

   <body>

   <p class="sideend">
       Last updated $Date: 2002-12-18 15:42:18 +0100 (Wed, 18 Dec 2002) $ $Author: ruff $
   </p>
   <p class="sitetitel">XmlBlaster Programmers Reference Overview</p>

   IMPORTANT NOTE: Many features are not yet documented here (work in progress).<br />
   The examples are in Java, but similar coding is used for other languages like C++ and Perl

   <p /><br />
   <table cellpadding="2" cellspacing="4">
      <thead>
         <tr>
         <th><b>Requirement - ID</b></th>
         <th>Status</th>
         <th>Info</th>
         <th>Topic</th>
         </tr>
      </thead>

      <xsl:for-each select="/files/url">
         <xsl:sort select="document(.)/article/title/text()" order="ascending"/>
         <xsl:sort select="document(.)/article/articleinfo/@userlevel" order="ascending"/>
         <xsl:call-template name="article"/>
      </xsl:for-each>
      
   </table>
   <p class="sideend">
   This page is generated from the requirement XML files in the xmlBlaster/doc/requirements directory of the distribution
   </p>
   </body>
   </html>
</xsl:template>

<xsl:template match="url">
</xsl:template>   
   

   <!-- Create the HTML output for one requirement -->
<xsl:template name="article">
   <tr>
<!--
       title/text() = requirement/@id
       articleinfo/@userlevel = requirement/@status
-->
      <xsl:message terminate="no">now processing '<xsl:value-of select="."/>'</xsl:message>
      <td class="reqId"><xsl:value-of select="document(.)/article/title/text()"/></td>
      <td>
         <xsl:attribute name="class"><xsl:value-of select="document(.)/article/articleinfo/@userlevel"/></xsl:attribute>
         <xsl:value-of select="document(.)/article/articleinfo/@userlevel"/>
      </td>
      <td>
         <a><xsl:attribute name="href"><xsl:value-of select="document(.)/article/title/text()"/>.html</xsl:attribute>&gt;&gt;</a>
      </td>
      <td class="topic"><xsl:value-of select="document(.)/article/subtitle/text()"/></td>
   </tr>

</xsl:template>


<!-- Error handling -->
   <xsl:template match="*">
  <center><p class="xmlerror"><blink>XSL parsing error</blink><br />
  Can't handle your supplied XML file</p>
  <p><xsl:value-of select="name()" /></p></center>
  <xsl:apply-templates />
</xsl:template>

</xsl:stylesheet>




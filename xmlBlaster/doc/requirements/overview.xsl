<?xml version='1.0'?>
<!--
Name:      html.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generating a html table with all requirements, to be used as a 'reference handbook'
Version:   $Id: overview.xsl,v 1.1 2000/03/24 23:45:44 ruff Exp $
Author:    ruff@swand.lake.de
-->


<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                xmlns="http://www.w3.org/TR/xhtml1/transitional"
                exclude-result-prefixes="#default">


<xsl:output method="html"/>


<!-- loop over all requirement files -->
<xsl:template match="/">
   <html>
   <head>
      <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
      <link REL="stylesheet" type="text/css" href="../xmlBlaster.css" />
      <title>xmlBlaster - Requirements</title>
   </head>

   <body>

   <p class="sideend">
       Last updated $Date: 2000/03/24 23:45:44 $ $Author: ruff $
   </p>
   <p class="sitetitel">XmlBlaster Programmers Reference Overview</p>

   IMPORTANT NOTE: Many features are not yet documented here (work in progress)!<br />
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
         <xsl:apply-templates select="document(.)/requirement"/>
        <xsl:sort select="document(.)/requirement/@status" order="ascending"/>
        <xsl:sort select="document(.)/requirement/@id" order="ascending"/>
      </xsl:for-each>
   </table>
   <p class="sideend">
   This page is generated from the requirement XML files in the xmlBlaster/doc/requirements directory of the distribution
   </p>
   </body>
   </html>
</xsl:template>


<!-- Create the HTML output for one requirement -->
<xsl:template match="requirement">
   <tr>
      <td class="reqId"><xsl:value-of select="@id"/></td>
      <td>
         <xsl:attribute name="class"><xsl:value-of select="@status"/></xsl:attribute>
         <xsl:value-of select="@status"/>
      </td>
      <td>
         <a><xsl:attribute name="href"><xsl:value-of select="@id"/>.html</xsl:attribute>&gt;&gt;</a>
      </td>
      <td class="topic"><xsl:value-of select="topic"/></td>
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





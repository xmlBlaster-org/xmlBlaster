<?xml version='1.0'?>
<!--
Name:      html.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generating a html table with all requirements, to be used as a 'reference handbook'
Version:   $Id: html.xsl,v 1.7 2000/03/19 00:27:26 ruff Exp $
Author:    ruff@swand.lake.de
-->


<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                xmlns="http://www.w3.org/TR/xhtml1/transitional"
                exclude-result-prefixes="#default">


<xsl:output method="html"/>

<xsl:template match="/">
   <html>
   <head>
      <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
      <link REL="stylesheet" type="text/css" href="../xmlBlaster.css" />
      <title>xmlBlaster - Requirements</title>
   </head>

   <body>

   <p class="sideend">
       Last updated $Date: 2000/03/19 00:27:26 $ $Author: ruff $
   </p>
   <p class="sitetitel">XmlBlaster Requirements Reference</p>

   <p /><br />
   <table cellpadding="2" cellspacing="4">
      <thead>
         <tr>
         <th><b>ID</b></th>
         <th>Status</th>
         <th>Topic / Description / Example</th>
         </tr>
      </thead>
      <xsl:for-each select="/files/url">
         <xsl:apply-templates select="document(.)/requirement"/>
      </xsl:for-each>
   </table>
   </body>
   </html>
</xsl:template>

<xsl:template match="requirement">
   <tr>
      <td class="reqId"><xsl:value-of select="@id"/></td>
      <td>
         <xsl:attribute name="class"><xsl:value-of select="@status"/></xsl:attribute>
         <xsl:value-of select="@status"/>
      </td>
      <td class="topic"><xsl:value-of select="topic"/></td>
   </tr>

   <tr>
      <td></td>
      <td></td>
      <td class="description" colspan="1"><xsl:value-of select="description"/></td>
   </tr>

   <xsl:if test="example">
      <td></td>
      <td></td>
      <td class="example" colspan="1"><xsl:value-of select="example" disable-output-escaping="yes"/></td>
   </xsl:if>
</xsl:template>

<!-- xsl:template match="code">
   <p class="code">
      <xsl:value-of select="."/>
   </p>
</xsl:template -->

</xsl:stylesheet>





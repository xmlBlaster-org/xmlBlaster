<?xml version='1.0'?>
<!--
Name:      html.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generating a html table with all requirements, to be used as a 'reference handbook'
           This is the old version, displaying everything on one page
           Use overview.xsl instead
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
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
      <link REL="stylesheet" type="text/css" href="../howto/xmlBlaster.css" />
      <title>xmlBlaster - Requirements</title>
   </head>

   <body>

   <p class="sideend">
       Last updated $Date$ $Author$
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
         <th>Topic / Description / Example</th>
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
      <td class="description" colspan="1">
         <xsl:value-of select="description"/>
         <!-- xsl:value-of select="description" disable-output-escaping="yes"/ -->
      </td>
   </tr>

   <xsl:if test="example">
      <td></td>
      <td></td>
      <td class="example" colspan="1">
         <pre><xsl:value-of select="example"/></pre>
      </td>
   </xsl:if>
</xsl:template>


<xsl:template match="*">
  <center><p class="xmlerror"><blink>XSL parsing error</blink><br />
  Can't handle your supplied XML file</p>
  <p><xsl:value-of select="name()" /></p></center>
  <xsl:apply-templates />
</xsl:template>


</xsl:stylesheet>





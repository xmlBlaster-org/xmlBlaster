<?xml version='1.0'?>
<!--
Name:      detail.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generating a detailed html view for one requirement
See:       xmlBlaster/doc/requirements/requirement.dtd
Version:   $Id: detail.xsl,v 1.3 2000/05/02 09:39:23 ruff Exp $
Author:    ruff@swand.lake.de
-->


<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                xmlns="http://www.w3.org/TR/xhtml1/transitional"
                exclude-result-prefixes="#default">


<xsl:output method="html"/>


<!-- Create the HTML output for one requirement -->
<xsl:template match="requirement">
   <html>
   <head>
      <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
      <link REL="stylesheet" type="text/css" href="../xmlBlaster.css" />
      <title>xmlBlaster - Requirement <xsl:value-of select="@id"/></title>
   </head>

   <body>

   <!-- p class="sideend"> Last updated $Date: 2000/05/02 09:39:23 $ $Author: ruff $ </p -->

   <p class="sitetitel">REQUIREMENT</p>
   <p class="sitetitel"><xsl:value-of select="@id"/></p>

   <p /><br />
   <table cellpadding="2" cellspacing="4">
      <tr>
         <td class="reqId">Type</td>
         <td><xsl:value-of select="@type"/></td>
      </tr>
      <tr>
         <td class="reqId">Priority</td>
         <td><xsl:value-of select="@prio"/></td>
      </tr>
      <tr>
         <td class="reqId">Status</td>
         <td>
            <xsl:attribute name="class"><xsl:value-of select="@status"/></xsl:attribute>
            <xsl:value-of select="@status"/>
         </td>
      </tr>
   <p /><br />
   </table>

   <table cellpadding="2" cellspacing="4">
      <tr>
         <td class="reqId">Topic</td>
         <td class="topic"><xsl:value-of select="topic"/></td>
      </tr>
      <tr>
         <td class="reqId">Description</td>
         <td class="description"><xsl:value-of select="description" disable-output-escaping="yes" /></td>
      </tr>
      <tr>
         <td class="reqId">Example</td>
         <td class="example"><pre><xsl:value-of select="example"/></pre></td>
      </tr>
      <xsl:for-each select="see">
         <tr>
            <xsl:if test="@type='API'">
               <td class="reqId">See Javadoc API</td>
               <td>
               <a>
                  <xsl:attribute name="href">../api/<xsl:value-of select="translate(.,'.','/')"/>.html</xsl:attribute>
                  <xsl:value-of select="."/>
               </a>
               </td>
            </xsl:if>

            <xsl:if test="@type='REQ'">
               <td class="reqId">See Requirement</td>
               <td>
               <a>
                  <xsl:attribute name="href"><xsl:value-of select="."/>.html</xsl:attribute>
                  <xsl:value-of select="."/>
               </a>
               </td>
            </xsl:if>

            <xsl:if test="@type='OTHER'">
               <td class="reqId">See</td>
               <td><xsl:value-of select="."/></td>
            </xsl:if>
         </tr>
      </xsl:for-each>

      <xsl:for-each select="testcase">
         <xsl:if test="@status='CLOSED'">
            <tr>
               <xsl:if test="test/@tool='SUITE'">
                  <td class="reqId">See Testcase Code</td>
                    <td>
                  <a>
                     <xsl:attribute name="href">../../<xsl:value-of select="translate(test,'.','/')"/>.html</xsl:attribute>
                     <xsl:value-of select="test"/>
                  </a>
                  </td>
               </xsl:if>

               <xsl:if test="test/@tool!='SUITE'">
                  <td class="reqId">Testcase</td>
                  <td><xsl:value-of select="test"/></td>
               </xsl:if>
            </tr>
         </xsl:if>
      </xsl:for-each>

   </table>

   <p class="sideend">
   This page is generated from the requirement XML file xmlBlaster/doc/requirements/<xsl:value-of select="@id"/>.xml
   </p>
   <p class="sideend">
      <a href="requirement.html" target="Content">Back to overview</a><br />
   </p>
   </body>
   </html>
</xsl:template>


<!-- Error handling -->
<xsl:template match="*">
  <center><p class="xmlerror"><blink>XSL parsing error</blink><br />
  Can't handle your supplied XML file</p>
  <p><xsl:value-of select="name()" /></p></center>
  <xsl:apply-templates />
</xsl:template>


</xsl:stylesheet>





<?xml version='1.0' encoding='UTF-8' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
<xsl:output method='xml' version='1.0' encoding='UTF-8' indent='yes'/>

<!--
Linux invoke:  xsltproc -o generic.xml transformToKeyValue.xsl some.xml

Transforms a specific XML file to a key/value looking xml file
-->

<xsl:template match ='/'>
    <generic>
      <xsl:apply-templates/>
    </generic>
</xsl:template>

 <xsl:template match='@*'>
   <xsl:element name='entity'>
      <xsl:attribute name='name'><xsl:value-of select='name()'/></xsl:attribute>
      <xsl:value-of select='.'/>
   </xsl:element>
</xsl:template>


<xsl:template match='*'>
   <xsl:element name='entity'>
      <xsl:attribute name='name'><xsl:value-of select='name()'/></xsl:attribute>
      <xsl:apply-templates select='@*'/>
      <xsl:apply-templates/>
   </xsl:element>
</xsl:template>

</xsl:stylesheet>




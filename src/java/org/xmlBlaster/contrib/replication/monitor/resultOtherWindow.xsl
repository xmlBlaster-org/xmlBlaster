<?xml version='1.0' encoding='iso-8859-1' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
<xsl:output method='text'/>

<xsl:template match ='/'>
<xsl:value-of select='/MBeanOperation/Operation/attribute::return'/>
</xsl:template>

</xsl:stylesheet>




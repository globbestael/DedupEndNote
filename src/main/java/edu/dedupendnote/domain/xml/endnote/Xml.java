//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2024.02.24 at 01:53:14 PM CET 
//


package edu.dedupendnote.domain.xml.endnote;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "records"
})
@XmlRootElement(name = "xml")
public class Xml {

    @XmlElement(required = true)
    protected EndnoteXmlRecords records;

    /**
     * Gets the value of the records property.
     * 
     * @return
     *     possible object is
     *     {@link EndnoteXmlRecords }
     *     
     */
    public EndnoteXmlRecords getRecords() {
        return records;
    }

    /**
     * Sets the value of the records property.
     * 
     * @param value
     *     allowed object is
     *     {@link EndnoteXmlRecords }
     *     
     */
    public void setRecords(EndnoteXmlRecords value) {
        this.records = value;
    }

}

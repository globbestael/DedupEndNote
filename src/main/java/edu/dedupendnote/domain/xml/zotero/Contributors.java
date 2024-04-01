//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2024.04.01 at 03:17:30 PM CEST 
//


package edu.dedupendnote.domain.xml.zotero;

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
    "authors",
    "secondaryAuthors",
    "tertiaryAuthors",
    "subsidiaryAuthors",
    "translatedAuthors"
})
@XmlRootElement(name = "contributors")
public class Contributors {

    protected Authors authors;
    @XmlElement(name = "secondary-authors")
    protected SecondaryAuthors secondaryAuthors;
    @XmlElement(name = "tertiary-authors")
    protected TertiaryAuthors tertiaryAuthors;
    @XmlElement(name = "subsidiary-authors")
    protected SubsidiaryAuthors subsidiaryAuthors;
    @XmlElement(name = "translated-authors")
    protected TranslatedAuthors translatedAuthors;

    /**
     * Gets the value of the authors property.
     * 
     * @return
     *     possible object is
     *     {@link Authors }
     *     
     */
    public Authors getAuthors() {
        return authors;
    }

    /**
     * Sets the value of the authors property.
     * 
     * @param value
     *     allowed object is
     *     {@link Authors }
     *     
     */
    public void setAuthors(Authors value) {
        this.authors = value;
    }

    /**
     * Gets the value of the secondaryAuthors property.
     * 
     * @return
     *     possible object is
     *     {@link SecondaryAuthors }
     *     
     */
    public SecondaryAuthors getSecondaryAuthors() {
        return secondaryAuthors;
    }

    /**
     * Sets the value of the secondaryAuthors property.
     * 
     * @param value
     *     allowed object is
     *     {@link SecondaryAuthors }
     *     
     */
    public void setSecondaryAuthors(SecondaryAuthors value) {
        this.secondaryAuthors = value;
    }

    /**
     * Gets the value of the tertiaryAuthors property.
     * 
     * @return
     *     possible object is
     *     {@link TertiaryAuthors }
     *     
     */
    public TertiaryAuthors getTertiaryAuthors() {
        return tertiaryAuthors;
    }

    /**
     * Sets the value of the tertiaryAuthors property.
     * 
     * @param value
     *     allowed object is
     *     {@link TertiaryAuthors }
     *     
     */
    public void setTertiaryAuthors(TertiaryAuthors value) {
        this.tertiaryAuthors = value;
    }

    /**
     * Gets the value of the subsidiaryAuthors property.
     * 
     * @return
     *     possible object is
     *     {@link SubsidiaryAuthors }
     *     
     */
    public SubsidiaryAuthors getSubsidiaryAuthors() {
        return subsidiaryAuthors;
    }

    /**
     * Sets the value of the subsidiaryAuthors property.
     * 
     * @param value
     *     allowed object is
     *     {@link SubsidiaryAuthors }
     *     
     */
    public void setSubsidiaryAuthors(SubsidiaryAuthors value) {
        this.subsidiaryAuthors = value;
    }

    /**
     * Gets the value of the translatedAuthors property.
     * 
     * @return
     *     possible object is
     *     {@link TranslatedAuthors }
     *     
     */
    public TranslatedAuthors getTranslatedAuthors() {
        return translatedAuthors;
    }

    /**
     * Sets the value of the translatedAuthors property.
     * 
     * @param value
     *     allowed object is
     *     {@link TranslatedAuthors }
     *     
     */
    public void setTranslatedAuthors(TranslatedAuthors value) {
        this.translatedAuthors = value;
    }

}
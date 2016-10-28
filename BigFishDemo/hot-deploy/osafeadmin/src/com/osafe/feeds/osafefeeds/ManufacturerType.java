//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.09.23 at 12:22:17 PM IST 
//


package com.osafe.feeds.osafefeeds;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ManufacturerType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ManufacturerType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="ManufacturerId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="ManufacturerName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="Description" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="LongDescription" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="ManufacturerImage" type="{}ManufacturerImageType" minOccurs="0"/>
 *         &lt;element name="Address" type="{}ManufacturerAddressType" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ManufacturerType", propOrder = {

})
public class ManufacturerType {

    @XmlElement(name = "ManufacturerId", required = true, defaultValue = "")
    protected String manufacturerId;
    @XmlElement(name = "ManufacturerName", required = true, defaultValue = "")
    protected String manufacturerName;
    @XmlElement(name = "Description", defaultValue = "")
    protected String description;
    @XmlElement(name = "LongDescription", defaultValue = "")
    protected String longDescription;
    @XmlElement(name = "ManufacturerImage")
    protected ManufacturerImageType manufacturerImage;
    @XmlElement(name = "Address")
    protected ManufacturerAddressType address;

    /**
     * Gets the value of the manufacturerId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getManufacturerId() {
        return manufacturerId;
    }

    /**
     * Sets the value of the manufacturerId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setManufacturerId(String value) {
        this.manufacturerId = value;
    }

    /**
     * Gets the value of the manufacturerName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getManufacturerName() {
        return manufacturerName;
    }

    /**
     * Sets the value of the manufacturerName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setManufacturerName(String value) {
        this.manufacturerName = value;
    }

    /**
     * Gets the value of the description property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the value of the description property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDescription(String value) {
        this.description = value;
    }

    /**
     * Gets the value of the longDescription property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLongDescription() {
        return longDescription;
    }

    /**
     * Sets the value of the longDescription property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLongDescription(String value) {
        this.longDescription = value;
    }

    /**
     * Gets the value of the manufacturerImage property.
     * 
     * @return
     *     possible object is
     *     {@link ManufacturerImageType }
     *     
     */
    public ManufacturerImageType getManufacturerImage() {
        return manufacturerImage;
    }

    /**
     * Sets the value of the manufacturerImage property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManufacturerImageType }
     *     
     */
    public void setManufacturerImage(ManufacturerImageType value) {
        this.manufacturerImage = value;
    }

    /**
     * Gets the value of the address property.
     * 
     * @return
     *     possible object is
     *     {@link ManufacturerAddressType }
     *     
     */
    public ManufacturerAddressType getAddress() {
        return address;
    }

    /**
     * Sets the value of the address property.
     * 
     * @param value
     *     allowed object is
     *     {@link ManufacturerAddressType }
     *     
     */
    public void setAddress(ManufacturerAddressType value) {
        this.address = value;
    }

}
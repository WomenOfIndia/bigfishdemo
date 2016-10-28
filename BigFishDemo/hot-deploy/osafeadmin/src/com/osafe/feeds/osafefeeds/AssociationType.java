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
 * <p>Java class for AssociationType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AssociationType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="MasterProductId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="MasterProductIdTo" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="ProductAssocType" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="FromDate" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="ThruDate" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AssociationType", propOrder = {

})
public class AssociationType {

    @XmlElement(name = "MasterProductId", required = true, defaultValue = "")
    protected String masterProductId;
    @XmlElement(name = "MasterProductIdTo", required = true, defaultValue = "")
    protected String masterProductIdTo;
    @XmlElement(name = "ProductAssocType", defaultValue = "")
    protected String productAssocType;
    @XmlElement(name = "FromDate", required = true, defaultValue = "")
    protected String fromDate;
    @XmlElement(name = "ThruDate", defaultValue = "")
    protected String thruDate;

    /**
     * Gets the value of the masterProductId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMasterProductId() {
        return masterProductId;
    }

    /**
     * Sets the value of the masterProductId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMasterProductId(String value) {
        this.masterProductId = value;
    }

    /**
     * Gets the value of the masterProductIdTo property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMasterProductIdTo() {
        return masterProductIdTo;
    }

    /**
     * Sets the value of the masterProductIdTo property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMasterProductIdTo(String value) {
        this.masterProductIdTo = value;
    }

    /**
     * Gets the value of the productAssocType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProductAssocType() {
        return productAssocType;
    }

    /**
     * Sets the value of the productAssocType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProductAssocType(String value) {
        this.productAssocType = value;
    }

    /**
     * Gets the value of the fromDate property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFromDate() {
        return fromDate;
    }

    /**
     * Sets the value of the fromDate property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFromDate(String value) {
        this.fromDate = value;
    }

    /**
     * Gets the value of the thruDate property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getThruDate() {
        return thruDate;
    }

    /**
     * Sets the value of the thruDate property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setThruDate(String value) {
        this.thruDate = value;
    }

}

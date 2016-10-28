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
 * <p>Java class for FacetCatGroupType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="FacetCatGroupType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;all>
 *         &lt;element name="ProductCategoryId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="SequenceNum" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="FromDate" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="ThruDate" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="MinDisplay" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="MaxDisplay" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="Tooltip" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="FacetGroup" type="{}FacetGroupType" minOccurs="0"/>
 *       &lt;/all>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "FacetCatGroupType", propOrder = {

})
public class FacetCatGroupType {

    @XmlElement(name = "ProductCategoryId", required = true, defaultValue = "")
    protected String productCategoryId;
    @XmlElement(name = "SequenceNum", defaultValue = "")
    protected String sequenceNum;
    @XmlElement(name = "FromDate", defaultValue = "")
    protected String fromDate;
    @XmlElement(name = "ThruDate", defaultValue = "")
    protected String thruDate;
    @XmlElement(name = "MinDisplay", defaultValue = "")
    protected String minDisplay;
    @XmlElement(name = "MaxDisplay", defaultValue = "")
    protected String maxDisplay;
    @XmlElement(name = "Tooltip", defaultValue = "")
    protected String tooltip;
    @XmlElement(name = "FacetGroup")
    protected FacetGroupType facetGroup;

    /**
     * Gets the value of the productCategoryId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getProductCategoryId() {
        return productCategoryId;
    }

    /**
     * Sets the value of the productCategoryId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setProductCategoryId(String value) {
        this.productCategoryId = value;
    }

    /**
     * Gets the value of the sequenceNum property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSequenceNum() {
        return sequenceNum;
    }

    /**
     * Sets the value of the sequenceNum property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSequenceNum(String value) {
        this.sequenceNum = value;
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

    /**
     * Gets the value of the minDisplay property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMinDisplay() {
        return minDisplay;
    }

    /**
     * Sets the value of the minDisplay property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMinDisplay(String value) {
        this.minDisplay = value;
    }

    /**
     * Gets the value of the maxDisplay property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMaxDisplay() {
        return maxDisplay;
    }

    /**
     * Sets the value of the maxDisplay property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMaxDisplay(String value) {
        this.maxDisplay = value;
    }

    /**
     * Gets the value of the tooltip property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTooltip() {
        return tooltip;
    }

    /**
     * Sets the value of the tooltip property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTooltip(String value) {
        this.tooltip = value;
    }

    /**
     * Gets the value of the facetGroup property.
     * 
     * @return
     *     possible object is
     *     {@link FacetGroupType }
     *     
     */
    public FacetGroupType getFacetGroup() {
        return facetGroup;
    }

    /**
     * Sets the value of the facetGroup property.
     * 
     * @param value
     *     allowed object is
     *     {@link FacetGroupType }
     *     
     */
    public void setFacetGroup(FacetGroupType value) {
        this.facetGroup = value;
    }

}

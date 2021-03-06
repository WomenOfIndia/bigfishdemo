<#if resultScreenList?has_content>  
  <#assign selectedKey = ""/>
  <#if mode=="edit">
    <#assign selectedKey = parameters.key!""/>
  </#if>
  <input type="hidden" name="key" value="${selectedKey!""}"/>
  <input type="hidden" name="screenType" value="${parameters.screenType!""}"/>
  <input type="hidden" name="mode" value="${mode!""}"/>
  <#list resultScreenList as screenList>
    <#assign key = screenList.key!"" />
    <#assign description = screenList.description!"" />
    <#assign screen = screenList.screen!"" />
    <#assign style = screenList.style!"" />
    <#assign mandatory = screenList.mandatory!"" />
    <#assign value = screenList.value!"" />
    <#assign group = screenList.group!"" />
    <#if mode=="add">
      <#assign screen = parameters.screenType!""/>
    </#if>
    
    <#assign hasNext = screenList_has_next/>  
    <#-- if mode equals add and we reached the last List Item (which is the empty map we added in groovy), then display this-->
    <#if (selectedKey == key) || (mode == "add" && !hasNext)>
      <#if divSeqItemEntry?has_content>
	    <#assign key = divSeqItemEntry.key!parameters.addKey!"" />
	    <#assign description = divSeqItemEntry.description!"" />
	    <#assign screen = divSeqItemEntry.screen!"" />
	    <#assign style = divSeqItemEntry.style!"" />
	    <#assign addMandatory = divSeqItemEntry.mandatory!"" />
	    <#assign value = divSeqItemEntry.value!"" />
	    <#assign group = divSeqItemEntry.group!"" />
	  </#if>
      <div class="infoRow">
        <div class="infoRow">
        <#-- ==== Spot Name === -->
            <div class="infoEntry">
                <div class="infoCaption"><label>${uiLabelMap.KeyCaption}</label></div>
                <#-- ===== Spot Name ==== -->
                <div class="infoValue">
                  <#if mode=="edit">
                    <input type="hidden" name="key_${screenList_index}" value="${key!}"/>${key!""}
                  <#elseif mode=="add">
                    <input type="text" <#if mode=="add">id="divSequenceKey"</#if>name="key_${screenList_index}" value="${parameters.get("key_${screenList_index}")!key!""}"/>
                  </#if>
               </div>
            </div>
        </div>
        <#-- ==== screen === -->
            <div class="infoEntry">
                <div class="infoCaption"><label>${uiLabelMap.ScreenCaption}</label></div>
                <div class="infoValue">
                    <input type="hidden" name="screen_${screenList_index}" value="${screen!}"/>${screen!""}
                    <input type="hidden" name="screen" value="${screen!""}"/>
               </div>
            </div>
        </div>
       
        <div class="infoRow">
        <#-- ==== div === -->
            <div class="infoEntry">
                <div class="infoCaption"><label>${uiLabelMap.StyleCaption}</label></div>
                <div class="infoValue">
                    <input type="text" name="style_${screenList_index}" value="${parameters.get("style_${screenList_index}")!style!}"/>
               </div>
            </div>
        </div>
        <div class="infoRow">
        <#-- ==== Description === -->
            <div class="infoEntry long">
                <div class="infoCaption"><label>${uiLabelMap.DescriptionCaption}</label></div>
                <#-- ===== Spot Name ==== -->
               <div class="infoValue">
                  <textarea class="smallArea" name="description_${screenList_index}" cols="50" rows="1">${parameters.get("description_${screenList_index}")!description!}</textarea>
               </div>
            </div>
        </div>
        <div class="infoRow">
            <div class="infoEntry">
              <div class="infoCaption"><label>${uiLabelMap.MandatoryCaption}</label></div>
              <#if mode?has_content && mode == "add">
                <#assign mandatory = parameters.mandatory!"NA"/>
                <#if !mandatory?has_content>
                  <#assign mandatory = addMandatory/>
                </#if>
                <div class="entry checkbox short">
                      <input type="radio" name="mandatory" value="NA" <#if mandatory == "NA">checked="checked"</#if>/>${uiLabelMap.NALabel}
                      <input type="radio" name="mandatory" value="SYS_YES" <#if mandatory == "SYS_YES">checked="checked"</#if>/>${uiLabelMap.SysYesLabel}
                      <input type="radio" name="mandatory" value="SYS_NO" <#if mandatory == "SYS_NO">checked="checked"</#if>/>${uiLabelMap.SysNoLabel}
                      <input type="radio" name="mandatory" value="YES" <#if mandatory == "YES">checked="checked"</#if>/>${uiLabelMap.YesLabel}
                      <input type="radio" name="mandatory" value="NO" <#if mandatory == "NO">checked="checked"</#if>/>${uiLabelMap.NoLabel}
                </div>
              <#elseif mode?has_content && mode == "edit">
                <#if screenList.mandatory?has_content && (screenList.mandatory == "YES" || screenList.mandatory == "NO")>
                  <div class="entry checkbox short">
                    <#assign mandatory = request.getParameter("mandatory_${screenList_index}")!screenList.mandatory!''/>
                    <input type="radio" name="mandatory_${screenList_index}" value="YES" <#if mandatory == "YES">checked="checked"</#if>/>${uiLabelMap.YesLabel}
                    <input type="radio" name="mandatory_${screenList_index}" value="NO" <#if mandatory == "NO">checked="checked"</#if>/>${uiLabelMap.NoLabel}
                  </div>
               <#else>
                 <div class="infoValue">
                   <input type="hidden" name="mandatory_${screenList_index}" value="${mandatory!"NA"}">${mandatory!"NA"}
                 </div>
               </#if>
             </#if>
             <div class="infoIcon">
               <a class="helper" href="javascript:void(0);" onMouseover="showTooltip(event,'${uiLabelMap.MandatoryHelpInfo}');" onMouseout="hideTooltip()"><span class="helperIcon"></span></a>
             </div>
            </div>
        </div>
       <#-- ====== Value ==== -->
        <div class="infoRow">
            <div class="infoEntry">
                <div class="infoCaption"><label>${uiLabelMap.SeqIdCaption}</label></div>
                <div class="infoValue">
                    <input type="text" name="value_${screenList_index}" value="${parameters.get("value_${screenList_index}")!parameters.newValue!value!}"/>
                </div>
               <div class="infoIcon">
                  <a class="helper" href="javascript:void(0);" onMouseover="showTooltip(event,'${uiLabelMap.SeqIdHelpInfo}');" onMouseout="hideTooltip()"><span class="helperIcon"></span></a>
               </div>
            </div>
        </div>
          <#-- ====== Group ==== -->
          <div class="infoRow">
            <div class="infoEntry">
                <div class="infoCaption"><label>${uiLabelMap.GroupCaption}</label></div>
                <div class="infoValue">
                    <input type="text" name="group_${screenList_index}" value="${parameters.get("group_${screenList_index}")!group!}"/>
                </div>
               <div class="infoIcon">
                  <a class="helper" href="javascript:void(0);" onMouseover="showTooltip(event,'${uiLabelMap.GroupHelperInfo}');" onMouseout="hideTooltip()"><span class="helperIcon"></span></a>
               </div>
            </div>
          </div>
    <#else>
      <input type="hidden" name="key_${screenList_index}" value="${key!}"/>
      <input type="hidden" name="screen_${screenList_index}" value="${screen!}"/>
      <input type="hidden" name="style_${screenList_index}" value="${style!}"/>
      <input type="hidden" name="description_${screenList_index}" value="${description!}"/>
      <input type="hidden" name="mandatory_${screenList_index}" value="${mandatory!"NA"}">
      <input type="hidden" name="value_${screenList_index}" value="${value!}"/>
      <input type="hidden" name="group_${screenList_index}" value="${group!}"/>
    </#if>
  </#list>
</#if>

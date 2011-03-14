/**
 * Copyright (C) 2009 eXo Platform SAS.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.portal.webui.navigation;

import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.UserPortalConfig;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.portal.config.model.PageNavigation;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.user.UserNavigation;
import org.exoplatform.portal.webui.page.UIPageNodeForm;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.portal.webui.workspace.UIPortalApplication;
import org.exoplatform.portal.webui.workspace.UIWorkingWorkspace;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIComponent;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.core.UIPopupWindow;
import org.exoplatform.webui.core.UITree;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;

@ComponentConfig(template = "system:/groovy/portal/webui/navigation/UINavigationManagement.gtmpl", events = {
   @EventConfig(listeners = UINavigationManagement.SaveActionListener.class),
   @EventConfig(listeners = UINavigationManagement.AddRootNodeActionListener.class)})
public class UINavigationManagement extends UIContainer
{

   private String owner;

   private String ownerType;

   @SuppressWarnings("unused")
   public UINavigationManagement() throws Exception
   {
      addChild(UINavigationNodeSelector.class, null, null);
   }

   public void setOwner(String owner)
   {
      this.owner = owner;
   }

   public String getOwner()
   {
      return this.owner;
   }

   public <T extends UIComponent> T setRendered(boolean b)
   {
      return super.<T> setRendered(b);
   }

   public void loadView(Event<? extends UIComponent> event) throws Exception
   {
      UINavigationNodeSelector uiNodeSelector = getChild(UINavigationNodeSelector.class);
      UITree uiTree = uiNodeSelector.getChild(UITree.class);
      uiTree.createEvent("ChangeNode", event.getExecutionPhase(), event.getRequestContext()).broadcast();
   }

   public void setOwnerType(String ownerType)
   {
      this.ownerType = ownerType;
   }

   public String getOwnerType()
   {
      return this.ownerType;
   }

   static public class SaveActionListener extends EventListener<UINavigationManagement>
   {

      public void execute(Event<UINavigationManagement> event) throws Exception
      {
         PortalRequestContext prContext = Util.getPortalRequestContext();
         UINavigationManagement uiManagement = event.getSource();
         UINavigationNodeSelector uiNodeSelector = uiManagement.getChild(UINavigationNodeSelector.class);
         UserPortalConfigService portalConfigService = uiManagement.getApplicationComponent(UserPortalConfigService.class);

         UserNavigation navigation = uiNodeSelector.getEdittedNavigation();

         SiteKey siteKey = navigation.getKey();
         String editedOwnerId = siteKey.getName();

         // Check existed
         UserPortalConfig userPortalConfig;
         if (PortalConfig.PORTAL_TYPE.equals(siteKey.getTypeName()))
         {
            userPortalConfig = portalConfigService.getUserPortalConfig(editedOwnerId, event.getRequestContext().getRemoteUser());

            if (userPortalConfig == null)
            {
               UIApplication uiApp = Util.getPortalRequestContext().getUIApplication();
               uiApp.addMessage(new ApplicationMessage("UIPortalForm.msg.notExistAnymore", null));
               UIPopupWindow uiPopup = uiManagement.getParent();
               uiPopup.setShow(false);
               UIPortalApplication uiPortalApp = (UIPortalApplication)prContext.getUIApplication();
               UIWorkingWorkspace uiWorkingWS = uiPortalApp.getChildById(UIPortalApplication.UI_WORKING_WS_ID);
               prContext.addUIComponentToUpdateByAjax(uiWorkingWS);
               prContext.setFullRender(true);
               return;
            }
         }
         else
         {
            userPortalConfig =  portalConfigService.getUserPortalConfig(prContext.getPortalOwner(), event.getRequestContext().getRemoteUser());
         }

         UserNavigation persistNavigation =  userPortalConfig.getUserPortal().getNavigation(siteKey);
         if (persistNavigation == null)
         {
            UIApplication uiApp = Util.getPortalRequestContext().getUIApplication();
            uiApp.addMessage(new ApplicationMessage("UINavigationManagement.msg.NavigationNotExistAnymore", null));
            UIPopupWindow uiPopup = uiManagement.getParent();
            uiPopup.setShow(false);
            UIPortalApplication uiPortalApp = (UIPortalApplication)prContext.getUIApplication();
            UIWorkingWorkspace uiWorkingWS = uiPortalApp.getChildById(UIPortalApplication.UI_WORKING_WS_ID);
            prContext.addUIComponentToUpdateByAjax(uiWorkingWS);
            prContext.setFullRender(true);
            return;
         }

         UIPopupWindow uiPopup = uiManagement.getParent();
         uiPopup.setShow(false);
         UIWorkingWorkspace uiWorkingWS = Util.getUIPortalApplication().getChildById(UIPortalApplication.UI_WORKING_WS_ID);
         prContext.addUIComponentToUpdateByAjax(uiWorkingWS);
         prContext.setFullRender(true);

         uiNodeSelector.getRootNode().save();
      }
   }

   static public class AddRootNodeActionListener extends EventListener<UINavigationManagement>
   {

      @Override
      public void execute(Event<UINavigationManagement> event) throws Exception
      {
         UINavigationManagement uiManagement = event.getSource();
         UINavigationNodeSelector uiNodeSelector = uiManagement.getChild(UINavigationNodeSelector.class);
         UIPopupWindow uiManagementPopup = uiNodeSelector.getAncestorOfType(UIPopupWindow.class);
         UIPageNodeForm uiNodeForm = uiManagementPopup.createUIComponent(UIPageNodeForm.class, null, null);
         uiNodeForm.setValues(null);
         uiManagementPopup.setUIComponent(uiNodeForm);

         uiNodeForm.setSelectedParent(uiNodeSelector.getSelectedNode());
         uiNodeForm.setContextPageNavigation(uiNodeSelector.getEdittedNavigation());

         uiManagementPopup.setWindowSize(800, 500);
         event.getRequestContext().addUIComponentToUpdateByAjax(uiManagementPopup.getParent());
      }

   }
}
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

package org.exoplatform.portal.resource;

import org.exoplatform.commons.cache.future.FutureMap;
import org.exoplatform.commons.cache.future.Loader;
import org.exoplatform.commons.utils.BinaryOutput;
import org.exoplatform.commons.utils.ByteArrayOutput;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.commons.utils.Safe;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.management.annotations.Impact;
import org.exoplatform.management.annotations.ImpactType;
import org.exoplatform.management.annotations.Managed;
import org.exoplatform.management.annotations.ManagedDescription;
import org.exoplatform.management.annotations.ManagedName;
import org.exoplatform.management.jmx.annotations.NameTemplate;
import org.exoplatform.management.jmx.annotations.Property;
import org.exoplatform.management.rest.annotations.RESTEndpoint;
import org.exoplatform.portal.resource.compressor.ResourceCompressor;
import org.exoplatform.portal.resource.compressor.ResourceType;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.Orientation;
import org.gatein.wci.impl.DefaultServletContainerFactory;
import org.picocontainer.Startable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

@Managed
@NameTemplate({@Property(key = "view", value = "portal"), @Property(key = "service", value = "management"),
   @Property(key = "type", value = "skin")})
@ManagedDescription("Skin service")
@RESTEndpoint(path = "skinservice")
public class SkinService implements Startable
{

   protected static Log log = ExoLogger.getLogger("portal.SkinService");

   private static final Map<Orientation, String> suffixMap = new EnumMap<Orientation, String>(Orientation.class);

   static
   {
      suffixMap.put(Orientation.LT, "-lt.css");
      suffixMap.put(Orientation.RT, "-rt.css");
      suffixMap.put(Orientation.TL, "-lt.css");
      suffixMap.put(Orientation.TR, "-lt.css");
   }

   private static final String LEFT_P = "\\(";

   private static final String RIGHT_P = "\\)";

   /** Immutable and therefore thread safe. */
   private static final Pattern IMPORT_PATTERN =
      Pattern.compile("(@import\\s+" + "url" + LEFT_P + "['\"]?" + ")([^'\";]+.css)(" + "['\"]?" + RIGHT_P + "\\s*;)");

   /** Immutable and therefore thread safe. */
   private static final Pattern BACKGROUND_PATTERN =
      Pattern.compile("(background[^;]+url" + LEFT_P + "['\"]?" + ")([^'\";]+)(" + "['\"]?" + RIGHT_P + "[^;]*;)");  

   /** Immutable and therefore thread safe. */
   private static final Pattern LT = Pattern.compile("[^{;]*;\\s*/\\*\\s*orientation=lt\\s*\\*/");

   /** Immutable and therefore thread safe. */
   private static final Pattern RT = Pattern.compile("[^{;]*;\\s*/\\*\\s*orientation=rt\\s*\\*/");

   /** One month caching. */
   private static final int ONE_MONTH = 2592000;

   /** One hour caching. */
   private static final int ONE_HOUR = 3600;

   public static final String DEFAULT_SKIN = "Default";

   /** The deployer. */
   private final AbstractResourceHandler deployer;

   /** The removal. */
   private final AbstractResourceHandler removal;

   private final Map<SkinKey, SkinConfig> portalSkins_;

   private final Map<SkinKey, SkinConfig> skinConfigs_;

   private final HashSet<String> availableSkins_;

   private final FutureMap<String, CachedStylesheet, Orientation> ltCache;

   private final FutureMap<String, CachedStylesheet, Orientation> rtCache;

   private final Map<String, Set<String>> portletThemes_;

   private final MainResourceResolver mainResolver;

   /**
    * The name of the portal container
    */
   final String portalContainerName;

   /**
    * An id used for caching request. The id life cycle is the same than the
    * class instance because we consider css will change until server is
    * restarted. Of course this only applies for the developing mode set to
    * false.
    */
   final String id = Long.toString(System.currentTimeMillis());

   private ResourceCompressor compressor;

   public SkinService(ExoContainerContext context, ResourceCompressor compressor)
   {
      Loader<String, CachedStylesheet, Orientation> loader = new Loader<String, CachedStylesheet, Orientation>()
      {
         public CachedStylesheet retrieve(Orientation context, String key) throws Exception
         {
            Resource skin = getCSSResource(key, key);
            if (skin == null)
            {
               return null;
            }
            
            StringBuffer sb = new StringBuffer();
            processCSSRecursively(sb, true, skin, context);
            String css = sb.toString();;
            try
            {
               if (SkinService.this.compressor.isSupported(ResourceType.STYLESHEET))
               {
                  css = SkinService.this.compressor.compress(css, ResourceType.STYLESHEET);
               }
            }
            catch (Exception e)
            {
               log.warn("Error when compressing CSS " + key + " for orientation " + context + " will use normal CSS instead", e);
            }

            return new CachedStylesheet(css);
         }
      };

      //
      this.compressor = compressor;
      portalSkins_ = new LinkedHashMap<SkinKey, SkinConfig>();
      skinConfigs_ = new LinkedHashMap<SkinKey, SkinConfig>(20);
      availableSkins_ = new HashSet<String>(5);
      ltCache = new FutureMap<String, CachedStylesheet, Orientation>(loader);
      rtCache = new FutureMap<String, CachedStylesheet, Orientation>(loader);
      portletThemes_ = new HashMap<String, Set<String>>();
      portalContainerName = context.getPortalContainerName();
      mainResolver = new MainResourceResolver(portalContainerName, skinConfigs_);
      deployer = new GateInSkinConfigDeployer(portalContainerName, this);
      removal = new GateInSkinConfigRemoval(this);
   }

   /**
    * Add a new category for portlet themes if it does not exist
    * 
    * @param categoryName the category name
    */
   public void addCategoryTheme(String categoryName)
   {
      if (!portletThemes_.containsKey(categoryName))
      {
         portletThemes_.put(categoryName, new HashSet<String>());
      }
   }

   /**
    * @deprecated use {@link #addPortalSkin(String, String, String)} instead 
    */
   @Deprecated
   public void addPortalSkin(String module, String skinName, String cssPath, ServletContext scontext)
   {
      addPortalSkin(module, skinName, cssPath);
   }
   
   /**
    * Add a portal skin with the <code>priority</code> is Integer.MAX_VALUE
    * and the <code>overwrite</code> is false by default
    * 
    * @param module
    * @param skinName
    * @param cssPath
    */
   public void addPortalSkin(String module, String skinName, String cssPath)
   {
      addPortalSkin(module, skinName, cssPath, Integer.MAX_VALUE, false);
   }

   /**
    * @deprecated use {@link #addPortalSkin(String, String, String, boolean)} instead
    */
   @Deprecated
   public void addPortalSkin(String module, String skinName, String cssPath, ServletContext scontext, boolean overwrite)
   {
      addPortalSkin(module, skinName, cssPath, overwrite);
   }

   /**
    * Add a portal skin with the <code>priority</code> is Integer.MAX_VALUE by default
    * 
    * @param module
    * @param skinName
    * @param cssPath
    * @param overwrite
    */
   public void addPortalSkin(String module, String skinName, String cssPath, boolean overwrite)
   {
      addPortalSkin(module, skinName, cssPath, Integer.MAX_VALUE, overwrite);
   }

   /**
    * Register a portal skin
    * 
    * @param module
    *           skin module identifier
    * @param skinName
    *           skin name
    * @param cssPath
    *           path uri to the css file. This is relative to the root context,
    *           use leading '/'
    * @param overwrite
    *           if any previous skin should be replaced by that one
    * @param cssPriority          
    *           priority to support sorting in skin list
    */
   public void addPortalSkin(String module, String skinName, String cssPath, int priority, boolean overwrite)
   {
      availableSkins_.add(skinName);
      SkinKey key = new SkinKey(module, skinName);
      SkinConfig skinConfig = portalSkins_.get(key);
      if (skinConfig == null || overwrite)
      {
         if (priority < 0)
         {
            priority = Integer.MAX_VALUE;
         }
         
         skinConfig = new SimpleSkin(this, module, skinName, cssPath, priority);
         portalSkins_.put(key, skinConfig);
         
         if (log.isDebugEnabled())
         {
            log.debug("Adding Portal skin : Bind " + key + " to " + skinConfig);
         }
      }
   }
   
   /**
    * Register a portal skin with the specific <code>cssData</code>
    * 
    * @deprecated This method is not supported anymore.
    *             The resource resolver pluggability mechanism should be used somehow
    * @param module
    *           skin module identifier
    * @param skinName
    *           skin name
    * @param cssPath
    *           path uri to the css file. This is relative to the root context,
    *           use leading '/'
    * @param cssData
    *           the content of css
    */
   @Deprecated
   public void addPortalSkin(String module, String skinName, String cssPath, String cssData)
   {
      throw new UnsupportedOperationException("This method is not supported anymore.");
   }
   
   /**
    * @deprecated use {@link #addSkin(String, String, String)} instead
    */
   @Deprecated
   public void addSkin(String module, String skinName, String cssPath, ServletContext scontext)
   {
      addSkin(module, skinName, cssPath);
   }
   
   /**
    * Add a skin with the <code>priority</code> is Integer.MAX_VALUE
    * and the <code>overwrite</code> is false by default
    * 
    * @param module
    * @param skinName
    * @param cssPath
    */
   public void addSkin(String module, String skinName, String cssPath)
   {
      addSkin(module, skinName, cssPath, Integer.MAX_VALUE, false);
   }

   /**
    * @deprecated use {@link #addSkin(String, String, String, boolean)} instead
    */
   @Deprecated
   public void addSkin(String module, String skinName, String cssPath, ServletContext scontext, boolean overwrite)
   {
      addSkin(module, skinName, cssPath, overwrite);
   }
   
   /**
    * Add a portal skin with the <code>priority</code> is Integer.MAX_VALUE
    * 
    * @param module
    * @param skinName
    * @param cssPath
    * @param overwrite
    */
   public void addSkin(String module, String skinName, String cssPath, boolean overwrite)
   {
      addSkin(module, skinName, cssPath, Integer.MAX_VALUE, overwrite);
   }

   /**
    * 
    * Register the Skin for available portal Skins. Support priority
    * 
    * @param module
    *           skin module identifier
    * @param skinName
    *           skin name
    * @param cssPath
    *           path uri to the css file. This is relative to the root context,
    *           use leading '/'
    * @param overwrite
    *           if any previous skin should be replaced by that one
    * @param priority
    *           priority to support sorting in skin list
    */
   public void addSkin(String module, String skinName, String cssPath, int priority, boolean overwrite)
   {
      availableSkins_.add(skinName);
      SkinKey key = new SkinKey(module, skinName);
      SkinConfig skinConfig = skinConfigs_.get(key);
      if (skinConfig == null || overwrite)
      {
         if (priority < 0)
         {
            priority = Integer.MAX_VALUE;
         }
         
         skinConfig = new SimpleSkin(this, module, skinName, cssPath, priority);
         skinConfigs_.put(key, skinConfig);

         if (log.isDebugEnabled())
         {
            log.debug("Adding skin : Bind " + key + " to " + skinConfig);
         }
      }
   }

   /**
    * 
    * Register the Skin for available portal Skins. Do not replace existed Skin
    * 
    * @param module
    *           skin module identifier
    * @param skinName
    *           skin name
    * @param cssPath
    *           path uri to the css file. This is relative to the root context,
    *           use leading '/'
    * @param scontext
    *           the webapp's {@link javax.servlet.ServletContext}
    */
   @Deprecated
   public void addSkin(String module, String skinName, String cssPath, String cssData)
   {
      availableSkins_.add(skinName);
      SkinKey key = new SkinKey(module, skinName);
      SkinConfig skinConfig = skinConfigs_.get(key);
      if (skinConfig == null)
      {
         skinConfigs_.put(key, new SimpleSkin(this, module, skinName, cssPath));
      }
      ltCache.remove(cssPath);
      rtCache.remove(cssPath);
   }
   
   /**
    * Merge several skins into one single skin.
    * 
    * @param skins
    *           the skins to merge
    * @return the merged skin
    */
   public Skin merge(Collection<SkinConfig> skins)
   {
      return new CompositeSkin(this, skins);
   }

   /**
    * Add a resource resolver to plug external resolvers.
    * 
    * @param resolver
    *           a resolver to add
    */
   public void addResourceResolver(ResourceResolver resolver)
   {
      mainResolver.resolvers.addIfAbsent(resolver);
   }

   /**
    * Registry theme category with its themes for portlet Theme
    * @param categoryName
    *             category name that will be registried
    * @param themesName
    *             list theme  name of categoryName
    */
   public void addTheme(String categoryName, List<String> themesName)
   {
      if (!portletThemes_.containsKey(categoryName))
         portletThemes_.put(categoryName, new HashSet<String>());
      Set<String> catThemes = portletThemes_.get(categoryName);
      for (String theme : themesName)
         catThemes.add(theme);
   }

   /**
    * Get names of all the currently registered skins.
    * 
    * @return an unmodifiable Set of the currently registered skins
    */
   public Set<String> getAvailableSkinNames()
   {
      return availableSkins_;
   }

   /**
    * Return the CSS content of the file specified by the given URI.
    * 
    * @param path
    *           path of the css to find
    * @return the css contet or null if not found.
    */
   public String getCSS(String path)
   {
      try
      {
         final ByteArrayOutput output = new ByteArrayOutput();
         boolean success = renderCSS(new ResourceRenderer()
         {
            public BinaryOutput getOutput() throws IOException
            {
               return output;
            }
            public void setExpiration(long seconds)
            {
            }
         }, path);
         
         if (success)
         {
            return output.getString();
         }
         else
         {
            return null;
         }
      }
      catch (IOException e)
      {
         log.error("Error while rendering css " + path, e);
         return null;
      }
      catch (RenderingException e)
      {
         log.error("Error while rendering css " + path, e);
         return null;
      }
   }

   /**
    * Render css content of the file specified by the given URI
    * @param renderer
    *          the webapp's {@link org.exoplatform.portal.resource.ResourceRenderer}
    * @param path
    *          path uri to the css file
    * @throws RenderingException
    * @throws IOException
    * @return <code>true</code> if the <code>CSS resource </code>is found and rendered;
    *         <code>false</code> otherwise.
    */
   public boolean renderCSS(ResourceRenderer renderer, String path) throws RenderingException, IOException
   {
      Orientation orientation = Orientation.LT;
      if (path.endsWith("-lt.css"))
      {
         path = path.substring(0, path.length() - "-lt.css".length()) + ".css";
      }
      else if (path.endsWith("-rt.css"))
      {
         path = path.substring(0, path.length() - "-rt.css".length()) + ".css";
         orientation = Orientation.RT;
      }

      // Check if it is running under developing mode
      if (PropertyManager.isDevelopping())
      {
         StringBuffer sb = new StringBuffer();
         Resource skin = getCSSResource(path, path);
         if (skin == null)
         {
            return false;
         }
         processCSSRecursively(sb, false, skin, orientation);
         byte[] bytes = sb.toString().getBytes("UTF-8");
         renderer.getOutput().write(bytes);
      }
      else
      {
         //
         FutureMap<String, CachedStylesheet, Orientation> cache = orientation == Orientation.LT ? ltCache : rtCache;
         CachedStylesheet cachedCss = cache.get(orientation, path);
         
         if (cachedCss == null)
         {
            return false;
         }
         
         if (path.startsWith("/" + portalContainerName + "/resource"))
         {
            renderer.setExpiration(ONE_MONTH);
         }
         else
         {
            renderer.setExpiration(ONE_HOUR);
         }
         
         cachedCss.writeTo(renderer.getOutput());
      }
      return true;
   }

   /**
    * Return CSS data corresponding to the <code>path</code>
    * 
    * @param path
    *          path uri to the css file
    * @return css content of URI file or null if not found
    */
   @Deprecated
   public String getMergedCSS(String path)
   {
      return getCSS(path);
   }

   /**
    * Return a collection of Portal Skins that its elements are ordered by CSS priority
    * 
    * @param skinName
    *          name of Portal Skin
    * @return 
    *       all org.exoplatform.portal.resource.SkinConfig of Portal Skin
    */
   public Collection<SkinConfig> getPortalSkins(String skinName)
   {
      Set<SkinKey> keys = portalSkins_.keySet();
      List<SkinConfig> portalSkins = new ArrayList<SkinConfig>();
      for (SkinKey key : keys)
      {
         if (key.getName().equals(skinName))
            portalSkins.add(portalSkins_.get(key));
      }
      Collections.sort(portalSkins, new Comparator<SkinConfig>()
      {
         public int compare(SkinConfig o1, SkinConfig o2)
         {
            return o1.getCSSPriority() - o2.getCSSPriority();
         }
      });
      return portalSkins;
   }

   /**
    * Return the map of portlet themes
    * 
    * @return the map of portlet themes
    */
   public Map<String, Set<String>> getPortletThemes()
   {
      return portletThemes_;
   }

   /**
    * Return a SkinConfig mapping by the module and skin name
    * 
    * @param module
    * @param skinName
    * @return SkinConfig by SkinKey(module, skinName), or SkinConfig by SkinKey(module, SkinService.DEFAULT_SKIN)
    */
   public SkinConfig getSkin(String module, String skinName)
   {
      SkinConfig config = skinConfigs_.get(new SkinKey(module, skinName));
      if (config == null)
         skinConfigs_.get(new SkinKey(module, SkinService.DEFAULT_SKIN));
      return config;
   }

   /**
    * Remove SkinKey from SkinCache by portalName and skinName
    * 
    * @deprecated the method name is wrong to the behaviour it does.
    *             Use {@link #removeSkin(String, String)} instead
    * 
    * @param portalName
    * @param skinName
    */
   @Deprecated
   public void invalidatePortalSkinCache(String portalName, String skinName)
   {
      SkinKey key = new SkinKey(portalName, skinName);
      skinConfigs_.remove(key);
   }
   
   /**
    * Invalidate skin from the cache
    * 
    * @param path the key
    */
   public void invalidateCachedSkin(String path)
   {
      ltCache.remove(path);
      rtCache.remove(path);
   }

   /**
    * Return last modifed date of cached css
    * Return null if cached css can not be found
    * @param path - path must not be null
    */
   public long getLastModified(String path)
   {
      if (path == null)
      {
         throw new IllegalArgumentException("path must not be null");
      }
      
      if (PropertyManager.isDevelopping())
      {
         return Long.MAX_VALUE;
      }

      FutureMap<String, CachedStylesheet, Orientation> cache = ltCache;
      Orientation orientation = Orientation.LT;
      if (path.endsWith("-lt.css"))
      {
         path = path.substring(0, path.length() - "-lt.css".length()) + ".css";
      }
      else if (path.endsWith("-rt.css"))
      {
         path = path.substring(0, path.length() - "-rt.css".length()) + ".css";
         orientation = Orientation.RT;
      }

      CachedStylesheet cachedCSS = cache.get(orientation, path);
      if (cachedCSS == null)
      {
         return Long.MAX_VALUE;
      }
      else
      {
         return cachedCSS.getLastModified();
      }
   }

   /**
    * @deprecated The method name is not clear.
    *             Using {@link #removeSkin(String, String)} instead
    */
   @Deprecated
   public void remove(String module, String skinName)
   {
      removeSkin(module, skinName);
   }
   
   /**
    * Remove a Skin from the service as well as its cache
    * 
    * @param module
    * @param skinName
    */
   public void removeSkin(String module, String skinName)
   {
      SkinKey key;
      if (skinName.length() == 0)
      {
         key = new SkinKey(module, DEFAULT_SKIN);
      }
      else
      {
         key = new SkinKey(module, skinName);
      }
      
      removeSkin(key);
   }
   
   /**
    * Remove a Skin mapped to the <code>key</code>
    * 
    * @param key key whose mapping skin is to be removed from the service
    */
   public void removeSkin(SkinKey key)
   {
      if (key == null)
      {
         return;
      }
      
      SkinConfig remove = skinConfigs_.remove(key);
      
      if (remove != null)
      {
         invalidateCachedSkin(remove.getCSSPath());
      }
   }
   
   /**
    * @deprecated This is deprecated as its name was not clear.
    *             Use {@link #removeSkins(List)} instead
    */
   @Deprecated
   public void remove(List<SkinKey> keys) throws Exception
   {
      removeSkins(keys);
   }
   
   /**
    * Remove SkinConfig from Portal Skin Config by SkinKey
    * @param keys
    *          SkinKey list these will be removed
    * @throws Exception
    */
   public void removeSkins(List<SkinKey> keys) throws Exception
   {
      if (keys == null)
      {
         return;
      }
      
      for (SkinKey key : keys)
      {
         removeSkin(key);
      }
   }


   /**
    * Remove Skin from Portal available Skin by skin name
    * @param skinName
    *          name of skin that will be removed
    * @throws Exception
    */
   public void removeSupportedSkin(String skinName) throws Exception
   {
      availableSkins_.remove(skinName);
   }

   /**
    * Return the number of skin config maintaining in this SkinService
    * 
    * @return the number of skin config maintaining in this SkinService
    */
   public int size()
   {
      return skinConfigs_.size();
   }
   
   /**
    *
    * This method delegates the resource resolving to MainResourceResolver and prints out appropriated log messages 
    * 
    * Consider the two cases the method is invoked 
    * 
    * Case 1: Resolve nested .css file 
    * 
    *  In Stylesheet.css we have the statement 
    * 
    *  @import url(xyzt.css);
    * 
    *  To resolve the resource from xyzt.css, getCSSResource("xyzt.css", "Stylesheet.css") is called
    *  
    * Case 2: Resolve top root .css file
    * 
    *  To resolve a top root Stylesheet.css file, getCSSResource("Stylesheet.css", "Stylesheet.css") is called
    * 
    * @param cssPath
    * @param outerCssFile
    * @return
    * 
    */
   private Resource getCSSResource(String cssPath, String outerCssFile)
   {
      Resource resource = mainResolver.resolve(cssPath);
      if (resource == null && log.isErrorEnabled())
      {
         String logMessage;
         if (!cssPath.equals(outerCssFile))
         {
            int lastIndexOfSlash = cssPath.lastIndexOf('/');
            String loadedCssFile = (lastIndexOfSlash >= 0)?(cssPath.substring(lastIndexOfSlash + 1)) : cssPath;
            logMessage =
               "Invalid <CSS FILE> configuration, please check the @import url(" + loadedCssFile + ") in "
                  + outerCssFile + " , SkinService could not load the skin " + cssPath;
         }
         else
         {
            logMessage =
               "Not found <CSS FILE>, the path " + cssPath + " is invalid, SkinService could not load the skin "
                  + cssPath;
         }
         log.error(logMessage);
      }
      return resource;
   }

   /**
    * Apply CSS for Skin <br/>
    * If skin is null, do nothing
    * @param appendable
    * @param merge
    * @param skin
    * @param orientation
    * @throws RenderingException
    * @throws IOException
    */
   private void processCSSRecursively(Appendable appendable, boolean merge, Resource skin, Orientation orientation)
      throws RenderingException, IOException
   {
      if(skin == null)
      {
         return;
      }
      // The root URL for the entry
      String basePath = skin.getContextPath() + skin.getParentPath();

      //
      Reader tmp = skin.read();
      if (tmp == null)
      {
         throw new RenderingException("No skin resolved for path " + skin.getResourcePath());
      }
      BufferedReader reader = new SkipCommentReader(tmp, new CommentBlockHandler.OrientationCommentBlockHandler());
      try
      {
         String line = reader.readLine();
         while (line != null)
         {            
            line = proccessOrientation(line, orientation);            
            line = proccessBackgroundUrl(line, basePath);                       
            
            Matcher matcher = IMPORT_PATTERN.matcher(line);
            while (matcher.find()) 
            {
               String includedPath = matcher.group(2);               
               if (!includedPath.startsWith("/")) 
               {
                  includedPath = basePath + includedPath;
               }
               
               String embeddedPath = includedPath.substring(0, includedPath.length() - ".css".length());
               StringBuffer strReplace = new StringBuffer();
               if (merge) 
               {                  
                  Resource ssskin = getCSSResource(includedPath, basePath + skin.getFileName());
                  processCSSRecursively(strReplace, merge, ssskin, orientation);                                    
               } 
               else 
               {                     
                  strReplace.append(matcher.group(1));
                  strReplace.append(embeddedPath);
                  strReplace.append(getSuffix(orientation));
                  strReplace.append(matcher.group(3));                                    
               }               
               String str = strReplace.toString().replaceAll("\\$", "\\\\\\$");
               matcher.appendReplacement((StringBuffer)appendable, str);
            }
            matcher.appendTail((StringBuffer)appendable);
            
            if ((line = reader.readLine()) != null)
            {
               appendable.append("\n");
            }
         }         
      }
      finally
      {
         Safe.close(reader);
      }
   }

   private String proccessBackgroundUrl(String line, String basePath)
   {
      // Rewrite background url pattern
      Matcher matcher = BACKGROUND_PATTERN.matcher(line);
      
      StringBuffer tmpBuilder = new StringBuffer();
      while (matcher.find()) 
      {
         if (!matcher.group(2).startsWith("\"/")) {
            if (!matcher.group(2).startsWith("'/")) {
               if (!matcher.group(2).startsWith("/")) {
                  StringBuilder strReplace = new StringBuilder();
                  strReplace.append(matcher.group(1));
                  strReplace.append(basePath);
                  strReplace.append(matcher.group(2));
                  strReplace.append(matcher.group(3));
                  
                  matcher.appendReplacement(tmpBuilder, strReplace.toString());          
               }             
            }
         }                  
      }
      matcher.appendTail(tmpBuilder);
      return tmpBuilder.toString();
   }

   private String proccessOrientation(String line, Orientation orientation)
   {
      Pattern orientationPattern = orientation == Orientation.LT ? RT : LT;
      Matcher matcher = orientationPattern.matcher(line);
      StringBuffer tmpBuilder = new StringBuffer();
      while (matcher.find()) 
      {
         matcher.appendReplacement(tmpBuilder, "");
      }
      matcher.appendTail(tmpBuilder);
      return tmpBuilder.toString();
   }

   /**
    * Get Suffix of Orientation
    * @param orientation
    * @return Suffix of Orientation
    */
   String getSuffix(Orientation orientation)
   {
      if (orientation == null)
      {
         orientation = Orientation.LT;
      }
      return suffixMap.get(orientation);
   }
   
   /**
    * Get all available skin
    * @return all available skin
    *    
    */
   @Managed
   @ManagedDescription("The list of registered skins identifiers")
   public String[] getSkinList()
   {
      // get all available skin
      List<String> availableSkin = new ArrayList<String>();
      for (String skin : availableSkins_)
      {
         availableSkin.add(skin);
      }
      // sort skin name asc
      Collections.sort(availableSkin);

      return availableSkin.toArray(new String[availableSkin.size()]);
   }

   /**
    * Registry ServletContext into MainResourceResolver of SkinService
    * @param sContext
    *          ServletContext will be registried
    */
   public void registerContext(ServletContext sContext)
   {
      mainResolver.registerContext(sContext);
   }
   
   /**
    * unregister a {@link ServletContext} into {@link MainResourceResolver} of {@link SkinService} 
    * 
    * @param servletContext ServletContext will unregistered
    */
   public void unregisterServletContext(ServletContext servletContext)
   {
      mainResolver.removeServletContext(servletContext);
   }

   /**
    * Clean cache, reload all Skins
    */
   @Managed
   @ManagedDescription("Reload all skins")
   @Impact(ImpactType.WRITE)
   public void reloadSkins()
   {
      // remove all ltCache, rtCache
      ltCache.clear();
      rtCache.clear();
   }

   /**
    * reload skin by skin ID
    * @param skinId
    *          the skin ID that will be reloaded
    */
   @Managed
   @ManagedDescription("Reload a specified skin")
   public void reloadSkin(@ManagedDescription("The skin id") @ManagedName("skinId") String skinId)
   {
      ltCache.remove(skinId);
      rtCache.remove(skinId);
   }

   /**
    * Start service.
    * Registry org.exoplatform.portal.resource.GateInSkinConfigDeployer and org.exoplatform.portal.resource.GateInSkinConfigRemoval into ServletContainer.
    * @see org.picocontainer.Startable#start()
    */
   public void start()
   {
      DefaultServletContainerFactory.getInstance().getServletContainer().addWebAppListener(deployer);
      DefaultServletContainerFactory.getInstance().getServletContainer().addWebAppListener(removal);
   }

   /**
    * Stop service
    * Remove org.exoplatform.portal.resource.GateInSkinConfigDeployer and org.exoplatform.portal.resource.GateInSkinConfigRemoval from ServletContainer.
    * @see org.picocontainer.Startable#stop()
    */
   public void stop()
   {
      DefaultServletContainerFactory.getInstance().getServletContainer().removeWebAppListener(deployer);
      DefaultServletContainerFactory.getInstance().getServletContainer().removeWebAppListener(removal);
   }
}

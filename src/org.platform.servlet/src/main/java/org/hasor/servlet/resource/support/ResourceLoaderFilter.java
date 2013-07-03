/*
 * Copyright 2008-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hasor.servlet.resource.support;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.hasor.MoreFramework;
import org.hasor.context.AppContext;
import org.hasor.servlet.resource.ResourceLoader;
import org.hasor.servlet.resource.ResourceLoaderCreator;
import org.hasor.servlet.resource.support.ResourceSettings.LoaderConfig;
import org.hasor.servlet.resource.util.MimeType;
import org.more.util.IOUtils;
import org.more.util.ResourcesUtils;
import org.more.util.StringUtils;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
/**
 * ����װ��jar����zip���е���Դ
 * @version : 2013-6-5
 * @author ������ (zyc@byshell.org)
 */
public class ResourceLoaderFilter implements Filter {
    @Inject
    private AppContext       appContext = null;
    private MimeType         mimeType   = null;
    private ResourceSettings settings   = null;
    private ResourceLoader[] loaderList = null;
    private File             cacheDir   = null;
    //
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.settings = appContext.getInstance(ResourceSettings.class);
        //1.��������loaderCreator
        ArrayList<ResourceLoaderCreatorDefinition> loaderCreatorList = new ArrayList<ResourceLoaderCreatorDefinition>();
        TypeLiteral<ResourceLoaderCreatorDefinition> LOADER_DEFS = TypeLiteral.get(ResourceLoaderCreatorDefinition.class);
        for (Binding<ResourceLoaderCreatorDefinition> entry : this.appContext.getGuice().findBindingsByType(LOADER_DEFS)) {
            ResourceLoaderCreatorDefinition define = entry.getProvider().get();
            define.setAppContext(this.appContext);
            loaderCreatorList.add(define);
        }
        //2.����loader
        ArrayList<ResourceLoader> resourceLoaderList = new ArrayList<ResourceLoader>();
        for (LoaderConfig item : this.settings.getLoaders()) {
            String loaderType = item.loaderType;
            String configVal = item.config.getText();
            ResourceLoaderCreator creator = null;
            //���Ѿ�ע���TemplateLoader�л�ȡһ��TemplateLoaderCreator���й�����
            for (ResourceLoaderCreatorDefinition define : loaderCreatorList)
                if (StringUtils.eqUnCaseSensitive(define.getName(), loaderType)) {
                    creator = define.get();
                    break;
                }
            if (creator == null) {
                MoreFramework.warning("missing %s ResourceLoaderCreator!", loaderType);
            } else {
                try {
                    ResourceLoader loader = creator.newInstance(this.appContext, item.config);
                    if (loader == null)
                        MoreFramework.error("%s ResourceLoaderCreator call newInstance return is null. config is %s.", loaderType, configVal);
                    else {
                        resourceLoaderList.add(loader);
                    }
                } catch (Exception e) {
                    MoreFramework.error("%s ResourceLoaderCreator has error.%s", e);
                }
            }
            //
        }
        //3.����
        this.loaderList = resourceLoaderList.toArray(new ResourceLoader[resourceLoaderList.size()]);
        //4.����·��
        this.cacheDir = new File(this.appContext.getWorkSpace().getCacheDir(this.settings.getCacheDir()));
        if (!chekcCacheDir()) {
            MoreFramework.warning("init ResourceLoaderFilter error can not create %s", this.cacheDir);
            int i = 0;
            while (true) {
                this.cacheDir = new File(this.appContext.getWorkSpace().getCacheDir(this.settings.getCacheDir() + "_" + String.valueOf(i)));
                if (chekcCacheDir())
                    break;
            }
        }
        MoreFramework.info("use cacheDir %s", this.cacheDir);
    }
    //
    private boolean chekcCacheDir() {
        this.cacheDir.mkdirs();
        if (this.cacheDir.isDirectory() == false && this.cacheDir.exists() == true)
            return false;
        else
            return true;
    }
    //
    private MimeType getMimeType() throws IOException {
        if (this.mimeType != null)
            return this.mimeType;
        this.mimeType = new MimeType();
        List<URL> listURL = ResourcesUtils.getResources("/META-INF/mime.types.xml");
        if (listURL != null)
            for (URL resourceURL : listURL)
                try {
                    InputStream inStream = ResourcesUtils.getResourceAsStream(resourceURL);
                    this.mimeType.loadStream(inStream, "utf-8");
                } catch (Exception e) {
                    MoreFramework.warning("loadMimeType error at %s", resourceURL);
                }
        return this.mimeType;
    }
    //
    public void forwardTo(File file, ServletRequest request, ServletResponse response) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String requestURI = req.getRequestURI();
        String fileExt = requestURI.substring(requestURI.lastIndexOf("."));
        String typeMimeType = req.getServletContext().getMimeType(fileExt);
        if (StringUtils.isBlank(typeMimeType))
            typeMimeType = this.getMimeType().get(fileExt.substring(1).toLowerCase());
        //
        response.setContentType(typeMimeType);
        FileInputStream cacheFile = new FileInputStream(file);
        IOUtils.copy(cacheFile, response.getOutputStream());
        cacheFile.close();
    }
    //
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (this.settings.isEnable() == false) {
            chain.doFilter(request, response);
            return;
        }
        //1.ȷ��ʱ������
        HttpServletRequest req = (HttpServletRequest) request;
        String requestURI = req.getRequestURI();
        try {
            requestURI = URLDecoder.decode(requestURI, "utf-8");
        } catch (Exception e) {}
        boolean hit = false;
        for (String s : this.settings.getContentTypes()) {
            if (requestURI.endsWith("." + s) == true) {
                hit = true;
                break;
            }
        }
        if (hit == false) {
            chain.doFilter(request, response);
            return;
        }
        //2.��黺��·�����Ƿ����
        File cacheFile = new File(this.cacheDir, requestURI);
        if (cacheFile.exists()) {
            this.forwardTo(cacheFile, request, response);
            return;
        }
        //3.����������Դ
        InputStream inStream = null;
        for (ResourceLoader loader : loaderList) {
            inStream = loader.getResourceAsStream(requestURI);
            if (inStream != null)
                break;
        }
        if (inStream == null) {
            chain.doFilter(request, response);
            return;
        }
        //4.д����ʱ�ļ���
        cacheFile.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(cacheFile);
        IOUtils.copy(inStream, out);
        inStream.close();
        out.flush();
        out.close();
        this.forwardTo(cacheFile, request, response);
    }
    @Override
    public void destroy() {}
}
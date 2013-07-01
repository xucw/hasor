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
package org.more.web.freemarker;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.more.hypha.ApplicationContext;
import org.more.services.freemarker.FreemarkerService;
import org.more.services.freemarker.loader.DirTemplateLoader;
import org.more.util.StringUtil;
import org.more.util.config.Config;
import org.more.web.AbstractServletFilter;
import org.more.web.hypha.ContextLoaderListener;
import freemarker.template.TemplateException;
/**
 * freemarker �齨��Web���ֵ�֧�֣������Ѿ�ʵ����Filter�ӿڲ��Ҽ̳���HttpServlet�ࡣ
 * @version : 2011-9-14
 * @author ������ (zyc@byshell.org)
 */
public class FreeMarkerRoot extends AbstractServletFilter {
    /*-----------------------------------------------------------------*/
    private static final long serialVersionUID  = -9157250446565992949L;
    private FreemarkerService freemarkerService = null;
    private String[]          files             = { "*.html", "*.htm", "*.flt" };
    /*-----------------------------------------------------------------*/
    protected void init(Config<ServletContext> config) throws ServletException {
        try {
            ApplicationContext context = (ApplicationContext) this.getServletContext().getAttribute(ContextLoaderListener.ContextName);
            this.freemarkerService = context.getService(FreemarkerService.class);
            File webPath = new File(this.getAbsoluteContextPath());
            this.freemarkerService.addLoader(0, new DirTemplateLoader(webPath));//WebĿ¼
            {
                //���ò���
                Enumeration<String> enums = config.getInitParameterNames();
                while (enums.hasMoreElements()) {
                    String name = enums.nextElement();
                    this.freemarkerService.setAttribute(name, config.getInitParameter(name));
                }
            }
            {
                String fs = (String) config.getInitParameter("freemarker-files");
                if (fs != null)
                    this.files = fs.split(",");
            }
            this.getServletContext().setAttribute("org.more.freemarker.ROOT", this.freemarkerService);
        } catch (Throwable e) {
            e.printStackTrace();
            if (e instanceof ServletException)
                throw (ServletException) e;
            else
                throw new ServletException(e.getLocalizedMessage(), e);
        }
    };
    /*-----------------------------------------------------------------*/
    protected void processTemplate(String templateName, HttpServletResponse res, Map<String, Object> params) throws IOException, ServletException {
        try {
            this.freemarkerService.process(templateName, params, res.getWriter());
        } catch (TemplateException e) {
            throw new ServletException(e);
        }
    }
    /**��ȡһ��RootMap�����Map���ڴ��һЩ���󣬵�ģ�屻ִ��ʱ��Map�е������ڽ���ģ��ʱ���Ա�ģ����ʵ���*/
    protected Map<String, Object> getRootMap(HttpServletRequest req, HttpServletResponse res) {
        Map<String, String[]> paramMap = req.getParameterMap();
        HashMap<String, Object> reqParams = new HashMap<String, Object>();
        for (String key : paramMap.keySet()) {
            String[] param = paramMap.get(key);
            if (param == null || param.length == 0)
                reqParams.put(key, null);
            else if (param.length == 1)
                reqParams.put(key, param[0]);
            else
                reqParams.put(key, param);
        }
        //���root�еĶ���
        HashMap<String, Object> rootMap = new HashMap<String, Object>();
        rootMap.put("req", req);
        rootMap.put("request", req);
        rootMap.put("res", res);
        rootMap.put("response", res);
        rootMap.put("reqMap", reqParams);
        rootMap.put("session", req.getSession(true));
        rootMap.put("context", req.getSession(true).getServletContext());
        return rootMap;
    }
    /** ������ȹ����� */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String templateName = this.getRequestPath(req);
        //ȷ�����Խ�������չ��
        for (String pattern : this.files)
            /**ȷ���Ƿ���ģ��*/
            if (StringUtil.matchWild(pattern, templateName) == true) {
                Map<String, Object> params = this.getRootMap(req, res);
                if (this.freemarkerService.containsTemplate(templateName) == true)
                    this.processTemplate(templateName, res, params);
                else
                    this.processTemplate("404", res, params);
                return;
            }
        /**��Դ����*/
        if (new File(templateName).isDirectory() == true) {
            chain.doFilter(req, res);
            return;
        }
        InputStream in = this.freemarkerService.getResourceAsStream(templateName);
        if (in == null)
            chain.doFilter(req, res);
        else {
            OutputStream out = res.getOutputStream();
            byte[] arrayData = new byte[4096];
            String mimeType = request.getServletContext().getMimeType(templateName);
            res.setContentType(mimeType);
            int length = 0;
            while ((length = in.read(arrayData)) > 0)
                out.write(arrayData, 0, length);
            out.flush();
        }
    };
    /** �������Servlet����servicesֻ�ᴦ��ģ�������ļ��� */
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        //
        String templateName = this.getRequestPath(req);
        Map<String, Object> params = this.getRootMap(req, res);
        //ȷ�����Խ�������չ��
        for (String pattern : this.files)
            /**ȷ���Ƿ���ģ��*/
            if (StringUtil.matchWild(pattern, templateName) == true) {
                if (this.freemarkerService.containsTemplate(templateName) == true)
                    this.processTemplate(templateName, res, params);
                else
                    this.processTemplate("404", res, params);
                return;
            }
        params.put("message", "δ���ø��ļ�Ϊģ��������Դ�������Դ�ļ������ڡ�");
        this.processTemplate("500", res, params);
    };
    /**��ȡ{@link FreemarkerService}����*/
    protected FreemarkerService getFreemarkerService() {
        return this.freemarkerService;
    };
};
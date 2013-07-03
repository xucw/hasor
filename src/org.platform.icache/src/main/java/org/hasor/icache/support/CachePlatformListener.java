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
package org.hasor.icache.support;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hasor.MoreFramework;
import org.hasor.binder.ApiBinder;
import org.hasor.context.AppContext;
import org.hasor.context.PlatformListener;
import org.hasor.icache.Cache;
import org.hasor.icache.CacheDefine;
import org.hasor.icache.CacheManager;
import org.hasor.icache.DefaultCache;
import org.hasor.icache.DefaultKeyBuilder;
import org.hasor.icache.KeyBuilder;
import org.hasor.icache.KeyBuilderDefine;
import org.hasor.icache.NeedCache;
import org.hasor.startup.PlatformExt;
import org.more.util.StringUtils;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.name.Names;
/**
 * ���������������Lv0
 * @version : 2013-4-8
 * @author ������ (zyc@byshell.org)
 */
@PlatformExt(displayName = "CacheModuleServiceListener", description = "org.platform.icache����������֧�֡�", startIndex = PlatformExt.Lv_0)
public class CachePlatformListener implements PlatformListener {
    private CacheManager  cacheManager = null;
    private CacheSettings settings     = null;
    /**��ʼ��.*/
    @Override
    public void initialize(ApiBinder event) {
        //1.����Aop
        event.getGuiceBinder().bindInterceptor(new ClassNeedCacheMatcher(), new MethodPowerMatcher(), new CacheInterceptor());
        /*�����ļ�������*/
        this.settings = new CacheSettings();
        this.settings.loadConfig(event.getSettings());
        event.getGuiceBinder().bind(CacheSettings.class).toInstance(this.settings);
        //2.���뻺������
        this.loadCache(event);
        this.loadKeyBuilder(event);
        //3.ע��Manager
        event.getGuiceBinder().bind(CacheManager.class).to(DefaultCacheManager.class).asEagerSingleton();
    }
    @Override
    public void initialized(AppContext appContext) {
        appContext.getSettings().addSettingsListener(this.settings);
        //
        this.cacheManager = appContext.getInstance(CacheManager.class);
        this.cacheManager.initManager(appContext);
        MoreFramework.info("online ->> cache is %s", (this.settings.isCacheEnable() ? "enable." : "disable."));
    }
    @Override
    public void destroy(AppContext appContext) {
        appContext.getSettings().removeSettingsListener(this.settings);
        this.cacheManager.destroyManager(appContext);
    }
    //
    /*װ��KeyBuilder*/
    protected void loadKeyBuilder(ApiBinder event) {
        //1.��ȡ
        Set<Class<?>> iKeyBuilderSet = event.getClassSet(KeyBuilderDefine.class);
        if (iKeyBuilderSet == null)
            return;
        List<Class<? extends KeyBuilder>> iKeyBuilderList = new ArrayList<Class<? extends KeyBuilder>>();
        for (Class<?> cls : iKeyBuilderSet) {
            if (KeyBuilder.class.isAssignableFrom(cls) == false) {
                MoreFramework.warning("loadKeyBuilder : not implemented IKeyBuilder of type %s.", cls);
            } else
                iKeyBuilderList.add((Class<? extends KeyBuilder>) cls);
        }
        //2.����
        Collections.sort(iKeyBuilderList, new Comparator<Class<?>>() {
            @Override
            public int compare(Class<?> o1, Class<?> o2) {
                KeyBuilderDefine o1Anno = o1.getAnnotation(KeyBuilderDefine.class);
                KeyBuilderDefine o2Anno = o2.getAnnotation(KeyBuilderDefine.class);
                int o1AnnoIndex = o1Anno.sort();
                int o2AnnoIndex = o2Anno.sort();
                return (o1AnnoIndex < o2AnnoIndex ? -1 : (o1AnnoIndex == o2AnnoIndex ? 0 : 1));
            }
        });
        //3.ע�����
        long defaultKeyBuilderIndex = Long.MAX_VALUE;
        Binder binder = event.getGuiceBinder();
        for (Class<? extends KeyBuilder> keyBuildertype : iKeyBuilderList) {
            KeyBuilderDefine keyBuilderAnno = keyBuildertype.getAnnotation(KeyBuilderDefine.class);
            Key<? extends KeyBuilder> keyBuilderKey = Key.get(keyBuildertype);
            KeyBuilderDefinition keyBuilderDefine = new KeyBuilderDefinition(keyBuilderAnno.value(), keyBuilderKey);
            binder.bind(KeyBuilderDefinition.class).annotatedWith(UniqueAnnotations.create()).toInstance(keyBuilderDefine);
            MoreFramework.info("KeyBuilder type:" + MoreFramework.logString(keyBuildertype) + " mapping " + MoreFramework.logString(keyBuilderAnno.value()));
            //ȷ���Ƿ�Ϊdefaut
            if (keyBuildertype.isAnnotationPresent(DefaultKeyBuilder.class) == true) {
                MoreFramework.warning("KeyBuilder type:" + MoreFramework.logString(keyBuildertype) + " is DefaultKeyBuilder on " + MoreFramework.logString(keyBuilderAnno.value()));
                DefaultKeyBuilder defaultKeyBuilder = keyBuildertype.getAnnotation(DefaultKeyBuilder.class);
                if (defaultKeyBuilder.value() <= defaultKeyBuilderIndex/*��ԽСԽ����*/) {
                    defaultKeyBuilderIndex = defaultKeyBuilder.value();
                    binder.bind(KeyBuilder.class).toProvider(keyBuilderDefine);
                }
            }
        }
    }
    //
    /*װ��Cache*/
    protected void loadCache(ApiBinder event) {
        //1.��ȡ
        Set<Class<?>> cacheSet = event.getClassSet(CacheDefine.class);
        if (cacheSet == null)
            return;
        List<Class<Cache<?>>> cacheList = new ArrayList<Class<Cache<?>>>();
        if (cacheSet != null)
            for (Class<?> cls : cacheSet) {
                if (Cache.class.isAssignableFrom(cls) == false) {
                    MoreFramework.warning("loadCache : not implemented ICache of type %s", cls);
                } else
                    cacheList.add((Class<Cache<?>>) cls);
            }
        //3.ע�����
        long defaultCacheIndex = Long.MAX_VALUE;
        Binder binder = event.getGuiceBinder();
        Map<String, Integer> cacheIndex = new HashMap<String, Integer>();
        for (Class<Cache<?>> cacheType : cacheList) {
            CacheDefine cacheAnno = cacheType.getAnnotation(CacheDefine.class);
            for (String cacheName : cacheAnno.value()) {
                MoreFramework.info(cacheName + " at Cache of type " + MoreFramework.logString(cacheType));
                //
                int maxIndex = (cacheIndex.containsKey(cacheName) == false) ? Integer.MAX_VALUE : cacheIndex.get(cacheName);
                if (cacheAnno.sort() <= maxIndex/*��ԽСԽ����*/) {
                    cacheIndex.put(cacheName, cacheAnno.sort());
                    //
                    CacheDefinition cacheDefine = new CacheDefinition(cacheName, cacheType);
                    binder.bind(CacheDefinition.class).annotatedWith(Names.named(cacheName)).toInstance(cacheDefine);
                    binder.bind(Cache.class).annotatedWith(Names.named(cacheName)).toProvider(cacheDefine);
                    //ȷ���Ƿ�Ϊdefaut
                    if (cacheType.isAnnotationPresent(DefaultCache.class) == true) {
                        MoreFramework.warning(cacheName + " is DefaultCache!");
                        DefaultCache defaultCache = cacheType.getAnnotation(DefaultCache.class);
                        if (defaultCache.value() <= defaultCacheIndex/*��ԽСԽ����*/) {
                            defaultCacheIndex = defaultCache.value();
                            binder.bind(Cache.class).toProvider(cacheDefine);
                        }
                    }
                }
            }
        }
    }
    /*-------------------------------------------------------------------------------------*/
    /*���������Ƿ�ƥ�䡣����ֻҪ���ͻ򷽷��ϱ����@NeedCache��*/
    private class ClassNeedCacheMatcher extends AbstractMatcher<Class<?>> {
        @Override
        public boolean matches(Class<?> matcherType) {
            /*������ڽ���״̬����Ի�����*/
            if (settings.isCacheEnable() == false)
                return false;
            /*----------------------------*/
            if (matcherType.isAnnotationPresent(NeedCache.class) == true)
                return true;
            Method[] m1s = matcherType.getMethods();
            Method[] m2s = matcherType.getDeclaredMethods();
            for (Method m1 : m1s) {
                if (m1.isAnnotationPresent(NeedCache.class) == true)
                    return true;
            }
            for (Method m2 : m2s) {
                if (m2.isAnnotationPresent(NeedCache.class) == true)
                    return true;
            }
            return false;
        }
    }
    /*�����ⷽ���Ƿ�ƥ�䡣���򣺷����򷽷��������ϱ����@NeedCache��*/
    private class MethodPowerMatcher extends AbstractMatcher<Method> {
        @Override
        public boolean matches(Method matcherType) {
            /*������ڽ���״̬����Ի�����*/
            if (settings.isCacheEnable() == false)
                return false;
            /*----------------------------*/
            if (matcherType.isAnnotationPresent(NeedCache.class) == true)
                return true;
            if (matcherType.getDeclaringClass().isAnnotationPresent(NeedCache.class) == true)
                return true;
            return false;
        }
    }
    /*������*/
    private class CacheInterceptor implements MethodInterceptor {
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            /*������ڽ���״̬����Ի�����*/
            if (settings.isCacheEnable() == false)
                return invocation.proceed();
            /*----------------------------*/
            //1.��ȡ��������
            Method targetMethod = invocation.getMethod();
            NeedCache cacheAnno = targetMethod.getAnnotation(NeedCache.class);
            if (cacheAnno == null)
                cacheAnno = targetMethod.getDeclaringClass().getAnnotation(NeedCache.class);
            if (cacheAnno == null)
                return invocation.proceed();
            //2.��ȡKey
            StringBuilder cacheKey = new StringBuilder(targetMethod.toString());
            Object[] args = invocation.getArguments();
            if (args != null)
                for (Object arg : args) {
                    if (arg == null) {
                        cacheKey.append("NULL");
                        continue;
                    }
                    /*��֤arg������Ϊ��*/
                    KeyBuilder keyBuilder = cacheManager.getKeyBuilder(arg.getClass());
                    cacheKey.append(keyBuilder.serializeKey(arg));
                }
            MoreFramework.debug("MethodInterceptor Method : %s", targetMethod);
            MoreFramework.debug("MethodInterceptor Cache key :%s", cacheKey.toString());
            //3.��ȡ����
            Cache<Object> cacheObject = null;
            if (StringUtils.isBlank(cacheAnno.cacheName()) == true)
                cacheObject = cacheManager.getDefaultCache();
            else
                cacheObject = cacheManager.getCache(cacheAnno.cacheName());
            //4.��������
            String key = cacheKey.toString();
            Object returnData = null;
            if (cacheObject.hasCache(key) == true) {
                MoreFramework.debug("the method return data is from Cache.");
                returnData = cacheObject.fromCache(key);
            } else {
                MoreFramework.debug("set data to Cache key :" + key);
                returnData = invocation.proceed();
                cacheObject.toCache(key, returnData, cacheAnno.timeout());
            }
            return returnData;
        }
    }
}
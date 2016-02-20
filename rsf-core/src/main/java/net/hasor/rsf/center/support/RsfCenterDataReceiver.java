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
package net.hasor.rsf.center.support;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.hasor.rsf.RsfContext;
import net.hasor.rsf.RsfUpdater;
import net.hasor.rsf.center.RsfCenterListener;
import net.hasor.rsf.domain.RsfCenterException;
/**
 * 注册中心数据接收器，负责更新注册中心推送过来的配置信息。
 * @version : 2016年2月18日
 * @author 赵永春(zyc@hasor.net)
 */
public class RsfCenterDataReceiver implements RsfCenterListener {
    protected Logger   logger = LoggerFactory.getLogger(getClass());
    private RsfContext rsfContext;
    @Override
    public boolean onEvent(String serviceID, String eventType, String eventBody) throws Throwable {
        RsfUpdater rsfUpdater = rsfContext.getUpdater();
        EventProcess process = EventProcessMapping.findEventProcess(eventType);
        if (process == null) {
            throw new RsfCenterException(eventType + " eventType is undefined.");
        }
        if (this.rsfContext.getServiceInfo(serviceID) == null) {
            throw new RsfCenterException(serviceID + " service is undefined.");
        }
        //
        return process.processEvent(rsfUpdater, serviceID, eventBody);
    }
}
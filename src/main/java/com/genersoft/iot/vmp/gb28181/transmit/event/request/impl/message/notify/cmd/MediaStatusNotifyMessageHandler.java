package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.notify.cmd;

import com.genersoft.iot.vmp.common.InviteInfo;
import com.genersoft.iot.vmp.common.InviteSessionType;
import com.genersoft.iot.vmp.conf.exception.SsrcTransactionNotFoundException;
import com.genersoft.iot.vmp.gb28181.bean.*;
import com.genersoft.iot.vmp.gb28181.service.IDeviceChannelService;
import com.genersoft.iot.vmp.gb28181.service.IInviteStreamService;
import com.genersoft.iot.vmp.gb28181.service.IPlatformService;
import com.genersoft.iot.vmp.gb28181.session.SipInviteSessionManager;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.impl.SIPCommander;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.impl.SIPCommanderFroPlatform;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.SIPRequestProcessorParent;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.notify.NotifyMessageHandler;
import com.genersoft.iot.vmp.media.event.hook.Hook;
import com.genersoft.iot.vmp.media.event.hook.HookSubscribe;
import com.genersoft.iot.vmp.media.event.hook.HookType;
import com.genersoft.iot.vmp.service.ISendRtpServerService;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.SipException;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Response;
import java.text.ParseException;

import static com.genersoft.iot.vmp.gb28181.utils.XmlUtil.getText;

/**
 * 媒体通知
 */
@Slf4j
@Component
public class MediaStatusNotifyMessageHandler extends SIPRequestProcessorParent implements InitializingBean, IMessageHandler {

    private final String cmdType = "MediaStatus";

    @Autowired
    private NotifyMessageHandler notifyMessageHandler;

    @Autowired
    private SIPCommander cmder;

    @Autowired
    private SIPCommanderFroPlatform sipCommanderFroPlatform;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private IPlatformService platformService;

    @Autowired
    private HookSubscribe subscribe;

    @Autowired
    private IInviteStreamService inviteStreamService;

    @Autowired
    private SipInviteSessionManager sessionManager;

    @Autowired
    private IDeviceChannelService deviceChannelService;

    @Autowired
    private ISendRtpServerService sendRtpServerService;

    @Override
    public void afterPropertiesSet() throws Exception {
        notifyMessageHandler.addHandler(cmdType, this);
    }

    @Override
    public void handForDevice(RequestEvent evt, Device device, Element rootElement) {

        // 回复200 OK
        try {
             responseAck((SIPRequest) evt.getRequest(), Response.OK);
        } catch (SipException | InvalidArgumentException | ParseException e) {
            log.error("[命令发送失败] 国标级联 录像流推送完毕，回复200OK: {}", e.getMessage());
        }
        CallIdHeader callIdHeader = (CallIdHeader)evt.getRequest().getHeader(CallIdHeader.NAME);
        String NotifyType =getText(rootElement, "NotifyType");
        if ("121".equals(NotifyType)){
            log.info("[录像流]推送完毕，收到关流通知");

            SsrcTransaction ssrcTransaction = sessionManager.getSsrcTransactionByCallId(callIdHeader.getCallId());
            if (ssrcTransaction != null) {
                log.info("[录像流]推送完毕，关流通知， device: {}, channelId: {}", ssrcTransaction.getDeviceId(), ssrcTransaction.getChannelId());
                InviteInfo inviteInfo = inviteStreamService.getInviteInfo(InviteSessionType.DOWNLOAD, ssrcTransaction.getChannelId(), ssrcTransaction.getStream());
                if (inviteInfo.getStreamInfo() != null) {
                    inviteInfo.getStreamInfo().setProgress(1);
                    inviteStreamService.updateInviteInfo(inviteInfo);
                }
                DeviceChannel deviceChannel = deviceChannelService.getOneById(ssrcTransaction.getChannelId());
                if (deviceChannel == null) {
                    log.warn("[级联消息发送]：未找到国标设备通道: {}", ssrcTransaction.getChannelId());
                    return;
                }
                try {
                    cmder.streamByeCmd(device, deviceChannel.getDeviceId(), null, callIdHeader.getCallId());
                } catch (InvalidArgumentException | ParseException  | SipException | SsrcTransactionNotFoundException e) {
                    log.error("[录像流]推送完毕，收到关流通知， 发送BYE失败 {}", e.getMessage());
                }
                // 去除监听流注销自动停止下载的监听
                Hook hook = Hook.getInstance(HookType.on_media_arrival, "rtp", ssrcTransaction.getStream(), ssrcTransaction.getMediaServerId());
                subscribe.removeSubscribe(hook);
                // 如果级联播放，需要给上级发送此通知 TODO 多个上级同时观看一个下级 可能存在停错的问题，需要将点播CallId进行上下级绑定
                SendRtpInfo sendRtpItem =  sendRtpServerService.queryByChannelId(ssrcTransaction.getChannelId(), ssrcTransaction.getPlatformId());
                if (sendRtpItem != null) {
                    Platform parentPlatform = platformService.queryPlatformByServerGBId(sendRtpItem.getTargetId());
                    if (parentPlatform == null) {
                        log.warn("[级联消息发送]：发送MediaStatus发现上级平台{}不存在", sendRtpItem.getTargetId());
                        return;
                    }
                    try {
                        sipCommanderFroPlatform.sendMediaStatusNotify(parentPlatform, sendRtpItem);
                    } catch (SipException | InvalidArgumentException | ParseException e) {
                        log.error("[命令发送失败] 国标级联 录像播放完毕: {}", e.getMessage());
                    }
                }
            }else {
                log.info("[录像流]推送完毕，关流通知， 但是未找到对应的下载信息");
            }
        }
    }

    @Override
    public void handForPlatform(RequestEvent evt, Platform parentPlatform, Element element) {

    }
}

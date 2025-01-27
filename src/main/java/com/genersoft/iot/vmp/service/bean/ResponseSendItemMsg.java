package com.genersoft.iot.vmp.service.bean;

import com.genersoft.iot.vmp.gb28181.bean.SendRtpInfo;
import com.genersoft.iot.vmp.media.bean.MediaServer;

/**
 * redis消息：下级回复推送信息
 * @author lin
 */
public class ResponseSendItemMsg {

    private SendRtpInfo sendRtpItem;

    private MediaServer mediaServerItem;

    public SendRtpInfo getSendRtpItem() {
        return sendRtpItem;
    }

    public void setSendRtpItem(SendRtpInfo sendRtpItem) {
        this.sendRtpItem = sendRtpItem;
    }

    public MediaServer getMediaServerItem() {
        return mediaServerItem;
    }

    public void setMediaServerItem(MediaServer mediaServerItem) {
        this.mediaServerItem = mediaServerItem;
    }
}

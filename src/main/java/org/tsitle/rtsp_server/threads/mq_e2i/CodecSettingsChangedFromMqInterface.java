package org.tsitle.rtsp_server.threads.mq_e2i;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.mq.common.mqdata.MqCodecSettings;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdStreamSource;

public interface CodecSettingsChangedFromMqInterface {

	void onCodecSettingsChangedFromMq(@NonNull RtspProtoIdStreamSource idStreamSource, @NonNull MqCodecSettings codecSettings);

}

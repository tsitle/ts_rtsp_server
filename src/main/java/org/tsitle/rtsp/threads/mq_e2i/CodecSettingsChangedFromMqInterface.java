package org.tsitle.rtsp.threads.mq_e2i;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.mq.mqdata.MqCodecSettings;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;

public interface CodecSettingsChangedFromMqInterface {

	void onCodecSettingsChangedFromMq(@NonNull RtspProtoIdStreamSource idStreamSource, @NonNull MqCodecSettings codecSettings);

}

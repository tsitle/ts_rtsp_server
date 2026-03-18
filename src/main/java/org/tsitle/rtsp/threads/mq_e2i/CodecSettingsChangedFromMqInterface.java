package org.tsitle.rtsp.threads.mq_e2i;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.mq.mqdata.MqCodecSettings;

public interface CodecSettingsChangedFromMqInterface {

	void onCodecSettingsChangedFromMq(int streamSourceId, @NonNull MqCodecSettings codecSettings);

}

package org.tsitle.rtsp_server.threads.mq_e2i;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_rtsp_mq.common.mqdata.MqCodecSettings;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

public interface CodecSettingsChangedFromMqInterface {

	void onCodecSettingsChangedFromMq(@NonNull RtspProtoIdEsSource idEsSource, @NonNull MqCodecSettings codecSettings);

	void onCodecMetadataFromMq(@NonNull RtspProtoIdEsSource idEsSource, @NonNull String metadataHex);

}

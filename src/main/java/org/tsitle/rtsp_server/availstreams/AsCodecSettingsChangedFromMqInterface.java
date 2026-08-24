package org.tsitle.rtsp_server.availstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_mq.common.mqdata.MqCodecSettings;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

public interface AsCodecSettingsChangedFromMqInterface {

	void onCodecSettingsChangedFromMq(@NonNull RtspProtoIdEsSource idEsSource, @NonNull MqCodecSettings codecSettings);

	void onCodecMetadataFromMq(@NonNull RtspProtoIdEsSource idEsSource, @NonNull String metadataHex);

}

package org.tsitle.rtsp_server.availstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_mq.common.mqdata.MqCodecSettings;
import io.github.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

public interface AsCodecSettingsChangedFromDmxRtspInterface {

	void onCodecSettingsChangedFromDmxRtsp(@NonNull RtspProtoIdEsSource idEsSource, @NonNull MqCodecSettings codecSettings);

	void onCodecMetadataFromDmxRtsp(@NonNull RtspProtoIdEsSource idEsSource, @NonNull String metadataHex);

}

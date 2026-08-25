package org.tsitle.rtsp_server.availstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_mq.common.mqdata.MqCodecSettings;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;

public interface AsCodecSettingsChangedFromDmxJbInterface {

	void onCodecSettingsChangedFromDmxJb(@NonNull RtspProtoIdEsSource idEsSource, @NonNull MqCodecSettings codecSettings);

	void onCodecMetadataFromDmxJb(@NonNull RtspProtoIdEsSource idEsSource, @NonNull String metadataHex);

	void onFileTagsChangedFromDmxJb(@NonNull RtspProtoIdInputSource idInputSource, @NonNull String fileTags);

}

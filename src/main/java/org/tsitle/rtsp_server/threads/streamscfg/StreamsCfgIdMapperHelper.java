package org.tsitle.rtsp_server.threads.streamscfg;

import org.jspecify.annotations.NonNull;
import io.github.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import io.github.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import io.github.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;

final class StreamsCfgIdMapperHelper {

	private static final int ES_ID_HASH_LEN = 8;

	private StreamsCfgIdMapperHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull RtspProtoIdInputSource computeInternalIsId(@NonNull String externalId) {
		return RtspProtoIdInputSource.of(externalId);
	}

	static @NonNull RtspProtoIdEsSource computeInternalEsId(@NonNull String externalId) {
		return RtspProtoIdEsSource.of(
				"E" + HashMd5Helper.hashOfString(
						"internal_es_#" + externalId + "#",
						false
				).substring(0, ES_ID_HASH_LEN)
		);
	}

}

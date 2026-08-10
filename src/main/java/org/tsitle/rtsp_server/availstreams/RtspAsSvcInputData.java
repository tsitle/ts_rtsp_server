package org.tsitle.rtsp_server.availstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamsSsNg;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamsStreamNg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RtspAsSvcInputData {

	public final @NonNull Set<@NonNull RtspProtoIdInputSource> isIdsAdded = new HashSet<>();
	public final @NonNull Set<@NonNull RtspProtoIdInputSource> isIdsModified = new HashSet<>();
	public final @NonNull Set<@NonNull RtspProtoIdInputSource> isIdsDeleted = new HashSet<>();

	public final @NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspSrvConfigStreamsStreamNg> mapIsIdToCfgObj = new HashMap<>();
	public final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspSrvConfigStreamsSsNg> mapEsIdToCfgObj = new HashMap<>();
	public final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> mapEsIdToEsei = new HashMap<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		isIdsAdded.clear();
		isIdsModified.clear();
		isIdsDeleted.clear();

		mapIsIdToCfgObj.clear();
		mapEsIdToCfgObj.clear();
		mapEsIdToEsei.clear();
	}

}
